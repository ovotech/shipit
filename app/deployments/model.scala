package deployments
import cats.data.EitherT
import cats.syntax.apply._
import play.api.mvc.QueryStringBindable
import play.api.mvc.QueryStringBindable.{bindableOption, bindableString}

import java.time.OffsetDateTime

case class Link(
    title: String,
    url: String
)

case class Deployment(
    team: String,
    service: String,
    buildId: String,
    timestamp: OffsetDateTime,
    environment: Environment,
    links: List[Link],
    note: Option[String]
)

case class SearchTerms(
    team: Option[String],
    service: Option[String],
    buildId: Option[String],
    environment: Option[Environment]
)

sealed abstract class Environment(val name: String)

object Environment {

  case object LoadTest extends Environment("loadtest")
  case object Nonprod  extends Environment("nonprod")
  case object Prod     extends Environment("prod")

  def fromString(str: String): Either[String, Environment] =
    str.toLowerCase match {
      case "prd" | "prod"                                     => Right(Prod)
      case "uat" | "nonprod"                                  => Right(Nonprod)
      case str if "load\\W?test".r.findFirstIn(str).isDefined => Right(LoadTest)
      case other                                              => Left(s"Unknown environment $other")
    }
}

object SearchTerms {

  val empty: SearchTerms =
    SearchTerms(None, None, None, None)

  private def eitherT[A](a: Some[Either[String, Option[A]]]): EitherT[Option, String, Option[A]] =
    EitherT(a: Option[Either[String, Option[A]]])

  private def eitherTStr(a: Some[Either[String, Option[String]]]): EitherT[Option, String, Option[String]] =
    eitherT[String](a).map(_.filter(_.nonEmpty))

  private val bindableEnv: QueryStringBindable[Environment] =
    new QueryStringBindable[Environment] {
      def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, Environment]] =
        params.get(key).flatMap(_.headOption).filter(_.nonEmpty).map(Environment.fromString)
      def unbind(key: String, value: Environment): String =
        value.name
    }

  implicit val queryBindable: QueryStringBindable[SearchTerms] =
    new QueryStringBindable[SearchTerms] {
      def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, SearchTerms]] =
        (
          eitherTStr(bindableOption(bindableString).bind("team", params)),
          eitherTStr(bindableOption(bindableString).bind("service", params)),
          eitherTStr(bindableOption(bindableString).bind("buildId", params)),
          eitherT(bindableOption(bindableEnv).bind("environment", params))
        ).mapN(SearchTerms.apply).value

      def unbind(key: String, value: SearchTerms): String =
        List(
          bindableOption(bindableString).unbind("team", value.team),
          bindableOption(bindableString).unbind("service", value.team),
          bindableOption(bindableString).unbind("buildId", value.team),
          bindableOption(bindableEnv).unbind("environment", value.environment)
        ).filter(_.trim.nonEmpty).mkString("&")
    }
}
