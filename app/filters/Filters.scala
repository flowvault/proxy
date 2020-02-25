package filters

import javax.inject.Inject
import akka.stream.Materializer
import io.flow.log.RollbarLogger
import play.api.http.HttpFilters
import play.api.mvc._
import play.filters.cors.CORSFilter

import scala.concurrent.{ExecutionContext, Future}

/**
  * Taken from lib-play to avoid pulling in lib-play as a dependency
  */
class CorsWithLoggingFilter @javax.inject.Inject() (corsFilter: CORSFilter, loggingFilter: LoggingFilter) extends HttpFilters {
  def filters: Seq[EssentialFilter] = Seq(corsFilter, loggingFilter)
}

class LoggingFilter @Inject() (
  logger: RollbarLogger
) (
  implicit val mat: Materializer, ec: ExecutionContext
) extends Filter {

  private val LoggedHeaders = Seq(
    "User-Agent",
    "X-Forwarded-For",
    "CF-Connecting-IP",
    "True-Client-IP",
    "X-Apidoc-Version",
  ).map(_.toLowerCase)

  def apply(nextFilter: RequestHeader => Future[Result])
           (requestHeader: RequestHeader): Future[Result] = {

    val startTime = System.currentTimeMillis
    val headerMap = requestHeader.headers.toMap

    nextFilter(requestHeader).map { result =>
      val endTime = System.currentTimeMillis
      val requestTime = endTime - startTime

      val line = Seq(
        requestHeader.method,
        s"${requestHeader.host}${requestHeader.path}",
        result.header.status,
        s"${requestTime}ms",
        headerMap.getOrElse("User-Agent", Nil).mkString(","),
        headerMap.getOrElse("X-Forwarded-For", Nil).mkString(","),
        headerMap.getOrElse(
          "CF-Connecting-IP",
          headerMap.getOrElse("True-Client-IP", Nil)
        ).mkString(",")
      ).mkString(" ")

      logger
        .withKeyValue("method", requestHeader.method)
        .withKeyValue("host", requestHeader.host)
        .withKeyValue("path", requestHeader.path)
        .withKeyValue("query_params", requestHeader.queryString)
        .withKeyValue("http_code", result.header.status)
        .withKeyValue("request_time_ms", requestTime)
        .withKeyValue("request_headers",
          headerMap
            .map { case (key, value) => key.toLowerCase -> value }
            .filterKeys(LoggedHeaders.contains))
        .info(line)

      result.withHeaders("Request-Time" -> requestTime.toString)
    }
  }
}
