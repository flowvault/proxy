package lib

import java.util.concurrent.ConcurrentHashMap

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

  private[this] val Cache = new ConcurrentHashMap[Int, Either[Seq[String], MultiService]]()
  private[this] val CacheKey: Int = 1

  private[this] lazy val Uris: List[String] = config.nonEmptyList("apibuilder.service.uris")

  private[this] def load(): Either[Seq[String], MultiService] = {
    Cache.computeIfAbsent(
      CacheKey,
      _ => {
        logger.
          withKeyValues("uri", Uris).
          info("ApiBuilderServicesFetcher: fetching configuration")
        MultiService.fromUrls(Uris)
      }
    )
  }

  lazy val multiService: MultiService = load() match {
    case Left(errors) => sys.error(s"Error loading API Builder services from uris[$Uris]: ${errors.mkString(", ")}")
    case Right(multi) => multi
  }

}
