package controllers

import akka.actor.ActorSystem
import io.flow.common.v0.models.{Environment, Organization, Role, UserReference}
import io.flow.token.v0.{Client => TokenClient}
import io.flow.organization.v0.{Client => OrganizationClient}
import io.flow.organization.v0.models.{Membership, OrganizationAuthorizationForm}
import io.flow.token.v0.models.{OrganizationTokenReference, TokenAuthenticationForm, TokenReference}
import java.util.UUID
import javax.inject.{Inject, Singleton}
import lib.{ApidocServicesFetcher, Authorization, AuthorizationParser, Config, Constants, Index, FlowAuth}
import lib.{Operation, ResolvedToken, Route, Server, ProxyConfigFetcher}
import play.api.Logger
import play.api.libs.json.Json
import play.api.mvc._
import org.apache.commons.codec.binary.Base64
import scala.concurrent.Future

@Singleton
class ReverseProxy @Inject () (
  system: ActorSystem,
  authorizationParser: AuthorizationParser,
  config: Config,
  flowAuth: FlowAuth,
  proxyConfigFetcher: ProxyConfigFetcher,
  apidocServicesFetcher: ApidocServicesFetcher,
  serverProxyFactory: ServerProxy.Factory,
  ws: play.api.libs.ws.WSClient
) extends Controller with lib.Errors {

  val index: Index = proxyConfigFetcher.current()

  private[this] implicit val ec = system.dispatchers.lookup("reverse-proxy-context")

  private[this] val organizationClient = {
    val server = findServerByName("organization").getOrElse {
      sys.error("There is no server named 'organization' in the current config: " + index.config.sources.map(_.uri))
    }
    Logger.info(s"Creating OrganizationClient w/ baseUrl[${server.host}]")
    new OrganizationClient(ws, baseUrl = server.host)
  }

  private[this] val tokenClient = {
    val server = findServerByName("token").getOrElse {
      sys.error("There is no server named 'token' in the current config: " + index.config.sources.map(_.uri))
    }
    Logger.info(s"Creating TokenClient w/ baseUrl[${server.host}]")
    new TokenClient(ws, baseUrl = server.host)
  }

  private[this] val multiService = apidocServicesFetcher.current()
  
  private[this] val proxies: Map[String, ServerProxy] = {
    Logger.info(s"ReverseProxy loading config sources: ${index.config.sources}")
    val all = scala.collection.mutable.Map[String, ServerProxy]()
    index.config.servers.map { s =>
      all.isDefinedAt(s.name) match {
        case true => {
          sys.error(s"Duplicate server with name[${s.name}]")
        }
        case false => {
          all += (s.name -> serverProxyFactory(ServerProxyDefinition(s, multiService)))
        }
      }
    }
    all.toMap
  }

  def handle = Action.async(parse.raw) { request: Request[RawBuffer] =>
    val requestId: String = request.headers.get(Constants.Headers.FlowRequestId).getOrElse {
      "api" + UUID.randomUUID.toString.replaceAll("-", "") // make easy to cut & paste
    }

    authorizationParser.parse(request.headers.get("Authorization")) match {
      case Authorization.NoCredentials => {
        proxyPostAuth(requestId, request, token = None)
      }

      case Authorization.Unrecognized => Future(
        unauthorized(s"Authorization header value must start with one of: " + Authorization.Unrecognized.valid.mkString(", "))
      )

      case Authorization.InvalidApiToken => Future(
        unauthorized(s"API Token is not valid")
      )

      case Authorization.InvalidJwt(missing) => Future(
        unauthorized(s"JWT Token is not valid. Missing ${missing.mkString(", ")} from the JWT Claimset")
      )

      case Authorization.InvalidBearer => Future(
        unauthorized("Value for Bearer header was not formatted correctly. We expect a JWT Token.")
      )

      case Authorization.Token(token) => {
        resolveToken(requestId, token).flatMap {
          case None => Future(
            unauthorized(s"API Token is not valid")
          )
          case Some(token) => {
            proxyPostAuth(requestId, request, token = ResolvedToken.fromToken(requestId, token))
          }
        }
      }

      case Authorization.User(userId) => {
        proxyPostAuth(requestId, request, token = Some(ResolvedToken.fromUser(requestId, userId)))
      }
    }
  }

  private[this] def proxyPostAuth(
    requestId: String,
    request: Request[RawBuffer],
    token: Option[ResolvedToken]
  ): Future[Result] = {
    // If we have a callback param indicated JSONP, respect the method parameter as well
    val method = request.queryString.get("callback") match {
      case None => request.method
      case Some(_) => request.queryString.get("method").getOrElse(Nil).headOption.map(_.toUpperCase).getOrElse(request.method)
    }

    resolve(requestId, method, request, token).flatMap { result =>
      result match {
        case Left(result) => Future(result)

        case Right(operation) => {
          operation.route.organization(request.path) match {
            case None  => {
              lookup(operation.server.name).proxy(
                requestId,
                request,
                operation.route,
                token
              )
            }

            case Some(org) => {
              token match {
                case None => {
                  lookup(operation.server.name).proxy(
                    requestId,
                    request,
                    operation.route,
                    None
                  )
                }

                case Some(t) => {
                  resolveOrganizationAuthorization(t, org).flatMap {
                    case None => Future(
                      unauthorized(s"Not authorized to access $org or the organization does not exist")
                    )

                    case Some(orgToken) => {
                      lookup(operation.server.name).proxy(
                        requestId,
                        request,
                        operation.route,
                        Some(orgToken)
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
    * Resolves the incoming method and path to a specific operation. Also implements
    * overrides from incoming request headers:
    * 
    *   - headers['X-Flow-Server']: If specified we use this server name
    *   - headers['X-Flow-Host']: If specified we use this host
    * 
    * If any override headers are specified, we also verify that we
    * have an auth token identifying a user that is a member of the
    * flow organization. Otherwise we return an error.
    */
  private[this] def resolve(
    requestId: String,
    method: String,
    request: Request[RawBuffer],
    token: Option[ResolvedToken]
  ): Future[Either[Result, Operation]] = {
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

          case Some(operation) => {
            Right(operation)
          }
        }
      }

      case false => {
        token match {
          case None => Future(
            Left(
              unauthorized(s"Must authenticate to specify[${Constants.Headers.FlowServer} or ${Constants.Headers.FlowHost}]")
            )
          )

          case Some(t) => {
            resolveOrganizationAuthorization(t, Constants.FlowOrganizationId).map {
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
                        Operation(
                          route = Route(
                            method = method,
                            path = path
                          ),
                          server = Server(name = "override", host = host)
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

                      case Some(server) => {
                        Right(
                          Operation(
                            Route(
                              method = method,
                              path = path
                            ),
                            server = server
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
  
  private[this] def lookup(name: String): ServerProxy = {
    proxies.get(name).getOrElse {
      sys.error(s"No proxy defined for the server with name[$name]")
    }
  }

  private[this] def unauthorized(message: String) = {
    NotFound(genericError(message))
  }

  private[this] def notFound(message: String) = {
    NotFound(genericError(message))
  }

  private[this] def unprocessableEntity(message: String) = {
    UnprocessableEntity(genericError(message))
  }

  private[this] def findServerByName(name: String): Option[Server] = {
    index.config.servers.find(_.name == name)
  }


  /**
    * Queries token server to check if the specified token is a known
    * valid token.
    */
  private[this] def resolveToken(requestId: String, token: String): Future[Option[TokenReference]] = {
    tokenClient.tokens.postAuthentications(
      TokenAuthenticationForm(token = token),
      requestHeaders = Seq(
        Constants.Headers.FlowRequestId -> requestId
      )
    ).map { tokenReference =>
      Some(tokenReference)

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
    token: ResolvedToken,
    organization: String
  ): Future[Option[ResolvedToken]] = {
    val authFuture = (token.environment, token.organizationId) match {

      case (Some(env), Some(orgId)) => {
        organizationClient.organizationAuthorizations.post(
          OrganizationAuthorizationForm(
            organization = organization,
            environment = Environment(env)
          ),
          requestHeaders = flowAuth.headers(token)
        )
      }

      case (_, _) => {
        organizationClient.organizationAuthorizations.getByOrganization(
          organization = organization,
          requestHeaders = flowAuth.headers(token)
        )
      }
    }

    authFuture.map { orgAuth =>
      Some(
        token.copy(
          organizationId = Some(organization),
          environment = Some(orgAuth.environment.toString),
          role = Some(orgAuth.role.toString)
        )
      )
    }.recover {
      case io.flow.organization.v0.errors.UnitResponse(401) => {
        Logger.warn(s"Token[$token] was not authorized for organization[$organization]")
        None
      }

      case io.flow.organization.v0.errors.UnitResponse(404) => {
        Logger.warn(s"Token[$token] organization[$organization] not found")
        None
      }

      case ex: Throwable => {
        sys.error(s"Error communicating with organization server at[${organizationClient.baseUrl}]: ${ex.getMessage}")
      }
    }
  }

}
