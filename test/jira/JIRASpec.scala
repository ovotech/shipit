package jira

import java.time.{Clock, Instant, OffsetDateTime, ZoneId}

import io.circe.parser._
import models.{Deployment, DeploymentResult, Link}
import org.scalatest._
import play.api.libs.json.Json

class JIRASpec extends FlatSpec with Matchers with OptionValues {

  it should "build a payload for a deployment with a note" in {
    val deployment = Deployment(
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
    val payload =
      JIRA.buildPayload(deployment,
                        "My Component",
                        OffsetDateTime.now(Clock.fixed(Instant.ofEpochMilli(1494326516000L), ZoneId.of("UTC"))))
    val json = parse(Json.stringify(payload)).right.get

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
        |    ],
        |    "assignee" : {
        |      "name" : "osp-service"
        |    },
        |    "customfield_10302" : "2017-05-09T10:41:56Z",
        |    "customfield_11201" : "2017-05-09T10:41:56Z",
        |    "customfield_10500" : "UAT / peer review",
        |    "customfield_10301" : "Deploy using build pipeline. See links for more details",
        |    "customfield_10300" : "Back out using build pipeline. See links for more details"
        |  }
        |}
      """.stripMargin
    ).right.get

    assert(json == expectedJson)
  }

}
