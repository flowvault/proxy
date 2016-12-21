package lib

import javax.inject.{Inject, Singleton}
import play.api.inject.Module
import io.flow.signalfx.v0.models.{Datapoint, DatapointForm}
import play.api.{Configuration, Environment, Logger, Mode}

import scala.concurrent.{ExecutionContext, Future}

object SignalfxMetricName {
  val ResponseTime = "response.time"
  val Value = "value"
}

class SignalfxModule extends Module {
  def bindings(env: Environment, conf: Configuration) = {
    env.mode match {
      case Mode.Prod | Mode.Dev => Seq(
        bind[Signalfx].to[DefaultSignalfx]
      )
      case Mode.Test => Seq(
        bind[Signalfx].to[MockSignalfx]
      )
    }
  }
}

trait Signalfx {
  def recordResponseTime(
    server: String,
    method: String,
    path: String,
    ms: Long,
    response: Int,
    organization: Option[String] = None,
    partner: Option[String] = None
  )(implicit ec: ExecutionContext)
}

@Singleton
case class DefaultSignalfx @Inject()(
  signalfxClient: io.flow.signalfx.v0.interfaces.Client,
  config: Config,
  env: Environment
) extends Signalfx {

  private[this] lazy val token = config.requiredString("signalfx.token")

  def recordResponseTime(
    server: String,
    method: String,
    path: String,
    ms: Long,
    response: Int,
    organization: Option[String] = None,
    partner: Option[String] = None
  )(implicit ec: ExecutionContext) = {
    val baseDimensions = Map("server" -> server, "method" -> method, "path" -> path, "response" -> response.toString)
    val dimensions = (organization, partner) match {
      case (Some(o), Some(p)) => baseDimensions ++ Map("organization" -> o) ++ Map("partner" -> p)
      case (None, Some(p)) => baseDimensions ++ Map("partner" -> p)
      case (Some(o), None) => baseDimensions ++ Map("partner" -> o)
      case (None, None) => baseDimensions
    }

    signalfxClient.datapoints.post(
      datapointForm = DatapointForm(
        guage = Some(
          Seq(
            Datapoint(
              metric = SignalfxMetricName.ResponseTime,
              value = BigDecimal(ms),
              dimensions = dimensions
            )
          )
        )
      ),
      requestHeaders = Seq(("X-SF-TOKEN", token))
    ).recover {
      case e: Throwable => Logger.error(s"SignalfxError Error calling cloudwatch: ${e.getMessage}")
    }
  }
}

@Singleton
case class MockSignalfx @Inject()() extends Signalfx {
  def recordResponseTime(
    server: String,
    method: String,
    path: String,
    ms: Long,
    response: Int,
    organization: Option[String] = None,
    partner: Option[String] = None
  )(implicit ec: ExecutionContext) = Future {
    Logger.info(s"MockSignalfx received server $server method $method path $path $ms ms Response $response")
  }
}
