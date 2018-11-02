package lib

import io.flow.log.RollbarLogger
import play.api.http.HttpErrorHandler
import play.api.mvc._
import play.api.mvc.Results._

import scala.concurrent._
import javax.inject.{Inject, Singleton}

@Singleton
class ErrorHandler @Inject() (
  logger: RollbarLogger,
  proxyConfigFetcher: ProxyConfigFetcher
) extends HttpErrorHandler with Errors {

  lazy val index: Index = proxyConfigFetcher.current()

  def onClientError(request: RequestHeader, statusCode: Int, message: String): Future[Result] = {
    val errorId = generateErrorId()
    val operation = index.resolve(Method(request.method), request.uri)

    logger.
      withKeyValue("error_id", errorId).
      withKeyValue("request_method", request.method.toString).
      withKeyValue("request_uri", request.uri).
      withKeyValue("http_status_code", statusCode).
      withKeyValue("message", message).
      withKeyValue("route_path", operation.map(_.route.path)).
      withKeyValue("route_server_name", operation.map(_.server.name)).
      withKeyValue("route_server_host", operation.map(_.server.host)).
      warn("Client error")

    Future.successful(
      Status(statusCode)(clientErrors(Seq(s"Invalid request (err #$errorId)")))
    )
  }

  def onServerError(request: RequestHeader, ex: Throwable): Future[Result] = {
    val errorId = generateErrorId()
    val operation = index.resolve(Method(request.method), request.uri)

    logger.
      withKeyValue("error_id", errorId).
      withKeyValue("request_method", request.method.toString).
      withKeyValue("request_uri", request.uri).
      withKeyValue("route_path", operation.map(_.route.path)).
      withKeyValue("route_server_name", operation.map(_.server.name)).
      withKeyValue("route_server_host", operation.map(_.server.host)).
      error("FlowError", ex)

    Future.successful(
      InternalServerError(serverErrors(Seq(s"A server error occurred (err #$errorId)")))
    )
  }
}
