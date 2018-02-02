package handlers

import javax.inject.{Inject, Singleton}

import controllers.ServerProxyDefinition
import io.apibuilder.validation.FormData
import lib.{ProxyRequest, ResolvedToken, Route}

/**
  * Converts url form encodes into a JSON body, then
  * delegates processing to the application json handler
  */
@Singleton
class UrlFormEncodedHandler @Inject() (
  applicationJsonHandler: ApplicationJsonHandler
) {

  def process(
    definition: ServerProxyDefinition,
    request: ProxyRequest,
    route: Route,
    token: ResolvedToken
  ) = {
    val newBody = FormData.parseEncodedToJsObject(
      request.bodyUtf8.getOrElse {
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