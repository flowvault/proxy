package handlers

import javax.inject.{Inject, Singleton}
import lib.{ProxyRequest, ResolvedToken, Route, Server}
import play.api.libs.ws.WSClient
import play.api.mvc.Result

import scala.concurrent.Future

/**
  * Converts query parameters for a JSON P GET request
  * into a JSON body, then delegates processing to the
  * application url form encoded handler
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
