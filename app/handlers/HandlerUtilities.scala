package handlers

import controllers.ServerProxyDefinition
import lib._
import play.api.Logger
import play.api.libs.json.{JsObject, JsValue, Json}
import play.api.libs.ws.{WSClient, WSRequest}
import play.api.mvc.Headers

import scala.util.{Failure, Success, Try}

trait HandlerUtilities extends Errors {

  def config: Config

  def flowAuth: FlowAuth

  def wsClient: WSClient

  def buildRequestApplicationJson(
    definition: ServerProxyDefinition,
    request: ProxyRequest,
    route: Route,
    token: ResolvedToken
  ): WSRequest = {
    baseRequest(definition, request, route)
      .addHttpHeaders(
        setApplicationJsonContentType(
          proxyHeaders(definition, request, token)
        ).headers: _*
      )
  }

  def buildRequest(
    definition: ServerProxyDefinition,
    request: ProxyRequest,
    route: Route,
    token: ResolvedToken
  ): WSRequest = {
    baseRequest(definition, request, route)
      .addHttpHeaders(
        proxyHeaders(definition, request, token).headers: _*
      )
  }

  private[this] def baseRequest(
    definition: ServerProxyDefinition,
    request: ProxyRequest,
    route: Route
  ): WSRequest = {
    wsClient.url(definition.server.host + request.path)
      .withFollowRedirects(false)
      .withMethod(route.method)
      .withRequestTimeout(definition.requestTimeout)
      .addQueryStringParameters(request.queryParametersAsSeq(): _*)
  }


  def log4xx(request: ProxyRequest, status: Int, body: String): Unit = {
    // GET too noisy due to bots
    if (request.method != "GET" && status >= 400 && status < 500) {
      val finalBody = Try {
        Json.parse(body)
      } match {
        case Success(js) => toLogValue(request, js, typ = None)
        case Failure(_) => body
      }

      Logger.info(s"[proxy $request] responded with status:$status: $finalBody")
    }
  }

  def log4xx(request: ProxyRequest, status: Int, js: JsValue, errors: Seq[String]): Unit = {
    // GET too noisy due to bots
    if (request.method != "GET" && status >= 400 && status < 500) {
      // TODO: PARSE TYPE
      val finalBody = toLogValue(request, js, typ = None)
      Logger.info(s"[proxy $request] responded with status:$status Invalid JSON: ${errors.mkString(", ")} BODY: $finalBody")
    }
  }

  def toLogValue(
    request: ProxyRequest,
    js: JsValue,
    typ: Option[String]
  ): JsValue = {
    if (config.isVerboseLogEnabled(request.path)) {
      js
    } else {
      LoggingUtil.logger.safeJson(js, typ = None)
    }
  }

  def logFormData(
    definition: ServerProxyDefinition,
    request: ProxyRequest,
    body: JsValue)
  : Unit = {
    if (request.method != "GET") {
      val typ = definition.multiService.bodyTypeFromPath(request.method, request.path)
      val safeBody = body match {
        case j: JsObject if typ.isEmpty && j.value.isEmpty => "{}"
        case _: JsObject => toLogValue(request, body, typ)
        case _ => "{...} Body of type[${body.getClass.getName}] fully redacted"
      }
      Logger.info(s"[proxy $request] body type[${typ.getOrElse("unknown")}]: $safeBody")
    }
  }

  /**
    * Overwrites the "Content-Type" header, setting a single
    * header for "Content-Type" with the value "application/json"
    */
  def setApplicationJsonContentType(
    headers: Headers
  ): Headers = {
    headers.
      remove("Content-Type").
      add("Content-Type" -> ContentType.ApplicationJson.toString)
  }


  /**
    * Modifies headers by:
    *   - removing X-Flow-* headers if they were set
    *   - adding a default content-type
    */
  def proxyHeaders(
    definition: ServerProxyDefinition,
    request: ProxyRequest,
    token: ResolvedToken
  ): Headers = {

    val headersToAdd = Seq(
      Constants.Headers.FlowServer -> definition.server.name,
      Constants.Headers.FlowRequestId -> request.requestId,
      Constants.Headers.Host -> definition.hostHeaderValue,
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
