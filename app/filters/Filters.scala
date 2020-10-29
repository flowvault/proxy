package filters

import javax.inject.Inject
import akka.stream.Materializer
import io.flow.log.RollbarLogger
import lib.Constants
import lib.timed.TimedFuture
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

    val headerMap = requestHeader.headers.toMap

    TimedFuture(nextFilter(requestHeader)).map { timedResult =>
      val result = timedResult.value
      val requestTime = timedResult.durationMs

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
            .view
            .filterKeys(LoggedHeaders.contains))
        .info(line)

      val requestTimeStr = requestTime.toString
      result
        .withHeaders("Request-Time" -> requestTimeStr)
        .withHeaders(Constants.Headers.FlowProxyResponseTime -> requestTimeStr)

    }
  }
}
