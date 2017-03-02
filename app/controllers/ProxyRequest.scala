package controllers

import akka.util.ByteString
import java.nio.charset.Charset
import play.api.mvc.{Headers, RawBuffer, Request}

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

  private[this] val ValidMethods = Seq("POST", "PUT", "PATCH", "DELETE")

  def validate(request: Request[RawBuffer]): Either[Seq[String], ProxyRequest] = {
    val (method, methodErrors) = request.queryString.getOrElse("method", Nil).map(_.toUpperCase).toList match {
      case Nil => (request.method, Nil)

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

    val (envelopes, envelopeErrors) = request.queryString.getOrElse("envelope", Nil).map(_.toUpperCase).toList match {
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
      case Nil => Right(new ProxyRequest(request, method = method, envelopes = envelopes))
      case errors => Left(errors)
    }
  }
}

/**
  * @param method Either the 'request' query parameter, or default http method of the request
  * @param envelopes List of envelopes to use in the processing of the request
  */
class ProxyRequest(
  val request: Request[RawBuffer],
  val method: String,
  envelopes: Seq[Envelope]
) {
  // Provides the query string, minus the reserved fields for proxy
  val queryString: Map[String, Seq[String]] = queryString - "method" - "callback" - "envelope"
  val headers: Headers = request.headers

  val path: String = request.path
  val uri: String = request.uri

  val jsonpCallback: Option[String] = request.queryString.getOrElse("callback", Nil).headOption
  val responseEnvelope: Boolean = jsonpCallback.isDefined || envelopes.contains(Envelope.Response)

  val body: ProxyRequestBody= request.body.asBytes() match {
    case None => ProxyRequestBody.File(request.body.asFile)
    case Some(bytes) => ProxyRequestBody.Bytes(bytes)
  }

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
}


