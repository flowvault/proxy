package handlers

import javax.inject.{Inject, Singleton}

import controllers.ServerProxyDefinition
import lib._
import play.api.http.HttpEntity
import play.api.libs.ws.{WSClient, WSResponse}
import play.api.mvc.{Result, Results}

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
          .map(processResponse)
          .recover { case ex: Throwable => throw new Exception(ex) }
      }

      case Some(ProxyRequestBody.File(file)) => {
        req
          .post(file)
          .map(processResponse)
          .recover { case ex: Throwable => throw new Exception(ex) }
      }

      case Some(ProxyRequestBody.Bytes(bytes)) => {
        req
          .withBody(bytes)
          .stream
          .map(processResponse)
          .recover { case ex: Throwable => throw new Exception(ex) }
      }

      case Some(ProxyRequestBody.Json(json)) => {
        req
          .withBody(json)
          .stream
          .map(processResponse)
          .recover { case ex: Throwable => throw new Exception(ex) }
      }
    }
  }

}
