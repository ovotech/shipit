package elasticsearch

import java.time.{LocalDateTime, ZoneOffset}

import com.amazonaws.auth.{AWSCredentialsProviderChain, DefaultAWSCredentialsProviderChain}
import com.google.common.base.Supplier
import com.sksamuel.elastic4s.ElasticProperties
import com.sksamuel.elastic4s.http.JavaClient
import org.elasticsearch.client.RestClientBuilder.HttpClientConfigCallback
import vc.inreach.aws.request.{AWSSigner, AWSSigningRequestInterceptor}

/**
  * here be dragons
  */
object AWSClient {

  private val dateSupplier: Supplier[LocalDateTime] =
    () => LocalDateTime.now(ZoneOffset.UTC)

  private val credentials: AWSCredentialsProviderChain =
    new DefaultAWSCredentialsProviderChain()

  private val awsSigner: AWSSigner =
    new AWSSigner(credentials, "eu-west-1", "es", dateSupplier)

  private val configCallback: HttpClientConfigCallback =
    _.addInterceptorLast(new AWSSigningRequestInterceptor(awsSigner))

  def create(props: ElasticProperties): JavaClient =
    JavaClient(props, configCallback)
}
