package handlers

import helpers.{BasePlaySpec, MockStandaloneServer}
import lib._
import play.api.mvc.Headers

class GenericHandlerSpec extends BasePlaySpec {

  import scala.concurrent.ExecutionContext.Implicits.global

  private[this] val genericHandler = app.injector.instanceOf[GenericHandler]
  private[this] val mockStandaloneServer = app.injector.instanceOf[MockStandaloneServer]

  def createProxyRequest(
    requestMethod: String,
    requestPath: String,
    body: Option[ProxyRequestBody] = None,
    queryParameters: Map[String, Seq[String]] = Map.empty,
    headers: Map[String, Seq[String]] = Map.empty
  ): ProxyRequest = {
    rightOrErrors(
      ProxyRequest.validate(
        requestMethod = requestMethod,
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
/*
  "GET request" in {
    mockStandaloneServer.withServer { (server, client) =>
      println(s"server: $server")
      val url = s"${server.host}/users/"
      println(s"URL - $url")
      val u = await(client.url(url).get()).body
      println(s"USER: $u")

      val result = await(
        genericHandler.process(
          server = server,
          request = createProxyRequest(
            requestMethod = "GET",
            requestPath = "/users/"
          ),
          route = Route("GET", "/users/"),
          token = ResolvedToken(requestId = createTestId())
        )
      )
    }
  }
  */

}
