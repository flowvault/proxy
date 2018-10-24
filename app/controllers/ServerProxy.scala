package controllers

import akka.actor.ActorSystem
import com.google.inject.AbstractModule
import com.google.inject.assistedinject.{Assisted, FactoryModuleBuilder}
import io.apibuilder.validation.FormData
import javax.inject.Inject
import akka.stream.ActorMaterializer
import play.api.mvc._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}
import lib._
import play.api.libs.ws.WSClient

import scala.annotation.tailrec

/**
  * Server Proxy is responsible for proxying all requests to a given
  * server. The primary purpose of the proxy is to segment our thread
  * pools by server - so if one server is having difficulty, it is
  * less likely to impact other servers.
  */
trait ServerProxy {

  def server: Server

  def proxy(
    request: ProxyRequest,
    route: Route,
    token: ResolvedToken,
    organization: Option[String] = None,
    partner: Option[String] = None
  ): Future[play.api.mvc.Result]

}

object ServerProxy {

  val DefaultContextName = s"default-server-context"

  trait Factory {
    def apply(server: Server): ServerProxy
  }

  /**
    * Maps a query string that may contain multiple values per parameter
    * to a sequence of query parameters. Uses the underlying form data to
    * also upcast the parameters (mapping the incoming parameters to a json
    * document, upcasting, then back to query parameters)
    *
    * @todo Add example query string
    * @example
    * {{{
    *    query(
    *      Map[String, Seq[String]](
    *        "foo" -> Seq("a", "b"),
    *        "foo2" -> Seq("c")
    *      )
    *    ) == Seq(
    *      ("foo", "a"),
    *      ("foo", "b"),
    *      ("foo2", "c")
    *    )
    *  }}}
    * @param incoming A map of query parameter keys to sequences of their values.
    * @return A sequence of keys, each paired with exactly one value. The keys are further
    *         normalized to match Flow expectations (e.g. number[] => number)
    */
  def query(
             incoming: Map[String, Seq[String]]
           ): Seq[(String, String)] = {
    Util.toFlatSeq(
      FormData.parseEncoded(FormData.toEncoded(FormData.toJson(incoming)))
    )
  }
}

class ServerProxyModule extends AbstractModule {
  override def configure(): Unit = {
    install(new FactoryModuleBuilder()
      .implement(classOf[ServerProxy], classOf[ServerProxyImpl])
      .build(classOf[ServerProxy.Factory])
    )
  }
}

class ServerProxyImpl @Inject()(
  implicit val system: ActorSystem,
  wsClient: WSClient,
  urlFormEncodedHandler: handlers.UrlFormEncodedHandler,
  applicationJsonHandler: handlers.ApplicationJsonHandler,
  jsonpHandler: handlers.JsonpHandler,
  genericHandler: handlers.GenericHandler,
  val controllerComponents: ControllerComponents,
  @Assisted override val server: Server
) extends ServerProxy
  with BaseControllerHelpers
{

  private[this] implicit val (ec, name) = resolveContextName(server.name)
  private[this] implicit val materializer: ActorMaterializer = ActorMaterializer()

  /**
    * Returns the execution context to use, if found. Works by recursively
    * shortening service name by splitting on "-"
    */
  @tailrec
  private[this] def resolveContextName(name: String): (ExecutionContext, String) = {
    val contextName = s"$name-context"
    Try {
      system.dispatchers.lookup(contextName)
    } match {
      case Success(context) => {
        server.logger.
          withKeyValue("server", server.name).
          withKeyValue("context_name", contextName).
          info("Using execution context with this name")
        (context, name)
      }

      case Failure(_) => {
        val i = name.lastIndexOf("-")
        if (i > 0) {
          resolveContextName(name.substring(0, i))
        } else {
          server.logger.withKeyValue("context_name", name).warn("Execution context not found. Using default execution context")
          (system.dispatchers.lookup(ServerProxy.DefaultContextName), ServerProxy.DefaultContextName)
        }
      }
    }
  }

  override final def proxy(
    request: ProxyRequest,
    route: Route,
    token: ResolvedToken,
    organization: Option[String] = None,
    partner: Option[String] = None
  ): Future[Result] = {
    if (request.jsonpCallback.isDefined) {
      jsonpHandler.process(wsClient, server, request, route, token)
    } else {
      request.contentType match {
        case ContentType.UrlFormEncoded => {
          urlFormEncodedHandler.process(wsClient, server, request, route, token)
        }

        case ContentType.ApplicationJson => {
          applicationJsonHandler.process(wsClient, server, request, route, token)
        }

        case _ => {
          genericHandler.process(wsClient, server, request, route, token)
        }
      }
    }
  }

}
