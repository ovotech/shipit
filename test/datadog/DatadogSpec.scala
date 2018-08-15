package datadog

import java.time.OffsetDateTime

import io.circe.parser._
import models.{Deployment, Link}
import org.scalatest._
import play.api.libs.json.Json

class DatadogSpec extends FlatSpec with Matchers with OptionValues {

  it should "build a payload for a Datadog event" in {
    val deployment = Deployment(
      team = "Team America",
      service = "my lovely service",
      jiraComponent = None,
      buildId = "123",
      timestamp = OffsetDateTime.now,
      links = Seq(
        Link("PR", "https://github.com/pr"),
        Link("CI", "https://circleci.com/build/123")
      ),
      note = Some("this build was awesome")
    )
    val payload = Datadog.buildPayload(deployment)
    val json    = parse(Json.stringify(payload)).right.get

    val expectedJson = parse(
      """
        |{
        | "title": "Deployment: Team America/my lovely service",
        | "text": "Service [Team America/my lovely service] was deployed successfully.",
        | "tags": [
        |   "shipit:deployment",
        |   "team:Team America",
        |   "service:my lovely service"
        |  ]
        |}
      """.stripMargin
    ).right.get

    assert(json == expectedJson)
  }

}
