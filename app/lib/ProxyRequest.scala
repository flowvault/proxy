package lib

import java.util.UUID

import cats.data.Validated.{Invalid, Valid}
import cats.data.ValidatedNec
import cats.implicits._
import io.flow.log.RollbarLogger
import play.api.libs.json._
import play.api.mvc._

import scala.util.{Failure, Success, Try}

object ProxyRequest {

  val ReservedQueryParameters: Seq[String] = Seq("method", "callback", "envelope")

  def validate(request: Request[RawBuffer])(implicit logger: RollbarLogger): ValidatedNec[String, ProxyRequest] = {
    validate(
      requestMethod = request.method,
      requestPath = request.path,
      body = Some(
        request.body.asBytes() match {
          case None => ProxyRequestBody.File(request.body.asFile)
          case Some(bytes) => ProxyRequestBody.Bytes(bytes)
        }
      ),
      queryParameters = request.queryString,
      headers = request.headers
    )
  }

  def validate(
    requestMethod: String,
    requestPath: String,
    body: Option[ProxyRequestBody],
    queryParameters: Map[String, Seq[String]],
    headers: Headers
  )(implicit logger: RollbarLogger): ValidatedNec[String, ProxyRequest] = {
    val methodV = queryParameters.getOrElse("method", Nil).toList match {
      case Nil => Method(requestMethod).validNec

      case m :: Nil => {
        Method.fromString(m) match {
          case None => s"Invalid value '$m' for query parameter 'method' - must be one of ${Method.all.map(_.toString).mkString(", ")}".invalidNec
          case Some(methodInstance) => methodInstance.validNec
        }
      }

      case _ => {
        "Query parameter 'method', if specified, cannot be specified more than once".invalidNec
      }
    }

    val envelopesV = queryParameters.getOrElse("envelope", Nil).toList match {
      case Nil => Nil.validNec

      case values => {
        values.filter(Envelope.fromString(_).isEmpty) match {
          case Nil => values.flatMap(Envelope.fromString).distinct.validNec
          case invalid => {
            val label = invalid match {
              case one :: Nil => s"Invalid value '$one'"
              case multiple => s"Invalid values ${multiple.mkString("'", "', '", "'")}"
            }
            s"$label for query parameter 'envelope' - must be one of ${Envelope.all.map(_.toString).mkString(", ")}".invalidNec
          }
        }
      }
    }

    val jsonpCallbackV = queryParameters.getOrElse("callback", Nil).toList match {
      case Nil => None.validNec

      case cb :: Nil => {
        if (cb.trim.isEmpty) {
          "Callback query parameter, if specified, must be non empty".invalidNec

        } else if (isValidCallback(cb)) {
          Some(cb).validNec
        } else {
          "Callback query parameter, if specified, must contain only alphanumerics, '_' and '.' characters".invalidNec
        }
      }

      case _ => {
        "Query parameter 'callback', if specified, cannot be specified more than once".invalidNec
      }
    }

    /**
      * Returns the content type of the request. WS Client defaults to
      * application/octet-stream. Given this proxy is for APIs only,
      * assume application / JSON if no content type header is
      * provided.
      */
    val contentType: ContentType = headers.get("Content-Type").
      map(ContentType.apply).
      getOrElse(ContentType.ApplicationJson)

    (methodV, envelopesV, jsonpCallbackV).mapN { case (method, envelopes, jsonpCallback) =>
      ProxyRequest(
        headers = headers,
        originalMethod = requestMethod,
        method = method,
        pathWithQuery = requestPath,
        contentType = contentType,
        body = body,
        queryParameters = queryParameters.filter { case (k, _) => !ReservedQueryParameters.contains(k) },
        envelopes = envelopes,
        jsonpCallback = jsonpCallback
      )
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
  method: Method,
  pathWithQuery: String,
  contentType: ContentType,
  body: Option[ProxyRequestBody] = None,
  jsonpCallback: Option[String] = None,
  envelopes: Seq[Envelope] = Nil,
  queryParameters: Map[String, Seq[String]] = Map()
)(implicit logger: RollbarLogger) extends Results with Errors {
  assert(
    ProxyRequest.ReservedQueryParameters.filter { queryParameters.isDefinedAt } == Nil,
    "Cannot provide query reserved parameters"
  )

  val requestId: String = headers.get(Constants.Headers.FlowRequestId).getOrElse {
    "api" + UUID.randomUUID.toString.replaceAll("-", "") // make easy to cut & paste
  }

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
    * rawQueryString is everything after the ? - will return None
    * if empty
    */
  val rawQueryString: Option[String] = {
    val i = pathWithQuery.indexOf('?')
    if (i < 0) {
      None
    } else {
      Some(pathWithQuery.substring(i))
    }
  }

  /**
    * responseEnvelope is true for all requests with a jsonp callback as well
    * as requests that explicitly request an envelope
    */
  val responseEnvelope: Boolean = jsonpCallback.isDefined || envelopes.contains(Envelope.Response)

  val requestEnvelope: Boolean = envelopes.contains(Envelope.Request)

  /**
    * Assumes the body is a byte array, returning the string value as a UTF-8
    * encoded string.
    */
  def bodyUtf8: Option[String] = {
    body.flatMap {
      case ProxyRequestBody.Bytes(bytes) => Some(bytes.decodeString(ProxyRequestBody.Utf8))
      case ProxyRequestBody.Json(json) => Some(json.toString)
      case ProxyRequestBody.File(_) => None
    }
  }

  def queryParametersAsSeq(): Seq[(String, String)] = Util.toFlatSeq(queryParameters)

  /**
    * See https://support.cloudflare.com/hc/en-us/articles/200170986-How-does-CloudFlare-handle-HTTP-Request-headers-
    */
  def clientIp(): Option[String] = {
    headers.get(Constants.Headers.CfConnectingIp) match {
      case Some(ip) => Some(ip)
      case None => headers.get(Constants.Headers.CfTrueClientIp) match {
        case Some(ip) => Some(ip)
        case None => {
          // Sometimes we see an ip in forwarded-for header even if not in other
          // ip related headers
          headers.get(Constants.Headers.ForwardedFor).flatMap { ips =>
            ips.split(",").headOption
          }
        }
      }
    }
  }

  def log: RollbarLogger = {
    logger.
      requestId(requestId).
      withKeyValue("method", method.toString).
      withKeyValue("path_with_query", pathWithQuery)
  }

  override def toString: String = {
    s"id:$requestId $method $pathWithQuery"
  }

  def parseRequestEnvelope(): ValidatedNec[String, ProxyRequest] = {
    assert(requestEnvelope, "method only valid if request envelope")

    Try {
      Json.parse(
        bodyUtf8.getOrElse {
          sys.error("Must have a body for request envelopes")
        }
      )
    } match {
      case Failure(_) => {
        "Envelope requests require a valid JSON body".invalidNec
      }
      case Success(js) => {
        RequestEnvelope.validate(js, headers) match {
          case Valid(env) => {
            ProxyRequest.validate(
              requestMethod = originalMethod,
              requestPath = path,
              body = env.body,
              queryParameters = queryParameters ++ Map(
                "method" -> Seq(env.method.toString),
                Constants.Headers.FlowRequestId -> Seq(requestId)
              ),
              headers = env.headers,
            )
          }
          case Invalid(errors) => {
            s"Error in envelope request body: ${errors.toList.mkString(", ")}".invalidNec
          }
        }
      }
    }
  }

  /**
  * Returns a valid play result, taking into account any requests for response envelopes
  */
  def response(
    status: Int,
    body: String,
    contentType: ContentType,
    headers: Map[String,Seq[String]] = Map()
  ): Result = {
    if (responseEnvelope) {
      internalResponse(
        200,
        wrappedResponseBody(status, body, headers),
        contentType = ContentType.ApplicationJavascript,
        headers
      )
    } else {
      internalResponse(
        status,
        body,
        contentType,
        headers
      )
    }
  }

  private[this] val HeadersToRemove = Set(Constants.Headers.ContentLength, Constants.Headers.ContentType)
  private[this] def internalResponse(
    status: Int,
    body: String,
    contentType: ContentType,
    headers: Map[String,Seq[String]]
  ): Result = {
    val responseHeaders = Util.removeKeys(headers, HeadersToRemove)

    Status(status)(body).
      withHeaders(Util.toFlatSeq(responseHeaders): _*).
      as(contentType.toStringWithEncoding)
  }

  def responseUnauthorized(
    message: String
  ): Result = {
    responseError(401, message, Map.empty)
  }

  def responseUnprocessableEntity(
    message: String,
    headers: Map[String,Seq[String]] = Map.empty
  ): Result = {
    responseError(422, message, headers)
  }

  private[this] def responseError(
    status: Int,
    message: String,
    headers: Map[String,Seq[String]]
  ): Result = {
    log.
      fingerprint("ProxyResponseError").
      info(s"[proxy $toString] status:$status request.contentType:${contentType.toStringWithEncoding} $message")

    response(
      status = status,
      body = genericError(message).toString,
      ContentType.ApplicationJson,
      headers = headers
    )
  }

  /**
   * Wraps the specified response body based on the requested wrappers
   */
  private[this] def wrappedResponseBody(status: Int, body: String, headers: Map[String,Seq[String]]): String = {
    val env = envelopeBody(status, body, headers)
    jsonpCallback.fold(env)(jsonpEnvelopeBody(_, env))
  }

  /**
    * Create the envelope to passthrough response status, response headers
    */
  private[this] def envelopeBody(
    status: Int,
    body: String,
    headers: Map[String,Seq[String]]
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
