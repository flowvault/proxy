package handlers

import javax.inject.{Inject, Singleton}

import controllers.ServerProxyDefinition
import lib._
import play.api.Logger
import play.api.libs.json.{JsValue, Json}
import play.api.libs.ws.WSClient

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

@Singleton
class ApplicationJsonHandler @Inject() (
  config: Config,
  flowAuth: FlowAuth,
  wsClient: WSClient
) extends HandlerUtilities  {

  def process(
    definition: ServerProxyDefinition,
    request: ProxyRequest,
    route: Route,
    token: ResolvedToken
  ) = {
    val body = request.bodyUtf8.getOrElse("")

    Try {
      if (body.trim.isEmpty) {
        // e.g. PUT/DELETE with empty body
        Json.obj()
      } else {
        Json.parse(body)
      }
    } match {
      case Failure(e) => {
        Logger.info(s"[proxy $request] 422 invalid json")
        Future.successful(
          UnprocessableEntity(
            genericError(s"The body of an application/json request must contain valid json: ${e.getMessage}")
          ).withHeaders("X-Flow-Proxy-Validation" -> "proxy")
        )
      }

      case Success(js) => {
        processJson(
          definition,
          request,
          route,
          token,
          js
        )
      }
    }
  }

  private[handlers] def processJson(
    definition: ServerProxyDefinition,
    request: ProxyRequest,
    route: Route,
    token: ResolvedToken,
    js: JsValue
  ) = {
    logFormData(definition, request, js)

    definition.multiService.upcast(route.method, route.path, js) match {
      case Left(errors) => {
        log4xx(request, 422, js, errors)
        Future.successful(
          UnprocessableEntity(
            genericErrors(errors)
          ).withHeaders("X-Flow-Proxy-Validation" -> "apibuilder")
        )
      }

      case Right(validatedBody) => {
        buildRequestApplicationJson(definition, request, route, token)
          .withBody(validatedBody)
          .stream
          .recover { case ex: Throwable => throw new Exception(ex) }
      }
    }
  }

}