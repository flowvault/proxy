package lib

import io.apibuilder.validation.MultiService
import javax.inject.{Inject, Singleton}
import play.api.Logger

/**
  * Responsible for downloading the API Builder service specifications
  * from the URLs specified by the configuration parameter named
  * apibuilder.service.uris
  */
@Singleton
class ApiBuilderServicesFetcher @Inject() (
  config: Config
) {

  private[this] case object Lock

  private[this] lazy val Uris: Seq[String] = config.requiredString("apibuilder.service.uris").split(",").map(_.trim)
  private[this] var multiServiceCache: Option[Either[Seq[String], MultiService]] = None

  def load(urls: Seq[String]): Either[Seq[String], MultiService] = {
    Lock.synchronized {
      multiServiceCache match {
        case Some(ms) => ms
        case None => {
          multiServiceCache.getOrElse {
            Logger.info(s"ApiBuilderServicesFetcher: fetching configuration from uris[${Uris.mkString(", ")}]")
            val ms = MultiService.fromUrls(urls)
            multiServiceCache = Some(ms)
            ms
          }
        }
      }
    }
  }

  lazy val multiService: MultiService = load(Uris) match {
    case Left(errors) => sys.error(s"Error loading API Builder services from uris[$Uris]: $errors")
    case Right(multi) => multi
  }

}
