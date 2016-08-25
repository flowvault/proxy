package controllers

import akka.actor.ActorSystem
import com.google.inject.AbstractModule
import com.google.inject.assistedinject.{Assisted, FactoryModuleBuilder}
import java.net.URI
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
import lib.{Constants, FlowAuth, FlowAuthData, FormData}

case class ServiceProxyDefinition(
  host: String,
  names: Seq[String]
) {

  val contextName = names.sorted.head + "-context"
  val nameLabel = names.sorted.mkString(",")
  val hostHeaderValue = (new URI(host)).getHost

}

/**
  * Service Proxy is responsible for proxying all requests to a given
  * service. The primary purpose of the proxy is to segment our thread
  * pools by service - so if one service is having difficulty, it is
  * less likely to impact other services.
  */
trait ServiceProxy {

  def definition: ServiceProxyDefinition

  def proxy(
    requestId: String,
    method: String,
    request: Request[RawBuffer],
    auth: Option[FlowAuthData]
  ): Future[play.api.mvc.Result]

}

object ServiceProxy {

  val DefaultContextName = s"default-service-context"

  trait Factory {
    def apply(definition: ServiceProxyDefinition): ServiceProxy
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

class ServiceProxyModule extends AbstractModule {
  def configure {
    install(new FactoryModuleBuilder()
      .implement(classOf[ServiceProxy], classOf[ServiceProxyImpl])
      .build(classOf[ServiceProxy.Factory])
    )
  }
}

class ServiceProxyImpl @Inject () (
  system: ActorSystem,
  ws: WSClient,
  flowAuth: FlowAuth,
  @Assisted override val definition: ServiceProxyDefinition
) extends ServiceProxy with Controller{

  private[this] implicit val (ec, name) = {
    val name = definition.contextName
    Try {
      system.dispatchers.lookup(name)
    } match {
      case Success(ec) => {
        Logger.info(s"ServiceProxy[${definition.nameLabel}] using configured execution context[$name]")
        (ec, name)
      }

      case Failure(_) => {
        Logger.warn(s"ServiceProxy[${definition.nameLabel}] execution context[${name}] not found - using ${ServiceProxy.DefaultContextName}")
        (system.dispatchers.lookup(ServiceProxy.DefaultContextName), ServiceProxy.DefaultContextName)
      }
    }
  }

  val executionContextName: String = name

  // WS Client defaults to application/octet-stream. Given this proxy
  // is for APIs only, assume JSON if no content type header is
  // provided.
  private[this] val DefaultContentType = "application/json"
  private[this] val UrlFormEncodedContentType = "application/x-www-form-urlencoded"

  override final def proxy(
    requestId: String,
    method: String,
    request: Request[RawBuffer],
    auth: Option[FlowAuthData]
  ) = {
    Logger.info(s"[proxy] ${request.method} ${request.path} to [${definition.nameLabel}] $method ${definition.host}${request.path} requestId $requestId")

    request.queryString.get("callback").getOrElse(Nil).headOption match {
      case Some(callback) => jsonp(requestId, callback, method, request, auth)
      case None => standard(requestId, method, request, auth)
    }
  }

  private[this] def jsonp(
    requestId: String,
    callback: String,
    method: String,
    request: Request[RawBuffer],
    auth: Option[FlowAuthData]
  ) = {
    val body = FormData.toJson(
      request.queryString - "method" - "callback"
    )

    val finalHeaders = proxyHeaders(requestId, request.headers, auth).
      remove("Content-Type").
      add("Content-Type" -> DefaultContentType)

    println("JSON P BODY: " + body)
    println("Headers: " + finalHeaders)    

    val req = ws.url(definition.host + request.path)
      .withFollowRedirects(false)
      .withMethod(method)
      .withHeaders(finalHeaders.headers: _*)
      .withBody(body)

    val startMs = System.currentTimeMillis
    req.execute.map { response =>
      val timeToFirstByteMs = System.currentTimeMillis - startMs
      // Prefix is to avoid a JSONP/Flash vulnerability
      val finalBody = "/**/" + callback + "(" + response.body + ")"

      // TODO: Add envelope
      // TODO: x-forwarded-method
      Logger.info(s"[proxy] ${request.method} ${request.path} ${definition.nameLabel}:$method ${definition.host}${request.path} ${response.status} ${timeToFirstByteMs}ms requestId $requestId")

      Ok(finalBody).as("application/javascript; charset=utf-8")
    }
  }

  private[this] def standard(
    requestId: String,
    method: String,
    request: Request[RawBuffer],
    auth: Option[FlowAuthData]
  ) = {
    val finalHeaders = proxyHeaders(requestId, request.headers, auth)
    val req = ws.url(definition.host + request.path)
      .withFollowRedirects(false)
      .withMethod(method)
      .withHeaders(finalHeaders.headers: _*)
      .withQueryString(ServiceProxy.query(request.queryString): _*)

    val requestWithBody = finalHeaders.get("Content-Type").getOrElse(DefaultContentType) match {
      case UrlFormEncodedContentType => {
        val b: String = request.body.asBytes().get.decodeString("UTF-8")
        val newBody = FormData.toJson(FormData.parseEncoded(b))
        println("newBody: $newBody")
        req.withBody(newBody)
      }
      case _ => {
        req.withBody(request.body.asBytes().get)
      }
    }
  
    
    val startMs = System.currentTimeMillis

    requestWithBody.stream.map {
      case StreamedResponse(response, body) => {
        val timeToFirstByteMs = System.currentTimeMillis - startMs
        val contentType: Option[String] = response.headers.get("Content-Type").flatMap(_.headOption)
        val contentLength: Option[Long] = response.headers.get("Content-Length").flatMap(_.headOption).flatMap(toLongSafe(_))

        Logger.info(s"[proxy] ${request.method} ${request.path} ${definition.nameLabel}:$method ${definition.host}${request.path} ${response.status} ${timeToFirstByteMs}ms requestId $requestId")

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

  /**
    * Modifies headers by:
    *   - removing X-Flow-* headers if they were set
    *   - adding a default content-type
    */
  private[this] def proxyHeaders(requestId: String, headers: Headers, authData: Option[FlowAuthData]): Headers = {
    val headersToAdd = Seq(
      Constants.Headers.FlowService -> name,
      Constants.Headers.FlowRequestId -> requestId,
      Constants.Headers.Host -> definition.hostHeaderValue,
      Constants.Headers.ForwardedHost -> headers.get(Constants.Headers.Host).getOrElse(""),
      Constants.Headers.ForwardedOrigin -> headers.get(Constants.Headers.Origin).getOrElse("")
    ) ++ Seq(
      authData.map { data =>
        Constants.Headers.FlowAuth -> flowAuth.jwt(data)
      },
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

  private[this] def toLongSafe(value: String): Option[Long] = {
    Try {
      value.toLong
    } match {
      case Success(v) => Some(v)
      case Failure(_) => None
    }
  }
}
