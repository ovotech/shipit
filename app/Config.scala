import java.net.{HttpURLConnection, URL}

import com.amazonaws.util.EC2MetadataUtils
import cats.syntax.either._
import cats.instances.parallel._
import cats.syntax.parallel._
import ciris._
import ciris.cats._
import ciris.aws.ssm._

import scala.util.Try
import scala.util.control.NonFatal

case class ESConfig(
    region: String = "eu-west-1",
    endpointUrl: String
)

object ESConfig {

  def load(): Either[ConfigErrors, ESConfig] = {
    loadConfig(
      param[String]("shipit.es.endpointUrl")
    )(endpointUrl => ESConfig.apply(endpointUrl = endpointUrl))
  }

}

case class SlackConfig(
    webhookUrl: Secret[String]
)

object SlackConfig {

  def load(): Either[ConfigErrors, SlackConfig] = {
    loadConfig(param[Secret[String]]("shipit.slack.webhookUrl"))(SlackConfig.apply)
  }

}

case class JiraConfig(
    username: String,
    password: Secret[String],
    browseTicketsUrl: String = "https://ovotech.atlassian.net/browse/",
    issueApiUrl: String = "https://ovotech.atlassian.net/rest/api/2/issue"
)

object JiraConfig {

  def load(): Either[ConfigErrors, JiraConfig] = {
    loadConfig(
      param[String]("shipit.jira.username"),
      param[Secret[String]]("shipit.jira.password")
    )((username, password) => JiraConfig(username, password))
  }

}

case class GoogleConfig(
    clientId: String,
    clientSecret: Secret[String],
    redirectUrl: String
)

object GoogleConfig {

  def load(runningInAWS: Boolean): Either[ConfigErrors, GoogleConfig] = {
    if (runningInAWS) {
      loadConfig(
        param[String]("shipit.google.clientId"),
        param[Secret[String]]("shipit.google.clientSecret")
      )(GoogleConfig.apply(_, _, redirectUrl = "https://shipit-test.ovotech.org.uk/oauth2callback"))
    } else {
      // These credentials will only work when running the app on localhost:9000, i.e. on a developer machine
      GoogleConfig(
        clientId = "925213464964-ppl7hcrpsflavpf8rtlp2mnjm5p0pi0t.apps.googleusercontent.com",
        clientSecret = Secret("I9yZu57pLe4VklD7mJuQZyel"),
        redirectUrl = "http://localhost:9000/oauth2callback"
      ).asRight
    }
  }

}

case class AdminConfig(
    adminEmailAddresses: List[String]
)

object AdminConfig {

  def load(): Either[ConfigErrors, AdminConfig] = {
    AdminConfig(adminEmailAddresses = List("chris.birchall@ovoenergy.com")).asRight
  }

}

sealed trait LoggingConfig

case class GraylogEnabledLoggingConfig(
    graylogHostname: String,
    myHostname: String
) extends LoggingConfig
case object GraylogDisabledLoggingConfig extends LoggingConfig

object LoggingConfig {

  def load(runningInAWS: Boolean): Either[ConfigErrors, LoggingConfig] = {
    if (runningInAWS) {
      loadConfig(
        param[String]("shipit.graylog.hostname"),
        env[String]("HOSTNAME")
      )(GraylogEnabledLoggingConfig.apply)
    } else {
      GraylogDisabledLoggingConfig.asRight
    }
  }

}

case class Config(
    es: ESConfig,
    slack: SlackConfig,
    jira: JiraConfig,
    google: GoogleConfig,
    admin: AdminConfig,
    logging: LoggingConfig
)

object Config {

  def unsafeLoad(): Config = load() match {
    case Left(errors) => throw errors.toException // KABOOM!
    case Right(c)     => c
  }

  private val runningInAWS: Boolean = {
    // TODO there doesn't seem to be a nice equivalent to EC2MetadataUtils for retrieving task metadata
    val metadataUrl = new URL("http://169.254.170.2/v2/metadata")
    try {
      val connection = metadataUrl.openConnection().asInstanceOf[HttpURLConnection]
      try {
        connection.setRequestMethod("GET")
        connection.connect()
        connection.getResponseCode == 200
      } finally {
        connection.disconnect()
      }
    } catch {
      case NonFatal(e) => false
    }
  }

  def load(): Either[ConfigErrors, Config] = {
    println(s"Am I running in AWS? $runningInAWS")
    (
      ESConfig.load(),
      SlackConfig.load(),
      JiraConfig.load(),
      GoogleConfig.load(runningInAWS),
      AdminConfig.load(),
      LoggingConfig.load(runningInAWS)
    ).parMapN(Config.apply)
  }

}
