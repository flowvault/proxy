package lib

import java.util.UUID

import play.api.Logger
import play.api.libs.json._
import play.api.mvc._

import scala.util.{Failure, Success, Try}

object ProxyRequest {

  val ReservedQueryParameters = Seq("method", "callback", "envelope")

  def validate(request: Request[RawBuffer]): Either[Seq[String], ProxyRequest] = {
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
  ): Either[Seq[String], ProxyRequest] = {
    val (method, methodErrors) = queryParameters.getOrElse("method", Nil).toList match {
      case Nil => (Some(Method(requestMethod)), Nil)

      case m :: Nil => {
        Method.fromString(m) match {
          case None => (None, Seq(s"Invalid value '$m' for query parameter 'method' - must be one of ${Method.all.map(_.toString).mkString(", ")}"))
          case Some(methodInstance) => (Some(methodInstance), Nil)
        }
      }

      case _ => {
        (None, Seq("Query parameter 'method', if specified, cannot be specified more than once"))
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
          method = method.get,
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
  method: Method,
  pathWithQuery: String,
  body: Option[ProxyRequestBody] = None,
  jsonpCallback: Option[String] = None,
  envelopes: Seq[Envelope] = Nil,
  queryParameters: Map[String, Seq[String]] = Map()
) extends Results with Errors {
  assert(
    ProxyRequest.ReservedQueryParameters.filter { queryParameters.isDefinedAt } == Nil,
    "Cannot provide query reserved parameters"
  )

  val requestId: String = headers.get(Constants.Headers.FlowRequestId).getOrElse {
    "api" + UUID.randomUUID.toString.replaceAll("-", "") // make easy to cut & paste
  }

  val createdAtMillis: Long = System.currentTimeMillis()

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

  val requestEnvelope: Boolean = envelopes.contains(Envelope.Request)

  /**
    * Returns the content type of the request. WS Client defaults to
    * application/octet-stream. Given this proxy is for APIs only,
    * assume application / JSON if no content type header is
    * provided.
    */
  val contentType: ContentType = headers.get("Content-Type").
    map(ContentType.apply).
    getOrElse(ContentType.ApplicationJson)

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

  override def toString: String = {
    s"id:$requestId $method $pathWithQuery"
  }

  def parseRequestEnvelope(): Either[Seq[String], ProxyRequest] = {
    assert(requestEnvelope, "method only valid if request envelope")

    Try {
      Json.parse(
        bodyUtf8.getOrElse {
          sys.error("Must have a body for request envelopes")
        }
      )
    } match {
      case Success(js) => {
        val (method, methodErrors) = parseMethod(js, "method") match {
          case Left(errors) => ("", errors)
          case Right(m) => (m, Nil)
        }

        val body = (js \ "body").asOpt[JsValue].map(ProxyRequestBody.Json)

        val headers: Map[String, Seq[String]] = (js \ "headers").getOrElse(Json.obj()).as[Map[String, Seq[String]]]

        methodErrors match {
          case Nil => ProxyRequest.validate(
            requestMethod = originalMethod,
            requestPath = path,
            body = body,
            queryParameters = queryParameters ++ Map(
              "method" -> Seq(method),
              Constants.Headers.FlowRequestId -> Seq(requestId)
            ),
            headers = Headers(Util.toFlatSeq(headers): _*)
          )
          case errors => Left(Seq(s"Error in envelope request body: ${errors.mkString(", ")}"))
        }
      }

      case Failure(_) => {
        Left(Seq("Envelope requests require a valid JSON body"))
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

  private[this] def internalResponse(
    status: Int,
    body: String,
    contentType: ContentType,
    headers: Map[String,Seq[String]] = Map()
  ): Result = {
    val responseHeaders = Util.removeKeys(
      headers,
      Seq(Constants.Headers.ContentLength, Constants.Headers.ContentType)
    )

    Status(status)(body).
      withHeaders(Util.toFlatSeq(responseHeaders): _*).
      as(contentType.toStringWithEncoding)
  }

  def responseUnauthorized(
    message: String,
    headers: Map[String,Seq[String]] = Map()
  ): Result = {
    responseError(401, message, headers)
  }

  def responseUnprocessableEntity(
    message: String,
    headers: Map[String,Seq[String]] = Map()
  ): Result = {
    responseError(422, message, headers)
  }

  private[this] def responseError(
    status: Int,
    message: String,
    headers: Map[String,Seq[String]] = Map()
  ): Result = {
    Logger.info(s"[proxy $toString] status:$status $message")

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

  private[this] def parseMethod(json: JsValue, field: String): Either[Seq[String], String] = {
    (json \ field).validateOpt[String] match {
      case JsError(_) => Left(Seq(s"Field '$field' must be one of ${Method.all.map(_.toString).mkString(", ")}"))
      case JsSuccess(value, _) => value match {
        case None => Left(Seq(s"Field '$field' is required"))
        case Some(v) => Right(v)
      }
    }
  }

}
