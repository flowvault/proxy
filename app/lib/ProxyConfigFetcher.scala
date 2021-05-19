package lib

import cats.data.Validated.{Invalid, Valid}
import cats.data.ValidatedNec
import cats.implicits._
import io.apibuilder.validation.util.ValidatedUrlDownloader
import io.flow.log.RollbarLogger

import java.net.URI
import javax.inject.{Inject, Singleton}
import scala.concurrent.duration.{FiniteDuration, SECONDS}
import scala.io.Source

case class ProxyConfigSource(
  uri: String,
  version: String
)

case class ProxyConfig(
  sources: Seq[ProxyConfigSource],
  servers: Seq[Server],
  operations: Seq[Operation]
) {

  def merge(other: ProxyConfig): ProxyConfig = {
    other.servers.find { s => servers.exists(_.name == s.name) } match {
      case Some(existing) => {
        sys.error(s"Duplicate server named[${existing.name}] -- cannot merge configuration files")
      }
      case _ => //
    }

    ProxyConfig(
      sources = sources ++ other.sources,
      servers = servers ++ other.servers,
      operations = operations ++ other.operations
    )
  }

}

case class InternalProxyConfig(
  uri: String,
  version: String,
  servers: Seq[InternalServer],
  operations: Seq[InternalOperation],
  errors: Seq[String]
) {

  def validate(): ValidatedNec[String, ProxyConfig] = {
    val errorsV = errors.toList match {
      case Nil => ().validNec
      case _ => errors.map(_.invalidNec).traverse(identity)
    }

    val uriV = uri match {
      case "" => "Missing uri".invalidNec
      case _ => uri.validNec
    }

    val versionV = version match {
      case "" => "Missing version".invalidNec
      case _ => version.validNec
    }

    val serversV = servers.toList.map(_.validate).traverse(identity)

    val operationsV = serversV.andThen { servers =>
      operations.toList.map(_.validate(servers)).traverse(identity)
    }

    (errorsV, uriV, versionV, serversV, operationsV).mapN { case (_, uri, version, servers, operations) =>
      ProxyConfig(
        Seq(
          ProxyConfigSource(
            uri = uri,
            version = version
          )
        ),
        servers = servers,
        operations = operations
      )
    }
  }

}

case class Server(
  name: String,
  host: String,
  logger: RollbarLogger
) {

  // TODO: Move to proxy configuration file
  val requestTimeout: FiniteDuration = name match {
    case "payment" | "payment-internal" | "partner" | "label" | "label-internal" | "return" => FiniteDuration(60, SECONDS)
    case "session" => FiniteDuration(10, SECONDS)
    case "token" | "organization" => FiniteDuration(5, SECONDS)
    case _ => FiniteDuration(30, SECONDS) // TODO: Figure out what the optimal value should be for this
  }

  val hostHeaderValue: String = Option(new URI(host).getHost).getOrElse {
    sys.error(s"Could not parse host from server[$name] host[$host]")
  }

}


case class Operation(
  route: Route,
  server: Server
)

case class InternalServer(
  name: String,
  host: String,
  logger: RollbarLogger
) {

  def validate: ValidatedNec[String, Server] = {
    if (name.isEmpty || host.isEmpty) {
      "Server name and host are required".invalidNec
    } else {
      Server(
        name = name,
        host = host,
        logger
      ).validNec
    }
  }

}

case class InternalOperation(
  method: String,
  path: String,
  server: String
) {

  def validate(servers: Seq[Server]): ValidatedNec[String, Operation] = {
    if (method.isEmpty || path.isEmpty || server.isEmpty) {
      "Operation method, path, and server are required".invalidNec
    } else {
      servers.find(_.name == server) match {
        case None => {
          s"Server[$server] not found".invalidNec
        }

        case Some(s) => {
          Operation(
            Route(
              method = Method(method),
              path = path
            ),
            server = s
          ).validNec
        }
      }
    }
  }

}

/**
  * Responsible for downloading the configuration from the URL
  * specified by the configuration parameter named
  * proxy.config.uris. Exposes an API to refresh the configuration
  * periodically.
  *
  * When downloading the configuration, we load it into an instance of
  * the Index class to pre-build the data needed to resolve paths.
  */
@Singleton
class ProxyConfigFetcher @Inject() (
  config: Config,
  configParser: ConfigParser,
  logger: RollbarLogger
) {

  private[this] lazy val Uris: List[String] = config.nonEmptyList("proxy.config.uris")

  /**
    * Loads proxy configuration from the specified URIs
    */
  def load(uris: Seq[String]): ValidatedNec[String, ProxyConfig] = {
    uris.toList match {
      case Nil => {
        // Shouldn't this be an `Invalid`?
        sys.error("Must have at least one configuration uri")
      }

      case uri :: rest => {
        load(uri).andThen(combine(rest, _))
      }
    }
  }

  @scala.annotation.tailrec
  private[this] def combine(uris: Seq[String], config: ProxyConfig): ValidatedNec[String, ProxyConfig] = {
    uris.toList match {
      case Nil => {
        config.validNec
      }

      case uri :: rest => {
        load(uri) match {
          case Valid(newConfig) => combine(rest, config.merge(newConfig))
          case invalid => invalid
        }
      }
    }
  }

  private[this] def load(uri: String): ValidatedNec[String, ProxyConfig] = {
    logger.withKeyValue("uri", uri).info("Fetching configuration")
    ValidatedUrlDownloader.withInputStream(uri) { is =>
      val contents = Source.fromInputStream(is).mkString
      if (contents.trim.isEmpty) {
        s"No content returned from uri '$uri'".invalidNec
      } else {
        configParser.parse(uri, contents).validate()
      }
    }
  }

  private[this] def refresh(): Option[Index] = {
    load(Uris) match {
      case Invalid(errors) => {
        logger.
          withKeyValues("uris", Uris).
          withKeyValues("errors", errors.toList).
          error("Failed to load proxy configuration")
        None
      }
      case Valid(cfg) => {
        Option(Index(cfg))
      }
    }
  }

  private[this] val lastLoad: Index = refresh().getOrElse {
    Index(
      ProxyConfig(
        sources = Nil,
        servers = Nil,
        operations = Nil
      )
    )
  }

  def current(): Index = lastLoad

}
