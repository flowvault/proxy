package controllers

import akka.actor.ActorSystem
import io.flow.common.v0.models.{Error, UserReference}
import io.flow.common.v0.models.json._
import io.flow.token.v0.{Client => TokenClient}
import io.flow.organization.v0.{Client => OrganizationClient}
import io.flow.organization.v0.models.Membership
import io.flow.token.v0.models.TokenAuthenticationForm
import java.util.UUID
import javax.inject.{Inject, Singleton}
import lib.{Authorization, AuthorizationParser, Config, Constants, Index, FlowAuth, FlowAuthData, Route, Server, ProxyConfigFetcher}
import play.api.Logger
import play.api.libs.json.Json
import play.api.mvc._
import org.apache.commons.codec.binary.Base64
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

@Singleton
class ReverseProxy @Inject () (
  system: ActorSystem,
  authorizationParser: AuthorizationParser,
  config: Config,
  flowAuth: FlowAuth,
  proxyConfigFetcher: ProxyConfigFetcher,
  serverProxyFactory: ServerProxy.Factory
) extends Controller {

  val index: Index = proxyConfigFetcher.current()

  private[this] val organizationClient = {
    val svc = findServerByName("organization").getOrElse {
      sys.error("There is no server named 'organization' in the current config: " + config)
    }
    Logger.info(s"Creating OrganizationClient w/ baseUrl[${svc.host}]")
    new OrganizationClient(baseUrl = svc.host)
  }

  private[this] val tokenClient = {
    val svc = findServerByName("token").getOrElse {
      sys.error("There is no server named 'token' in the current config: " + config)
    }
    Logger.info(s"Creating TokenClient w/ baseUrl[${svc.host}]")
    new TokenClient(baseUrl = svc.host)
  }

  private[this] implicit val ec = system.dispatchers.lookup("reverse-proxy-context")

  private[this] val proxies: Map[String, ServerProxy] = {
    Logger.info(s"ReverseProxy loading config sources: ${index.config.sources}")
    val all = scala.collection.mutable.Map[String, ServerProxy]()
    index.config.servers.map { s =>
      all.remove(s.host) match {
        case None => {
          all += (s.host -> serverProxyFactory(ServerProxyDefinition(host = s.host, names = Seq(s.name))))
        }
        case Some(existing) => {
          all += (
            s.host -> serverProxyFactory(
              existing.definition.copy(
                names = existing.definition.names ++ Seq(s.name))
            )
          )
        }
      }
    }
    all.toMap
  }

  private[this] val dynamicPoxies = scala.collection.mutable.Map[String, ServerProxy]()
  
  def handle = Action.async(parse.raw) { request: Request[RawBuffer] =>
    val requestId: String = request.headers.get(Constants.Headers.FlowRequestId).getOrElse {
      "api" + UUID.randomUUID.toString.replaceAll("-", "") // make easy to cut & paste
    }

    authorizationParser.parse(request.headers.get("Authorization")) match {
      case Authorization.NoCredentials => {
        proxyPostAuth(requestId, request, userId = None)
      }

      case Authorization.Unrecognized => Future {
        unauthorized(s"Authorization header value must start with one of: " + Authorization.Unrecognized.valid.mkString(", "))
      }

      case Authorization.InvalidApiToken => Future {
        unauthorized(s"API Token is not valid")
      }

      case Authorization.InvalidJwt => Future {
        unauthorized(s"JWT Token is not valid")
      }

      case Authorization.Token(token) => {
        resolveToken(requestId, token).flatMap {
          case None => Future {
            unauthorized(s"API Token is not valid")
          }
          case Some(user) => {
            proxyPostAuth(requestId, request, userId = Some(user.id))
          }
        }
      }

      case Authorization.User(userId) => {
        proxyPostAuth(requestId, request, userId = Some(userId))
      }
    }
  }

  private[this] def proxyPostAuth(
    requestId: String,
    request: Request[RawBuffer],
    userId: Option[String]
  ): Future[Result] = {
    // If we have a callback param indicated JSONP, respect the method parameter as well
    val method = request.queryString.get("callback") match {
      case None => request.method
      case Some(_) => request.queryString.get("method").getOrElse(Nil).headOption.map(_.toUpperCase).getOrElse(request.method)
    }

    resolve(requestId, method, request, userId).flatMap { result =>
      result match {
        case Left(result) => Future {
          result
        }

        case Right(route) => {
          route.organization(request.path) match {
            case None  => {
              lookup(route.host).proxy(
                requestId,
                request,
                method,
                userId.map { uid =>
                  FlowAuthData.user(requestId, uid)
                }
              )
            }

            case Some(org) => {
              userId match {
                case None => {
                  lookup(route.host).proxy(
                    requestId,
                    request,
                    method,
                    userId.map { uid =>
                      FlowAuthData.user(requestId, uid)
                    }
                  )
                }

                case Some(uid) => {
                  resolveOrganizationAuthorization(requestId, uid, org).flatMap {
                    case None => Future {
                      unauthorized(s"Not authorized to access $org or the organization does not exist")
                    }

                    case Some(auth) => {
                      lookup(route.host).proxy(
                        requestId,
                        request,
                        method,
                        Some(auth)
                      )
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  /**
    * Resolves the incoming method and path to a specific internal route. Also implements
    * overrides from incoming request headers:
    * 
    *   - headers['X-Flow-Server']: If specified we use this server name
    *   - headers['X-Flow-Host']: If specified we use this host
    * 
    * If any override headers are specified, we alsoverify that we
    * have an auth token identifying a user that is a member of the
    * flow organization. Otherwise we return an error.
    */
  private[this] def resolve(requestId: String, method: String, request: Request[RawBuffer], userId: Option[String]): Future[Either[Result, Route]] = {
    val path = request.path
    val serverNameOverride: Option[String] = request.headers.get(Constants.Headers.FlowServer)
    val hostOverride: Option[String] = request.headers.get(Constants.Headers.FlowHost)

    (serverNameOverride.isEmpty && hostOverride.isEmpty) match {
      case true => Future {
        index.resolve(method, path) match {
          case None => {
            Logger.info(s"Unrecognized URL $method $path - returning 404")
            Left(NotFound)
          }

          case Some(ir) => {
            Right(ir)
          }
        }
      }

      case false => {
        userId match {
          case None => Future {
            Left(
              unauthorized(s"Must authenticate to specify[${Constants.Headers.FlowServer} or ${Constants.Headers.FlowHost}]")
            )
          }

          case Some(uid) => {
            resolveOrganizationAuthorization(requestId, uid, Constants.FlowOrganizationId).map {
              case None => {
                Left(
                  unauthorized(s"Not authorized to access organization[${Constants.FlowOrganizationId}]")
                )
              }

              case Some(_) => {
                hostOverride match {
                  case Some(host) => {
                    (host.startsWith("http://") || host.startsWith("https://")) match {
                      case true => Right(
                        Route(
                          method = method,
                          path = path,
                          host = host
                        )
                      )
                      case false => Left(
                        unprocessableEntity(s"Value for ${Constants.Headers.FlowHost} header must start with http:// or https://")
                      )
                    }
                  }

                  case None => {
                    val name = serverNameOverride.getOrElse {
                      sys.error("Expected server name to be set")
                    }
                    findServerByName(name) match {
                      case None => {
                        Left(
                          unprocessableEntity(s"Invalid server name from Request Header[${Constants.Headers.FlowServer}]")
                        )
                      }

                      case Some(svc) => {
                        Right(
                          Route(
                            method = method,
                            path = path,
                            host = svc.host
                          )
                        )
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }
  
  private[this] def lookup(host: String): ServerProxy = {
    proxies.get(host).getOrElse {
      // TODO: This only happens for flow engineers sending requests
      // to specific hosts. Should we lock?
      dynamicPoxies.get(host).getOrElse {
        val proxy = serverProxyFactory(ServerProxyDefinition(host = host, names = Seq("fallback")))
        dynamicPoxies += (host -> proxy)
        proxy
      }
    }
  }

  private[this] def unauthorized(message: String) = {
    NotFound(errorJson("authorization_failed", message))
  }

  private[this] def notFound(message: String) = {
    NotFound(errorJson("not_found", message))
  }

  private[this] def unprocessableEntity(message: String) = {
    UnprocessableEntity(errorJson("validation_error", message))
  }

  private[this] def errorJson(key: String, message: String) = {
    Json.toJson(
      Seq(
        Error(key, message)
      )
    )
  }

  private[this] def findServerByName(name: String): Option[Server] = {
    index.config.servers.find(_.name == name)
  }


  /**
    * Queries token server to check if the specified token is a known
    * valid token.
    */
  private[this] def resolveToken(requestId: String, token: String): Future[Option[UserReference]] = {
    tokenClient.tokens.postAuthentications(
      TokenAuthenticationForm(token = token),
      requestHeaders = requestIdHeader(requestId)
    ).map { t =>
      Some(t.user)
    }.recover {
      case io.flow.token.v0.errors.UnitResponse(404) => {
        None
      }

      case ex: Throwable => {
        sys.error(s"Could not communicate with token server at[${tokenClient.baseUrl}]: $ex")
      }
    }
  }

  /**
    * Queries organization server to authorize this user for this
    * organization and also pulls the organization's environment.
    */
  private[this] def resolveOrganizationAuthorization(
    requestId: String,
    userId: String,
    organization: String
  ): Future[Option[FlowAuthData]] = {
    organizationClient.organizationAuthorizations.getByOrganization(
      organization = organization,
      requestHeaders = requestIdHeader(requestId) ++ Seq(
        Constants.Headers.FlowAuth -> flowAuth.jwt(FlowAuthData.user(requestId, userId))
      )
    ).map { orgAuth =>
      Some(
        FlowAuthData.org(requestId, userId, organization, orgAuth)
      )

    }.recover {
      case io.flow.organization.v0.errors.UnitResponse(401) => {
        Logger.warn(s"User[$userId] was not authorized to GET /organization-authorizations/$organization")
        None
      }
      case ex: Throwable => {
        sys.error(s"Could not communicate with organization server at[${organizationClient.baseUrl}]: $ex")
      }
    }
  }

  private[this] def requestIdHeader(requestId: String) = Seq(
    Constants.Headers.FlowRequestId -> requestId
  )
}
