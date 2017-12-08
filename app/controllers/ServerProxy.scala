package controllers

import akka.actor.ActorSystem
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.google.inject.AbstractModule
import com.google.inject.assistedinject.{Assisted, FactoryModuleBuilder}
import io.apibuilder.validation.FormData
import java.net.URI
import javax.inject.Inject

import actors.MetricActor
import akka.stream.ActorMaterializer
import io.apibuilder.spec.v0.models.ParameterLocation
import play.api.Logger
import play.api.libs.ws.{WSResponse, WSClient}
import play.api.mvc._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}
import play.api.http.HttpEntity
import lib._
import play.api.libs.json.{JsObject, JsValue, Json}

import scala.annotation.tailrec
import scala.concurrent.duration.{FiniteDuration, MILLISECONDS, SECONDS}
import akka.stream.scaladsl.StreamConverters

case class ServerProxyDefinition(
  server: Server,
  multiService: io.apibuilder.validation.MultiService // TODO Move higher level
) {

  val requestTimeout: FiniteDuration = server.name match {
    case "payment" | "payment-internal" | "partner" | "label" | "label-internal" => FiniteDuration(60, SECONDS)
    case "token" | "session" | "organization" => FiniteDuration(5, SECONDS)
    case _ => FiniteDuration(30, SECONDS) // TODO: Figure out what the optimal value should be for this
  }

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
             request: ProxyRequest,
             route: Route,
             token: ResolvedToken,
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
    * Maps a query string that may contain multiple values per parameter
    * to a sequence of query parameters. Uses the underlying form data to
    * also upcast the parameters (mapping the incoming parameters to a json
    * document, upcasting, then back to query parameters)
    *
    * @todo Add example query string
    * @example
    * {{{
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
    * @param incoming A map of query parameter keys to sequences of their values.
    * @return A sequence of keys, each paired with exactly one value. The keys are further
    *         normalized to match Flow expectations (e.g. number[] => number)
    */
  def query(
             incoming: Map[String, Seq[String]]
           ): Seq[(String, String)] = {
    Util.toFlatSeq(
      FormData.parseEncoded(FormData.toEncoded(FormData.toJson(incoming)))
    )
  }
}

class ServerProxyModule extends AbstractModule {
  def configure: Unit = {
    install(new FactoryModuleBuilder()
      .implement(classOf[ServerProxy], classOf[ServerProxyImpl])
      .build(classOf[ServerProxy.Factory])
    )
  }
}

class ServerProxyImpl @Inject()(
  @javax.inject.Named("metric-actor") val actor: akka.actor.ActorRef,
  implicit val system: ActorSystem,
  config: Config,
  ws: WSClient,
  flowAuth: FlowAuth,
  @Assisted override val definition: ServerProxyDefinition
) extends ServerProxy with Controller with lib.Errors {

  private[this] implicit val (ec, name) = resolveContextName(definition.server.name)
  private[this] implicit val materializer: ActorMaterializer = ActorMaterializer()

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
    request: ProxyRequest,
    route: Route,
    token: ResolvedToken,
    organization: Option[String] = None,
    partner: Option[String] = None
  ) = {
    Logger.info(s"[proxy $request] to [${definition.server.name}] ${route.method} ${definition.server.host}${request.path}")

    /**
      * Choose the type of request based on callback/envelope or standard implementation
      */
    if (request.responseEnvelope) {
      envelopeResponse(request, route, token, organization = organization, partner = partner)
    } else {
      standard(request, route, token, organization = organization, partner = partner)
    }
  }

  private[this] def envelopeResponse(
                                      request: ProxyRequest,
                                      route: Route,
                                      token: ResolvedToken,
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
            FormData.parseEncodedToJsObject(b)
          }

          case ContentType.ApplicationJson => {
            request.bodyUtf8.getOrElse("") match {
              case "" => Json.obj()
              case b => Json.parse(b)
            }
          }

          case ContentType.Other(n) => {
            Logger.warn(s"[proxy $request] Unsupported Content-Type[$n] - will proxy with empty json body")
            Json.obj()
          }
        }
      }
    }

    logFormData(request, formData)

    definition.multiService.upcast(route.method, route.path, formData) match {
      case Left(errors) => {
        log4xx(request, 422, formData, errors)
        Future.successful(request.response(422, genericErrors(errors).toString))
      }

      case Right(body) => {
        val finalHeaders = setApplicationJsonContentType(
          proxyHeaders(request, token)
        )

        val req = ws.url(definition.server.host + request.path)
          .withFollowRedirects(false)
          .withMethod(route.method)
          .withHttpHeaders(finalHeaders.headers: _*)
          .addQueryStringParameters(definition.definedQueryParameters(route.method, route.path, request.queryParametersAsSeq()): _*)
          .withBody(body)

        val startMs = System.currentTimeMillis
        req.execute.map { response =>
          logResponse(
            request = request,
            definition = definition,
            route = route,
            timeToFirstByteMs = System.currentTimeMillis - startMs,
            status = response.status,
            organization = organization,
            partner = partner
          )

          request.response(response.status, response.body, response.headers)
        }.recover {
          case ex: Throwable => {
            throw new Exception(ex)
          }
        }
      }
    }
  }

  private[this] def standard(
    request: ProxyRequest,
    route: Route,
    token: ResolvedToken,
    organization: Option[String] = None,
    partner: Option[String] = None
  ): Future[Result] = {
    val req = ws.url(definition.server.host + request.path)
      .withFollowRedirects(false)
      .withMethod(route.method)
      .addQueryStringParameters(request.queryParametersAsSeq(): _*)

    val finalHeaders = proxyHeaders(request, token)
    val response = request.contentType match {

      // We turn url form encoded into application/json
      case ContentType.UrlFormEncoded => {
        val b = request.bodyUtf8.getOrElse {
          sys.error(s"Failed to serialize body as string for ContentType.UrlFormEncoded")
        }
        val newBody = FormData.parseEncodedToJsObject(b)

        logFormData(request, newBody)

        definition.multiService.upcast(route.method, route.path, newBody) match {
          case Left(errors) => {
            log4xx(request, 422, newBody, errors)
            Future.successful(
              UnprocessableEntity(
                genericErrors(errors)
              ).withHeaders("X-Flow-Proxy-Validation" -> "apibuilder")
            )
          }

          case Right(validatedBody) => {
            req
              .withHttpHeaders(setApplicationJsonContentType(finalHeaders).headers: _*)
              .withBody(validatedBody)
              .withRequestTimeout(definition.requestTimeout)
              .stream
              .recover { case ex: Throwable => throw new Exception(ex) }
          }
        }
      }

      case ContentType.ApplicationJson => {
        val body = request.bodyUtf8.getOrElse("")

        Try {
          if (body.trim.isEmpty) {
            // e.g. PUT/DELETE with empty body
            Json.obj()
          } else {
            Json.parse(body)
          }
        } match {
          case Failure(e) => {
            Logger.info(s"[proxy $request] 422 invalid json")
            Future.successful(
              UnprocessableEntity(
                genericError(s"The body of an application/json request must contain valid json: ${e.getMessage}")
              ).withHeaders("X-Flow-Proxy-Validation" -> "proxy")
            )
          }

          case Success(js) => {
            logFormData(request, js)

            definition.multiService.upcast(route.method, route.path, js) match {
              case Left(errors) => {
                log4xx(request, 422, js, errors)
                Future.successful(
                  UnprocessableEntity(
                    genericErrors(errors)
                  ).withHeaders("X-Flow-Proxy-Validation" -> "apibuilder")
                )
              }

              case Right(validatedBody) => {
                req
                  .withHttpHeaders(setApplicationJsonContentType(finalHeaders).headers: _*)
                  .withBody(validatedBody)
                  .withRequestTimeout(definition.requestTimeout)
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
              .withHttpHeaders(finalHeaders.headers: _*)
              .stream()
              .recover { case ex: Throwable => throw new Exception(ex) }
          }

          case Some(ProxyRequestBody.File(file)) => {
            req
              .withHttpHeaders(finalHeaders.headers: _*)
              .post(file)
              .recover { case ex: Throwable => throw new Exception(ex) }
          }

          case Some(ProxyRequestBody.Bytes(bytes)) => {
            req
              .withHttpHeaders(finalHeaders.headers: _*)
              .withBody(bytes)
              .withRequestTimeout(definition.requestTimeout)
              .stream
              .recover { case ex: Throwable => throw new Exception(ex) }
          }

          case Some(ProxyRequestBody.Json(json)) => {
            req
              .withHttpHeaders(finalHeaders.headers: _*)
              .withBody(json)
              .withRequestTimeout(definition.requestTimeout)
              .stream
              .recover { case ex: Throwable => throw new Exception(ex) }
          }
        }
      }
    }

    val startMs = System.currentTimeMillis

    response.map {
      case r: Result if r.header.status >= 400 && r.header.status < 500 => logBodyStream(request, r.header.status, r.body.dataStream)

      case r: WSResponse => {
        if (r.status >= 400 && r.status < 500) {
          logBodyStream(request, r.status, r.bodyAsSource)
        }
        val timeToFirstByteMs = System.currentTimeMillis - startMs
        val contentType: Option[String] = r.headers.get("Content-Type").flatMap(_.headOption)
        val contentLength: Option[Long] = r.headers.get("Content-Length").flatMap(_.headOption).flatMap(toLongSafe)

        logResponse(
          request = request,
          definition = definition,
          route = route,
          timeToFirstByteMs = timeToFirstByteMs,
          status = r.status,
          organization = organization,
          partner = partner
        )

        val headers: Seq[(String, String)] = toHeaders(r.headers)

        // If there's a content length, send that, otherwise return the body chunked
        contentLength match {
          case Some(length) => {
            Status(r.status).
              sendEntity(HttpEntity.Streamed(r.bodyAsSource, Some(length), contentType)).
              withHeaders(headers: _*)
          }

          case None => {
            contentType match {
              case None => Status(r.status).chunked(r.bodyAsSource).withHeaders(headers: _*)
              case Some(ct) => Status(r.status).chunked(r.bodyAsSource).withHeaders(headers: _*).as(ct)
            }
          }
        }
      }
      case other => {
        sys.error("Unhandled response of type: " + other.getClass.getName)
      }
    }
  }

  /**
    * Modifies headers by:
    *   - removing X-Flow-* headers if they were set
    *   - adding a default content-type
    */
  private[this] def proxyHeaders(
                                  request: ProxyRequest,
                                  token: ResolvedToken
                                ): Headers = {

    val headersToAdd = Seq(
      Constants.Headers.FlowServer -> name,
      Constants.Headers.FlowRequestId -> request.requestId,
      Constants.Headers.Host -> definition.hostHeaderValue,
      Constants.Headers.ForwardedHost -> request.headers.get(Constants.Headers.Host).getOrElse(""),
      Constants.Headers.ForwardedOrigin -> request.headers.get(Constants.Headers.Origin).getOrElse(""),
      Constants.Headers.ForwardedMethod -> request.originalMethod
    ) ++ Seq(
      Some(
        Constants.Headers.FlowAuth -> flowAuth.jwt(token)
      ),

      request.clientIp().map { ip =>
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

  private[this] def logFormData(request: ProxyRequest, body: JsValue): Unit = {
    if (request.method != "GET") {
      val typ = definition.multiService.bodyTypeFromPath(request.method, request.path)
      val safeBody = body match {
        case j: JsObject if typ.isEmpty && j.value.isEmpty => "{}"
        case j: JsObject => toLogValue(request, body, typ)
        case _ => "{...} Body of type[${body.getClass.getName}] fully redacted"
      }
      Logger.info(s"[proxy $request] body type[${typ.getOrElse("unknown")}]: $safeBody")
    }
  }

  private[this] def logBodyStream(request: ProxyRequest, status: Int, body: Source[ByteString, _]): Result = {
    Try {
      val is = body.runWith(StreamConverters.asInputStream(FiniteDuration(100, MILLISECONDS)))
      scala.io.Source.fromInputStream(is, "UTF-8").mkString
    } match {
      case Success(msg) => {
        log4xx(request, status, msg)
        Result(ResponseHeader(status, Map.empty, None), HttpEntity.Strict(data = ByteString(msg), contentType = Option(request.contentType.toString)))
      }
      case Failure(ex) => {
        log4xx(request, status, s"Failed to deserialize ${ex.getMessage}")
        Result(ResponseHeader(status, Map.empty, None), HttpEntity.Strict(data = ByteString(ex.getMessage), contentType = Option(request.contentType.toString)))
      }
    }
  }

  private[this] def log4xx(request: ProxyRequest, status: Int, body: String): Unit = {
    // GET too noisy due to bots
    if (request.method != "GET" && status >= 400 && status < 500) {
      val finalBody = Try {
        Json.parse(body)
      } match {
        case Success(js) => toLogValue(request, js, typ = None)
        case Failure(_) => body
      }

      Logger.info(s"[proxy $request] responded with status:$status: $finalBody")
    }
  }

  private[this] def log4xx(request: ProxyRequest, status: Int, js: JsValue, errors: Seq[String]): Unit = {
    // GET too noisy due to bots
    if (request.method != "GET" && status >= 400 && status < 500) {
      // TODO: PARSE TYPE
      val finalBody = toLogValue(request, js, typ = None)
      Logger.info(s"[proxy $request] responded with status:$status Invalid JSON: ${errors.mkString(", ")} BODY: $finalBody")
    }
  }

  private[this] def toHeaders(headers: Map[String, Seq[String]]): Seq[(String, String)] = {
    headers.flatMap { case (k, vs) =>
      vs.map { v =>
        (k, v)
      }
    }.toSeq
  }

  private[this] def toLogValue(
    request: ProxyRequest,
    js: JsValue,
    typ: Option[String]
  ): JsValue = {
    if (config.isVerboseLogEnabled(request.path)) {
      js
    } else {
      LoggingUtil.logger.safeJson(js, typ = None)
    }
  }

  /**
    * Logs data about a response from an underlying service.
    *   - Publishes metrics
    *   - Logs warnings if the response code is unexpected based
    *     on the documented API Builder specification
    */
  private[this] def logResponse(
    request: ProxyRequest,
    definition: ServerProxyDefinition,
    route: Route,
    timeToFirstByteMs: Long,
    status: Int,
    organization: Option[String],
    partner: Option[String]
  ): Unit = {
    actor ! MetricActor.Messages.Send(definition.server.name, route.method, route.path, timeToFirstByteMs, status, organization, partner)
    Logger.info(s"[proxy $request] ${definition.server.name}:${route.method} ${definition.server.host} status:$status ${timeToFirstByteMs}ms")

    definition.multiService.validate(request.method, request.path) match {
      case Left(_) => {
        Logger.warn(s"[proxy $request] FlowError UnknownRoute path[${request.method} ${request.path}] was not found as a valid API Builder Operation")
      }
      case Right(op) => {
        definition.multiService.validateResponseCode(op, status) match {
          case Left(error) => {
            Logger.warn(s"[proxy $request] FlowError $error")
          }
          case Right(_) => // no-op
        }
      }
    }
  }

}
