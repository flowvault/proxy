package helpers

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Source, StreamConverters}
import akka.util.ByteString
import handlers.Handler
import io.flow.log.RollbarLogger
import lib._
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{Headers, Result}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.{FiniteDuration, MILLISECONDS}

trait HandlerBasePlaySpec extends BasePlaySpec {

  def logger: RollbarLogger

  case class SimulatedResponse(
    server: Server,
    request: ProxyRequest,
    result: Result,
    body: String
  ) {

    val status: Int = result.header.status
    val contentType: Option[String] = result.body.contentType
    val contentLength: Option[Long] = result.body.contentLength

    def header(name: String): Option[String] = {
      result.header.headers.get(name)
    }

    def bodyAsJson: JsValue = {
      Json.parse(body)
    }

  }

  def createProxyRequest(
    requestMethod: Method,
    requestPath: String,
    body: Option[String] = None,
    queryParameters: Map[String, Seq[String]] = Map.empty,
    headers: Map[String, Seq[String]] = Map.empty
  ): ProxyRequest = {
    rightOrErrors(
      ProxyRequest.validate(
        requestMethod = requestMethod.toString,
        requestPath = requestPath,
        body = body.map { b =>
          ProxyRequestBody.Bytes(
            ByteString(b.getBytes("UTF-8"))
          )
        },
        queryParameters = queryParameters,
        headers = Headers(
          headers.flatMap { case (k, values) =>
            values.map { v =>
              (k, v)
            }
          }.toSeq: _*
        )
      )(logger)
    )
  }

   private[this] def toString(source: Source[ByteString, _]): String = {
    implicit val system: ActorSystem = ActorSystem("toString")
    implicit val materializer: ActorMaterializer = ActorMaterializer()
    val is = source.runWith(StreamConverters.asInputStream(FiniteDuration(100, MILLISECONDS)))
    scala.io.Source.fromInputStream(is, "UTF-8").mkString
  }

   def simulate(
    handler: Handler,
    method: Method,
    path: String,
    serverName: String = "test",
    queryParameters: Map[String, Seq[String]] = Map.empty,
    body: Option[String] = None
  )(
   implicit ec: ExecutionContext
  ): SimulatedResponse = {
    MockStandaloneServer.withTestClient { (client, port) =>
      val server = Server(
        name = serverName,
        host = s"http://localhost:$port",
        logger = logger
      )

      val proxyRequest = createProxyRequest(
        requestMethod = method,
        requestPath = path,
        queryParameters = queryParameters,
        body = body
      )

      val result = await(
        handler.process(
          wsClient = client,
          server = server,
          request = proxyRequest,
          route = Route(method, path),
          token = ResolvedToken(requestId = createTestId())
        )
      )

      SimulatedResponse(
        server = server,
        request = proxyRequest,
        result = result,
        body = toString(result.body.dataStream)
      )
    }
  }


}
