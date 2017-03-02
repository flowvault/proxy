package lib

import java.nio.charset.Charset

import akka.util.ByteString
import play.api.mvc.{Headers, RawBuffer, Request}

sealed trait ContentType
object ContentType {

  case object ApplicationJson extends ContentType { override def toString = "application/json" }
  case object UrlFormEncoded extends ContentType { override def toString = "application/x-www-form-urlencoded" }
  case class Other(name: String) extends ContentType { override def toString = name }

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
      headers = request.headers,
      jsonpCallback = request.queryString.getOrElse("callback", Nil).headOption
    )
  }

  def validate(
    requestMethod: String,
    requestPath: String,
    body: ProxyRequestBody,
    queryParameters: Map[String, Seq[String]],
    headers: Headers,
    jsonpCallback: Option[String]
  ): Either[Seq[String], ProxyRequest] = {
    val (method, methodErrors) = queryParameters.getOrElse("method", Nil).map(_.toUpperCase).toList match {
      case Nil => (requestMethod, Nil)

      case m :: Nil => {
        if (ValidMethods.contains(m)) {
          (m, Nil)
        } else {
          (m, Seq(s"Invalid value for parameter 'method' - must be one of ${ValidMethods.mkString(", ")}"))
        }
      }

      case m :: _ => {
        (m, Seq(s"Only a single value for parameter 'method' can be specified"))
      }
    }

    val (envelopes, envelopeErrors) = queryParameters.getOrElse("envelope", Nil).toList match {
      case Nil => (Nil, Nil)

      case values => {
        val (invalid, valid) = values.map(Envelope.fromString).partition(_.isEmpty)
        if (invalid.isEmpty) {
          (valid.flatten.distinct, Nil)
        } else {
          (valid.flatten, Seq(s"Invalid value for parameter 'envelope' - must be one of ${Envelope.all.map(_.toString).mkString(", ")}"))
        }
      }
    }

    methodErrors ++ envelopeErrors match {
      case Nil => Right(
        ProxyRequest(
          headers = headers,
          originalMethod = requestMethod,
          method = method,
          path = requestPath,
          body = body,
          queryParameters = queryParameters.filter { case (k, _) => !ReservedQueryParameters.contains(k) },
          envelopes = envelopes
        )
      )
      case errors => Left(errors)
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
  path: String,
  body: ProxyRequestBody,
  jsonpCallback: Option[String] = None,
  envelopes: Seq[Envelope] = Nil,
  queryParameters: Map[String, Seq[String]] = Map()
) {
  assert(
    ProxyRequest.ReservedQueryParameters.filterNot { queryParameters.isDefinedAt } == Nil,
    "Cannot provide query reserved parameters"
  )

  assert(
    method.toUpperCase.trim == method,
    "Method[$method] must be in uppercase, trimmed"
  )

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
    s"$method $path"
  }

}
