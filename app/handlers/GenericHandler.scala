package handlers

import javax.inject.{Inject, Singleton}

import io.apibuilder.validation.MultiService
import lib._
import play.api.http.HttpEntity
import play.api.libs.ws.{WSClient, WSRequest, WSResponse}
import play.api.mvc.{Headers, Result, Results}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class GenericHandler @Inject() (
  override val config: Config,
  override val flowAuth: FlowAuth,
  wsClient: WSClient,
  apiBuilderServicesFetcher: ApiBuilderServicesFetcher
) extends Handler with HandlerUtilities  {

  override def multiService: MultiService = apiBuilderServicesFetcher.multiService

  override def process(
    server: Server,
    request: ProxyRequest,
    route: Route,
    token: ResolvedToken
  )(
    implicit ec: ExecutionContext
  ): Future[Result] = {
    process(
      request,
      buildRequest(server, request, route, token),
      request.body
    )
  }

  private[handlers] def buildRequest(
    server: Server,
    request: ProxyRequest,
    route: Route,
    token: ResolvedToken
  ): WSRequest = {
    println(s"URL: ${server.host + request.path}")
    wsClient.url(server.host + request.path)
      .withFollowRedirects(false)
      .withMethod(route.method)
      .withRequestTimeout(server.requestTimeout)
      .addQueryStringParameters(
        definedQueryParameters(request, route): _*
      )
      .addHttpHeaders(
        proxyHeaders(server, request, token).headers: _*
      )
  }

  private[handlers] def process(
    request: ProxyRequest,
    wsRequest: WSRequest,
    body: Option[ProxyRequestBody]
  )(
    implicit ec: ExecutionContext
  ): Future[Result] = {

    body match {
      case None => {
        processResponse(request, wsRequest.stream())
      }

      case Some(ProxyRequestBody.File(file)) => {
        request.method.toUpperCase match {
          case "POST" => processResponse(request, wsRequest.post(file))
          case "PUT" => processResponse(request, wsRequest.put(file))
          case "PATCH" => processResponse(request, wsRequest.patch(file))
          case _ => Future.successful(
            request.responseUnprocessableEntity(
              s"Invalid method '${request.method}' for body with file. Must be POST, PUT, or PATCH"
            )
          )
        }
      }

      case Some(ProxyRequestBody.Bytes(bytes)) => {
        processResponse(request, wsRequest.withBody(bytes).stream())
      }

      case Some(ProxyRequestBody.Json(json)) => {
        logFormData(request, json)

        processResponse(
          request,
          setContentTypeHeader(wsRequest, ContentType.ApplicationJson)
            .withBody(json)
            .stream
        )
      }
    }
  }

  private[this] def processResponse(
    request: ProxyRequest,
    response: Future[WSResponse]
  )(
    implicit ec: ExecutionContext
  ): Future[Result] = {
    println(s"request: ${request}")
    println(s"pathWithQuery: ${request.pathWithQuery}")
    response.map { response =>
      if (request.responseEnvelope) {
        request.response(response.status, response.body, response.headers)
      } else {
        /**
          * Returns the content type of the request. WS Client defaults to
          * application/octet-stream. Given this proxy is for APIs only,
          * assume application / JSON if no content type header is
          * provided.
          */
        val contentType: String = response.headers.
          get("Content-Type").
          flatMap(_.headOption).
          getOrElse(ContentType.ApplicationJson.toString)

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
    }.recover {
      case ex: Throwable => throw new Exception(ex)
    }
  }

  /**
    * Removes any existing Content-Type headers from the map, adding
    * a header with single new value to the specified contentType
    */
  private[this] def setContentTypeHeader(
    wsRequest: WSRequest,
    contentType: ContentType
  ): WSRequest = {
    val headers = Seq(
      wsRequest.headers.flatMap { case (key, values) =>
        if (key.toLowerCase == "content-type") {
          Seq(
            ("Content-Type", contentType.toString)
          )
        } else {
          values.map { v =>
            (key, v)
          }
        }
      }.toSeq
    ).flatten

    wsRequest.withHttpHeaders(headers: _*)
  }

  /**
    * Modifies headers by:
    *   - removing X-Flow-* headers if they were set
    *   - adding a default content-type
    */
  private[this] def proxyHeaders(
    server: Server,
    request: ProxyRequest,
    token: ResolvedToken
  ): Headers = {

    val headersToAdd = Seq(
      Constants.Headers.FlowServer -> server.name,
      Constants.Headers.FlowRequestId -> request.requestId,
      Constants.Headers.Host -> server.hostHeaderValue,
      Constants.Headers.ForwardedHost -> request.headers.get(Constants.Headers.Host).getOrElse(""),
      Constants.Headers.ForwardedOrigin -> request.headers.get(Constants.Headers.Origin).getOrElse(""),
      Constants.Headers.ForwardedMethod -> request.originalMethod
    ) ++ Seq(
      Some(
        Constants.Headers.FlowAuth -> flowAuth.jwt(token)
      ),

      request.clientIp().map { ip =>
        Constants.Headers.FlowIp -> ip
      },

      request.headers.get("Content-Type") match {
        case None => Some("Content-Type" -> request.contentType.toString)
        case Some(_) => None
      }
    ).flatten

    val cleanHeaders = Constants.Headers.namesToRemove.foldLeft(request.headers) { case (h, n) => h.remove(n) }

    headersToAdd.foldLeft(cleanHeaders) { case (h, addl) => h.add(addl) }
  }
}
