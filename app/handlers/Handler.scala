package handlers

import lib.{ProxyRequest, ResolvedToken, Route, Server}
import play.api.libs.ws.WSClient
import play.api.mvc.Result

import scala.concurrent.{ExecutionContext, Future}

trait Handler {

  def process(
    wsClient: WSClient,
    server: Server,
    request: ProxyRequest,
    route: Route,
    token: ResolvedToken
  )(
    implicit ec: ExecutionContext
  ): Future[Result]

}
