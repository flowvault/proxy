package handlers

import javax.inject.{Inject, Singleton}

import io.apibuilder.validation.FormData
import lib._
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
      case None => Map.empty[String, Seq[String]]
      case Some(b) => FormData.parseEncoded(b)
    }

    if (isJson(map)) {
      Future.successful(
        request.responseUnprocessableEntity(
          s"The content type you specified '${ContentType.UrlFormEncoded.toString}' does not match the body. " +
          s"Please specify 'Content-type: ${ContentType.ApplicationJson.toString}' when providing a JSON Body."
        )
      )
    } else {
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

  private[this] def isJson(data: Map[String, Seq[String]]) = {
    data.size == 1 && data.values.toSeq.flatMap(_.map(Option.apply)).flatten.isEmpty
  }
}
