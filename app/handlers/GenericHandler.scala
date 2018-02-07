package handlers

import akka.actor.ActorRef
import javax.inject.{Inject, Named, Singleton}

import actors.MetricActor
import io.apibuilder.spec.v0.models.ParameterLocation
import io.apibuilder.validation.MultiService
import lib._
import play.api.Logger
import play.api.http.HttpEntity
import play.api.libs.json.{JsObject, JsValue}
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
) extends Handler with HandlerUtilities {

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
    val msg = request.body.map {
      case ProxyRequestBody.Json(json) => s"body:${safeBody(request, json)}"
      case ProxyRequestBody.Bytes(_) => "body:bytes"
      case ProxyRequestBody.File(_) => "body:file"
    }
    log(request, server, "start", msg)

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
      val duration = System.currentTimeMillis() - request.createdAtMillis
      metricActor ! toMetricMessage(server, request, response.status, token, duration)
      logResponse(request, server, response, duration)

      /**
        * Returns the content type of the response, defaulting to the
        * request Content-Type
        */
      val contentType: ContentType = response.header(Constants.Headers.ContentType).map(ContentType.apply).getOrElse(
        response.status match {
          case 301 | 302 | 303 | 307 => ContentType.TextHtml
          case _ => request.contentType
        }
      )
      val contentLength: Option[String] = response.header("Content-Length")

      // Remove content type (to avoid adding twice below) then add common Flow headers
      val responseHeaders = Util.removeKeys(
        response.headers,
        Seq(Constants.Headers.ContentType, Constants.Headers.ContentLength)
      ) ++ Map(
        Constants.Headers.FlowRequestId -> Seq(request.requestId),
        Constants.Headers.FlowServer -> Seq(server.name)
      )

      if (request.responseEnvelope) {
        request.response(response.status, response.body, contentType, responseHeaders)
      } else {
        contentLength match {
          case None => {
            Results.Status(response.status).
              chunked(response.bodyAsSource).
              withHeaders(Util.toFlatSeq(responseHeaders): _*).
              as(contentType.toStringWithEncoding)
          }

          case Some(length) => {
            Results.Status(response.status).
              sendEntity(
                HttpEntity.Streamed(response.bodyAsSource, Some(length.toLong), Some(contentType.toStringWithEncoding))
              ).
              withHeaders(Util.toFlatSeq(responseHeaders): _*)
          }
        }
      }
    }.recover {
      case ex: Throwable => throw new Exception(ex)
    }
  }

  private[this] def toMetricMessage(
    server: Server,
    request: ProxyRequest,
    responseStatus: Int,
    token: ResolvedToken,
    duration: Long
  ): MetricActor.Messages.Send = {
    MetricActor.Messages.Send(
      server = server.name,
      requestId = request.requestId,
      method = request.method.toString,
      path = request.pathWithQuery,
      ms = duration,
      response = responseStatus,
      organizationId = token.organizationId,
      partnerId = token.partnerId,
      userId = token.userId
    )
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
      Constants.Headers.ContentType -> request.contentType.toStringWithEncoding,
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

    val cleanHeaders = Util.removeKeys(
      request.headers.toMap,
      Constants.Headers.namesToRemove
    )

    headersToAdd.foldLeft(new Headers(Util.toFlatSeq(cleanHeaders))) { case (h, addl) => h.add(addl) }
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

  private[this] def logResponse(
    request: ProxyRequest,
    server: Server,
    response: WSResponse,
    duration: Long
  ): Unit = {
    val extra = response.status match {
      case 415 => {
        " request.headers:" + request.headers.headers.
          map { case (k, v) =>
            if (k.toLowerCase == "authorization") {
              s"$k=redacted"
            } else {
              s"$k=$v"
            }
          }.sorted.mkString(", ")
      }

      case 422 => {
        // common validation error - TODO: Show body
        ""
      }

      case _ => {
        ""
      }
    }

    log(request, server, "done", Some(s"status:${response.status} timeToFirstByteMs:$duration$extra"))
  }

  private[this] def log(
    request: ProxyRequest,
    server: Server,
    stage: String,
    message: Option[String] = None
  ): Unit = {
    val m = message match {
      case None => ""
      case Some(msg) => s" $msg"
    }
    Logger.info(s"[proxy $request] $stage server:${server.name} ${request.method} ${server.host}${request.pathWithQuery} request.contentType:${request.contentType.toStringWithEncoding}$m")
  }

  private[this] def safeBody(
    request: ProxyRequest,
    body: JsValue
  ): String = {
    val typ = multiService.bodyTypeFromPath(request.method.toString, request.path)
    body match {
      case j: JsObject if typ.isEmpty && j.value.isEmpty => "{}"
      case _: JsObject => toLogValue(request, body, typ).toString
      case _ => "Body of type[${body.getClass.getName}] fully redacted"
    }
  }
}