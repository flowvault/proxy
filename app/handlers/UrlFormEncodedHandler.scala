package handlers

import javax.inject.{Inject, Singleton}

import controllers.ServerProxyDefinition
import io.apibuilder.validation.FormData
import lib.{ProxyRequest, ResolvedToken, Route}
import play.api.mvc.Result

import scala.concurrent.{ExecutionContext, Future}

/**
  * Converts url form encodes into a JSON body, then
  * delegates processing to the application json handler
  */
@Singleton
class UrlFormEncodedHandler @Inject() (
  applicationJsonHandler: ApplicationJsonHandler
) extends Handler {

  override def process(
    definition: ServerProxyDefinition,
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
        applicationJsonHandler.processJson(
          definition,
          request,
          route,
          token,
          FormData.parseEncodedToJsObject(body)
        )
      }
    }
  }

}
