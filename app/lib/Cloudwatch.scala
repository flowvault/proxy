package lib

import javax.inject.{Inject, Singleton}

import play.api.inject.Module

import collection.JavaConverters._
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClient
import com.amazonaws.services.cloudwatch.model.{Dimension, MetricDatum, PutMetricDataRequest, StandardUnit}
import play.api.{Configuration, Environment, Logger, Mode}

import scala.concurrent.{ExecutionContext, Future}

object MetricName {
  val ResponseTime = "response.time"
}

class CloudwatchModule extends Module {
  def bindings(env: Environment, conf: Configuration) = {
    env.mode match {
      case Mode.Prod | Mode.Dev => Seq(
        bind[Cloudwatch].to[DefaultCloudwatch]
      )
      case Mode.Test => Seq(
        bind[Cloudwatch].to[MockCloudwatch]
      )
    }
  }
}

trait Cloudwatch {
  def recordResponseTime(
    server: String,
    method: String,
    path: String,
    ms: Long,
    response: Int
  )(implicit ec: ExecutionContext)
}

@Singleton
case class DefaultCloudwatch @Inject()(config: Config, env: Environment) extends Cloudwatch {
  private[this] lazy val accessKey = config.requiredString("aws.access.key")
  private[this] lazy val secretKey = config.requiredString("aws.secret.key")
  private[this] lazy val credentials = new BasicAWSCredentials(accessKey, secretKey)
  private[this] lazy val client = new AmazonCloudWatchClient(credentials)

  def recordResponseTime(
    server: String,
    method: String,
    path: String,
    ms: Long,
    response: Int
  )(implicit ec: ExecutionContext) = Future {
    val dims = Map(
      "method" -> method,
      "path" -> path,
      "response" -> response
    ).map { d =>
      new Dimension().withName(d._1).withValue(d._2.toString)
    }.asJavaCollection

    client.putMetricData(
      new PutMetricDataRequest()
        .withNamespace(server)
        .withMetricData(
          new MetricDatum()
            .withMetricName(MetricName.ResponseTime)
            .withUnit(StandardUnit.Milliseconds)
            .withDimensions(dims)
            .withValue(ms.toDouble)
        )
    )
  }
}

@Singleton
case class MockCloudwatch @Inject()() extends Cloudwatch {
  def recordResponseTime(
    server: String,
    method: String,
    path: String,
    ms: Long,
    response: Int
  )(implicit ec: ExecutionContext) = Future {
    Logger.info(s"MockCloudwatch received server $server method $method path $path $ms ms Response $response")
  }
}
