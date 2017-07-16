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

  private[this] lazy val Uris: Seq[String] = config.requiredString("apibuilder.service.uris").split(",").map(_.trim)

  def load(urls: Seq[String]): Either[Seq[String], MultiService] = {
    Logger.info(s"ApiBuilderServicesFetcher: fetching configuration from uris[${Uris.mkString(", ")}]")
    MultiService.fromUrls(urls)
  }

  def current(): MultiService = load(Uris) match {
    case Left(errors) => sys.error(s"Error loading API Builder services from uris[$Uris]: $errors")
    case Right(multi) => multi
  }

}