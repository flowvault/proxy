package handlers

import java.util.UUID

import controllers.ServerProxyDefinition
import helpers.BasePlaySpec
import lib._
import play.api.mvc.Headers

class GenericHandlerSpec extends BasePlaySpec {

  import scala.concurrent.ExecutionContext.Implicits.global

  private[this] val genericHandler = app.injector.instanceOf[GenericHandler]

  val server = Server(
    name = "test",
    host = "http://localhost:7899"
  )

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

  "GET request" in {
    val result = await(
      genericHandler.process(
        definition = ServerProxyDefinition(server),
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
