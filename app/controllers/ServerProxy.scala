package controllers

import akka.actor.ActorSystem
import com.google.inject.AbstractModule
import com.google.inject.assistedinject.{Assisted, FactoryModuleBuilder}
import io.flow.lib.apidoc.json.validation.FormData
import java.net.URI
import java.util.UUID
import java.util.concurrent.Executors
import javax.inject.Inject
import play.api.Logger
import play.api.http.Status
import play.api.inject.Module
import play.api.libs.ws.{StreamedResponse, WSClient, WSRequest}
import play.api.mvc._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}
import play.api.http.HttpEntity
import lib.{Constants, FlowAuth, FlowAuthData, Route, Server}
import play.api.libs.json.{JsArray, JsValue, Json}

case class ServerProxyDefinition(
  server: Server,
  multiService: io.flow.lib.apidoc.json.validation.MultiService // TODO Move higher level
) {

  val contextName = s"${server.name}-context"

  val hostHeaderValue = Option((new URI(server.host)).getHost).getOrElse {
    sys.error(s"Could not parse host from server[$server]")
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
    request: Request[RawBuffer],
    route: Route,
    auth: Option[FlowAuthData]
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
    *  @return A sequence of keys, each paired with exactly one value.
    */
  def query(
    incoming: Map[String, Seq[String]]
  ): Seq[(String, String)] = {
    incoming.map { case (k, vs) =>
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
  system: ActorSystem,
  ws: WSClient,
  flowAuth: FlowAuth,
  @Assisted override val definition: ServerProxyDefinition
) extends ServerProxy with Controller{

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
    request: Request[RawBuffer],
    route: Route,
    auth: Option[FlowAuthData]
  ) = {
    Logger.info(s"[proxy] ${request.method} ${request.path} to [${definition.server.name}] ${route.method} ${definition.server.host}${request.path} requestId $requestId")

    request.queryString.get("callback").getOrElse(Nil).headOption match {
      case Some(callback) => {
        jsonp(requestId, callback, request, route, auth)
      }

      case None => {
        standard(requestId, route, request, auth)
      }
    }
  }

  private[this] def jsonp(
    requestId: String,
    callback: String,
    request: Request[RawBuffer],
    route: Route,
    auth: Option[FlowAuthData]
  ) = {
    val formData = FormData.toJson(request.queryString - "method" - "callback")
    definition.multiService.validate(route.method, route.path, formData) match {
      case Left(errors) => {
        val finalBody = jsonpEnvelope(callback, 422, Map(), errors.toString)
        Logger.info(s"[proxy] ${request.method} ${request.path} ${definition.server.name}:${route.method} ${definition.server.host}${request.path} 422 based on apidoc schema")
        Future(Ok(finalBody).as("application/javascript; charset=utf-8"))
      }

      case Right(body) => {
        val finalHeaders = setContentType(proxyHeaders(requestId, request.headers, request.method, auth), ApplicationJsonContentType)

        val req = ws.url(definition.server.host + request.path)
          .withFollowRedirects(false)
          .withMethod(route.method)
          .withHeaders(finalHeaders.headers: _*)
          .withBody(body)

        val startMs = System.currentTimeMillis
        req.execute.map { response =>
          val timeToFirstByteMs = System.currentTimeMillis - startMs
          val finalBody = jsonpEnvelope(callback, response.status, response.allHeaders, response.body)
          Logger.info(s"[proxy] ${request.method} ${request.path} ${definition.server.name}:${route.method} ${definition.server.host}${request.path} ${response.status} ${timeToFirstByteMs}ms requestId $requestId")
          Ok(finalBody).as("application/javascript; charset=utf-8")
        }
      }
    }
  }

  /**
    * Create the jsonp envelope to passthrough response status, response headers
    */
  private[this] def jsonpEnvelope(
    callback: String,
    status: Int,
    headers: Map[String,Seq[String]],
    body: String
  ): String = {
    // Prefix /**/ is to avoid a JSONP/Flash vulnerability
    val jsonHeaders = Json.toJson(headers)
    "/**/" + s"""$callback({\n  "status": $status,\n  "headers": ${jsonHeaders},\n  "body": $body\n})""" +
    ")"
  }

  private[this] def standard(
    requestId: String,
    route: Route,
    request: Request[RawBuffer],
    auth: Option[FlowAuthData]
  ) = {
    val finalHeaders = proxyHeaders(requestId, request.headers, request.method, auth)
    val req = ws.url(definition.server.host + request.path)
      .withFollowRedirects(false)
      .withMethod(route.method)
      .withQueryString(ServerProxy.query(request.queryString): _*)

    val response = finalHeaders.get("Content-Type").getOrElse(DefaultContentType) match {

      // We turn url form encoded into application/json
      case UrlFormEncodedContentType => {
        val b: String = request.body.asBytes().get.decodeString("UTF-8")
        val newBody = FormData.toJson(FormData.parseEncoded(b))

        definition.multiService.validate(route.method, route.path, newBody) match {
          case Left(errors) => {
            Logger.info(s"[proxy] ${request.method} ${request.path} ${definition.server.name}:${route.method} ${definition.server.host}${request.path} 422 based on apidoc schema")
            Future(
              UnprocessableEntity(
                Json.toJson(makeErrors("validation_error", errors))
              ).withHeaders("X-Flow-Proxy-Validation" -> "apidoc")
            )
          }

          case Right(validatedBody) => {
            req
              .withHeaders(setContentType(finalHeaders, ApplicationJsonContentType).headers: _*)
              .withBody(validatedBody)
              .stream
              .recover { case ex: Throwable => errorHandler(requestId, request, ex) }
          }
        }
      }

      case _ => {
        req
          .withHeaders(finalHeaders.headers: _*)
          .withBody(request.body.asBytes().get)
          .stream
      }
    }
    
    val startMs = System.currentTimeMillis

    response.map {
      case r: Result => {
        r
      }

      case StreamedResponse(response, body) => {
        val timeToFirstByteMs = System.currentTimeMillis - startMs
        val contentType: Option[String] = response.headers.get("Content-Type").flatMap(_.headOption)
        val contentLength: Option[Long] = response.headers.get("Content-Length").flatMap(_.headOption).flatMap(toLongSafe(_))

        Logger.info(s"[proxy] ${request.method} ${request.path} ${definition.server.name}:${route.method} ${definition.server.host}${request.path} ${response.status} ${timeToFirstByteMs}ms requestId $requestId")

        // If there's a content length, send that, otherwise return the body chunked
        contentLength match {
          case Some(length) => {
            Status(response.status).sendEntity(HttpEntity.Streamed(body, Some(length), contentType))
          }

          case None => {
            contentType match {
              case None => Status(response.status).chunked(body)
              case Some(ct) => Status(response.status).chunked(body).as(ct)
            }
          }
        }
      }
      case other => {
        sys.error("Unhandled response: " + other)
      }
    }
  }

  private[this] def errorHandler(
    requestId: String,
    request: Request[RawBuffer],
    ex: Throwable
  ) = {
    val errorId = "api" + UUID.randomUUID.toString.replaceAll("-", "")
    Logger.error(s"[proxy] FlowError [$errorId] ${request.method} ${request.path} $requestId: ${ex.getMessage}", ex)
    val msg = s"A server error has occurred (#$errorId)"
    InternalServerError(makeErrors("server_error", Seq(msg)))
  }

  /**
    * Generate error message compatible with flow 'error' type
    */
  private[this] def makeErrors(code: String, errors: Seq[String]): JsValue = {
    JsArray(
      errors.map { error =>
        Json.obj("code" -> code, "message" -> error)
      }
    )
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
    authData: Option[FlowAuthData]
  ): Headers = {

    val headersToAdd = Seq(
      Constants.Headers.FlowServer -> name,
      Constants.Headers.FlowRequestId -> requestId,
      Constants.Headers.Host -> definition.hostHeaderValue,
      Constants.Headers.ForwardedHost -> headers.get(Constants.Headers.Host).getOrElse(""),
      Constants.Headers.ForwardedOrigin -> headers.get(Constants.Headers.Origin).getOrElse(""),
      Constants.Headers.ForwardedMethod -> method
    ) ++ Seq(
      authData.map { data =>
        Constants.Headers.FlowAuth -> flowAuth.jwt(data)
      },

      (
        headers.get("cf-connecting-ip").map { ip =>  // IP Address from cloudflare
          Constants.Headers.FlowIp -> ip
        }
      ),

      (
        headers.get("Content-Type") match {
          case None => Some("Content-Type" -> DefaultContentType)
          case Some(_) => None
        }
      )      
    ).flatten

    val cleanHeaders = Constants.Headers.namesToRemove.foldLeft(headers) { case (h, n) => h.remove(n) }

    headersToAdd.foldLeft(cleanHeaders) { case (h, addl) => h.add(addl) }
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
}
