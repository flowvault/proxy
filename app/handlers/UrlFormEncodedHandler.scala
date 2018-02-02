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
    val newBody = FormData.parseEncodedToJsObject(
      request.bodyUtf8.getOrElse {
        // TODO: Return 422 on invalid content herek
        sys.error(s"Request[${request.requestId}] Failed to serialize body as string for ContentType.UrlFormEncoded")
      }
    )

    applicationJsonHandler.processJson(
      definition,
      request,
      route,
      token,
      newBody
    )
  }

}
