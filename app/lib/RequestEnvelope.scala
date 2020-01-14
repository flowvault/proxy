package lib

import cats.data.ValidatedNec
import cats.implicits._
import play.api.libs.json.{JsError, JsSuccess, JsValue}
import play.api.mvc.Headers

case class RequestEnvelope(
  method: Method,
  headers: Headers,
  body: Option[ProxyRequestBody.Json],
)

object RequestEnvelope {

  object Fields {
    val Body = "body"
    val Method = "method"
    val Headers = "headers"
  }

  def validate(js: JsValue, requestHeaders: Headers): ValidatedNec[String, RequestEnvelope] = {
    val validatedMethod = validateMethod(js)
    val validatedHeaders = validateHeaders(js)
    val validatedBody = validateBody(js)

    (
      validatedMethod, validatedHeaders, validatedBody
    ).mapN { case (method, headers, body) =>
      RequestEnvelope(
        method = method,
        headers = merge(headers, requestHeaders),
        body = body,
      )
    }
  }

  private[lib] def validateBody(js: JsValue): ValidatedNec[String, Option[ProxyRequestBody.Json]] = {
    (js \ Fields.Body).asOpt[JsValue].map(ProxyRequestBody.Json).validNec
  }

  private[lib] def validateMethod(js: JsValue): ValidatedNec[String, Method] = {
    (js \ Fields.Method).validateOpt[String] match {
      case JsError(_) => s"Request envelope field '${Fields.Method}' must be a string".invalidNec
      case JsSuccess(value, _) => value match {
        case None => s"Request envelope field '${Fields.Method}' is required".invalidNec
        case Some(v) => validateMethod(v)
      }
    }
  }

  private[this] def validateMethod(value: String): ValidatedNec[String, Method] = {
    Method.fromString(value) match {
      case None => s"Request envelope field '${Fields.Method}' must be one of ${Method.all.map(_.toString).mkString(", ")}".invalidNec
      case Some(m) => m.validNec
    }
  }

  /**
   * Read the headers from either:
   *   a. the json envelope if specified
   *   b. the original request headers
   * We handle two types of formats here for headers:
   * (from javascript libraries):
   *   { "name": "value1" }
   *   { "name": "value2" }
   * and
   * (from play libraries):
   *   { "name": ["value1", "value2"] }
   */
  private[lib] def validateHeaders(js: JsValue): ValidatedNec[String, Map[String, Seq[String]]] = {
    (js \ Fields.Headers).asOpt[JsValue] match {
      case None => Map.empty.validNec
      case Some(js) => {
        js.asOpt[Map[String, Seq[String]]] match {
          case Some(v) => v.validNec
          case None => {
            // handle simple k->v which is default serialization from JS libraries
            js.asOpt[Map[String, String]] match {
              case None => "Request envelope field 'headers' must be an object".invalidNec
              case Some(all) => all.map { case (k, v) => k -> Seq(v) }.validNec
            }
          }
        }
      }
    }
  }

  private[this] def merge(envelopeHeaders: Map[String, Seq[String]], requestHeaders: Headers): Headers = {
    Headers(
      Util.toFlatSeq(
        safeHeaders(requestHeaders) ++ envelopeHeaders
      ): _*
    )
  }

  private[this] val WhitelistHeaders = Constants.Headers.namesToWhitelist ++ Seq("Authorization")
  private[this] def safeHeaders(headers: Headers): Map[String, Seq[String]] = {
    headers.toMap.filter { case (k, _) => WhitelistHeaders.contains(k) }
  }
}
