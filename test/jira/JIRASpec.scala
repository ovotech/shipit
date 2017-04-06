package jira

import java.time.OffsetDateTime

import io.circe.parser._
import models.{Deployment, DeploymentResult, Link}
import org.scalatest._
import play.api.libs.json.Json

class JIRASpec extends FlatSpec with Matchers with OptionValues {

  it should "build a payload for a deployment with a note" in {
    val deployment = Deployment(
      id = "foo",
      team = "Team America",
      service = "my lovely service",
      jiraComponent = Some("My Component"),
      buildId = "123",
      timestamp = OffsetDateTime.now,
      links = Seq(
        Link("PR", "https://github.com/pr"),
        Link("CI", "https://circleci.com/build/123")
      ),
      note = Some("this build was awesome"),
      result = DeploymentResult.Succeeded
    )
    val payload = JIRA.buildPayload(deployment, "My Component")
    val json    = parse(Json.stringify(payload)).right.get

    val expectedJson = parse(
      """
        |{
        |  "fields": {
        |    "project": {
        |      "key": "REL"
        |    },
        |    "summary": "Service 'Team America/my lovely service' was deployed successfully.",
        |    "description": "Service 'Team America/my lovely service' was deployed successfully.\n\nLinks:\n* [PR|https://github.com/pr]\n* [CI|https://circleci.com/build/123]\n\nNotes:\nthis build was awesome\n\nThis ticket was created by :shipit: - [See recent deployments|https://shipit.ovotech.org.uk/deployments]",
        |    "issuetype": {
        |      "name": "Standard Change"
        |    },
        |    "components": [
        |      {
        |        "name": "My Component"
        |      }
        |    ]
        |  }
        |}
      """.stripMargin
    ).right.get

    assert(json == expectedJson)
  }

}
