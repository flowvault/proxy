package controllers

import akka.actor.ActorSystem
import io.flow.common.v0.models.Environment
import io.flow.token.v0.{Client => TokenClient}
import io.flow.organization.v0.{Client => OrganizationClient}
import io.flow.organization.v0.models.{OrganizationAuthorizationForm}
import io.flow.token.v0.models.{OrganizationTokenReference, TokenAuthenticationForm, TokenReference}
import java.util.UUID
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
  flowAuth: FlowAuth,
  proxyConfigFetcher: ProxyConfigFetcher,
  apidocServicesFetcher: ApidocServicesFetcher,
  serverProxyFactory: ServerProxy.Factory,
  ws: play.api.libs.ws.WSClient
) extends Controller with lib.Errors {

  val index: Index = proxyConfigFetcher.current()

  private[this] implicit val ec = system.dispatchers.lookup("reverse-proxy-context")

  private[this] val organizationClient = {
    val server = mustFindServerByName("organization")
    Logger.info(s"Creating OrganizationClient w/ baseUrl[${server.host}]")
    new OrganizationClient(ws, baseUrl = server.host)
  }

  private[this] val tokenClient = {
    val server = mustFindServerByName("token")
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

  def handle: Action[RawBuffer] = Action.async(parse.raw) { request =>
    ProxyRequest.validate(request) match {
      case Left(errors) => Future.successful {
        UnprocessableEntity(genericErrors(errors))
      }
      case Right(pr) => internalHandle(pr)
    }
  }

  private[this] def internalHandle(request: ProxyRequest): Future[Result] = {
    val requestId: String = request.headers.get(Constants.Headers.FlowRequestId).getOrElse {
      "api" + UUID.randomUUID.toString.replaceAll("-", "") // make easy to cut & paste
    }

    authorizationParser.parse(request.headers.get("Authorization")) match {
      case Authorization.NoCredentials => {
        proxyPostAuth(requestId, request, token = None)
      }

      case Authorization.Unrecognized => Future(
        request.response(401, s"Authorization header value must start with one of: " + Authorization.Unrecognized.valid.mkString(", "))
      )

      case Authorization.InvalidApiToken => Future(
        request.response(401, s"API Token is not valid")
      )

      case Authorization.InvalidJwt(missing) => Future(
        request.response(401, s"JWT Token is not valid. Missing ${missing.mkString(", ")} from the JWT Claimset")
      )

      case Authorization.InvalidBearer => Future(
        request.response(401, "Value for Bearer header was not formatted correctly. We expect a JWT Token.")
      )

      case Authorization.Token(token) => {
        resolveToken(requestId, token).flatMap {
          case None => Future(
            request.response(401, "API Token is not valid")
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
    request: ProxyRequest,
    token: Option[ResolvedToken]
  ): Future[Result] = {
    resolve(requestId, request, token).flatMap {
      case Left(result) => {
        Future(result)
      }

      case Right(operation) => {
        operation.route.organization(request.path) match {
          case None => {
            operation.route.partner(request.path) match {
              case None => proxyDefault(operation, requestId, request, token)
              case Some(partner) => proxyPartner(operation, partner, requestId, request, token)
            }
          }

          case Some(org) => {
            proxyOrganization(operation, org, requestId, request, token)
          }
        }
      }
    }
  }


  private[this] def proxyDefault(
    operation: Operation,
    requestId: String,
    request: ProxyRequest,
    token: Option[ResolvedToken]
  ): Future[Result] = {
    lookup(operation.server.name).proxy(
      requestId,
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
    requestId: String,
    request: ProxyRequest,
    token: Option[ResolvedToken]
  ): Future[Result] = {
    token match {
      case None => {
        // Pass to backend w/ no auth headers and let backend enforce
        // if path requires auth or not. Needed to support use case
        // like adding a credit card over JSONP
        proxyDefault(operation, requestId, request, None)
      }

      case Some(t) => {
        resolveOrganizationAuthorization(t, organization).flatMap {
          case None => Future(
            request.response(422, s"Not authorized to access $organization or the organization does not exist")
          )

          case Some(orgToken) => {
            // Use org token here as the data returned came from the
            // organization service (supports having a sandbox token
            // on a production org)
            lookup(operation.server.name).proxy(
              requestId,
              request,
              operation.route,
              Some(orgToken),
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
    requestId: String,
    request: ProxyRequest,
    token: Option[ResolvedToken]
  ): Future[Result] = {
    token match {
      case None => {
        // Currently all partner requests require authorization. Deny
        // access as there is no auth token present.
        Future(
          request.response(401, "Missing authorization headers")
        )
      }

      case Some(t) => {
        t.partnerId == Some(partner) match {
          case false => Future(
            request.response(401, s"Not authorized to access $partner or the partner does not exist")
          )

          case true => {
            lookup(operation.server.name).proxy(
              requestId,
              request,
              operation.route,
              token,
              None,
              Some(partner)
            )
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
    request: ProxyRequest,
    token: Option[ResolvedToken]
  ): Future[Either[Result, Operation]] = {
    val path = request.path
    val serverNameOverride: Option[String] = request.headers.get(Constants.Headers.FlowServer)
    val hostOverride: Option[String] = request.headers.get(Constants.Headers.FlowHost)

    (serverNameOverride.isEmpty && hostOverride.isEmpty) match {
      case true => Future {
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

      case false => {
        token match {
          case None => Future(
            Left(
              request.response(401, s"Must authenticate to specify[${Constants.Headers.FlowServer} or ${Constants.Headers.FlowHost}]")
            )
          )

          case Some(t) => {
            resolveOrganizationAuthorization(t, Constants.FlowOrganizationId).map {
              case None => {
                Left(
                  request.response(401, s"Not authorized to access organization[${Constants.FlowOrganizationId}]")
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
