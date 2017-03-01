package controllers

import akka.actor.ActorSystem
import com.google.inject.AbstractModule
import com.google.inject.assistedinject.{Assisted, FactoryModuleBuilder}
import io.flow.lib.apidoc.json.validation.FormData
import java.net.URI
import javax.inject.Inject

import actors.MetricActor
import com.bryzek.apidoc.spec.v0.models.ParameterLocation
import play.api.Logger
import play.api.libs.ws.{StreamedResponse, WSClient}
import play.api.mvc._

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}
import play.api.http.HttpEntity
import lib._
import play.api.libs.json.{JsObject, JsString, JsValue, Json}

case class ServerProxyDefinition(
  server: Server,
  multiService: io.flow.lib.apidoc.json.validation.MultiService // TODO Move higher level
) {

  val contextName: String = s"${server.name}-context"

  val hostHeaderValue: String = Option(new URI(server.host).getHost).getOrElse {
    sys.error(s"Could not parse host from server[$server]")
  }

  /**
    * Returns the subset of query parameters that are documented as acceptable for this method
    */
  def definedQueryParameters(
    method: String,
    path: String,
    allQueryParameters: Seq[(String, String)]
  ): Seq[(String, String)] = {
    multiService.parametersFromPath(method, path) match {
      case None => {
        allQueryParameters
      }

      case Some(parameters) => {
        val definedNames = parameters.filter { p =>
          p.location == ParameterLocation.Query
        }.map(_.name)
        allQueryParameters.filter { case (key, _) => definedNames.contains(key) }
      }
    }
  }

}

/**
  * Server Proxy is responsible for proxying all requests to a given
  * server. The primary purpose of the proxy is to segment our thread
  * pools by server - so if one server is having difficulty, it is
  * less likely to impact other servers.
  */
trait ServerProxy {

  def definition: ServerProxyDefinition

  def proxy(
    requestId: String,
    request: ProxyRequest,
    route: Route,
    token: Option[ResolvedToken],
    organization: Option[String] = None,
    partner: Option[String] = None
  ): Future[play.api.mvc.Result]

}

object ServerProxy {

  val DefaultContextName = s"default-server-context"

  trait Factory {
    def apply(definition: ServerProxyDefinition): ServerProxy
  }

  /**
    *  Maps a query string that may contain multiple values per parameter
    *  to a sequence of query parameters.
    *
    *  @todo Add example query string
    *  @example
    *  {{{
    *    query(
    *      Map[String, Seq[String]](
    *        "foo" -> Seq("a", "b"),
    *        "foo2" -> Seq("c")
    *      )
    *    ) == Seq(
    *      ("foo", "a"),
    *      ("foo", "b"),
    *      ("foo2", "c")
    *    )
    *  }}}
    *
    *  @param incoming A map of query parameter keys to sequences of their values.
    *  @return A sequence of keys, each paired with exactly one value. The keys are further
    *          normalized to match Flow expectations (e.g. number[] => number)
    */
  def query(
    incoming: Map[String, Seq[String]]
  ): Seq[(String, String)] = {
    val rewritten = FormData.parseEncoded(FormData.toEncoded(FormData.toJson(incoming)))
    rewritten.map { case (k, vs) =>
      vs.map(k -> _)
    }.flatten.toSeq
  }
}

class ServerProxyModule extends AbstractModule {
  def configure {
    install(new FactoryModuleBuilder()
      .implement(classOf[ServerProxy], classOf[ServerProxyImpl])
      .build(classOf[ServerProxy.Factory])
    )
  }
}

