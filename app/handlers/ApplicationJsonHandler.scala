package handlers

import io.flow.log.RollbarLogger
import javax.inject.{Inject, Singleton}
import lib._
import play.api.libs.json.{JsValue, Json}
import play.api.libs.ws.WSClient
import play.api.mvc.Result

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

@Singleton
class ApplicationJsonHandler @Inject() (
  logger: RollbarLogger,
  apiBuilderServicesFetcher: ApiBuilderServicesFetcher,
  genericHandler: GenericHandler
) extends Handler {

  override def process(
    wsClient: WSClient,
    server: Server,
    request: ProxyRequest,
    route: Route,
    token: ResolvedToken
  )(
    implicit ec: ExecutionContext
  ): Future[Result] = {
    val body = request.bodyUtf8.getOrElse("")

    Try {
      if (body.trim.isEmpty) {
        // e.g. PUT/DELETE with empty body
        Json.obj()
      } else {
        Json.parse(body)
      }
    } match {
      case Failure(e) => {
        Future.successful(
          request.responseUnprocessableEntity(
            s"The body of an application/json request must contain valid json: ${e.getMessage}",
            Map("X-Flow-Proxy-Validation" -> Seq("proxy"))
          )
        )
      }

      case Success(js) => {
        processJson(
          wsClient,
          server,
          request,
          route,
          token,
          js
        )
      }
    }
  }

  private[handlers] def processJson(
    wsClient: WSClient,
    server: Server,
    request: ProxyRequest,
    route: Route,
    token: ResolvedToken,
    js: JsValue
  )(
    implicit ec: ExecutionContext
  ): Future[Result] = {
    apiBuilderServicesFetcher.multiService.upcast(route.method.toString, route.path, js) match {
      case Left(errors) => {
        Future.successful(
          request.responseUnprocessableEntity(
            errors.mkString(", "),
            Map("X-Flow-Proxy-Validation" -> Seq("apibuilder"))
          )
        )
      }

      case Right(validatedBody) => {
        genericHandler.process(
          wsClient,
          server,
          request.copy(
            contentType = ContentType.ApplicationJson,
            body = Some(ProxyRequestBody.Json(validatedBody))
          )(logger),
          route,
          token
        )
      }
    }
  }

}
