package controllers

import io.flow.usage.util.UsageUtil
import javax.inject.{Inject, Singleton}
import lib.{ApiBuilderServicesFetcher, Config, Method}
import play.api.mvc._
import play.api.libs.json._

import scala.concurrent.Future

case class RouteResult(
  method: Method,
  operation: Option[lib.Operation]
)

@Singleton
class Internal @Inject() (
  apiBuilderServicesFetcher: ApiBuilderServicesFetcher,
  config: Config,
  reverseProxy: ReverseProxy,
  val controllerComponents: ControllerComponents,
  uu: UsageUtil
) extends BaseController {

  private[this] val expectedTypeNames = List(
    ("io.flow.shopify.external.v0", "shopify_cart"),
    ("io.flow.shopify.v0", "shopify_cart_add_form"),
    ("io.flow.common.v0", "user"),
    ("io.flow.beacon.v0", "event"),
    ("io.flow.billing.v0", "account"),
    ("io.flow.experience.v0", "experience"),
    ("io.flow.experience.v0", "order"),
    ("io.flow.partner.v0", "label"),
  )

  private[this] val HealthyJson = Json.obj(
    "status" -> "healthy"
  )

  private[this] val RobotsTxt = "User-agent: *\nDisallow: /"

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
            val missingTypes = expectedTypeNames.filter { el =>
              apiBuilderServicesFetcher.multiService.findType(el._1, el._2).isEmpty
            }
            if (missingTypes.isEmpty) {
              Ok(HealthyJson)
            } else {
              UnprocessableEntity(
                Json.toJson(
                  Seq("No apibuilder services found")
                )
              )
            }
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
