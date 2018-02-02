package handlers

import javax.inject.{Inject, Singleton}

import controllers.ServerProxyDefinition
import lib._
import play.api.libs.ws.WSClient
import play.api.mvc.Result

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class GenericHandler @Inject() (
  config: Config,
  flowAuth: FlowAuth,
  wsClient: WSClient
) extends Handler with HandlerUtilities  {

  override def process(
    definition: ServerProxyDefinition,
    request: ProxyRequest,
    route: Route,
    token: ResolvedToken
  )(
    implicit ec: ExecutionContext
  ): Future[Result] = {
    val req = buildRequest(definition, request, route, token)

    request.body match {
      case None => {
        req
          .stream()
          .map
          .recover { case ex: Throwable => throw new Exception(ex) }
      }

      case Some(ProxyRequestBody.File(file)) => {
        req
          .post(file)
          .recover { case ex: Throwable => throw new Exception(ex) }
      }

      case Some(ProxyRequestBody.Bytes(bytes)) => {
        req
          .withBody(bytes)
          .stream
          .recover { case ex: Throwable => throw new Exception(ex) }
      }

      case Some(ProxyRequestBody.Json(json)) => {
        req
          .withBody(json)
          .stream
          .recover { case ex: Throwable => throw new Exception(ex) }
      }
    }
  }

}
