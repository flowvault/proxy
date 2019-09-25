package lib

import io.flow.log.RollbarLogger
import play.api.http.HttpErrorHandler
import play.api.mvc._
import play.api.mvc.Results._

import scala.concurrent._
import javax.inject.{Inject, Singleton}

@Singleton
class ErrorHandler @Inject() (
  proxyConfigFetcher: ProxyConfigFetcher,
  defaultLogger: RollbarLogger
) extends HttpErrorHandler with Errors {

  lazy val index: Index = proxyConfigFetcher.current()

  private[this] case class ErrorLogger(errorId: String, logger: RollbarLogger)

  def onClientError(request: RequestHeader, statusCode: Int, message: String): Future[Result] = {
    val errorLogger = rollbarLogger(request)
    errorLogger.logger.
      withKeyValue("http_status_code", statusCode).
      withKeyValue("message", message).
      warn("ClientError")

    val r = Status(statusCode)(clientErrors(Seq(s"Invalid request (err #${errorLogger.errorId})")))
    Future.successful(r)
  }

  def onServerError(request: RequestHeader, ex: Throwable): Future[Result] = {
    val errorLogger = rollbarLogger(request)
    errorLogger.logger.error("ServerError", ex)

    val r = InternalServerError(serverErrors(Seq(s"A server error occurred (err #${errorLogger.errorId})")))
    Future.successful(r)
  }

  private[this] def rollbarLogger(request: RequestHeader): ErrorLogger = {
    val operation = index.resolve(Method(request.method), request.uri)
    val errorId = generateErrorId()
    val baseLogger = operation.map(_.server.logger).getOrElse(defaultLogger)

    // add Authorization as header to remove
    val headerKeys = request.headers.keys.toSeq
    val headers = Util.filterKeys(request.headers.toMap, Constants.Headers.namesToWhitelist)

    ErrorLogger(
      errorId,
      baseLogger.
        withKeyValue("error_id", errorId).
        withKeyValue("request_ip", request.remoteAddress).
        withKeyValue("request_method", request.method.toString).
        withKeyValue("request_uri", request.uri).
        withKeyValue("route_path", operation.map(_.route.path)).
        withKeyValue("route_server_name", operation.map(_.server.name)).
        withKeyValue("route_server_host", operation.map(_.server.host)).
        withKeyValue("header_keys", headerKeys). // show all keys to figure out what we may be missing or want to add
        withKeyValue("headers", headers)
    )
  }
}
