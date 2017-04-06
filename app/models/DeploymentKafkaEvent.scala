package models

case class DeploymentKafkaEvent(
    team: String,
    service: String,
    jiraComponent: Option[String],
    buildId: String,
    links: Option[List[Link]],
    note: Option[String],
    result: Option[DeploymentResult]
)
