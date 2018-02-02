package handlers

import javax.inject.{Inject, Singleton}

import controllers.ServerProxyDefinition
import lib._
import play.api.http.HttpEntity
import play.api.libs.ws.{WSClient, WSRequest, WSResponse}
import play.api.mvc.{Result, Results}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class GenericHandler @Inject() (
  override val config: Config,
  override val flowAuth: FlowAuth,
  override val wsClient: WSClient
) extends Handler with HandlerUtilities  {

  override def process(
    definition: ServerProxyDefinition,
    request: ProxyRequest,
    route: Route,
    token: ResolvedToken
  )(
    implicit ec: ExecutionContext
  ): Future[Result] = {
    process(
      definition,
      request,
      buildRequest(definition, request, route, token),
      request.body
    )
  }

  private[handlers] def process(
    definition: ServerProxyDefinition,
    request: ProxyRequest,
    wsRequest: WSRequest,
    body: Option[ProxyRequestBody]
  )(
    implicit ec: ExecutionContext
  ): Future[Result] = {

    body match {
      case None => {
        wsRequest
          .stream()
          .map(processResponse(request, _))
          .recover { case ex: Throwable => throw new Exception(ex) }
      }

      case Some(ProxyRequestBody.File(file)) => {
        wsRequest
          .post(file)
          .map(processResponse(request, _))
          .recover { case ex: Throwable => throw new Exception(ex) }
      }

      case Some(ProxyRequestBody.Bytes(bytes)) => {
        wsRequest
          .withBody(bytes)
          .stream
          .map(processResponse(request, _))
          .recover { case ex: Throwable => throw new Exception(ex) }
      }

      case Some(ProxyRequestBody.Json(json)) => {
        logFormData(definition, request, json)

        setContentTypeHeader(wsRequest, ContentType.ApplicationJson)
          .withBody(json)
          .stream
          .map(processResponse(request, _))
          .recover { case ex: Throwable => throw new Exception(ex) }
      }
    }
  }

  private[this] def processResponse(request: ProxyRequest, response: WSResponse) = {
    if (request.responseEnvelope) {
      request.response(response.status, response.body, response.headers)
    } else {
      val contentType = response.headers.get("Content-Type").flatMap(_.headOption).getOrElse("application/octet-stream")
      // If there's a content length, send that, otherwise return the body chunked
      response.headers.get("Content-Length") match {
        case Some(Seq(length)) =>
          Results.Status(response.status).sendEntity(
            HttpEntity.Streamed(response.bodyAsSource, Some(length.toLong), Some(contentType))
          )
        case _ =>
          Results.Status(response.status).chunked(response.bodyAsSource).as(contentType)
      }
    }
  }

  private[this] def setContentTypeHeader(
    wsRequest: WSRequest,
    contentType: ContentType
  ): WSRequest = {
    val headers = Seq(
      addContentType(wsRequest.headers, contentType).flatMap { case (key, values) =>
        values.map { v =>
          (key, v)
        }
      }.toSeq
    ).flatten

    wsRequest.addHttpHeaders(headers: _*)
  }

  private[this] def addContentType(
    headers: Map[String, Seq[String]],
    contentType: ContentType
  ): Map[String, Seq[String]] = {
    val name = "Content-Type"
    headers.filter(_._1.toLowerCase != name.toLowerCase) ++ Map(
      "Content-Type" -> Seq(contentType.toString)
    )
  }

}
