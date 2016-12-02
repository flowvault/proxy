package lib

import javax.inject.{Inject, Singleton}

import com.librato.metrics.client._
import play.api.inject.Module

import collection.JavaConverters._
import play.api.{Configuration, Environment, Logger, Mode}

import scala.concurrent.{ExecutionContext, Future}

object LibratoMetricName {
  val ResponseTime = "response.time"
  val Value = "value"
}

class LibratoModule extends Module {
  def bindings(env: Environment, conf: Configuration) = {
    env.mode match {
      case Mode.Prod | Mode.Dev => Seq(
        bind[Librato].to[DefaultLibrato]
      )
      case Mode.Test => Seq(
        bind[Librato].to[MockLibrato]
      )
    }
  }
}

trait Librato {
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
case class DefaultLibrato @Inject()(config: Config, env: Environment) extends Librato {

  private[this] lazy val email = config.requiredString("librato.api.email")
  private[this] lazy val token = config.requiredString("librato.api.token")
  private[this] lazy val client = LibratoClient.builder(email, token).build()

  def recordResponseTime(
    server: String,
    method: String,
    path: String,
    ms: Long,
    response: Int,
    organization: Option[String] = None,
    partner: Option[String] = None
  )(implicit ec: ExecutionContext) = {
    Future {
      val otherTags = Map(
        "method" -> Some(method),
        "path" -> Some(path),
        "response" -> Some(response.toString),
        "organization" -> organization,
        "partner" -> partner
      ).flatMap { t =>
        t._2 match {
          case None => None
          case Some(value) => Some(new Tag(t._1, value))
        }
      }.toList

      val result = client.postMeasures(
        new Measures().add(
          new TaggedMeasure(LibratoMetricName.ResponseTime, ms.toDouble, new Tag("server", server), otherTags:_*)
        )
      )

      val errors = result.results.asScala.filter(_.isError).map(_.toString)
      if (!errors.isEmpty) {
        throw new Exception(errors.mkString(", "))
      }
    }.recover {
      case e: Throwable => Logger.error(s"LibratoError Error calling service: ${e.getMessage}")
    }
  }
}

@Singleton
case class MockLibrato @Inject()() extends Librato {
  def recordResponseTime(
    server: String,
    method: String,
    path: String,
    ms: Long,
    response: Int,
    organization: Option[String] = None,
    partner: Option[String] = None
  )(implicit ec: ExecutionContext) = Future {
    Logger.info(s"MockLibrato received server $server method $method path $path $ms ms Response $response")
  }
}
