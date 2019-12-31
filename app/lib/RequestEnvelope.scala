package lib

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

  def validate(js: JsValue, requestHeaders: Headers): Either[List[String], RequestEnvelope] = {
    val validatedMethod = validateMethod(js)
    val validatedHeaders = validateHeaders(js)
    val validatedBody = validateBody(js)

    Seq(
      validatedMethod, validatedHeaders, validatedBody
    ).flatMap(_.left.getOrElse(Nil)).toList match {
      case Nil => Right(
        RequestEnvelope(
          method = validatedMethod.right.get,
          headers = merge(validatedHeaders.right.get, requestHeaders),
          body = validatedBody.right.get,
        )
      )
      case errors => Left(errors)
    }
  }

  def validateBody(js: JsValue): Either[Seq[String], Option[ProxyRequestBody.Json]] = {
    Right(
      (js \ Fields.Body).asOpt[JsValue].map(ProxyRequestBody.Json)
    )
  }

  def validateMethod(js: JsValue): Either[Seq[String], Method] = {
    (js \ Fields.Method).validateOpt[String] match {
      case JsError(_) => Left(Seq(s"Request envelope field '${Fields.Method}' must be a string"))
      case JsSuccess(value, _) => value match {
        case None => Left(Seq(s"Request envelope field '${Fields.Method}' is required"))
        case Some(v) => validateMethod(v)
      }
    }
  }

  private[this] def validateMethod(value: String): Either[Seq[String], Method] = {
    Method.fromString(value) match {
      case None => Left(Seq(s"Request envelope field '${Fields.Method}' must be one of ${Method.all.map(_.toString).mkString(", ")}"))
      case Some(m) => Right(m)
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
  def validateHeaders(js: JsValue): Either[Seq[String], Map[String, Seq[String]]] = {
    (js \ Fields.Headers).asOpt[JsValue] match {
      case None => Right(Map.empty)
      case Some(js) => {
        js.asOpt[Map[String, Seq[String]]] match {
          case Some(v) => Right(v)
          case None => {
            // handle simple k->v which is default serialization from JS libraries
            js.asOpt[Map[String, String]] match {
              case None => Left(Seq("Request envelope field 'headers' must be an object"))
              case Some(all) => Right(all.map { case (k, v) => k -> Seq(v) })
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
