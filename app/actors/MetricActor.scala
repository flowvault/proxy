package actors

import akka.actor.{Actor, ActorLogging, ActorSystem}
import lib.Cloudwatch
import com.google.inject.AbstractModule
import play.api.libs.concurrent.AkkaGuiceSupport
import scala.concurrent.ExecutionContext

class Bindings extends AbstractModule with AkkaGuiceSupport {
  def configure() = {
    bindActor[MetricActor]("metric-actor")
  }
}

object MetricActor {
  object Messages {
    case class Send(
      server: String,
      requestId: String,
      method: String,
      path: String,
      ms: Long,
      response: Int,
      organizationId: Option[String],
      partnerId: Option[String],
      userId: Option[String]
    )
  }
}

@javax.inject.Singleton
class MetricActor @javax.inject.Inject() (
  config: lib.Config,
  system: ActorSystem,
  cloudwatch: Cloudwatch
) extends Actor with ActorLogging {

  private[this] implicit val ec: ExecutionContext = system.dispatchers.lookup("metric-actor-context")

  def receive = akka.event.LoggingReceive {
    case _ @ MetricActor.Messages.Send(server, requestId, method, path, ms, response, organizationId, partnerId, userId) => {
      cloudwatch.recordResponseTime(server, requestId, method, path, ms, response, organizationId, partnerId, userId)
    }

    case _: Any => // noop
  }

}
