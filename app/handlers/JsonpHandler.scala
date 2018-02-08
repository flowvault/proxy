package handlers

import javax.inject.{Inject, Singleton}

import io.apibuilder.validation.FormData
import lib.{ProxyRequest, ResolvedToken, Route, Server}
import play.api.libs.ws.WSClient
import play.api.mvc.Result

import scala.concurrent.{ExecutionContext, Future}

/**
  * Converts query parameters for a JSON P GET request
  * into a JSON body, then delegates processing to the
  * application json handler
  */
@Singleton
class JsonpHandler @Inject() (
  urlFormEncodedHandler: UrlFormEncodedHandler
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
    urlFormEncodedHandler.processUrlFormEncoded(
      wsClient,
      server,
      request,
      route,
      token,
      request.rawQueryString
    )
  }

}
