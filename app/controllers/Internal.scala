package controllers

import javax.inject.{Inject, Singleton}
import lib.Config
import play.api._
import play.api.mvc._
import play.api.libs.json._

@Singleton
class Internal @Inject() (
  config: Config,
  reverseProxy: ReverseProxy
) extends Controller {

  private[this] val HealthyJson = Json.obj(
    "status" -> "healthy"
  )

  def getHealthcheck() = Action { request =>
    config.missing.toList match {
      case Nil => {
        reverseProxy.index.config.services.toList match {
          case Nil => {
            UnprocessableEntity(
              Json.toJson(
                Seq("No services are configured")
              )
            )
          }

          case _ => {
            Ok(HealthyJson)
          }
        }
      }

      case missing => {
        UnprocessableEntity(
          Json.toJson(
            Seq("Missing environment variables: " + missing.mkString(", "))
          )
        )
      }
    }
  }

  def getConfig() = Action { request =>
    Ok(
      Json.obj(
        "version" -> reverseProxy.index.config.version,
        "services" -> reverseProxy.index.config.services.map { service =>
          Json.obj(
            "name" -> service.name,
            "host" -> service.host,
            "routes" -> service.routes.map { r =>
              Json.obj(
                "method" -> r.method,
                "path" -> r.path
              )
            }
          )
        }
      )
    )
  }

}
