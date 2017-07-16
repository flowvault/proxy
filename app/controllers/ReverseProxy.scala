package controllers

import akka.actor.ActorSystem
import io.flow.token.v0.{Client => TokenClient}
import io.flow.organization.v0.{Client => OrganizationClient}
import io.flow.session.v0.{Client => SessionClient}
import javax.inject.{Inject, Singleton}

import lib._
import play.api.Logger
import play.api.mvc._

import scala.concurrent.Future

@Singleton
class ReverseProxy @Inject () (
  system: ActorSystem,
  authorizationParser: AuthorizationParser,
  config: Config,
  override val flowAuth: FlowAuth,
  proxyConfigFetcher: ProxyConfigFetcher,
  apiBuilderServicesFetcher: ApiBuilderServicesFetcher,
  serverProxyFactory: ServerProxy.Factory,
  ws: play.api.libs.ws.WSClient
) extends Controller
  with lib.Errors
  with auth.OrganizationAuth
  with auth.TokenAuth
  with auth.SessionAuth
{

  val index: Index = proxyConfigFetcher.current()

  private[this] implicit val ec = system.dispatchers.lookup("reverse-proxy-context")

  override val organizationClient: OrganizationClient = {
    val server = mustFindServerByName("organization")
    Logger.info(s"Creating OrganizationClient w/ baseUrl[${server.host}]")
    new OrganizationClient(ws, baseUrl = server.host)
  }

  override val sessionClient: SessionClient = {
    val server = mustFindServerByName("session")
    Logger.info(s"Creating SessionClient w/ baseUrl[${server.host}]")
    new SessionClient(ws, baseUrl = server.host)
  }

  override val tokenClient: TokenClient = {
    val server = mustFindServerByName("token")
    Logger.info(s"Creating TokenClient w/ baseUrl[${server.host}]")
    new TokenClient(ws, baseUrl = server.host)
  }

  private[this] val multiService = apiBuilderServicesFetcher.current()

  private[this] val proxies: Map[String, ServerProxy] = {
    Logger.info(s"ReverseProxy loading config sources: ${index.config.sources}")
    val all = scala.collection.mutable.Map[String, ServerProxy]()
    index.config.servers.map { s =>
      if (all.isDefinedAt(s.name)) {
        sys.error(s"Duplicate server with name[${s.name}]")
      } else {
        all += (s.name -> serverProxyFactory(ServerProxyDefinition(s, multiService)))
      }
    }
    all.toMap
  }

  def handle: Action[RawBuffer] = Action.async(parse.raw) { request =>
    ProxyRequest.validate(request) match {
      case Left(errors) => Future.successful {
        UnprocessableEntity(genericErrors(errors))
      }
      case Right(pr) => {
        if (pr.requestEnvelope) {
          pr.parseRequestEnvelope()  match {
            case Left(errors) => Future.successful {
              UnprocessableEntity(genericErrors(errors))
            }
            case Right(enveloperProxyRequest) => {
              internalHandle(enveloperProxyRequest)
            }
          }
        } else {
          internalHandle(pr)
        }
      }
    }
  }

  private[this] def internalHandle(request: ProxyRequest): Future[Result] = {
    authorizationParser.parse(request.headers.get("Authorization")) match {
      case Authorization.NoCredentials => {
        proxyPostAuth(request, token = ResolvedToken(requestId = request.requestId))
      }

      case Authorization.Unrecognized => Future.successful(
        request.responseError(401, "Authorization header value must start with one of: " + Authorization.Prefixes.all.mkString(", "))
      )

      case Authorization.InvalidApiToken => Future.successful(
        request.responseError(401, "API Token is not valid")
      )

      case Authorization.InvalidJwt(missing) => Future.successful(
        request.responseError(401, s"JWT Token is not valid. Missing ${missing.mkString(", ")} from the JWT Claimset")
      )

      case Authorization.InvalidBearer => Future.successful(
        request.responseError(401, "Value for Bearer header was not formatted correctly. We expect a JWT Token.")
      )

      case Authorization.Token(token) => {
        resolveToken(
          requestId = request.requestId,
          token = token
        ).flatMap {
          case None => Future.successful(
            request.responseError(401, "API Token is not valid")
          )
          case Some(t) => {
            proxyPostAuth(request, token = t)
          }
        }
      }

      case Authorization.Session(sessionId) => {
        resolveSession(
          requestId = request.requestId,
          sessionId = sessionId
        ).flatMap {
          case None => Future.successful(
            request.responseError(401, "Session is not valid")
          )
          case Some(token) => {
            proxyPostAuth(request, token)
          }
        }
      }

      case Authorization.User(userId) => {
        proxyPostAuth(
          request,
          ResolvedToken(
            requestId = request.requestId,
            userId = Some(userId)
          )
        )
      }
    }
  }

  private[this] def proxyPostAuth(
    request: ProxyRequest,
    token: ResolvedToken
  ): Future[Result] = {
    resolve(request, token).flatMap {
      case Left(result) => {
        Future.successful(result)
      }

      case Right(operation) => {
        operation.route.organization(request.path) match {
          case None => {
            operation.route.partner(request.path) match {
              case None => proxyDefault(operation, request, token)
              case Some(partner) => {
                // should return 401 if the path is for a partner route, but the token doesn't have an explicit partnerId
                token.partnerId match {
                  case None => Future.successful(request.responseError(401, invalidPartnerMessage(partner)))
                  case Some(_) => proxyPartner(operation, partner, request, token)
                }
              }
            }
          }

          case Some(org) => {
            // should return 401 if route is for an org, but token is a partner token
            // note that console uses a token without an org, just a user - so can't be too strict here
            token.partnerId match {
              case None => proxyOrganization(operation, org, request, token)
              case Some(_) => Future.successful(request.responseError(401, invalidOrgMessage(org)))
            }
          }
        }
      }
    }
  }

  private[this] def proxyDefault(
    operation: Operation,
    request: ProxyRequest,
    token: ResolvedToken
  ): Future[Result] = {
    lookup(operation.server.name).proxy(
      request,
      operation.route,
      token,
      None,
      None
    )
  }

  private[this] def proxyOrganization(
    operation: Operation,
    organization: String,
    request: ProxyRequest,
    token: ResolvedToken
  ): Future[Result] = {
    token.userId match {
      case None => {
        token.organizationId match {
          case None => {
            // Pass to backend w/ no auth headers and let backend enforce
            // if path requires auth or not. Needed to support use case
            // like adding a credit card over JSONP or anonymous org
            // access from sessions
            proxyDefault(operation, request, token)
          }

          case Some(tokenOrganizationId) => {
            if (tokenOrganizationId == organization) {
              proxyDefault(operation, request, token)
            } else {
              Future.successful {
                request.responseError(422, invalidOrgMessage(organization))
              }
            }
          }
        }
      }

      case Some(_) => {
        authorizeOrganization(token, organization).flatMap {
          case None => Future.successful {
            request.responseError(422, invalidOrgMessage(organization))
          }

          case Some(orgToken) => {
            // Use org token here as the data returned came from the
            // organization service (supports having a sandbox token
            // on a production org)
            lookup(operation.server.name).proxy(
              request,
              operation.route,
              orgToken,
              Some(organization),
              None
            )
          }
        }
      }
    }
  }

  private[this] def proxyPartner(
    operation: Operation,
    partner: String,
    request: ProxyRequest,
    token: ResolvedToken
  ): Future[Result] = {
    token.userId match {
      case None => {
        // Currently all partner requests require authorization. Deny
        // access as there is no auth token present.
        Future.successful(
          request.responseError(401, "Missing authorization headers")
        )
      }

      case Some(_) => {
        if (token.partnerId.contains(partner)) {
          lookup(operation.server.name).proxy(
            request,
            operation.route,
            token,
            partner = Some(partner)
          )
        } else {
          Future.successful(
            request.responseError(401, invalidPartnerMessage(partner))
          )
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
    request: ProxyRequest,
    token: ResolvedToken
  ): Future[Either[Result, Operation]] = {
    val path = request.path
    val serverNameOverride: Option[String] = request.headers.get(Constants.Headers.FlowServer)
    val hostOverride: Option[String] = request.headers.get(Constants.Headers.FlowHost)

    if (serverNameOverride.isEmpty && hostOverride.isEmpty) {
      Future {
        index.resolve(request.method, path) match {
          case None => {
            multiService.validate(request.method, path) match {
              case Left(errors) => {
                Logger.info(s"Unrecognized method ${request.method} for $path - returning 422 w/ available methods: $errors")
                Left(request.response(422, genericErrors(errors).toString))
              }
              case Right(_) => {
                Logger.info(s"Unrecognized URL ${request.method} $path - returning 404")
                Left(NotFound)
              }
            }
          }

          case Some(operation) => {
            Right(operation)
          }
        }
      }
    } else {
      token.userId match {
        case None => Future.successful(
          Left(
            request.responseError(401, s"Must authenticate to specify[${Constants.Headers.FlowServer} or ${Constants.Headers.FlowHost}]")
          )
        )

        case Some(_) => {
          authorizeOrganization(token, Constants.FlowOrganizationId).map {
            case None => {
              Left(
                request.responseError(401, invalidOrgMessage(Constants.FlowOrganizationId))
              )
            }

            case Some(_) => {
              hostOverride match {
                case Some(host) => {
                  if (host.startsWith("http://") || host.startsWith("https://")) {
                    Right(
                      Operation(
                        route = Route(
                          method = request.method,
                          path = path
                        ),
                        server = Server(name = "override", host = host)
                      )
                    )
                  } else {
                    Left(
                      request.response(422, s"Value for ${Constants.Headers.FlowHost} header must start with http:// or https://")
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
                        request.response(422, s"Invalid server name from Request Header[${Constants.Headers.FlowServer}]")
                      )
                    }

                    case Some(server) => {
                      Right(
                        Operation(
                          Route(
                            method = request.method,
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

  private[this] def lookup(name: String): ServerProxy = {
    proxies.get(name).getOrElse {
      sys.error(s"No proxy defined for the server with name[$name]")
    }
  }

  private[this] def findServerByName(name: String): Option[Server] = {
    index.config.servers.find(_.name == name)
  }

  private[this] def mustFindServerByName(name: String): Server = {
    findServerByName(name).getOrElse {
      sys.error(s"There is no server named '$name' in the current config: " + index.config.sources.map(_.uri))
    }
  }

  private[this] def invalidOrgMessage(organization: String): String = {
    s"Not authorized to access organization '$organization' or the organization does not exist"
  }

  private[this] def invalidPartnerMessage(partner: String): String = {
    s"Not authorized to access partner '$partner' or the partner does not exist"
  }

}
