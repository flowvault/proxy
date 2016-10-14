package lib

import play.api.http.HttpErrorHandler
import play.api.Logger
import play.api.mvc._
import play.api.mvc.Results._
import scala.concurrent._
import javax.inject.Singleton

@Singleton
class ErrorHandler extends HttpErrorHandler with Errors {

  def onClientError(request: RequestHeader, statusCode: Int, message: String) = {
    val errorId = generateErrorId()
    Logger.warn(s"[proxy] FlowError Client Error $errorId path[${request.uri}] status[$statusCode]: $message")
    Future.successful(
      Status(statusCode)(clientErrors(Seq(s"A client error occurred: $message")))
    )
  }

  def onServerError(request: RequestHeader, ex: Throwable) = {
    val errorId = generateErrorId()
    Logger.error(s"[proxy] FlowError $errorId ${request.method} ${request.uri}: ${ex.getMessage}", ex)
    Future.successful(
      InternalServerError(serverErrors(Seq(s"A server error occurred (err #$errorId)")))
    )
  }
}
