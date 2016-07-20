package controllers

import javax.inject.Inject
import lib.Constants
import play.api.routing.SimpleRouter
import play.api.http._
import play.api.mvc._
import scala.runtime.AbstractPartialFunction

class RequestHandler @Inject() (
  errorHandler: HttpErrorHandler,
  configuration: HttpConfiguration,
  filters: HttpFilters,
  myRouter: Router
) extends DefaultHttpRequestHandler(
  myRouter, errorHandler, configuration, filters
)


class Router @Inject() (
  internal: Internal,
  proxy: ReverseProxy
) extends SimpleRouter {

  override def routes = new AbstractPartialFunction[RequestHeader, Handler] {
    override def applyOrElse[A <: RequestHeader, B >: Handler](request: A, default: A => B) = {
      (request.method, request.path, request.headers.get(Constants.Headers.FlowService)) match {
        case ("GET", "/_internal_/healthcheck", None) => internal.getHealthcheck
        case ("GET", "/_internal_/config", None) => internal.getConfig
        case _ => proxy.handle
      }
    }

    def isDefinedAt(rh: RequestHeader) = true
  }

}
