package deployments
import cats.arrow.FunctionK
import cats.data.EitherT
import play.api.mvc.QueryStringBindable
import play.api.mvc.QueryStringBindable.{bindableOption, bindableString}
import cats.syntax.apply._

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
    links: List[Link],
    note: Option[String]
)

case class SearchTerms(
    team: Option[String],
    service: Option[String],
    buildId: Option[String]
)

object SearchTerms {

  val empty: SearchTerms =
    SearchTerms(None, None, None)

  private def eitherT[A](a: Some[Either[String, Option[A]]]): EitherT[Option, String, Option[A]] =
    EitherT(a: Option[Either[String, Option[A]]])

  private def eitherTStr(a: Some[Either[String, Option[String]]]): EitherT[Option, String, Option[String]] =
    eitherT[String](a).map(_.filter(_.nonEmpty))

  implicit val queryBindable: QueryStringBindable[SearchTerms] =
    new QueryStringBindable[SearchTerms] {
      def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, SearchTerms]] =
        (
          eitherTStr(bindableOption(bindableString).bind("team", params)),
          eitherTStr(bindableOption(bindableString).bind("service", params)),
          eitherTStr(bindableOption(bindableString).bind("buildId", params))
        ).mapN(SearchTerms.apply).value

      def unbind(key: String, value: SearchTerms): String =
        List(
          bindableOption(bindableString).unbind("team", value.team),
          bindableOption(bindableString).unbind("service", value.team),
          bindableOption(bindableString).unbind("buildId", value.team)
        ).filter(_.trim.nonEmpty).mkString("&")
    }
}
