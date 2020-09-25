import java.net.{HttpURLConnection, URL}

import cats.effect.{Blocker, ContextShift, IO}
import cats.implicits._
import ciris._
import ciris.aws.ssm._

import scala.concurrent.ExecutionContext
import scala.util.control.NonFatal

case class ESConfig(
    region: String = "eu-west-1",
    endpointUrl: String
)

object ESConfig {

  def load(param: Param): ConfigValue[ESConfig] = {
    param("shipit.es.oldEndpointUrl").map(endpointUrl => ESConfig.apply(endpointUrl = endpointUrl))
  }

}

case class SlackConfig(
    webhookUrl: Secret[String]
)

object SlackConfig {

  def load(param: Param): ConfigValue[SlackConfig] = {
    param("shipit.slack.webhookUrl").secret.map(SlackConfig.apply)
  }

}

case class DatadogConfig(
    apiKey: Secret[String]
)

object DatadogConfig {

  def load(param: Param): ConfigValue[DatadogConfig] = {
    param("shipit.datadog.apiKey").secret.map(DatadogConfig.apply)
  }

}

case class GoogleConfig(
    clientId: String,
    clientSecret: Secret[String],
    redirectUrl: String
)

object GoogleConfig {

  def load(param: Param, runningInAWS: Boolean): ConfigValue[GoogleConfig] = {
    if (runningInAWS) {
      (
        param("shipit.google.clientId"),
        param("shipit.google.clientSecret").secret
      ).mapN(GoogleConfig.apply(_, _, redirectUrl = "https://shipit.ovotech.org.uk/oauth2callback"))
    } else {
      // These credentials will only work when running the app on localhost:9000, i.e. on a developer machine
      ConfigValue.default(
        GoogleConfig(
          clientId = "925213464964-ppl7hcrpsflavpf8rtlp2mnjm5p0pi0t.apps.googleusercontent.com",
          clientSecret = Secret("I9yZu57pLe4VklD7mJuQZyel"),
          redirectUrl = "http://localhost:9000/oauth2callback"
        )
      )
    }
  }

}

case class AdminConfig(
    adminEmailAddresses: List[String]
)

object AdminConfig {

  val load: ConfigValue[AdminConfig] =
    ConfigValue.default(
      AdminConfig(
        adminEmailAddresses = List(
          "rui.morais@ovoenergy.com",
          "tom.verran@ovoenergy.com"
        )
      )
    )

}

sealed trait LoggingConfig

case class GraylogEnabledLoggingConfig(
    graylogHostname: String,
    myHostname: String
) extends LoggingConfig
case object GraylogDisabledLoggingConfig extends LoggingConfig

object LoggingConfig {

  def load(param: Param, runningInAWS: Boolean): ConfigValue[LoggingConfig] = {
    if (runningInAWS) {
      (
        param("shipit.graylog.hostname"),
        env("HOSTNAME")
      ).mapN(GraylogEnabledLoggingConfig.apply)
    } else {
      ConfigValue.default(GraylogDisabledLoggingConfig)
    }
  }

}

case class PlayConfig(secretKey: Secret[String])

object PlayConfig {

  def load(param: Param): ConfigValue[PlayConfig] =
    param("shipit.play.secretKey").secret.map(PlayConfig.apply)

}

case class Config(
    es: ESConfig,
    slack: SlackConfig,
    datadog: DatadogConfig,
    google: GoogleConfig,
    admin: AdminConfig,
    logging: LoggingConfig,
    play: PlayConfig
)

object Config {

  implicit val contextShift: ContextShift[IO] = IO.contextShift(ExecutionContext.global)

  def unsafeLoad(): Config = load().unsafeRunSync()

  private val runningInAWS: Boolean = {
    // TODO there doesn't seem to be a nice equivalent to EC2MetadataUtils for retrieving task metadata
    val metadataUrl = new URL("http://169.254.170.2/v2/metadata")
    try {
      val connection = metadataUrl.openConnection().asInstanceOf[HttpURLConnection]
      try {
        connection.setRequestMethod("GET")
        connection.setConnectTimeout(2000)
        connection.setReadTimeout(2000)
        connection.connect()
        connection.getResponseCode == 200
      } finally {
        connection.disconnect()
      }
    } catch {
      case NonFatal(e) => false
    }
  }

  def load(): IO[Config] = {
    println(s"Am I running in AWS? $runningInAWS")
    Blocker[IO].flatMap(params[IO]).use { param =>
      val config =
        (
          ESConfig.load(param),
          SlackConfig.load(param),
          DatadogConfig.load(param),
          GoogleConfig.load(param, runningInAWS),
          AdminConfig.load,
          LoggingConfig.load(param, runningInAWS),
          PlayConfig.load(param)
        ).parMapN(Config.apply)

      config.load[IO]
    }
  }

}
