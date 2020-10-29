package handlers

import io.apibuilder.validation.{AnyType, MultiService}
import io.flow.log.RollbarLogger
import lib._
import play.api.libs.json.{JsValue, Json}

trait HandlerUtilities extends Errors {

  def config: Config
  def logger: RollbarLogger

  def multiService: MultiService

  def toLogValue(
    request: ProxyRequest,
    js: JsValue,
    typ: Option[AnyType],
  ): JsValue = {
    if (config.isVerboseLogEnabled(request.path)) {
      js
    } else {
      typ match {
        case None => Json.obj("redacted" -> "object type not known. cannot log")
        case Some(_) => LoggingUtil(logger).logger.safeJson(js, anyType = typ)
      }
    }
  }

}
