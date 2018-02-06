package handlers

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Source, StreamConverters}
import akka.util.ByteString
import helpers.{BasePlaySpec, MockStandaloneServer}
import lib._
import play.api.libs.json.{JsArray, Json}
import play.api.mvc.{Headers, Result}

import scala.concurrent.duration.{FiniteDuration, MILLISECONDS}

class GenericHandlerSpec extends BasePlaySpec {

  import scala.concurrent.ExecutionContext.Implicits.global

  private[this] def genericHandler = app.injector.instanceOf[GenericHandler]

  private[this] def createProxyRequest(
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

  private[this] def simulateResponse(
    method: Method,
    path: String
  ): (Result, String) = {
    MockStandaloneServer.withServer { (server, client) =>
      val response = await(
        genericHandler.process(
          wsClient = client,
          server = server,
          request = createProxyRequest(
            requestMethod = method,
            requestPath = path
          ),
          route = Route(method, path),
          token = ResolvedToken(requestId = createTestId())
        )
      )
      (response, toString(response.body.dataStream))
    }
  }

  "GET application/json" in {
    val (response, body) = simulateResponse(Method.Get, "/users/")
    response.header.status must equal(200)
    response.header.headers.get("Content-Type") must equal(Some("application/json"))
    Json.parse(body) must equal(
      JsArray(
        Seq(
          Json.obj("id" -> 1)
        )
      )
    )
  }

  "GET redirect" in {
    val (response, body) = simulateResponse(Method.Get, "/redirect/example")
    response.header.status must equal(303)
    response.header.headers.get("Location") must equal(Some("http://localhost/foo"))
    response.header.headers.get("Content-Type") must equal(None)
    body must equal("")
  }

}
