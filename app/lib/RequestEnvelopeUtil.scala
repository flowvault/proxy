package lib

import play.api.libs.json.{JsError, JsObject, JsSuccess, JsValue}
import play.api.mvc.Headers

case class RequestEnvelope(
  method: Method,
  headers: Headers,
  body: Option[ProxyRequestBody.Json],
)

object RequestEnvelopeUtil {

  object Fields {
    val Body = "body"
    val Method = "method"
    val Headers = "headers"
  }

  def validate(js: JsValue): Either[List[String], RequestEnvelope] = {
    val validatedMethod = RequestEnvelopeUtil.validateMethod(js)
    val validatedHeaders = RequestEnvelopeUtil.validateHeaders(js)
    val validatedBody = RequestEnvelopeUtil.validateBody(js)

    Seq(
      validatedMethod, validatedHeaders, validatedBody
    ).flatMap(_.left.getOrElse(Nil)).toList match {
      case Nil => Right(
        RequestEnvelope(
          method = validatedMethod.right.get,
          headers = validatedHeaders.right.get,
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
      case JsError(_) => Left(Seq(s"Field '${Fields.Method}' must be a string"))
      case JsSuccess(value, _) => value match {
        case None => Left(Seq(s"Field '${Fields.Method}' is required"))
        case Some(v) => validateMethod(v)
      }
    }
  }

  private[this] def validateMethod(value: String): Either[Seq[String], Method] = {
    Method.fromString(value) match {
      case None => Left(Seq(s"Field '${Fields.Method}' must be one of ${Method.all.map(_.toString).mkString(", ")}"))
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
  def validateHeaders(js: JsValue): Either[Seq[String], Headers] = {
    val all = (js \ Fields.Headers).asOpt[JsObject] match {
      case None => Nil
      case Some(headersJson) => {
        Util.toFlatSeq(headersJson.as[Map[String, Seq[String]]])
      }
    }
    Right(
      Headers(all: _*)
    )
  }

}