class ServerProxyImpl @Inject () (
  @javax.inject.Named("metric-actor") val actor: akka.actor.ActorRef,
  system: ActorSystem,
  ws: WSClient,
  flowAuth: FlowAuth,
  @Assisted override val definition: ServerProxyDefinition
) extends ServerProxy with Controller with lib.Errors {

  private[this] implicit val (ec, name) = {
    val name = definition.contextName
    Try {
      system.dispatchers.lookup(name)
    } match {
      case Success(ec) => {
        Logger.info(s"ServerProxy[${definition.server.name}] using configured execution context[$name]")
        (ec, name)
      }

      case Failure(_) => {
        Logger.warn(s"ServerProxy[${definition.server.name}] execution context[${name}] not found - using ${ServerProxy.DefaultContextName}")
        (system.dispatchers.lookup(ServerProxy.DefaultContextName), ServerProxy.DefaultContextName)
      }
    }
  }

  val executionContextName: String = name

  private[this] val ApplicationJsonContentType = "application/json"
  private[this] val UrlFormEncodedContentType = "application/x-www-form-urlencoded"

  // WS Client defaults to application/octet-stream. Given this proxy
  // is for APIs only, assume JSON if no content type header is
  // provided.
  private[this] val DefaultContentType = ApplicationJsonContentType

  override final def proxy(
    requestId: String,
    request: ProxyRequest,
    route: Route,
    token: Option[ResolvedToken],
    organization: Option[String] = None,
    partner: Option[String] = None
  ) = {
    Logger.info(s"[proxy] ${request.method} ${request.path} to [${definition.server.name}] ${route.method} ${definition.server.host}${request.path} requestId $requestId")

    /**
      * Common stuff between standard and envelope implementations
      */
    val finalHeaders: Headers = proxyHeaders(requestId, request.headers, request.method, token)
    val originalQuery: Map[String, Seq[String]] = request.queryString
    val finalQuery: Seq[(String, String)] = ServerProxy.query(originalQuery)

    val (originalPathWithQuery, rewrittenPathWithQuery): (String, String) = if (originalQuery.isEmpty) {
      (request.path, request.path)
    } else {
      val rewritten = finalQuery.map { case (key, value) =>
        s"$key=$value"
      }.mkString("&")
      (request.uri, s"${request.path}?$rewritten")
    }

    /**
      * Choose the type of request based on callback/envelope or standard implementation
      */
    request.queryString.getOrElse("callback", Nil).headOption match {
      case Some(callback) => {
        envelopeRequest(finalHeaders, finalQuery, originalPathWithQuery, requestId, request, route, token, callback = Some(callback), organization = organization, partner = partner)
      }

      case None => {
        request.queryString.getOrElse("envelope", Nil).headOption match {
          case Some("response") => {
            envelopeRequest(finalHeaders, finalQuery, originalPathWithQuery,requestId, request, route, token, organization = organization, partner = partner)
          }
          case None => {
            standard(finalHeaders, finalQuery, originalPathWithQuery, rewrittenPathWithQuery, requestId, route, request, token, organization = organization, partner = partner)
          }
        }
      }
    }
  }

  private[this] def envelopeRequest(
    finalHeaders: Headers,
    finalQuery: Seq[(String, String)],
    originalPathWithQuery: String,
    requestId: String,
    request: ProxyRequest,
    route: Route,
    token: Option[ResolvedToken],
    callback: Option[String] = None,
    organization: Option[String] = None,
    partner: Option[String] = None
  ) = {
    val finalHeaders = proxyHeaders(requestId, request.headers, request.method, token)

    val formData: JsValue = callback match {
      case Some(_) => {
        FormData.toJson(request.queryString - "method" - "callback")
      }
      case None => {
        finalHeaders.get("Content-Type").getOrElse(DefaultContentType) match {
          // We turn url form encoded into application/json
          case UrlFormEncodedContentType => {
            val b: String = request.bodyUtf8.get
            FormData.toJson(FormData.parseEncoded(b))
          }
          case ApplicationJsonContentType => {
            val b: String = request.bodyUtf8.get
            Json.parse(b)
          }
        }
      }
    }

    logFormData(requestId, request.path, route, formData)

    definition.multiService.upcast(route.method, route.path, formData) match {
      case Left(errors) => {
        val envBody = envelopeBody(422, Map(), genericErrors(errors).toString)
        val finalBody = callback match {
          case Some(cb) => jsonpEnvelopeBody(cb, envBody)
          case None => envBody
        }

        Logger.info(s"[proxy] ${request.method} ${request.path} ${definition.server.name}:${route.method} ${definition.server.host}${request.path} 422 based on apidoc schema")
        Future(Ok(finalBody).as("application/javascript; charset=utf-8"))
      }

      case Right(body) => {
        val finalHeaders = setContentType(proxyHeaders(requestId, request.headers, request.method, token), ApplicationJsonContentType)

        val req = ws.url(definition.server.host + request.path)
          .withFollowRedirects(false)
          .withMethod(route.method)
          .withHeaders(finalHeaders.headers: _*)
          .withQueryString(definition.definedQueryParameters(route.method, route.path, finalQuery): _*)
          .withBody(body)

        val startMs = System.currentTimeMillis
        req.execute.map { response =>
          val timeToFirstByteMs = System.currentTimeMillis - startMs

          val envBody = envelopeBody(response.status, response.allHeaders, response.body)
          val finalBody = callback match {
            case Some(cb) => jsonpEnvelopeBody(cb, envBody)
            case None => envBody
          }

          actor ! MetricActor.Messages.Send(definition.server.name, route.method, route.path, timeToFirstByteMs, response.status, organization, partner)
          Logger.info(s"[proxy] ${request.method} ${request.path} ${definition.server.name}:${route.method} ${definition.server.host}${request.path} ${response.status} ${timeToFirstByteMs}ms requestId $requestId")
          Ok(finalBody).as("application/javascript; charset=utf-8")
        }.recover {
          case ex: Throwable => {
            throw new Exception(ex)
          }
        }
      }
    }
  }

  /**
    * Create the envelope to passthrough response status, response headers
    */
  private[this] def envelopeBody(
    status: Int,
    headers: Map[String,Seq[String]],
    body: String
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

  private[this] def standard(
    finalHeaders: Headers,
    finalQuery: Seq[(String, String)],
    originalPathWithQuery: String,
    rewrittenPathWithQuery: String,
    requestId: String,
    route: Route,
    request: ProxyRequest,
    token: Option[ResolvedToken],
    organization: Option[String] = None,
    partner: Option[String] = None
  ) = {
    val req = ws.url(definition.server.host + request.path)
      .withFollowRedirects(false)
      .withMethod(route.method)
      .withQueryString(finalQuery: _*)

    val response = finalHeaders.get("Content-Type").getOrElse(DefaultContentType) match {

      // We turn url form encoded into application/json
      case UrlFormEncodedContentType => {
        val b: String = request.bodyUtf8.get
        val newBody = FormData.toJson(FormData.parseEncoded(b))

        logFormData(requestId, request.path, route, newBody)

        definition.multiService.upcast(route.method, route.path, newBody) match {
          case Left(errors) => {
            Logger.info(s"[proxy] ${request.method} $originalPathWithQuery 422 based on apidoc schema")
            Future(
              UnprocessableEntity(
                genericErrors(errors)
              ).withHeaders("X-Flow-Proxy-Validation" -> "apidoc")
            )
          }

          case Right(validatedBody) => {
            req
              .withHeaders(setContentType(finalHeaders, ApplicationJsonContentType).headers: _*)
              .withBody(validatedBody)
              .stream
              .recover { case ex: Throwable => throw new Exception(ex) }
          }
        }
      }

      case ApplicationJsonContentType => {
        val body = request.bodyUtf8.get
        Try {
          if (body.trim.isEmpty) {
            // e.g. PUT/DELETE with empty body
            Json.obj()
          } else {
            Json.parse(body)
          }
        } match {
          case Failure(e) => {
            Logger.info(s"[proxy] ${request.method} $originalPathWithQuery 422 invalid json")
            Future(
              UnprocessableEntity(
                genericError(s"The body of an application/json request must contain valid json: ${e.getMessage}")
              ).withHeaders("X-Flow-Proxy-Validation" -> "proxy")
            )
          }

          case Success(js) => {
            logFormData(requestId, request.path, route, js)

            definition.multiService.upcast(route.method, route.path, js) match {
              case Left(errors) => {
                Logger.info(s"[proxy] ${request.method} $originalPathWithQuery 422 based on apidoc schema")
                Future(
                  UnprocessableEntity(
                    genericErrors(errors)
                  ).withHeaders("X-Flow-Proxy-Validation" -> "apidoc")
                )
              }

              case Right(validatedBody) => {
                req
                  .withHeaders(setContentType(finalHeaders, ApplicationJsonContentType).headers: _*)
                  .withBody(validatedBody)
                  .stream
                  .recover { case ex: Throwable => throw new Exception(ex) }
              }
            }
          }
        }
      }

      case _ => {
        request.body match {
          case ProxyRequestBody.File(file) => {
            req
              .withHeaders(finalHeaders.headers: _*)
              .post(file)
              .recover { case ex: Throwable => throw new Exception(ex) }
          }

          case ProxyRequestBody.Bytes(bytes) => {
            req
              .withHeaders(finalHeaders.headers: _*)
              .withBody(bytes)
              .stream
              .recover { case ex: Throwable => throw new Exception(ex) }
          }
        }
      }
    }

    val startMs = System.currentTimeMillis

    response.map {
      case r: Result => {
        r
      }

      case StreamedResponse(r, body) => {
        val timeToFirstByteMs = System.currentTimeMillis - startMs
        val contentType: Option[String] = r.headers.get("Content-Type").flatMap(_.headOption)
        val contentLength: Option[Long] = r.headers.get("Content-Length").flatMap(_.headOption).flatMap(toLongSafe)

        actor ! MetricActor.Messages.Send(definition.server.name, route.method, route.path, timeToFirstByteMs, r.status, organization, partner)
        Logger.info(s"[proxy] ${request.method} $originalPathWithQuery ${definition.server.name}:${route.method} ${definition.server.host}$rewrittenPathWithQuery ${r.status} ${timeToFirstByteMs}ms requestId $requestId")

        // If there's a content length, send that, otherwise return the body chunked
        contentLength match {
          case Some(length) => {
            Status(r.status).sendEntity(HttpEntity.Streamed(body, Some(length), contentType))
          }

          case None => {
            contentType match {
              case None => Status(r.status).chunked(body)
              case Some(ct) => Status(r.status).chunked(body).as(ct)
            }
          }
        }
      }

      case r: play.api.libs.ws.ahc.AhcWSResponse => {
        Status(r.status)(r.body)
      }

      case other => {
        sys.error("Unhandled response: " + other.getClass.getName)
      }
    }
  }

  /**
    * Modifies headers by:
    *   - removing X-Flow-* headers if they were set
    *   - adding a default content-type
    */
  private[this] def proxyHeaders(
    requestId: String,
    headers: Headers,
    method: String,
    token: Option[ResolvedToken]
  ): Headers = {

    val headersToAdd = Seq(
      Constants.Headers.FlowServer -> name,
      Constants.Headers.FlowRequestId -> requestId,
      Constants.Headers.Host -> definition.hostHeaderValue,
      Constants.Headers.ForwardedHost -> headers.get(Constants.Headers.Host).getOrElse(""),
      Constants.Headers.ForwardedOrigin -> headers.get(Constants.Headers.Origin).getOrElse(""),
      Constants.Headers.ForwardedMethod -> method
    ) ++ Seq(
      token.map { t =>
        Constants.Headers.FlowAuth -> flowAuth.jwt(t)
      },

      clientIp(headers).map { ip =>
        Constants.Headers.FlowIp -> ip
      },

      headers.get("Content-Type") match {
        case None => Some("Content-Type" -> DefaultContentType)
        case Some(_) => None
      }
    ).flatten

    val cleanHeaders = Constants.Headers.namesToRemove.foldLeft(headers) { case (h, n) => h.remove(n) }

    headersToAdd.foldLeft(cleanHeaders) { case (h, addl) => h.add(addl) }
  }

  /**
    * See https://support.cloudflare.com/hc/en-us/articles/200170986-How-does-CloudFlare-handle-HTTP-Request-headers-
    */
  private[this] def clientIp(headers: Headers): Option[String] = {
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

  private[this] def setContentType(
    headers: Headers,
    contentType: String
  ): Headers = {
    headers.
      remove("Content-Type").
      add("Content-Type" -> contentType)
  }
  
  private[this] def toLongSafe(value: String): Option[Long] = {
    Try {
      value.toLong
    } match {
      case Success(v) => Some(v)
      case Failure(_) => None
    }
  }

  private[this] def logFormData(requestId: String, requestPath: String, route: Route, body: JsValue): Unit = {
    body match {
      case j: JsObject => {
        if (j.fields.nonEmpty) {
          val typ = definition.multiService.bodyTypeFromPath(route.method, route.path)
          val safeBody = LoggingUtil.safeJson(body, typ = typ)
          Logger.info(s"${route.method} $requestPath form body of type[${typ.getOrElse("unknown")}] requestId[$requestId]: $safeBody")
        }
      }
      case _ => // no-op
    }
  }
}
