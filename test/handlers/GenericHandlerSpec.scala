package handlers

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Source, StreamConverters}
import akka.util.ByteString
import helpers.{BasePlaySpec, MockStandaloneServer}
import lib._
import play.api.libs.json._
import play.api.mvc.{Headers, Result}

import scala.concurrent.duration.{FiniteDuration, MILLISECONDS}

class GenericHandlerSpec extends BasePlaySpec {

  import scala.concurrent.ExecutionContext.Implicits.global

  private[this] def genericHandler = app.injector.instanceOf[GenericHandler]

  private[this] case class SimulatedResponse(
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

  }

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
    implicit val system: ActorSystem = ActorSystem("toString")
    implicit val materializer: ActorMaterializer = ActorMaterializer()
    val is = source.runWith(StreamConverters.asInputStream(FiniteDuration(100, MILLISECONDS)))
    scala.io.Source.fromInputStream(is, "UTF-8").mkString
  }

  private[this] def simulate(
    method: Method,
    path: String,
    serverName: String = "test",
    queryParameters: Map[String, Seq[String]] = Map.empty
  ): SimulatedResponse = {
    MockStandaloneServer.withTestClient { (client, port) =>
      val server = Server(
        name = serverName,
        host = s"http://localhost:$port"
      )

      val proxyRequest = createProxyRequest(
        requestMethod = method,
        requestPath = path,
        queryParameters = queryParameters
      )

      val result = await(
        genericHandler.process(
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


  "defaults content type to application/json" in {
    val sim = simulate(Method.Get, "/users/1")
    sim.status must equal(200)
    sim.contentLength must equal(Some(sim.body.length))
    sim.contentType must equal(Some("application/json; charset=utf-8"))
    sim.header(Constants.Headers.FlowServer) must equal(Some(sim.server.name))
    sim.header(Constants.Headers.FlowRequestId) must equal(Some(sim.request.requestId))
    Json.parse(sim.body) must equal(
      Json.obj("id" -> 1)
    )
  }

  "propagates redirect" in {
    val sim = simulate(Method.Get, "/redirect/example")
    sim.result.header.status must equal(303)
    sim.contentLength must equal(Some(0))
    sim.header("Location") must equal(Some("http://localhost/foo"))
    // TODO: What do we want content type to be for redirects?
    sim.contentType must equal(Some("application/json; charset=utf-8"))
    sim.body must equal("")
  }

  "respects provided content type" in {
    val sim = simulate(Method.Get, "/file.pdf")
    sim.result.header.status must equal(200)
    sim.contentType must equal(Some("application/pdf; charset=utf-8"))
  }

  "propagates 404" in {
    val sim = simulate(Method.Get, "/non-existent-path")
    sim.result.header.status must equal(404)
  }

  "supports jsonp" in {
    val sim = simulate(Method.Post, "/users")
    sim.status must equal(201)
    sim.contentLength must equal(Some(sim.body.length))
    sim.contentType must equal(Some("application/json; charset=utf-8"))
    Json.parse(sim.body) must equal(
      Json.obj("id" -> 1)
    )

    val jsonp = simulate(
      Method.Post,
      "/users",
      queryParameters = Map("callback" -> Seq("foo"))
    )
    jsonp.status must equal(200)
    jsonp.body.startsWith("/**/foo({") must equal(true)
    jsonp.contentLength must equal(Some(jsonp.body.length))
    jsonp.contentType must equal(Some("application/javascript; charset=utf-8"))
  }

  "supports response envelope" in {
    val sim = simulate(Method.Post, "/users")
    sim.status must equal(201)

    val envelope = simulate(
      Method.Post,
      "/users",
      queryParameters = Map("envelope" -> Seq("response"))
    )
    envelope.status must equal(200)
    envelope.contentLength must equal(Some(envelope.body.length))
    envelope.contentType must equal(Some("application/javascript; charset=utf-8"))
    val js = Json.parse(envelope.body)
    (js \ "status").as[JsNumber].value.intValue() must equal(201)
    (js \ "body").as[JsObject] must equal(Json.obj("id" -> 1))
    (js \ "headers").as[JsObject].value.keys.toSeq.sorted must equal(
      Seq("Date", "X-Flow-Request-Id", "X-Flow-Server")
    )
  }
}
