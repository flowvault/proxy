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

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}
import play.api.http.HttpEntity
import lib._
import play.api.libs.json.{JsObject, JsValue, Json}

import scala.annotation.tailrec

case class ServerProxyDefinition(
  server: Server,
  multiService: io.flow.lib.apidoc.json.validation.MultiService // TODO Move higher level
) {

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

  private[this] implicit val (ec, name) = resolveContextName(definition.server.name)

  /**
    * Returns the execution context to use, if found. Works by recursively
    * shortening service name by splitting on "-"
    */
  @tailrec
  private[this] def resolveContextName(name: String): (ExecutionContext, String) = {
    val contextName = s"$name-context"
    Try {
      system.dispatchers.lookup(contextName)
    } match {
      case Success(ec) => {
        Logger.info(s"ServerProxy[${definition.server.name}] using configured execution context[$contextName]")
        (ec, name)
      }

      case Failure(_) => {
        val i = name.lastIndexOf("-")
        if (i > 0) {
          resolveContextName(name.substring(0, i))
        } else {
          Logger.warn(s"ServerProxy[${definition.server.name}] execution context[${name}] not found - using ${ServerProxy.DefaultContextName}")
          (system.dispatchers.lookup(ServerProxy.DefaultContextName), ServerProxy.DefaultContextName)
        }
      }
    }
  }

  override final def proxy(
    requestId: String,
    request: ProxyRequest,
    route: Route,
    token: Option[ResolvedToken],
    organization: Option[String] = None,
    partner: Option[String] = None
  ) = {
    Logger.info(s"[proxy] $request to [${definition.server.name}] ${route.method} ${definition.server.host}${request.path} requestId $requestId")

    /**
      * Choose the type of request based on callback/envelope or standard implementation
      */
    if (request.responseEnvelope) {
      envelopeResponse(requestId, request, route, token, organization = organization, partner = partner)
    } else {
      standard(requestId, request, route, token, organization = organization, partner = partner)
    }
  }

  private[this] def envelopeResponse(
    requestId: String,
    request: ProxyRequest,
    route: Route,
    token: Option[ResolvedToken],
    organization: Option[String] = None,
    partner: Option[String] = None
  ) = {
    val formData: JsValue = request.jsonpCallback match {
      case Some(_) => {
        FormData.toJson(request.queryParameters)
      }
      case None => {
        request.contentType match {
          // We turn url form encoded into application/json
          case ContentType.UrlFormEncoded => {
            val b = request.bodyUtf8.getOrElse {
              sys.error(s"Failed to serialize body as string for ContentType.UrlFormEncoded")
            }
            FormData.toJson(FormData.parseEncoded(b))
          }

          case ContentType.ApplicationJson => {
            request.bodyUtf8.getOrElse("") match {
              case "" => Json.obj()
              case b => Json.parse(b)
            }
          }

          case ContentType.Other(name) => {
            Logger.warn(s"[proxy] $request: Unsupported Content-Type[$name] - will proxy with empty json body")
            Json.obj()
          }
        }
      }
    }

    logFormData(requestId, request, formData)

    definition.multiService.upcast(route.method, route.path, formData) match {
      case Left(errors) => {
        Logger.info(s"[proxy] $request ${definition.server.name} 422 based on apidoc schema")
        Future(request.response(422, genericErrors(errors).toString))
      }

      case Right(body) => {
        val finalHeaders = setApplicationJsonContentType(
          proxyHeaders(requestId, request, token)
        )

        val req = ws.url(definition.server.host + request.path)
          .withFollowRedirects(false)
          .withMethod(route.method)
          .withHeaders(finalHeaders.headers: _*)
          .withQueryString(definition.definedQueryParameters(route.method, route.path, request.queryParametersAsSeq()): _*)
          .withBody(body)

        val startMs = System.currentTimeMillis
        req.execute.map { response =>
          val timeToFirstByteMs = System.currentTimeMillis - startMs

          actor ! MetricActor.Messages.Send(definition.server.name, route.method, route.path, timeToFirstByteMs, response.status, organization, partner)
          Logger.info(s"[proxy] $request ${definition.server.name}:${route.method} ${definition.server.host}${request.path} ${response.status} ${timeToFirstByteMs}ms requestId $requestId")
          request.response(response.status, response.body, response.allHeaders)
        }.recover {
          case ex: Throwable => {
            throw new Exception(ex)
          }
        }
      }
    }
  }

  private[this] def standard(
    requestId: String,
    request: ProxyRequest,
    route: Route,
    token: Option[ResolvedToken],
    organization: Option[String] = None,
    partner: Option[String] = None
  ) = {
    val req = ws.url(definition.server.host + request.path)
      .withFollowRedirects(false)
      .withMethod(route.method)
      .withQueryString(request.queryParametersAsSeq(): _*)

    val finalHeaders = proxyHeaders(requestId, request, token)
    val response = request.contentType match {

      // We turn url form encoded into application/json
      case ContentType.UrlFormEncoded => {
        val b = request.bodyUtf8.getOrElse {
          sys.error(s"Failed to serialize body as string for ContentType.UrlFormEncoded")
        }
        val newBody = FormData.toJson(FormData.parseEncoded(b))

        logFormData(requestId, request, newBody)

        definition.multiService.upcast(route.method, route.path, newBody) match {
          case Left(errors) => {
            Logger.info(s"[proxy] $request 422 based on apidoc schema")
            Future(
              UnprocessableEntity(
                genericErrors(errors)
              ).withHeaders("X-Flow-Proxy-Validation" -> "apidoc")
            )
          }

          case Right(validatedBody) => {
            req
              .withHeaders(setApplicationJsonContentType(finalHeaders).headers: _*)
              .withBody(validatedBody)
              .stream
              .recover { case ex: Throwable => throw new Exception(ex) }
          }
        }
      }

      case ContentType.ApplicationJson => {
        val body = request.bodyUtf8.getOrElse {
          sys.error("Failed to serialize body as string for ContentType.ApplicationJson")
        }

        Try {
          if (body.trim.isEmpty) {
            // e.g. PUT/DELETE with empty body
            Json.obj()
          } else {
            Json.parse(body)
          }
        } match {
          case Failure(e) => {
            Logger.info(s"[proxy] $request 422 invalid json")
            Future(
              UnprocessableEntity(
                genericError(s"The body of an application/json request must contain valid json: ${e.getMessage}")
              ).withHeaders("X-Flow-Proxy-Validation" -> "proxy")
            )
          }

          case Success(js) => {
            logFormData(requestId, request, js)

            definition.multiService.upcast(route.method, route.path, js) match {
              case Left(errors) => {
                Logger.info(s"[proxy] $request 422 based on apidoc schema")
                Future(
                  UnprocessableEntity(
                    genericErrors(errors)
                  ).withHeaders("X-Flow-Proxy-Validation" -> "apidoc")
                )
              }

              case Right(validatedBody) => {
                req
                  .withHeaders(setApplicationJsonContentType(finalHeaders).headers: _*)
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
          case None => {
            req
              .withHeaders(finalHeaders.headers: _*)
              .stream()
              .recover { case ex: Throwable => throw new Exception(ex) }
          }

          case Some(ProxyRequestBody.File(file)) => {
            req
              .withHeaders(finalHeaders.headers: _*)
              .post(file)
              .recover { case ex: Throwable => throw new Exception(ex) }
          }

          case Some(ProxyRequestBody.Bytes(bytes)) => {
            req
              .withHeaders(finalHeaders.headers: _*)
              .withBody(bytes)
              .stream
              .recover { case ex: Throwable => throw new Exception(ex) }
          }

          case Some(ProxyRequestBody.Json(json)) => {
            req
              .withHeaders(finalHeaders.headers: _*)
              .withBody(json)
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
        Logger.info(s"[proxy] $request ${definition.server.name}:${route.method} ${definition.server.host} ${r.status} ${timeToFirstByteMs}ms requestId $requestId")

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
    request: ProxyRequest,
    token: Option[ResolvedToken]
  ): Headers = {

    val headersToAdd = Seq(
      Constants.Headers.FlowServer -> name,
      Constants.Headers.FlowRequestId -> requestId,
      Constants.Headers.Host -> definition.hostHeaderValue,
      Constants.Headers.ForwardedHost -> request.headers.get(Constants.Headers.Host).getOrElse(""),
      Constants.Headers.ForwardedOrigin -> request.headers.get(Constants.Headers.Origin).getOrElse(""),
      Constants.Headers.ForwardedMethod -> request.originalMethod
    ) ++ Seq(
      token.map { t =>
        Constants.Headers.FlowAuth -> flowAuth.jwt(t)
      },

      request.clientIp.map { ip =>
        Constants.Headers.FlowIp -> ip
      },

      request.headers.get("Content-Type") match {
        case None => Some("Content-Type" -> request.contentType.toString)
        case Some(_) => None
      }
    ).flatten

    val cleanHeaders = Constants.Headers.namesToRemove.foldLeft(request.headers) { case (h, n) => h.remove(n) }

    headersToAdd.foldLeft(cleanHeaders) { case (h, addl) => h.add(addl) }
  }

  private[this] def setApplicationJsonContentType(
    headers: Headers
  ): Headers = {
    headers.
      remove("Content-Type").
      add("Content-Type" -> ContentType.ApplicationJson.toString)
  }
  
  private[this] def toLongSafe(value: String): Option[Long] = {
    Try {
      value.toLong
    } match {
      case Success(v) => Some(v)
      case Failure(_) => None
    }
  }

  private[this] def logFormData(requestId: String, request: ProxyRequest, body: JsValue): Unit = {
    body match {
      case j: JsObject => {
        if (j.fields.nonEmpty) {
          val typ = definition.multiService.bodyTypeFromPath(request.method, request.path)
          val safeBody = LoggingUtil.safeJson(body, typ = typ)
          Logger.info(s"$request form body of type[${typ.getOrElse("unknown")}] requestId[$requestId]: $safeBody")
        }
      }
      case _ => // no-op
    }
  }
}
