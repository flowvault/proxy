package handlers

import controllers.ServerProxyDefinition
import io.apibuilder.spec.v0.models.ParameterLocation
import io.apibuilder.validation.MultiService
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

  def multiService: MultiService

  def buildRequest(
    definition: ServerProxyDefinition,
    request: ProxyRequest,
    route: Route,
    token: ResolvedToken
  ): WSRequest = {
    wsClient.url(definition.server.host + request.path)
      .withFollowRedirects(false)
      .withMethod(route.method)
      .withRequestTimeout(definition.requestTimeout)
      .addQueryStringParameters(
        definedQueryParameters(request, route): _*
      )
      .addHttpHeaders(
        proxyHeaders(definition, request, token).headers: _*
      )
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
      val typ = multiService.bodyTypeFromPath(request.method, request.path)
      val safeBody = body match {
        case j: JsObject if typ.isEmpty && j.value.isEmpty => "{}"
        case _: JsObject => toLogValue(request, body, typ)
        case _ => "{...} Body of type[${body.getClass.getName}] fully redacted"
      }
      Logger.info(s"[proxy $request] body type[${typ.getOrElse("unknown")}]: $safeBody")
    }
  }

  /**
    * Modifies headers by:
    *   - removing X-Flow-* headers if they were set
    *   - adding a default content-type
    */
  private[this] def proxyHeaders(
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

  /**
    * Returns the subset of query parameters that are documented as acceptable for this method
    */
  def definedQueryParameters(
    request: ProxyRequest,
    route: Route
  ): Seq[(String, String)] = {
    val allQueryParameters = request.queryParametersAsSeq()
    if (request.requestEnvelope) {
      // For request envelopes - we ONLY proxy parameters defined in the spec
      multiService.operation(route.method, route.path) match {
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
              Logger.info(s"[HandlerUtilities $request] Filtering out query parameter[$key] as it is not defined as part of the spec")
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
