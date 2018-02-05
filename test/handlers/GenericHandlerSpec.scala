package handlers

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Source, StreamConverters}
import akka.util.ByteString
import helpers.{BasePlaySpec, MockStandaloneServer}
import lib._
import play.api.mvc.Headers

import scala.concurrent.duration.{FiniteDuration, MILLISECONDS}

class GenericHandlerSpec extends BasePlaySpec {

  import scala.concurrent.ExecutionContext.Implicits.global

  private[this] def genericHandler = app.injector.instanceOf[GenericHandler]

  def createProxyRequest(
    requestMethod: Method,
    requestPath: String,
    body: Option[ProxyRequestBody] = None,
    queryParameters: Map[String, Seq[String]] = Map.empty,
    headers: Map[String, Seq[String]] = Map.empty
  ): ProxyRequest = {
    rightOrErrors(
      ProxyRequest.validate(
        requestMethod = requestMethod.toString,
        requestPath = requestPath,
        body = body,
        queryParameters = queryParameters,
        headers = Headers(
          headers.flatMap { case (k, values) =>
            values.map { v =>
              (k, v)
            }
          }.toSeq: _*
        )
      )
    )
  }

  private[this] def toString(source: Source[ByteString, _]): String = {
    implicit val system: ActorSystem = ActorSystem("QuickStart")
    implicit val materializer: ActorMaterializer = ActorMaterializer()
    val is = source.runWith(StreamConverters.asInputStream(FiniteDuration(100, MILLISECONDS)))
    scala.io.Source.fromInputStream(is, "UTF-8").mkString
  }

  "GET request" in {
    MockStandaloneServer.withServer { (server, client) =>
      val response = await(
        genericHandler.process(
          wsClient = client,
          server = server,
          request = createProxyRequest(
            requestMethod = Method.Get,
            requestPath = "/users/"
          ),
          route = Route(Method.Get, "/users/"),
          token = ResolvedToken(requestId = createTestId())
        )
      )
      response.header.status must equal(200)
      response.header.headers.get("Content-Type") must equal(Some("application/json"))
      toString(response.body.dataStream) must equal("[{\"id\":1,\"name\":\"Joe Smith\"}]")
    }
  }

}
