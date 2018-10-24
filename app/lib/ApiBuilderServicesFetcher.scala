package lib

import io.apibuilder.validation.MultiService
import io.flow.log.RollbarLogger
import javax.inject.{Inject, Singleton}

/**
  * Responsible for downloading the API Builder service specifications
  * from the URLs specified by the configuration parameter named
  * apibuilder.service.uris
  */
@Singleton
class ApiBuilderServicesFetcher @Inject() (
  config: Config,
  logger: RollbarLogger
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
            logger.
              withKeyValue("uris", Uris.mkString(", ")).
              info("ApiBuilderServicesFetcher: fetching configuration")
            val ms = MultiService.fromUrls(urls)
            multiServiceCache = Some(ms)
            ms
          }
        }
      }
    }
  }

  lazy val multiService: MultiService = load(Uris) match {
    case Left(errors) => sys.error(s"Error loading API Builder services from uris[$Uris]: ${errors.mkString(", ")}")
    case Right(multi) => multi
  }

}
