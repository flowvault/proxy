package controllers

import javax.inject.Inject
import lib.Constants
import play.api.routing.SimpleRouter
import play.api.http._
import play.api.mvc._
import scala.runtime.AbstractPartialFunction

/**
  * We implement our own request handler to inject a custom router
  * that implements the reverse proxy.
  */
class RequestHandler @Inject() (
  errorHandler: HttpErrorHandler,
  configuration: HttpConfiguration,
  filters: HttpFilters,
  router: Router
) extends DefaultHttpRequestHandler(
  router, errorHandler, configuration, filters
)

/**
  * Exposes a few /_internal_ routes explicitly; otherwise delegates
  * the route to the reverse proxy.
  */
class Router @Inject() (
  internal: Internal,
  proxy: ReverseProxy
) extends SimpleRouter {

  override def routes = new AbstractPartialFunction[RequestHeader, Handler] {
    override def applyOrElse[A <: RequestHeader, B >: Handler](request: A, default: A => B) = {
      (request.method, request.path, request.headers.get(Constants.Headers.FlowServer)) match {
        case ("GET", "/_internal_/healthcheck", None) => internal.getHealthcheck
        case ("GET", "/_internal_/config", None) => internal.getConfig
        case ("GET", "/robots.txt", None) => internal.getRobots
        case _ => proxy.handle
      }
    }

    def isDefinedAt(rh: RequestHeader) = true
  }

}
