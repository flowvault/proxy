package filters

import javax.inject.Inject
import akka.stream.Materializer
import io.flow.log.RollbarLogger
import lib.{Constants, Hacks, Index, Method, ProxyConfigFetcher}
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
  logger: RollbarLogger,
  proxyConfigFetcher: ProxyConfigFetcher,
) (
  implicit val mat: Materializer, ec: ExecutionContext
) extends Filter {

  lazy val index: Index = proxyConfigFetcher.current()

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
      val operation = index.resolve(Method(requestHeader.method), requestHeader.path)

      val line = Seq(
        s"HTTP v${requestHeader.version}",
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

      val loggedRequestHeaders = headerMap
        .map { case (key, value) => key.toLowerCase -> value }
        .view
        .filterKeys(LoggedHeaders.contains)

      val convertToLatencyMetric = operation.exists { op =>
        Hacks.shouldConvertToLatencyMetric(requestHeader.method, op.route.path)
      }

      val log = logger
        .withKeyValue("https", requestHeader.secure)
        .withKeyValue("http_version", requestHeader.version)
        .withKeyValue("method", requestHeader.method)
        .withKeyValue("host", requestHeader.host)
        .withKeyValue("path", requestHeader.path)  // /foobar-sandbox/orders
        .withKeyValue("query_params", requestHeader.queryString)
        .withKeyValue("http_code", result.header.status)
        .withKeyValue("request_time_ms", requestTime)
        .withKeyValue("request_headers", loggedRequestHeaders)
        .withKeyValue("config_path", operation.map(_.route.path)) // /:organization_id/orders
        .withKeyValue("config_server", operation.map(_.server.name))
        .withKeyValue("config_host", operation.map(_.server.host))

      if (convertToLatencyMetric) {
        log.withKeyValue("convert_to_metric", convertToLatencyMetric).info(line)
      } else log.info(line)

      val requestTimeStr = requestTime.toString
      result
        .withHeaders("Request-Time" -> requestTimeStr)
        .withHeaders(Constants.Headers.FlowProxyResponseTime -> requestTimeStr)

    }
  }
}
