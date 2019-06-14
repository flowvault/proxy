package handlers

import io.apibuilder.validation.{ApiBuilderType, MultiService}
import io.flow.log.RollbarLogger
import lib._
import play.api.libs.json.{JsValue, Json}

import scala.util.{Failure, Success, Try}

trait HandlerUtilities extends Errors {

  def config: Config
  def logger: RollbarLogger

  def multiService: MultiService

  def log4xx(request: ProxyRequest, status: Int, body: String): Unit = {
    // GET too noisy due to bots
    if (request.method != Method.Get && status >= 400 && status < 500) {
      val finalBody = Try {
        Json.parse(body)
      } match {
        case Success(js) => toLogValue(request, js, typ = None)
        case Failure(_) => body
      }

      request.log.
        fingerprint("Proxy4xx").
        withKeyValue("status", status).
        withKeyValue("body", finalBody.toString).
        info(s"[proxy $request] responded with status:$status")
    }
  }

  def log4xx(request: ProxyRequest, status: Int, js: JsValue, errors: Seq[String]): Unit = {
    // GET too noisy due to bots
    if (request.method != Method.Get && status >= 400 && status < 500) {
      // TODO: PARSE TYPE
      val finalBody = toLogValue(request, js, typ = None)
      logger.info(s"[proxy $request] responded with status:$status Invalid JSON: ${errors.mkString(", ")} BODY: $finalBody")
    }
  }

  def toLogValue(
    request: ProxyRequest,
    js: JsValue,
    typ: Option[ApiBuilderType]
  ): JsValue = {
    if (config.isVerboseLogEnabled(request.path)) {
      js
    } else {
      typ match {
        case None => Json.obj("redacted" -> "object type not known. cannot log")
        case Some(_) => LoggingUtil(logger).logger.safeJson(js, apiBuilderType = typ)
      }
    }
  }

}
