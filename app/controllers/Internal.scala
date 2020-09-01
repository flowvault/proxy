package controllers

import io.flow.usage.util.UsageUtil
import javax.inject.{Inject, Singleton}
import lib.{Config, Method}
import play.api.mvc._
import play.api.libs.json._

import scala.concurrent.Future

case class RouteResult(
  method: Method,
  operation: Option[lib.Operation]
)

@Singleton
class Internal @Inject() (
  config: Config,
  reverseProxy: ReverseProxy,
  val controllerComponents: ControllerComponents,
  uu: UsageUtil
) extends BaseController {

  private[this] val HealthyJson = Json.obj(
    "status" -> "healthy"
  )

  /**
   * Googlebot white-listed URLs are here to allow their crawler to access our API during client page-load.
   * These endpoints are consumed by Shopify/FlowJS. This allow Google to crawl our clients sites and
   * localize prices (and more importantly schema data) so as to allow localized structured data for
   * localized adverts.
   */
  private[this] val RobotsTxt = """
    |User-agent: *
    |Disallow: /
    |
    |User-agent: Googlebot
    |Allow: /shopify/shops/*/sessions
    |Allow: /sessions/
    |Allow: /*/bundles/browser
    |Allow: /*/shopify/localized/variants/experience/
    |Allow: /*/experiences/*/items/query
    |Disallow: /
    |""".stripMargin.trim

  def getRobots = Action { _ =>
    Ok(RobotsTxt)
  }

  def getHealthcheck = Action { _ =>
    config.missing().toList match {
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
            "method" -> op.route.method.toString,
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

  def favicon = Action.async { _ =>
    Future.successful(
      NoContent
    )
  }

  // I can't seem to import the controller from lib-usage, so I have copied it here:
  def usage = Action {
    import io.flow.usage.v0.models.json._
    Ok(Json.toJson(uu.currentUsage))
  }

  def getRoute = Action.async { request =>
    Future.successful {
      val path = request.getQueryString("path").map(_.trim).filter(_.nonEmpty)

      val results = path match {
        case None => Nil
        case Some(p) => {
          Method.all.map { method =>
            RouteResult(
              method = method,
              operation = reverseProxy.index.resolve(method, p)
            )
          }
        }
      }

      Ok(
        views.html.route(path, results)
      )
    }
  }
}
