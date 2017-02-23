package controllers

import java.util.UUID
import javax.inject.{Inject, Singleton}

import lib.{Config, Constants}
import play.api._
import play.api.mvc._
import play.api.libs.json._

import scala.concurrent.Future

@Singleton
class Internal @Inject() (
  config: Config,
  reverseProxy: ReverseProxy
) extends Controller {

  private[this] val HealthyJson = Json.obj(
    "status" -> "healthy"
  )

  private[this] val RobotsTxt = "User-agent: *\nDisallow: /"

  def getRobots = Action { _ =>
    Ok(RobotsTxt)
  }

  def getHealthcheck = Action { _ =>
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

  def getConfig = Action { _ =>
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

  def diagnostics = Action.async(parse.raw) { request: Request[RawBuffer] =>
    val data = Seq(
      ("method", request.method),
      ("path", request.path),
      ("queryString", request.rawQueryString),
      ("headers", request.headers.headers.sortBy { _._1.toLowerCase }.map { case (k, v) =>
        s"$k: $v"
      }.mkString("<ul><li>", "</li>\n<li>\n", "</li></ul>")),
      ("body class", request.body.getClass.getName),
      ("body",request.body.asBytes().map(_.decodeString("UTF-8")).getOrElse(""))
    )

    val msg = data.map { case (k, v) =>
        s"<h2>$k</h2><blockquote>$v</blockquote>"
    }.mkString("\n")

    Future.successful(
      Ok(msg).as("text/html")
    )
  }

}
