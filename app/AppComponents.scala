import java.time.{LocalDateTime, ZoneOffset}

import com.amazonaws.auth._
import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.google.common.base.Supplier
import controllers.{ApiKeysController, AuthController, DeploymentsController, MainController}
import kafka.Graph
import com.gu.googleauth.GoogleAuthConfig
import io.searchbox.client.{JestClient, JestClientFactory}
import io.searchbox.client.config.HttpClientConfig
import org.apache.http.impl.client.HttpClientBuilder
import org.joda.time.DateTimeZone
import play.api.ApplicationLoader.Context
import play.api.routing.Router
import play.api.{BuiltInComponentsFromContext, Logger}
import play.api.libs.ws.ahc.AhcWSComponents
import router.Routes
import vc.inreach.aws.request.{AWSSigner, AWSSigningRequestInterceptor}

class AppComponents(context: Context)
    extends BuiltInComponentsFromContext(context)
    with AhcWSComponents {

  implicit val actorSys = actorSystem

  def mandatoryConfig(key: String): String = configuration.getString(key).getOrElse(sys.error(s"Missing config key: $key"))

  val googleAuthConfig = GoogleAuthConfig(
    clientId = mandatoryConfig("google.clientId"),
    clientSecret = mandatoryConfig("google.clientSecret"),
    redirectUrl = mandatoryConfig("google.redirectUrl"),
    domain = "ovoenergy.com"
  )

  val jestClient: JestClient = {
    val region = mandatoryConfig("aws.region")
    val service = "es"
    val url = mandatoryConfig("aws.es.endpointUrl")
    val awsCredentialsProvider = new AWSCredentialsProviderChain(
      new ContainerCredentialsProvider(),
      new ProfileCredentialsProvider()
    )
    val dateSupplier = new Supplier[LocalDateTime] { def get(): LocalDateTime = LocalDateTime.now(ZoneOffset.UTC) }
    val awsSigner = new AWSSigner(awsCredentialsProvider, region, service, dateSupplier)
    val factory = new JestClientFactory() {
      override protected def configureHttpClient(builder: HttpClientBuilder): HttpClientBuilder = {
        builder.addInterceptorLast(new AWSSigningRequestInterceptor(awsSigner))
        builder
      }
    }
    factory.setHttpClientConfig(new HttpClientConfig.Builder(url)
      .multiThreaded(true)
      .build())
    factory.getObject
  }

  val mainController = new MainController(googleAuthConfig, wsClient)
  val apiKeysController = new ApiKeysController(googleAuthConfig, wsClient, jestClient)
  val deploymentsController = new DeploymentsController(googleAuthConfig, wsClient, jestClient)
  val authController = new AuthController(googleAuthConfig, wsClient)

  lazy val router: Router = new Routes(
    httpErrorHandler,
    mainController,
    deploymentsController,
    apiKeysController,
    authController
  )

  def startKafkaConsumer(): Unit = {
    Logger.info("Starting Kafka consumer")

    val kafkaHosts = mandatoryConfig("kafka.hosts")
    val kafkaGroupId = mandatoryConfig("kafka.group.id")

    val kafkaGraph = Graph.build(kafkaHosts, kafkaGroupId, mandatoryConfig("kafka.topics.deployments"), ???) // TODO deserializer
    kafkaGraph.runForeach(_ => ()) // TODO write to ES
  }

}
