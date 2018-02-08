package handlers

import helpers.HandlerBasePlaySpec
import lib.{Constants, Method}
import play.api.libs.json._

class GenericHandlerSpec extends HandlerBasePlaySpec {

  import scala.concurrent.ExecutionContext.Implicits.global

  private[this] def genericHandler = app.injector.instanceOf[GenericHandler]

  "defaults content type to application/json" in {
    val sim = simulate(genericHandler, Method.Get, "/users/1")
    sim.status must equal(200)
    sim.contentLength must equal(Some(sim.body.length))
    sim.contentType must equal(Some("application/json; charset=utf-8"))
    sim.header(Constants.Headers.FlowServer) must equal(Some(sim.server.name))
    sim.header(Constants.Headers.FlowRequestId) must equal(Some(sim.request.requestId))
    sim.bodyAsJson must equal(
      Json.obj("id" -> 1)
    )
  }

  "propagates redirect" in {
    val sim = simulate(genericHandler, Method.Get, "/redirect/example")
    sim.result.header.status must equal(303)
    sim.contentLength must equal(Some(0))
    sim.header("Location") must equal(Some("http://localhost/foo"))
    sim.contentType must equal(Some("text/html; charset=utf-8"))
    sim.body must equal("")
  }

  "respects provided content type" in {
    val sim = simulate(genericHandler, Method.Get, "/file.pdf")
    sim.result.header.status must equal(200)
    sim.contentType must equal(Some("application/pdf; charset=utf-8"))
  }

  "propagates 404" in {
    val sim = simulate(genericHandler, Method.Get, "/non-existent-path")
    sim.result.header.status must equal(404)
  }

  "supports jsonp" in {
    val sim = simulate(genericHandler, Method.Post, "/users")
    sim.status must equal(201)
    sim.contentLength must equal(Some(sim.body.length))
    sim.contentType must equal(Some("application/json; charset=utf-8"))
    sim.bodyAsJson must equal(
      Json.obj("id" -> 1)
    )

    val jsonp = simulate(genericHandler, 
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
    val sim = simulate(genericHandler, Method.Post, "/users")
    sim.status must equal(201)

    val envelope = simulate(genericHandler, 
      Method.Post,
      "/users",
      queryParameters = Map("envelope" -> Seq("response"))
    )
    envelope.status must equal(200)
    envelope.contentLength must equal(Some(envelope.body.length))
    envelope.contentType must equal(Some("application/javascript; charset=utf-8"))
    val js = envelope.bodyAsJson
    (js \ "status").as[JsNumber].value.intValue() must equal(201)
    (js \ "body").as[JsObject] must equal(Json.obj("id" -> 1))
    (js \ "headers").as[JsObject].value.keys.toSeq.sorted must equal(
      Seq("Date", "X-Flow-Request-Id", "X-Flow-Server")
    )
  }
}
