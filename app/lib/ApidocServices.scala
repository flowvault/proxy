package lib

import com.bryzek.apidoc.spec.v0.models.Service
import io.flow.lib.apidoc.json.validation.{JsonValidator, TypeLookup}
import java.net.URL
import javax.inject.{Inject, Singleton}
import play.api.Logger
import play.api.libs.json._

object ApidocServices {
  val Empty = ApidocServices(uris = Nil, services = Nil)
}

case class ApidocServices(
  uris: Seq[String],
  services: Seq[Service]
) {
  private[this] val typeLookups = services.map { TypeLookup(_) }
  private[this] val jsonValidators = services.map { JsonValidator(_) }

  def merge(other: ApidocServices) = {
    ApidocServices(
      uris = uris ++ other.uris,
      services = services ++ other.services
    )
  }

  def typeForPath(method: String, path: String): Option[String] = {
    typeLookups.flatMap(_.forPath(method, path)).headOption
  }

  def validate(method: String, path: String, js: JsValue): Either[Seq[String], JsValue] = {
    typeForPath(method, path) match {
      case None => Right(js)
      case Some(typ) => {
        val start: Either[Seq[String], JsValue] = Right(js)
        jsonValidators.foldLeft(start) { case (result, validator) =>
          result match {
            case Left(errors) => Left(errors)
            case Right(v) => validator.validate(typ, v)
          }
        }
      }
    }
  }

}

/**
  * Responsible for downloading the apidoc service specifications from
  * the URLs specified by the configuration parameter named
  * apidoc.service.uris.
  */
@Singleton
class ApidocServicesFetcher @Inject() (
  config: Config
) {

  private[this] lazy val Uris: Seq[URL] = config.requiredString("apidoc.service.uris").split(",").map(_.trim).map { new URL(_) }

  def load(uris: Seq[URL]): Either[Seq[String], ApidocServices] = {
    combine(uris, ApidocServices.Empty)
  }

  @scala.annotation.tailrec
  private[this] def combine(uris: Seq[URL], service: ApidocServices): Either[Seq[String], ApidocServices] = {
    uris.toList match {
      case Nil => {
        Right(service)
      }

      case uri :: rest => {
        JsonValidator(uri) match {
          case Left(errors) => {
            Left(errors)
          }
          case Right(thisService) => {
            combine(
              rest,
              service.merge(ApidocServices(
                uris = Seq(uri.toString),
                services = Seq(thisService)
              ))
            )
          }
        }
      }
    }
  }
  
  def current(): ApidocServices = load(Uris) match {
    case Left(errors) => sys.error(s"Error loading apidoc services: $errors")
    case Right(services) => services
  }

}
