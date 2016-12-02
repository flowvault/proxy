package lib

import javax.inject.{Inject, Singleton}

import scala.concurrent.ExecutionContext

@Singleton
case class MultiMetric @Inject()(
  cloudwatch: Cloudwatch,
  librato: Librato
) {
  def recordResponseTime(
    server: String,
    method: String,
    path: String,
    ms: Long,
    response: Int,
    organization: Option[String] = None,
    partner: Option[String] = None
  )(implicit ec: ExecutionContext) = {
    cloudwatch.recordResponseTime(server, method, path, ms, response, organization, partner)
    librato.recordResponseTime(server, method, path, ms, response, organization, partner)
  }
}