package handlers

import javax.inject.{Inject, Singleton}

import controllers.ServerProxyDefinition
import io.apibuilder.validation.FormData
import lib.{ProxyRequest, ResolvedToken, Route}
import play.api.mvc.Result

import scala.concurrent.{ExecutionContext, Future}

/**
  * Converts query parameters for a JSON P GET request
  * into a JSON body, then delegates processing to the
  * application json handler
  */
@Singleton
class JsonpHandler @Inject() (
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
    applicationJsonHandler.processJson(
      definition,
      request,
      route,
      token,
      FormData.toJson(request.queryParameters)
    )
  }

}
