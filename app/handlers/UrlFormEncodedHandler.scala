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
        processUrlFormEncoded(server, request, route, token, Some(body))
      }
    }
  }

  /**
    * This method handles bodies that are both
    * application/json and url form encoded
    * transparently.
    */
  private[handlers] def processUrlFormEncoded(
    server: Server,
    request: ProxyRequest,
    route: Route,
    token: ResolvedToken,
    body: Option[String]
  )(
    implicit ec: ExecutionContext
  ): Future[Result] = {
    val js = body.getOrElse("").trim match {
      case "" => {
        // e.g. PUT/DELETE with empty body
        Json.obj()
      }
      case v => {
        Try {
          Json.parse(v)
        } match {
          case Failure(_) => FormData.parseEncodedToJsObject(v)
          case Success(value) => value
        }
      }
    }

    applicationJsonHandler.processJson(
      server,
      request,
      route,
      token,
      js
    )
  }
}
