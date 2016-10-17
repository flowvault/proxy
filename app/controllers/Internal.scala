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

  private[this] val RobotsTxt = "User-agent: *\nDisallow: /"

  def getRobots() = Action { request =>
    Ok(RobotsTxt)
  }

  def getHealthcheck() = Action { request =>
    config.missing.toList match {
      case Nil => {
        reverseProxy.index.config.operations.toList match {
          case Nil => {
            UnprocessableEntity(
              Json.toJson(
                Seq("No operations are configured")
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
        "sources" -> reverseProxy.index.config.sources.map { source =>
          Json.obj(
            "uri" -> source.uri,
            "version" -> source.version
          )
        },

        "servers" -> reverseProxy.index.config.servers.map { server =>
          Json.obj(
            "name" -> server.name,
            "host" -> server.host
          )
        },

        "operations" -> reverseProxy.index.config.operations.map { op =>
          Json.obj(
            "method" -> op.route.method,
            "path" -> op.route.path,
            "server" -> op.server.name
          )
        }
      )
    )
  }

}
