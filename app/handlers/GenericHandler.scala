package handlers

import javax.inject.{Inject, Singleton}
import io.apibuilder.spec.v0.models.ParameterLocation
import io.apibuilder.validation.{EncodingOptions, FormData, MultiService}
import lib._
import org.joda.time.DateTime
import play.api.http.HttpEntity
import play.api.http.Status.{UNPROCESSABLE_ENTITY, UNSUPPORTED_MEDIA_TYPE}
import play.api.libs.json.{JsObject, JsValue}
import play.api.libs.ws.{WSClient, WSRequest, WSResponse}
import play.api.mvc.{Headers, Result, Results}
import io.flow.log.RollbarLogger

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

@Singleton
class GenericHandler @Inject() (
  override val config: Config,
  override val logger: RollbarLogger,
  flowAuth: FlowAuth,
  apiBuilderServicesFetcher: ApiBuilderServicesFetcher
) extends Handler with HandlerUtilities {

  override def multiService: MultiService = apiBuilderServicesFetcher.multiService

  override def process(
    wsClient: WSClient,
    server: Server,
    request: ProxyRequest,
    route: Route,
    token: ResolvedToken
  )(
    implicit ec: ExecutionContext
  ): Future[Result] = {
    val body = request.body.map {
      case ProxyRequestBody.Json(json) => safeBody(request, json)
      case ProxyRequestBody.Bytes(_) => "bytes"
      case ProxyRequestBody.File(_) => "file"
    }
    log(request, server, "start", Map("body" -> body.getOrElse("-")))

    val wsRequest = buildRequest(wsClient, server, request, route, token)

    request.body match {
      case None => {
        processResponse(server, request, wsRequest.stream())
      }

      case Some(ProxyRequestBody.File(file)) => {
        request.method match {
          case Method.Post => processResponse(server, request, wsRequest.post(file))
          case Method.Put => processResponse(server, request, wsRequest.put(file))
          case Method.Patch => processResponse(server, request, wsRequest.patch(file))
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
          wsRequest.withBody(bytes).stream()
        )
      }

      case Some(ProxyRequestBody.Json(json)) => {
        processResponse(
          server,
          request,
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
    response: Future[WSResponse]
  )(
    implicit ec: ExecutionContext
  ): Future[Result] = {
    response.map { response =>
      val duration = System.currentTimeMillis() - request.createdAtMillis
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

      if (request.responseEnvelope || response.status == 422) {
        request.response(response.status, safeBody(request, response).getOrElse(""), contentType, responseHeaders)
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

        case Some(apibuilderOperation) => {
          val definedNames = apibuilderOperation.operation.parameters.filter { p =>
            p.location == ParameterLocation.Query
          }.map(_.name)

          allQueryParameters.filter { case (key, _) =>
            val isDefined = definedNames.contains(key)
            if (!isDefined) {
              request.log.
                withKeyValue("parameter", key).
                info("GenericHandler Filtering out query parameter as it is not defined as part of the spec")
            }
            isDefined
          }
        }
      }
    } else {
      FormData.normalize(allQueryParameters, options = Set(EncodingOptions.OmitArrayIndexes))
    }
  }

  private[this] def logResponse(
    request: ProxyRequest,
    server: Server,
    response: WSResponse,
    duration: Long
  ): Unit = {
    val extra: Map[String, String] = response.status match {
      case UNSUPPORTED_MEDIA_TYPE => {
        Redact.auth(request.headers.headers.toMap)
      }

      case UNPROCESSABLE_ENTITY => {
        Map("body" -> safeBody(request, response).getOrElse(""))
      }

      case _ => {
        // if session, show last 5 chars of the session id
        request.headers.headers.toMap.get("Authorization").filter(isSessionAuth).map { headerValue =>
          Map("SessionLast5" -> headerValue.takeRight(5))
        }.getOrElse(Map.empty[String, String])
      }
    }

    log(request, server, "done", extra ++ Map(
      "status" -> response.status.toString,
      "timeToFirstByteMs" -> duration.toString,
      "response.contentLength" -> response.header("Content-Length").getOrElse("-")
    ))
  }

  private[this] def isSessionAuth(value: String): Boolean = {
    value.toLowerCase().startsWith("session")
  }

  private[this] def log(
    request: ProxyRequest,
    server: Server,
    stage: String,
    attributes: Map[String, String]
  ): Unit = {
    // if canonical URL is in list of things that are noisy, do not log bodies
    val url = canonicalUrl(request).getOrElse("-")

    attributes.foldLeft(request.log) { case (l, el) =>
      el._1 match {
        case "body" if !Constants.logSanitizedBody(url) => l
        case _ => l.withKeyValue(el._1, el._2)
      }
    }.
      withKeyValue("server", server.name).
      withKeyValue("canonical_url", url).
      withKeyValue("request.contentLength" -> request.headers.get("Content-Length").getOrElse("-")).
      withKeyValue("request.contentType" -> request.contentType.toStringWithEncoding).
      info(s"[proxy ${org.joda.time.format.ISODateTimeFormat.dateTime.print(DateTime.now)} $request] $stage ${request.method} ${server.host}${request.pathWithQuery}")
  }

  private[this] def canonicalUrl(request: ProxyRequest): Option[String] = {
    apiBuilderServicesFetcher.multiService.operation(
      method = request.method.toString,
      path = request.path
    ).map(_.operation.path)
  }

  private[this] def safeBody(
    request: ProxyRequest,
    body: JsValue
  ): String = {
    val typ = multiService.bodyTypeFromPath(request.method.toString, request.path)
    body match {
      case j: JsObject if typ.isEmpty && j.value.isEmpty => "{}"
      case _: JsObject => toLogValue(request, body, typ).toString
      case _ => s"Body of type[${body.getClass.getName}] fully redacted"
    }
  }

  private[this] def safeBody(request: ProxyRequest, response: WSResponse): Option[String] = {
    Try(response.body) match {
      case Success(b) => Some(b)
      case Failure(e) =>
        request.log.warn("Error while retrieving response body. Returning empty body.", e)
        None
    }
  }
}