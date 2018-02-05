package handlers

import javax.inject.{Inject, Singleton}

import lib._
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.Result

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

@Singleton
class ApplicationJsonHandler @Inject() (
  apiBuilderServicesFetcher: ApiBuilderServicesFetcher,
  genericHandler: GenericHandler
) extends Handler {

  override def process(
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
          server,
          request.copy(
            body = Some(ProxyRequestBody.Json(validatedBody))
          ),
          route,
          token
        )
      }
    }
  }

}
