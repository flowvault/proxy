package handlers

import javax.inject.{Inject, Singleton}

import io.apibuilder.validation.FormData
import lib.{ProxyRequest, ResolvedToken, Route, Server}
import play.api.libs.json.Json
import play.api.mvc.Result

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

/**
  * Converts url form encoded into a JSON body, then
  * delegates processing to the application json handler
  */
@Singleton
class UrlFormEncodedHandler @Inject() (
  applicationJsonHandler: ApplicationJsonHandler
) extends Handler {

  override def process(
    server: Server,
    request: ProxyRequest,
    route: Route,
    token: ResolvedToken
  )(
    implicit ec: ExecutionContext
  ): Future[Result] = {
    request.bodyUtf8 match {
      case None => Future.successful(
        request.responseUnprocessableEntity(
          "Url form encoded requests must contain body encoded in ;UTF-8'"
        )
      )

      case Some(body) => {
        val js = Try {
          if (body.trim.isEmpty) {
            // e.g. PUT/DELETE with empty body
            Json.obj()
          } else {
            Json.parse(body)
          }
        } match {
          case Failure(_) => FormData.parseEncodedToJsObject(body)
          case Success(value) => value
        }

        applicationJsonHandler.processJson(
          server,
          request.copy(

          ),
          route,
          token,
          js
        )
      }
    }
  }

}
