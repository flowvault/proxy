package lib

import io.flow.log.RollbarLogger
import play.api.http.HttpErrorHandler
import play.api.mvc._
import play.api.mvc.Results._

import scala.concurrent._
import javax.inject.{Inject, Singleton}

@Singleton
class ErrorHandler @Inject() (
  logger: RollbarLogger
) extends HttpErrorHandler with Errors {

  def onClientError(request: RequestHeader, statusCode: Int, message: String): Future[Result] = {
    val errorId = generateErrorId()
    logger.
      withKeyValue("error_id", errorId).
      withKeyValue("request_method", request.method.toString).
      withKeyValue("request_uri", request.uri).
      withKeyValue("http_status_code", statusCode).
      withKeyValue("message", message).
      warn("Client error")
    Future.successful(
      Status(statusCode)(clientErrors(Seq(s"Invalid request (err #$errorId)")))
    )
  }

  def onServerError(request: RequestHeader, ex: Throwable): Future[Result] = {
    val errorId = generateErrorId()
    logger.
      withKeyValue("error_id", errorId).
      withKeyValue("request_method", request.method.toString).
      withKeyValue("request_uri", request.uri).
      error("FlowError", ex)

    Future.successful(
      InternalServerError(serverErrors(Seq(s"A server error occurred (err #$errorId)")))
    )
  }
}
