package lib

import java.nio.charset.Charset

import akka.util.ByteString
import play.api.libs.json.{JsValue, Json}
import play.api.mvc._

sealed trait ContentType
object ContentType {

  case object ApplicationJson extends ContentType { override def toString = "application/json" }
  case object UrlFormEncoded extends ContentType { override def toString = "application/x-www-form-urlencoded" }
  case class Other(name: String) extends ContentType { override def toString: String = name }

  val all = Seq(ApplicationJson, UrlFormEncoded)

  private[this]
  val byName = all.map(x => x.toString.toLowerCase -> x).toMap

  def apply(value: String): ContentType = fromString(value).getOrElse(Other(value))
  def fromString(value: String): Option[ContentType] = byName.get(value.toLowerCase)
}

sealed trait Envelope
object Envelope {
  case object Request extends Envelope { override def toString = "request" }
  case object Response extends Envelope { override def toString = "response" }

  val all = Seq(Request, Response)

  private[this]
  val byName = all.map(x => x.toString.toLowerCase -> x).toMap

  def fromString(value: String): Option[Envelope] = byName.get(value.toLowerCase)

}

sealed trait ProxyRequestBody
object ProxyRequestBody {
  val Utf8: Charset = Charset.forName("UTF-8")

  case class Bytes(bytes: ByteString) extends ProxyRequestBody
  case class File(file: java.io.File) extends ProxyRequestBody
}

object ProxyRequest {

  val ReservedQueryParameters = Seq("method", "callback", "envelope")

  private[this] val ValidMethods = Seq("POST", "PUT", "PATCH", "DELETE")

  def validate(request: Request[RawBuffer]): Either[Seq[String], ProxyRequest] = {
    validate(
      requestMethod = request.method,
      requestPath = request.uri,
      body = request.body.asBytes() match {
        case None => ProxyRequestBody.File(request.body.asFile)
        case Some(bytes) => ProxyRequestBody.Bytes(bytes)
      },
      queryParameters = request.queryString,
      headers = request.headers
    )
  }

  def validate(
    requestMethod: String,
    requestPath: String,
    body: ProxyRequestBody,
    queryParameters: Map[String, Seq[String]],
    headers: Headers
  ): Either[Seq[String], ProxyRequest] = {
    val (method, methodErrors) = queryParameters.getOrElse("method", Nil).toList match {
      case Nil => (requestMethod, Nil)

      case m :: Nil => {
        if (ValidMethods.contains(m.toUpperCase)) {
          (m, Nil)
        } else {
          (m, Seq(s"Invalid value '$m' for query parameter 'method' - must be one of ${ValidMethods.mkString(", ")}"))
        }
      }

      case m :: _ => {
        (m, Seq("Query parameter 'method', if specified, cannot be specified more than once"))
      }
    }

    val (envelopes, envelopeErrors) = queryParameters.getOrElse("envelope", Nil).toList match {
      case Nil => (Nil, Nil)

      case values => {
        values.filter(Envelope.fromString(_).isEmpty) match {
          case Nil => (values.flatMap(Envelope.fromString).distinct, Nil)
          case invalid => {
            val label = invalid match {
              case one :: Nil => s"Invalid value '$one'"
              case multiple => s"Invalid values ${multiple.mkString("'", "', '", "'")}"
            }
            (Nil, Seq(s"$label for query parameter 'envelope' - must be one of ${Envelope.all.map(_.toString).mkString(", ")}"))
          }
        }
      }
    }

    val (jsonpCallback, jsonpCallbackErrors) = queryParameters.getOrElse("callback", Nil).toList match {
      case Nil => (None, Nil)

      case cb :: Nil => {
        if (cb.trim.isEmpty) {
          (None, Seq("Callback query parameter, if specified, must be non empty"))

        } else if (isValidCallback(cb)) {
          (Some(cb), Nil)
        } else {
          (None, Seq("Callback query parameter, if specified, must contain only alphanumerics, '_' and '.' characters"))
        }
      }

      case _ => {
        (None, Seq("Query parameter 'callback', if specified, cannot be specified more than once"))
      }
    }

    methodErrors ++ envelopeErrors ++ jsonpCallbackErrors match {
      case Nil => Right(
        ProxyRequest(
          headers = headers,
          originalMethod = requestMethod,
          method = method.toUpperCase.trim,
          pathWithQuery = requestPath,
          body = body,
          queryParameters = queryParameters.filter { case (k, _) => !ReservedQueryParameters.contains(k) },
          envelopes = envelopes,
          jsonpCallback = jsonpCallback
        )
      )
      case errors => Left(errors)
    }
  }

