package lib

import javax.inject.{Inject, Singleton}
import play.api.Logger
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

  def merge(other: ProxyConfig) = {
    other.servers.find { s => servers.find(_.name == s.name).isDefined } match {
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
  host: String
)

case class Operation(
  route: Route,
  server: Server
)

case class InternalServer(
  name: String,
  host: String
) {

  def validate: Either[Seq[String], Server] = {
    (name.isEmpty || host.isEmpty) match {
      case true => Left(Seq("Server name and host are required"))
      case false => Right(
        Server(
          name = name,
          host = host
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
    (method.isEmpty || path.isEmpty || server.isEmpty) match {
      case true => {
        Left(Seq("Operation method, path, and server are required"))
      }

      case false => {
        servers.find(_.name == server) match {
          case None => Left(Seq(s"Server[$server] not found"))
          case Some(s) => {
            Right(
              Operation(
                Route(
                  method = method,
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
  config: Config
) {

  private[this] lazy val Uris: Seq[String] = config.requiredString("proxy.config.uris").split(",").map(_.trim)

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
          case Left(errors) => {
            Left(errors)
          }
          case Right(config) => {
            combine(rest, config)
          }
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
    Logger.info(s"ProxyConfigFetcher: fetching configuration from uri[$uri]")
    val contents = Source.fromURL(uri).mkString
    ConfigParser.parse(uri, contents).validate()
  }

  private[this] def refresh(): Option[Index] = {
    load(Uris) match {
      case Left(errors) => {
        Logger.error(s"Failed to load proxy configuration from Uris[$Uris]: $errors")
        None
      }
      case Right(cfg) => {
        Option(Index(cfg))
      }
    }
  }

  private[this] var lastLoad: Index = refresh().getOrElse {
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
