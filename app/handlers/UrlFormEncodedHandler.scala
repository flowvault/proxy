package handlers

import javax.inject.{Inject, Singleton}

import io.apibuilder.validation.FormData
import lib.{ProxyRequest, ResolvedToken, Route, Server}
import play.api.libs.ws.WSClient
import play.api.mvc.Result

import scala.concurrent.{ExecutionContext, Future}

/**
  * Converts url form encoded into a JSON body, then
  * delegates processing to the application json handler
  */
@Singleton
class UrlFormEncodedHandler @Inject() (
  applicationJsonHandler: ApplicationJsonHandler
) extends Handler {

  override def process(
    wsClient: WSClient,
    server: Server,
    request: ProxyRequest,
    route: Route,
    token: ResolvedToken
  )(
    implicit ec: ExecutionContext
  ): Future[Result] = {
    request.bodyUtf8 match {
      case None => Future.successful(
        request.responseUnprocessableEntity(
          "Url form encoded requests must contain body encoded in ;UTF-8'"
        )
      )

      case Some(body) => {
        processUrlFormEncoded(wsClient, server, request, route, token, Some(body))
      }
    }
  }

  /**
    * This method handles bodies that are both
    * application/json and url form encoded
    * transparently.
    */
  private[handlers] def processUrlFormEncoded(
    wsClient: WSClient,
    server: Server,
    request: ProxyRequest,
    route: Route,
    token: ResolvedToken,
    body: Option[String]
  )(
    implicit ec: ExecutionContext
  ): Future[Result] = {
    val map = body match {
      case None => {
        Map.empty[String, Seq[String]]
      }

      case Some(b) => {
        val data = FormData.parseEncoded(b)
        if (data.size == 1) {
          println(s"data.values: ${data.values}")
        }
        data
      }
    }

    applicationJsonHandler.processJson(
      wsClient,
      server,
      request,
      route,
      token,
      FormData.toJson(map)
    )
  }
}
