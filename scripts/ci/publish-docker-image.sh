#!/bin/bash
#
# Tags the Docker image with the git sha1 and uploads it to the ECR repo.
# Assumes the image has already been built with tag "1.0".

set -e

git_sha1="${CIRCLE_SHA1:-$(git rev-parse HEAD)}"
aws_account_id="$(aws sts get-caller-identity --query Account --output text)"

eval "$(aws ecr get-login --no-include-email --region eu-west-1)"

docker tag shipit:1.0 "${aws_account_id}.dkr.ecr.eu-west-1.amazonaws.com/shipit:$git_sha1"

docker push "${aws_account_id}.dkr.ecr.eu-west-1.amazonaws.com/shipit:$git_sha1"

