#!/usr/bin/env bash
<<COMMENT
Deployment script that updates the ECS service.
Creates a new revision of the ECS task definition and then updates the service to use that revision.
Based on https://github.com/circleci/go-ecs-ecr/blob/master/deploy.sh
Assumptions:
- The task definition contains only one container
- The Docker image has already been published, tagged with the git sha1
- The service already exists
The script builds container definitions by substituting placeholders
in a template. The template can include the following placeholders:
- @@AWS_ACCOUNT_ID
- @@VERSION
Required environment variables:
- AWS creds
COMMENT

set -e

template_path=aws/container-definition.json
timeout=300 # we expect deployment to take less than 5 minutes
cluster_name=shipit
service_name=shipit

git_sha1="${CIRCLE_SHA1:-$(git rev-parse HEAD)}"
aws_account_id="$(aws sts get-caller-identity --query Account --output text)"

export AWS_DEFAULT_REGION="eu-west-1"

basedir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/../.."
template=$basedir/$template_path

echo "Deploying version $git_sha1 to ECS"

JQ="jq --raw-output --exit-status"

make_container_definitions() {
  container_definitions=$(
    sed -e "
      s/@@VERSION/$git_sha1/g;
      s/@@AWS_ACCOUNT_ID/$aws_account_id/g;
    " $template
  )
}

get_role_arns() {
  currentTaskDefinitionArn=$(aws ecs describe-services --cluster "$cluster_name" --services "$service_name" \
    --query 'services[0].taskDefinition' --output text)
  taskRoleArn=$(aws ecs describe-task-definition --task-definition "$currentTaskDefinitionArn" \
    --query 'taskDefinition.taskRoleArn' --output text)
  executionRoleArn=$(aws ecs describe-task-definition --task-definition "$currentTaskDefinitionArn" \
    --query 'taskDefinition.executionRoleArn' --output text)
}

register_task_definition() {
  cmd_output=$(aws ecs register-task-definition \
    --family "$service_name" \
    --container-definitions "$container_definitions" \
    --network-mode awsvpc \
    --memory 512 \
    --cpu 256 \
    --requires-compatibilities FARGATE \
    --task-role-arn "$taskRoleArn" \
    --execution-role-arn "$executionRoleArn")
  if revisionArn=$(echo $cmd_output | $JQ '.taskDefinition.taskDefinitionArn'); then
    echo "Revision: $revisionArn"
  else
    echo "Failed to register task definition"
    return 1
  fi
}

update_service() {
  if [[ $(aws ecs update-service --cluster "$cluster_name" --service "$service_name" --task-definition "$revisionArn" | \
    $JQ '.service.taskDefinition') != "$revisionArn" ]]
  then
    echo "Error updating service."
    return 1
  fi
}

await_stabilization() {
  # wait for older revisions to disappear
  interval=5
  attempts=$(( $timeout / $interval ))
  for attempt in $(seq 1 $attempts); do
    echo "Attempt $attempt of $attempts"
    if stale=$(aws ecs describe-services --cluster "$cluster_name" --services "$service_name" | \
      $JQ ".services[0].deployments | .[] | select(.taskDefinition != \"$revisionArn\") | .taskDefinition")
    then
      echo "Waiting for stale deployments:"
      echo "$stale"
      sleep $interval
    else
      echo "Deployed!"
      return 0
    fi
  done
  echo "Service update took too long."
  return 1
}

deploy_to_ecs() {
  get_role_arns
  make_container_definitions
  register_task_definition
  update_service
  await_stabilization
}

deploy_to_ecs
