package slack

import java.time.OffsetDateTime

import io.circe.Json
import models.{Deployment, DeploymentResult, Link}
import org.scalatest._
import io.circe.parser._

class SlackSpec extends FlatSpec with Matchers with OptionValues {

  it should "build a payload for a deployment with a note" in {
    val deployment = Deployment(
      id = "foo",
      team = "Team America",
      service = "my lovely service",
      buildId = "123",
      timestamp = OffsetDateTime.now,
      links = Seq(
        Link("PR", "https://github.com/pr"),
        Link("CI", "https://circleci.com/build/123")
      ),
      note = Some("this build was awesome"),
      result = DeploymentResult.Succeeded
    )
    val payload = Slack.buildPayload(deployment)
    val json = parse(payload).right.get

    val expectedJson = parse(
      """
        |{
        | "attachments": [
        |   {
        |     "fallback": "Service [Team America/my lovely service] was deployed successfully. <https://shipit.ovotech.org.uk/deployments|See recent deployments>",
        |     "pretext": "Service [Team America/my lovely service] was deployed successfully. <https://shipit.ovotech.org.uk/deployments|See recent deployments>",
        |     "color": "#00D000",
        |     "fields": [
        |       {
        |         "title": "Build ID",
        |         "value": "123",
        |         "short": true
        |       },
        |       {
        |         "title": "Links",
        |         "value": "<https://github.com/pr|PR>, <https://circleci.com/build/123|CI>",
        |         "short": true
        |       },
        |       {
        |         "title": "Notes",
        |         "value": "this build was awesome",
        |         "short": true
        |       }
        |     ]
        |   }
        | ]
        |}
      """.stripMargin
    ).right.get

    assert(json == expectedJson)
  }

}