  private[this] val CallbackPattern = """^[a-zA-Z0-9_\.]+$""".r

  def isValidCallback(name: String): Boolean = {
    name match {
      case CallbackPattern() => true
      case _ => false
    }
  }
}

/**
  * @param method Either the 'request' query parameter, or default http method of the request
  * @param envelopes List of envelopes to use in the processing of the request
  */
case class ProxyRequest(
  headers: Headers,
  originalMethod: String,
  method: String,
  pathWithQuery: String,
  body: ProxyRequestBody,
  jsonpCallback: Option[String] = None,
  envelopes: Seq[Envelope] = Nil,
  queryParameters: Map[String, Seq[String]] = Map()
) extends Results {
  assert(
    ProxyRequest.ReservedQueryParameters.filter { queryParameters.isDefinedAt } == Nil,
    "Cannot provide query reserved parameters"
  )

  assert(
    method.toUpperCase.trim == method,
    s"Method[$method] must be in uppercase, trimmed"
  )

  /**
    * path is everything up to the ? - e.g. /users/
    */
  val path: String = {
    val i = pathWithQuery.indexOf('?')
    if (i < 0) {
      pathWithQuery
    } else {
      pathWithQuery.substring(0, i)
    }
  }

  /**
    * responseEnvelope is true for all requests with a jsonp callback as well
    * as requests that explicitly request an envelope
    */
  val responseEnvelope: Boolean = jsonpCallback.isDefined || envelopes.contains(Envelope.Response)

  /**
    * Returns the content type of the request. WS Client defaults to
    * application/octet-stream. Given this proxy is for APIs only,
    * assume application / JSON if no content type header is
    * provided.
    */
  val contentType: ContentType = headers.get("Content-Type").map(ContentType.apply).getOrElse(ContentType.ApplicationJson)

  /**
    * Assumes the body is a byte array, returning the string value as a UTF-8
    * encoded string.
    */
  def bodyUtf8: Option[String] = {
    body match {
      case ProxyRequestBody.Bytes(bytes) => Some(bytes.decodeString(ProxyRequestBody.Utf8))
      case ProxyRequestBody.File(_) => None
    }
  }

  def queryParametersAsSeq(): Seq[(String, String)] = {
    queryParameters.flatMap { case (name, values) =>
      values.map { v => (name, v) }
    }.toSeq
  }


  /**
    * See https://support.cloudflare.com/hc/en-us/articles/200170986-How-does-CloudFlare-handle-HTTP-Request-headers-
    */
  def clientIp(): Option[String] = {
    headers.get("cf-connecting-ip") match {
      case Some(ip) => Some(ip)
      case None => headers.get("true-client-ip") match {
        case Some(ip) => Some(ip)
        case None => {
          // Sometimes we see an ip in forwarded-for header even if not in other
          // ip related headers
          headers.get("X-Forwarded-For").flatMap { ips =>
            ips.split(",").headOption
          }
        }
      }
    }
  }

  override def toString: String = {
    s"$method $pathWithQuery"
  }

  /**
    * Returns a valid play result, taking into account any requests for response envelopes
    */
  def response(status: Int, body: String, headers: Map[String,Seq[String]] = Map()): Result = {
    if (responseEnvelope) {
      Ok(wrappedResponseBody(status, body, headers)).as("application/javascript; charset=utf-8")
    } else {
      val h: Seq[(String, String)] = headers.flatMap { case (k, values) =>
          values.map { v => (k, v) }
      }.toSeq
      Status(status)(body).withHeaders(h: _*)
    }
  }

  /**
  * Wraps the specified response body based on the requested wrappers
  */
  private[this] def wrappedResponseBody(status: Int, body: String, headers: Map[String,Seq[String]] = Map()): String = {
    val env = envelopeBody(status, body, headers)
    jsonpCallback.fold(env)(jsonpEnvelopeBody(_, env))
  }

  /**
    * Create the envelope to passthrough response status, response headers
    */
  private[this] def envelopeBody(
    status: Int,
    body: String,
    headers: Map[String,Seq[String]] = Map()
  ): String = {
    val jsonHeaders = Json.toJson(headers)
    s"""{\n  "status": $status,\n  "headers": ${jsonHeaders},\n  "body": $body\n}"""
  }

  /**
    * Create the jsonp envelope to passthrough response status, response headers
    */
  private[this] def jsonpEnvelopeBody(
    callback: String,
    body: String
  ): String = {
    // Prefix /**/ is to avoid a JSONP/Flash vulnerability
    "/**/" + s"""$callback($body)"""
  }
}
