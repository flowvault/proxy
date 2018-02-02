package handlers

import javax.inject.{Inject, Singleton}

import controllers.ServerProxyDefinition
import lib._
import play.api.Logger
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{Result, Results}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

@Singleton
class ApplicationJsonHandler @Inject() (
  genericHandler: GenericHandler
) extends Handler {

  override def process(
    definition: ServerProxyDefinition,
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
          definition,
          request,
          route,
          token,
          js
        )
      }
    }
  }

  private[handlers] def processJson(
    definition: ServerProxyDefinition,
    request: ProxyRequest,
    route: Route,
    token: ResolvedToken,
    js: JsValue
  )(
    implicit ec: ExecutionContext
  ): Future[Result] = {
    definition.multiService.upcast(route.method, route.path, js) match {
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
          definition,
          request,
          genericHandler.buildRequest(definition, request, route, token),
          Some(ProxyRequestBody.Json(validatedBody))
        )
      }
    }
  }

}
