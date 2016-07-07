package lib

import javax.inject.{Inject, Singleton}
import play.api.Logger
import scala.io.Source

/**
  * Responsible for downloading the configuration from the URL
  * specified by the configuration parameter named
  * proxy.config.url. Exposes an API to refresh the configuration
  * periodically.
  * 
  * When downloading the configuration, we load it into an instance of
  * the Index class to pre-build the data needed to resolve paths.
  */
@Singleton
class ProxyConfigFetcher @Inject() (
  config: Config
) {

  private[this] lazy val Uri = config.requiredString("proxy.config.uri")

  /**
    * Loads service definitions from the specified URI
    */
  def load(uri: String): Either[Seq[String], ProxyConfig] = {
    Logger.info(s"ProxyConfigFetcher: fetching configuration from uri[$uri]")
    val contents = Source.fromURL(uri).mkString
    ServiceParser.parse(contents)
  }

  private[this] def refresh(): Option[Index] = {
    load(Uri) match {
      case Left(errors) => {
        Logger.error(s"Failed to load proxy configuration from Uri[$Uri]: $errors")
        None
      }
      case Right(cfg) => {
        Option(Index(cfg))
      }
    }
  }

  private[this] var lastLoad: Index = refresh().getOrElse {
    Index(
      ProxyConfig(version = "0.0.0", services = Nil)
    )
  }

  def current(): Index = lastLoad

}
