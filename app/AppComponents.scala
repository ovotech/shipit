import java.time.{LocalDateTime, ZoneOffset}

import akka.actor.ActorSystem
import com.amazonaws.auth._
import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.google.common.base.Supplier
import controllers._
import com.gu.googleauth.{AntiForgeryChecker, AuthAction, GoogleAuthConfig, UserIdentity}
import com.gu.play.secretrotation.DualSecretTransition.InitialSecret
import datadog.Datadog
import io.searchbox.client.{JestClient, JestClientFactory}
import io.searchbox.client.config.HttpClientConfig
import logic.Deployments
import org.apache.http.impl.client.HttpClientBuilder
import play.api.ApplicationLoader.Context
import play.api.routing.Router
import play.api.{BuiltInComponentsFromContext, Configuration}
import play.api.libs.ws.ahc.AhcWSComponents
import play.api.mvc.{AnyContent, EssentialFilter}
import play.filters.HttpFiltersComponents
import play.filters.csrf.CSRFComponents
import router.Routes
import slack.Slack
import vc.inreach.aws.request.{AWSSigner, AWSSigningRequestInterceptor}

class AppComponents(context: Context, config: Config)
    extends BuiltInComponentsFromContext(context)
    with AhcWSComponents
    with CSRFComponents
    with HttpFiltersComponents
    with AssetsComponents {

  implicit val actorSys: ActorSystem = actorSystem

  override def configuration: Configuration =
    context.initialConfiguration ++ Configuration("play.http.secret.key" -> config.play.secretKey.value)

  val googleAuthConfig = GoogleAuthConfig(
    clientId = config.google.clientId,
    clientSecret = config.google.clientSecret.value,
    redirectUrl = config.google.redirectUrl,
    domain = "ovoenergy.com",
    antiForgeryChecker = AntiForgeryChecker(
      InitialSecret(httpConfiguration.secret.secret),
      AntiForgeryChecker.signatureAlgorithmFromPlay(httpConfiguration)
    )
  )

  val jestClient: JestClient = {
    val region  = config.es.region
    val service = "es"
    val url     = config.es.endpointUrl
    val awsCredentialsProvider = new AWSCredentialsProviderChain(
      new EC2ContainerCredentialsProviderWrapper(),
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

  val slackCtx = Slack.Context(wsClient, config.slack.webhookUrl.value)

  val datadogCtx = Datadog.Context(wsClient, config.datadog.apiKey.value)

  val isAdmin: UserIdentity => Boolean =
    (user: UserIdentity) => config.admin.adminEmailAddresses.contains(user.email)

  val deploymentsCtx = Deployments.Context(jestClient, slackCtx, datadogCtx, isAdmin)

  val authAction = new AuthAction[AnyContent](googleAuthConfig,
                                              routes.AuthController.login(),
                                              controllerComponents.parsers.default)(executionContext)

  val apiKeyAuth = new ApiKeyAuth(jestClient, defaultActionBuilder)

  val mainController = new MainController(controllerComponents, authAction, googleAuthConfig, wsClient)
  val apiKeysController =
    new ApiKeysController(controllerComponents, authAction, googleAuthConfig, wsClient, jestClient)
  val deploymentsController =
    new DeploymentsController(controllerComponents, authAction, apiKeyAuth, googleAuthConfig, wsClient, deploymentsCtx)
  val servicesController =
    new ServicesController(controllerComponents, authAction, googleAuthConfig, wsClient, jestClient)
  val authController = new AuthController(controllerComponents, googleAuthConfig, wsClient)

  lazy val router: Router = new Routes(
    httpErrorHandler,
    mainController,
    deploymentsController,
    servicesController,
    apiKeysController,
    authController,
    assets
  )

  override lazy val httpFilters: Seq[EssentialFilter] = Seq(csrfFilter)

}
