package controllers

import java.nio.charset.Charset

import akka.util.ByteString
import play.api.mvc.{Headers, RawBuffer, Request}

sealed trait ProxyRequestBody
object ProxyRequestBody {
  val Utf8 = Charset.forName("UTF-8")

  case class Bytes(bytes: ByteString) extends ProxyRequestBody
  case class File(file: java.io.File) extends ProxyRequestBody
}

class ProxyRequest(
  request: Request[RawBuffer]
) {
  val headers: Headers = request.headers
  val queryString: Map[String, Seq[String]] = request.queryString
  val method: String = request.method
  val path: String = request.path
  val uri: String = request.uri

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


