package controllers

import akka.actor.ActorSystem
import io.flow.common.v0.models.{Error, UserReference}
import io.flow.common.v0.models.json._
import io.flow.token.v0.{Client => TokenClient}
import io.flow.organization.v0.{Client => OrganizationClient}
import io.flow.organization.v0.models.Membership
import io.flow.token.v0.models.AuthenticationForm
import javax.inject.{Inject, Singleton}
import lib.{Authorization, AuthorizationParser, Config, Constants, Index, InternalRoute, FlowAuth, FlowAuthData, Route, Service, ProxyConfigFetcher}
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
  serviceProxyFactory: ServiceProxy.Factory
) extends Controller {

  val index: Index = proxyConfigFetcher.current()

  private[this] val organizationClient = {
    val svc = findServiceByName("organization").getOrElse {
      sys.error("There is no service named 'organization' in the current config: " + config)
    }
    Logger.info(s"Creating OrganizationClient w/ baseUrl[${svc.host}]")
    new OrganizationClient(baseUrl = svc.host)
  }

  private[this] val tokenClient = new TokenClient(baseUrl = config.requiredString("service.token.uri"))

  private[this] implicit val ec = system.dispatchers.lookup("reverse-proxy-context")

  private[this] val proxies: Map[String, ServiceProxy] = {
    Logger.info(s"ReverseProxy loading config version: ${index.config.version}")
    Map(
      index.config.services.map { s =>
        (s.host -> serviceProxyFactory(ServiceProxyDefinition(host = s.host, name = s.name)))
      }: _*
    )
  }

  private[this] val dynamicPoxies = scala.collection.mutable.Map[String, ServiceProxy]()
  
  def handle = Action.async(parse.raw) { request: Request[RawBuffer] =>
    authorizationParser.parse(request.headers.get("Authorization")) match {
      case Authorization.NoCredentials => {
        proxyPostAuth(request, userId = None)
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
        resolveToken(token).flatMap {
          case None => Future {
            unauthorized(s"API Token is not valid")
          }
          case Some(user) => {
            proxyPostAuth(request, userId = Some(user.id))
          }
        }
      }

      case Authorization.User(userId) => {
        proxyPostAuth(request, userId = Some(userId))
      }
    }
  }

  private[this] def proxyPostAuth(request: Request[RawBuffer], userId: Option[String]): Future[Result] = {
    resolve(request, userId).flatMap { result =>
      result match {
        case Left(result) => Future {
          result
        }

        case Right(internalRoute) => {
          internalRoute.organization(request.path) match {
            case None  => {
              lookup(internalRoute.host).proxy(
                request,
                userId.map { uid =>
                  FlowAuthData(
                    userId = uid,
                    organization = None,
                    role = None
                  )
                }
              )
            }

            case Some(org) => {
              userId match {
                case None => Future {
                  unauthorized("You must set a valid Authorization header")
                }

                case Some(uid) => {
                  resolveMembership(uid, org).flatMap {
                    case None => Future {
                      unauthorized(s"Not authorized to access $org or the organization does not exist")
                    }

                    case Some(membership) => {
                      lookup(internalRoute.host).proxy(
                        request,
                        Some(
                          FlowAuthData(
                            userId = uid,
                            organization = Some(org),
                          role = Some(membership.role.toString)
                          )
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

  /**
    * Resolves the incoming method and path to a specific internal route. Also implements
    * overrides from incoming request headers:
    * 
    *   - headers['X-Flow-Service']: If specified we use this service name
    *   - headers['X-Flow-Host']: If specified we use this host
    * 
    * If any override headers are specified, we alsoverify that we
    * have an auth token identifying a user that is a member of the
    * flow organization. Otherwise we return an error.
    */
  private[this] def resolve(request: Request[RawBuffer], userId: Option[String]): Future[Either[Result, InternalRoute]] = {
    val method = request.method
    val path = request.path
    val serviceNameOverride: Option[String] = request.headers.get(Constants.Headers.FlowService)
    val hostOverride: Option[String] = request.headers.get(Constants.Headers.FlowHost)

    (serviceNameOverride.isEmpty && hostOverride.isEmpty) match {
      case true => Future {
        index.resolve(method, path) match {
          case None => {
            Logger.info(s"Unrecognized path[$path] - returning 404")
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
              unauthorized(s"Must authenticate to specify[${Constants.Headers.FlowService} or ${Constants.Headers.FlowHost}]")
            )
          }

          case Some(uid) => {
            resolveMembership(uid, Constants.FlowOrganizationId).map {
              case None => {
                Left(
                  unauthorized(s"Not authorized to access organization[${Constants.FlowOrganizationId}]")
                )
              }

              case Some(_) => {
                val route = Route(
                  method = method,
                  path = path
                )
                
                hostOverride match {
                  case Some(host) => {
                    (host.startsWith("http://") || host.startsWith("https://")) match {
                      case true => Right(
                        InternalRoute(route, host)
                      )
                      case false => Left(
                        unprocessableEntity(s"Value for ${Constants.Headers.FlowHost} header must start with http:// or https://")
                      )
                    }
                  }

                  case None => {
                    val name = serviceNameOverride.getOrElse {
                      sys.error("Expected service name to be set")
                    }
                    findServiceByName(name) match {
                      case None => {
                        Left(
                          unprocessableEntity(s"Invalid service name from Request Header[${Constants.Headers.FlowService}]")
                        )
                      }

                      case Some(svc) => {
                        Right(
                          InternalRoute(route, svc.host)
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
  
  private[this] def lookup(host: String): ServiceProxy = {
    proxies.get(host).getOrElse {
      // TODO: This only happens for flow engineers sending requests
      // to specific hosts. Should we lock?
      dynamicPoxies.get(host).getOrElse {
        val proxy = serviceProxyFactory(ServiceProxyDefinition(host = host, name = "fallback"))
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

  private[this] def findServiceByName(name: String): Option[Service] = {
    index.config.services.find(_.name == name)
  }


  /**
    * Queries token service to check if the specified token is a known
    * valid token.
    */
  private[this] def resolveToken(token: String): Future[Option[UserReference]] = {
    tokenClient.tokens.postAuthentications(AuthenticationForm(token = token)).map { t =>
      Some(t.user)
    }.recover {
      case io.flow.token.v0.errors.UnitResponse(404) => {
        None
      }

      case ex: Throwable => {
        sys.error(s"Could not communicate with token service at[${tokenClient.baseUrl}]: $ex")
      }
    }
  }

  /**
    * Queries organization service to check if the specified user is a
    * member of the specified organization.
    */
  private[this] def resolveMembership(
    userId: String,
    organization: String
  ): Future[Option[Membership]] = {
    organizationClient.memberships.get(
      user = Some(userId),
      organization = Some(organization),
      limit = 1,
      requestHeaders = Seq(Constants.Headers.FlowAuth -> flowAuth.jwt(FlowAuthData(userId, organization = None, role = None)))
    ).map { memberships =>
      memberships.headOption
    }.recover {
      case io.flow.organization.v0.errors.UnitResponse(401) => {
        Logger.warn(s"User[$userId] was not authorized to GET /memberships")
        None
      }
      case ex: Throwable => {
        sys.error(s"Could not communicate with organization service at[${organizationClient.baseUrl}]: $ex")
      }
    }
  }
}
