package lib

import javax.inject.{Inject, Singleton}

import play.api.inject.{Binding, Module}

import collection.JavaConverters._
import com.amazonaws.auth.{AWSStaticCredentialsProvider, BasicAWSCredentials}
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClientBuilder
import com.amazonaws.services.cloudwatch.model.{Dimension, MetricDatum, PutMetricDataRequest, StandardUnit}
import play.api.{Configuration, Environment, Logger, Mode}

import scala.concurrent.{ExecutionContext, Future}

object CloudwatchMetricName {
  val ResponseTime = "response.time"
  val Value = "value"
}

class CloudwatchModule extends Module {

  def bindings(env: Environment, conf: Configuration): Seq[Binding[Cloudwatch]] = {
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
    requestId: String,
    method: String,
    path: String,
    ms: Long,
    response: Int,
    organizationId: Option[String] = None,
    partnerId: Option[String] = None,
    userId: Option[String] = None
  )(implicit ec: ExecutionContext): Future[Unit]
}

@Singleton
case class DefaultCloudwatch @Inject()(
  config: Config, env: Environment
) extends Cloudwatch {

  private[this] lazy val client = AmazonCloudWatchClientBuilder.standard().
    withCredentials(new AWSStaticCredentialsProvider(
      new BasicAWSCredentials(
        config.requiredString("aws.access.key"),
        config.requiredString("aws.secret.key"))
    )).build()

  def recordResponseTime(
    server: String,
    requestId: String,
    method: String,
    path: String,
    ms: Long,
    response: Int,
    organizationId: Option[String] = None,
    partnerId: Option[String] = None,
    userId: Option[String] = None
  )(implicit ec: ExecutionContext): Future[Unit] = {
    Future {
      val dims = Map(
        "server" -> Some(server),
        "request_id" -> Some(requestId),
        "method" -> Some(method),
        "path" -> Some(path),
        "response" -> Some(response.toString),
        "organization" -> organizationId,
        "partner" -> partnerId,
        "user_id" -> userId
      ).flatMap { t =>
        t._2 match {
          case None => None
          case Some(value) => Some(new Dimension().withName(t._1).withValue(value))
        }
      }.asJavaCollection

      client.putMetricData(
        new PutMetricDataRequest()
          .withNamespace(CloudwatchMetricName.ResponseTime)
          .withMetricData(
            new MetricDatum()
              .withMetricName(CloudwatchMetricName.Value)
              .withUnit(StandardUnit.Milliseconds)
              .withDimensions(dims)
              .withValue(ms.toDouble)
          )
      )

      ()
    }.recover {
      case e: Throwable => {
        Logger.error(
          s"FlowAlertError Proxy Cloudwatch Error calling cloudwatch: ${e.getMessage}", e
        )
        Future.successful(())
      }
    }
  }
}

@Singleton
case class MockCloudwatch @Inject()() extends Cloudwatch {
  def recordResponseTime(
    server: String,
    requestId: String,
    method: String,
    path: String,
    ms: Long,
    response: Int,
    organizationId: Option[String] = None,
    partnerId: Option[String] = None,
    userId: Option[String] = None,
  )(implicit ec: ExecutionContext): Future[Unit] = Future {
    Logger.info(
      s"MockCloudwatch received server $server requestId $requestId method $method path $path $ms ms Response $response"
    )
  }
}
