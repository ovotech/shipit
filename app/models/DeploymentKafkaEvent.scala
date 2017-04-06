package models

case class DeploymentKafkaEvent(
    team: String,
    service: String,
    buildId: String,
    links: Option[List[Link]],
    note: Option[String],
    result: Option[DeploymentResult]
)
