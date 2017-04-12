import java.time.{LocalDateTime, OffsetDateTime, ZoneOffset}

import akka.stream.scaladsl.Sink
import com.amazonaws.auth._
import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.google.common.base.Supplier
import controllers._
import kafka.{Graph, Serialization}
import com.gu.googleauth.GoogleAuthConfig
import io.searchbox.client.{JestClient, JestClientFactory}
import io.searchbox.client.config.HttpClientConfig
import jira.JIRA
import logic.Deployments
import models.DeploymentResult.Succeeded
import org.apache.http.impl.client.HttpClientBuilder
import play.api.ApplicationLoader.Context
import play.api.routing.Router
import play.api.{BuiltInComponentsFromContext, Logger}
import play.api.libs.ws.ahc.AhcWSComponents
import play.api.mvc.EssentialFilter
import play.filters.csrf.CSRFComponents
import router.Routes
import slack.Slack
import vc.inreach.aws.request.{AWSSigner, AWSSigningRequestInterceptor}

class AppComponents(context: Context)
    extends BuiltInComponentsFromContext(context)
    with AhcWSComponents
    with CSRFComponents {

  implicit val actorSys = actorSystem

  def mandatoryConfig(key: String): String =
    configuration.getString(key).getOrElse(sys.error(s"Missing config key: $key"))

  val googleAuthConfig = GoogleAuthConfig(
    clientId = mandatoryConfig("google.clientId"),
    clientSecret = mandatoryConfig("google.clientSecret"),
    redirectUrl = mandatoryConfig("google.redirectUrl"),
    domain = "ovoenergy.com"
  )

  val jestClient: JestClient = {
    val region  = mandatoryConfig("aws.region")
    val service = "es"
    val url     = mandatoryConfig("aws.es.endpointUrl")
    val awsCredentialsProvider = new AWSCredentialsProviderChain(
      new ContainerCredentialsProvider(),
      new ProfileCredentialsProvider()
    )
    val dateSupplier = new Supplier[LocalDateTime] { def get(): LocalDateTime = LocalDateTime.now(ZoneOffset.UTC) }
    val awsSigner    = new AWSSigner(awsCredentialsProvider, region, service, dateSupplier)
    val factory = new JestClientFactory() {
      override protected def configureHttpClient(builder: HttpClientBuilder): HttpClientBuilder = {
        builder.addInterceptorLast(new AWSSigningRequestInterceptor(awsSigner))
        builder
      }
    }
    factory.setHttpClientConfig(
      new HttpClientConfig.Builder(url)
        .multiThreaded(true)
        .build())
    factory.getObject
  }

  val slackWebhookUrl = mandatoryConfig("slack.webhookUrl")
  val slackCtx        = Slack.Context(wsClient, slackWebhookUrl)

  val jiraCtx = JIRA.Context(
    wsClient,
    mandatoryConfig("jira.browseTicketsUrl"),
    mandatoryConfig("jira.createIssueApiUrl"),
    mandatoryConfig("jira.username"),
    mandatoryConfig("jira.password")
  )
  val deploymentsCtx = Deployments.Context(jestClient, slackCtx, jiraCtx)

  val mainController        = new MainController(googleAuthConfig, wsClient)
  val apiKeysController     = new ApiKeysController(googleAuthConfig, wsClient, jestClient)
  val deploymentsController = new DeploymentsController(googleAuthConfig, wsClient, deploymentsCtx)
  val authController        = new AuthController(googleAuthConfig, wsClient)
  val assets                = new Assets(httpErrorHandler)

  lazy val router: Router = new Routes(
    httpErrorHandler,
    mainController,
    deploymentsController,
    apiKeysController,
    authController,
    assets
  )

  override lazy val httpFilters: Seq[EssentialFilter] = Seq(csrfFilter)

  val kafkaHosts   = mandatoryConfig("kafka.hosts")
  val kafkaGroupId = mandatoryConfig("kafka.group.id")

  val kafkaGraph = Graph.build(kafkaHosts,
                               kafkaGroupId,
                               mandatoryConfig("kafka.topics.deployments"),
                               Serialization.deploymentKafkaEventDeserializer) { event =>
    Deployments.createDeploymentFromKafkaEvent(event).run(deploymentsCtx)
  }

  def startKafkaConsumer(): Unit = {
    Logger.info("Starting Kafka consumer")

    kafkaGraph.runWith(Sink.ignore)
  }

}
