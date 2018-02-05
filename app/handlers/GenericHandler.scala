package handlers

import akka.actor.ActorRef
import javax.inject.{Inject, Named, Singleton}

import actors.MetricActor
import io.apibuilder.spec.v0.models.ParameterLocation
import io.apibuilder.validation.MultiService
import lib._
import play.api.Logger
import play.api.http.HttpEntity
import play.api.libs.ws.{WSClient, WSRequest, WSResponse}
import play.api.mvc.{Headers, Result, Results}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class GenericHandler @Inject() (
  @Named("metric-actor") val metricActor: ActorRef,
  override val config: Config,
  flowAuth: FlowAuth,
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
    process(wsClient, server, request, route, token)
  }

  private[handlers] def process(
    wsClient: WSClient,
    server: Server,
    request: ProxyRequest,
    route: Route,
    token: ResolvedToken
  )(
    implicit ec: ExecutionContext
  ): Future[Result] = {

    val wsRequest = buildRequest(wsClient, server, request, route, token)

    request.body match {
      case None => {
        processResponse(server, request, token, wsRequest.stream())
      }

      case Some(ProxyRequestBody.File(file)) => {
        request.method match {
          case Method.Post => processResponse(server, request, token, wsRequest.post(file))
          case Method.Put => processResponse(server, request, token, wsRequest.put(file))
          case Method.Patch => processResponse(server, request, token, wsRequest.patch(file))
          case _ => Future.successful(
            request.responseUnprocessableEntity(
              s"Invalid method '${request.method}' for body with file. Must be POST, PUT, or PATCH"
            )
          )
        }
      }

      case Some(ProxyRequestBody.Bytes(bytes)) => {
        processResponse(
          server,
          request,
          token,
          wsRequest.withBody(bytes).stream()
        )
      }

      case Some(ProxyRequestBody.Json(json)) => {
        logFormData(request, json)

        processResponse(server, 
          request,
          token,
          wsRequest.withBody(json).stream
        )
      }
    }

  }

  private[this] def buildRequest(
    wsClient: WSClient,
    server: Server,
    request: ProxyRequest,
    route: Route,
    token: ResolvedToken
  ): WSRequest = {
    wsClient.url(server.host + request.path)
      .withFollowRedirects(false)
      .withMethod(route.method.toString)
      .withRequestTimeout(server.requestTimeout)
      .addQueryStringParameters(
        definedQueryParameters(request, route): _*
      )
      .addHttpHeaders(
        proxyHeaders(server, request, token).headers: _*
      )
  }

  private[this] def processResponse(
    server: Server,
    request: ProxyRequest,
    token: ResolvedToken,
    response: Future[WSResponse]
  )(
    implicit ec: ExecutionContext
  ): Future[Result] = {
    response.map { response =>
      metricActor ! MetricActor.Messages.Send(
        server = server.name,
        requestId = request.requestId,
        method = request.method.toString,
        path = request.pathWithQuery,
        ms = System.currentTimeMillis() - request.createdAtMillis,
        response = response.status,
        organizationId = token.organizationId,
        partnerId = token.partnerId,
        userId = token.userId
      )

      if (request.responseEnvelope) {
        request.response(response.status, response.body, response.headers)
      } else {
        /**
          * Returns the content type of the response, defaulting to the
          * request Content-Type
          */
        val contentType: String = response.headers.
          get("Content-Type").
          flatMap(_.headOption).
          getOrElse(request.contentType.toString)

        // If there's a content length, send that, otherwise return the body chunked
        response.headers.get("Content-Length") match {
          case Some(Seq(length)) =>
            Results.Status(response.status).
              sendEntity(
                HttpEntity.Streamed(response.bodyAsSource, Some(length.toLong), Some(contentType))
              ).
              withHeaders(Util.toFlatSeq(response.headers): _*)

          case _ =>
            Results.Status(response.status).
              chunked(response.bodyAsSource).as(contentType).
              withHeaders(Util.toFlatSeq(response.headers): _*)

        }
      }
    }.recover {
      case ex: Throwable => throw new Exception(ex)
    }
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
      Constants.Headers.ContentType -> request.contentType.toString,
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
      }
    ).flatten

    val cleanHeaders = Constants.Headers.namesToRemove.foldLeft(request.headers) { case (h, n) => h.remove(n) }

    headersToAdd.foldLeft(cleanHeaders) { case (h, addl) => h.add(addl) }
  }

  /**
    * For envelope requests, returns the subset of query parameters
    * that are documented as acceptable for this method.
    */
  private[this] def definedQueryParameters(
    request: ProxyRequest,
    route: Route
  ): Seq[(String, String)] = {
    val allQueryParameters = request.queryParametersAsSeq()
    if (request.requestEnvelope) {
      multiService.operation(route.method.toString, route.path) match {
        case None => {
          allQueryParameters
        }

        case Some(operation) => {
          val definedNames = operation.parameters.filter { p =>
            p.location == ParameterLocation.Query
          }.map(_.name)

          allQueryParameters.filter { case (key, _) =>
            val isDefined = definedNames.contains(key)
            if (!isDefined) {
              Logger.info(s"[proxy $request] GenericHandler Filtering out query parameter[$key] as it is not defined as part of the spec")
            }
            isDefined
          }
        }
      }
    } else {
      allQueryParameters
    }
  }
}
