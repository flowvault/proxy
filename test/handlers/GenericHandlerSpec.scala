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

  def toString(body: Source[ByteString, _]): String = {
    implicit val system: ActorSystem = app.injector.instanceOf[ActorSystem]
    implicit val materializer: ActorMaterializer = ActorMaterializer()

    println(s"toString - 1")
    val is = body.runWith(StreamConverters.asInputStream(FiniteDuration(100, MILLISECONDS)))
    println(s"toString - 2")
    val r = scala.io.Source.fromInputStream(is, "UTF-8").mkString
    println(s"toString - 3")
    r
  }

  "GET request" in {
    MockStandaloneServer.withServer { (server, wsClient) =>
      /*
      println(s"server: $server")
      val url = s"${server.host}/users/"
      println(s"URL - $url")
      val u = await(wsClient.url(url).get()).body
      println(s"USER: $u")
      val response = await(
        genericHandler.process(
          server = server,
          request = createProxyRequest(
            requestMethod = Method.Get,
            requestPath = "/users/"
          ),
          route = Route(Method.Get, "/users/"),
          token = ResolvedToken(requestId = createTestId())
        )
      )
      wsClient.underlying
      println(s"response: ${response.body}")
      println(s"BODY: " + toString(response.body.dataStream))
*/
    }
  }

}
