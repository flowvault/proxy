package handlers

import controllers.ServerProxyDefinition
import lib.{ProxyRequest, ResolvedToken, Route}
import play.api.mvc.Result

import scala.concurrent.Future

trait Handler {

  def process(
    definition: ServerProxyDefinition,
    request: ProxyRequest,
    route: Route,
    token: ResolvedToken
  ): Future[Result]

}
