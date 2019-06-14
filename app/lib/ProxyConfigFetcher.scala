package lib

import java.net.URI

import io.flow.log.RollbarLogger
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
      case None => //
      case Some(existing) => {
        sys.error(s"Duplicate server named[${existing.name}] -- cannot merge configuration files")
      }
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

  def validate(): Either[Seq[String], ProxyConfig] = {
    val uriErrors = uri match {
      case "" => Seq("Missing uri")
      case _ => Nil
    }

    val versionErrors = version match {
      case "" => Seq("Missing version")
      case _ => Nil
    }

    val additionalErrors = scala.collection.mutable.ListBuffer[String]()
    val validServers = servers.flatMap { s =>
      s.validate match {
        case Left(e) => {
          additionalErrors ++= e
          None
        }
        case Right(valid) => {
          Some(valid)
        }
      }
    }

    val validOperations: Seq[Operation] = operations.flatMap { op =>
      op.validate(validServers) match {
        case Left(e) => {
          additionalErrors ++= e
          None
        }
        case Right(valid) => {
          Some(valid)
        }
      }
    }

    (errors ++ uriErrors ++ versionErrors ++ additionalErrors).toList match {
      case Nil => Right(
        ProxyConfig(
          Seq(
            ProxyConfigSource(
              uri = uri,
              version = version
            )
          ),
          servers = validServers,
          operations = validOperations
        )
      )
      case e => Left(e)
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

  def validate: Either[Seq[String], Server] = {
    if (name.isEmpty || host.isEmpty) {
      Left(Seq("Server name and host are required"))
    } else {
      Right(
        Server(
          name = name,
          host = host,
          logger
        )
      )
    }
  }

}

case class InternalOperation(
  method: String,
  path: String,
  server: String
) {

  def validate(servers: Seq[Server]): Either[Seq[String], Operation] = {
    if (method.isEmpty || path.isEmpty || server.isEmpty) {
      Left(Seq("Operation method, path, and server are required"))
    } else {
      servers.find(_.name == server) match {
        case None => {
          Left(Seq(s"Server[$server] not found"))
        }

        case Some(s) => {
          Right(
            Operation(
              Route(
                method = Method(method),
                path = path
              ),
              server = s
            )
          )
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
  def load(uris: Seq[String]): Either[Seq[String], ProxyConfig] = {
    uris.toList match {
      case Nil => {
        sys.error("Must have at least one configuration uri")
      }

      case uri :: rest => {
        load(uri) match {
          case Left(errors) => Left(errors)
          case Right(c) => combine(rest, c)
        }
      }
    }
  }

  @scala.annotation.tailrec
  private[this] def combine(uris: Seq[String], config: ProxyConfig): Either[Seq[String], ProxyConfig] = {
    uris.toList match {
      case Nil => {
        Right(config)
      }

      case uri :: rest => {
        load(uri) match {
          case Left(errors) => {
            Left(errors)
          }
          case Right(newConfig) => {
            combine(rest, config.merge(newConfig))
          }
        }
      }
    }
  }

  private[this] def load(uri: String): Either[Seq[String], ProxyConfig] = {
    logger.withKeyValue("uri", uri).info("Fetching configuration")
    val contents = Source.fromURL(uri).mkString
    configParser.parse(uri, contents).validate()
  }

  private[this] def refresh(): Option[Index] = {
    load(Uris) match {
      case Left(errors) => {
        logger.
          withKeyValue("uris", Uris.mkString(", ")).
          withKeyValue("errors", errors.mkString(", ")).
          error("Failed to load proxy configuration")
        None
      }
      case Right(cfg) => {
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
