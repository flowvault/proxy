package handlers

import javax.inject.{Inject, Singleton}

import controllers.ServerProxyDefinition
import io.apibuilder.validation.FormData
import lib._
import play.api.libs.ws.WSClient

import scala.concurrent.Future

@Singleton
class UrlFormEncodedHandler @Inject() (
  config: Config,
  flowAuth: FlowAuth,
  ws: WSClient
) extends HandlerUtilities  {

  def process(
    definition: ServerProxyDefinition,
    request: ProxyRequest,
    route: Route,
    token: ResolvedToken
  ) = {
    val finalHeaders = proxyHeaders(definition, request, token)

    val b = request.bodyUtf8.getOrElse {
      sys.error(s"Request[${request.requestId}] Failed to serialize body as string for ContentType.UrlFormEncoded")
    }
    val newBody = FormData.parseEncodedToJsObject(b)

    logFormData(request, newBody)

    definition.multiService.upcast(route.method, route.path, newBody) match {
      case Left(errors) => {
        log4xx(request, 422, newBody, errors)
        Future.successful(
          UnprocessableEntity(
            genericErrors(errors)
          ).withHeaders("X-Flow-Proxy-Validation" -> "apibuilder")
        )
      }

      case Right(validatedBody) => {
        buildRequest(ws, definition.server, request, route)
          .addHttpHeaders(
            setApplicationJsonContentType(finalHeaders).headers: _*
          )
          .withBody(validatedBody)
          .withRequestTimeout(definition.requestTimeout)
          .stream
          .recover { case ex: Throwable => throw new Exception(ex) }
      }
    }
  }

}
