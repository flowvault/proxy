package handlers

import lib.{ProxyRequest, ResolvedToken, Route, Server}
import play.api.mvc.Result

import scala.concurrent.{ExecutionContext, Future}

trait Handler {

  def process(
    server: Server,
    request: ProxyRequest,
    route: Route,
    token: ResolvedToken
  )(
    implicit ec: ExecutionContext
  ): Future[Result]

}
