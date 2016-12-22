package actors

import akka.actor.{Actor, ActorLogging, ActorSystem}
import lib.{Cloudwatch, Signalfx}
import com.google.inject.AbstractModule
import play.api.libs.concurrent.AkkaGuiceSupport
import scala.concurrent.ExecutionContext

class Bindings extends AbstractModule with AkkaGuiceSupport {
  def configure = {
    bindActor[MetricActor]("metric-actor")
  }
}

object MetricActor {
  object Messages {
    case class Send(server: String, method: String, path: String, ms: Long, response: Int, organization: Option[String], partner: Option[String])
    case class SendCloudwatch(server: String, method: String, path: String, ms: Long, response: Int, organization: Option[String], partner: Option[String])
    case class SendSignalfx(server: String, method: String, path: String, ms: Long, response: Int, organization: Option[String], partner: Option[String])
  }
}

@javax.inject.Singleton
class MetricActor @javax.inject.Inject() (
  config: lib.Config,
  system: ActorSystem,
  cloudwatch: Cloudwatch,
  signalfx: Signalfx
) extends Actor with ActorLogging {

  private[this] implicit val ec: ExecutionContext = system.dispatchers.lookup("metric-actor-context")

  def receive = akka.event.LoggingReceive {
    case msg @ MetricActor.Messages.Send(server, method, path, ms, response, organization, partner) => {
      self ! MetricActor.Messages.SendCloudwatch(server, method, path, ms, response, organization, partner)
      self ! MetricActor.Messages.SendSignalfx(server, method, path, ms, response, organization, partner)
    }

    case msg @ MetricActor.Messages.SendCloudwatch(server, method, path, ms, response, organization, partner) => {
      cloudwatch.recordResponseTime(server, method, path, ms, response, organization, partner)
    }

    case msg @ MetricActor.Messages.SendSignalfx(server, method, path, ms, response, organization, partner) => {
      signalfx.recordResponseTime(server, method, path, ms, response, organization, partner)
    }

    case msg: Any => // noop
  }

}
