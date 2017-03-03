package lib

import akka.util.ByteString
import org.scalatestplus.play._
import play.api.mvc.Headers

class ProxyRequestSpec extends PlaySpec with OneServerPerSuite {

  def rightOrErrors[T](result: Either[Seq[String], T]): T = {
    result match {
      case Left(errors) => sys.error("Unexpected error: " + errors.mkString(", "))
      case Right(obj) => obj
    }
  }

  private[this] val testBody = ProxyRequestBody.Bytes(ByteString("test".getBytes()))

  "isValidCallback" in {
    ProxyRequest.isValidCallback("f") must be(true)
    ProxyRequest.isValidCallback("f2") must be(true)
    ProxyRequest.isValidCallback("My.function_name") must be(true)
    ProxyRequest.isValidCallback("!") must be(false)
  }

  "validate w/ default values succeeds" in {
    val query = Map(
      "q" -> Seq("baz"),
      "callback" -> Seq("jj")
    )

    val request = rightOrErrors(
      ProxyRequest.validate(
        requestMethod = "get",
        requestPath = "/users/?foo=1&foo=2",
        body = testBody,
        queryParameters = query,
        headers = Headers(Seq(
          ("foo", "1"),
          ("foo", "2")
        ): _*)
      )
    )
    request.headers.getAll("foo") must be(Seq("1", "2"))
    request.headers.getAll("foo2") must be(Nil)
    request.originalMethod must be("get")
    request.method must be("GET")
    request.pathWithQuery must be("/users/?foo=1&foo=2")
    request.path must be("/users/")
    request.bodyUtf8 must be(Some("test"))
    request.jsonpCallback must be(Some("jj"))
    request.responseEnvelope must be(true)
    request.envelopes must be(Nil)
    request.queryParameters must be(query - "callback")
    request.toString must be("GET /users/?foo=1&foo=2")
    request.envelopes.contains(Envelope.Request) must be(false)
    request.envelopes.contains(Envelope.Response) must be(false)
  }

  "validate method" must {

    "accept valid" in {
      rightOrErrors(
        ProxyRequest.validate(
          requestMethod = "GET",
          requestPath = "/users/",
          body = testBody,
          queryParameters = Map("method" -> Seq("post")),
          headers = Headers()
        )
      ).method must be("POST")
    }

    "reject invalid" in {
      ProxyRequest.validate(
        requestMethod = "GET",
        requestPath = "/users/",
        body = testBody,
        queryParameters = Map("method" -> Seq("foo")),
        headers = Headers()
      ) must be(Left(
        Seq("Invalid value 'foo' for query parameter 'method' - must be one of GET, POST, PUT, PATCH, DELETE")
      ))
    }

    "reject multiple" in {
      ProxyRequest.validate(
        requestMethod = "GET",
        requestPath = "/users/",
        body = testBody,
        queryParameters = Map("method" -> Seq("foo", "bar")),
        headers = Headers()
      ) must be(Left(
        Seq("Query parameter 'method', if specified, cannot be specified more than once")
      ))
    }
  }

  "validate callback" must {

    "accept valid" in {
      val request = rightOrErrors(
        ProxyRequest.validate(
          requestMethod = "GET",
          requestPath = "/users/",
          body = testBody,
          queryParameters = Map("callback" -> Seq("my_json.Callback1")),
          headers = Headers()
        )
      )

      request.jsonpCallback must be(Some("my_json.Callback1"))
      request.responseEnvelope must be(true)
    }

    "reject invalid" in {
      ProxyRequest.validate(
        requestMethod = "GET",
        requestPath = "/users/",
        body = testBody,
        queryParameters = Map("callback" -> Seq("!!!")),
        headers = Headers()
      ) must be(Left(
        Seq("Callback query parameter, if specified, must contain only alphanumerics, '_' and '.' characters")
      ))
    }

    "reject empty" in {
      ProxyRequest.validate(
        requestMethod = "GET",
        requestPath = "/users/",
        body = testBody,
        queryParameters = Map("callback" -> Seq("   ")),
        headers = Headers()
      ) must be(Left(
        Seq("Callback query parameter, if specified, must be non empty")
      ))
    }

    "reject multiple" in {
      ProxyRequest.validate(
        requestMethod = "GET",
        requestPath = "/users/",
        body = testBody,
        queryParameters = Map("callback" -> Seq("foo", "bar")),
        headers = Headers()
      ) must be(Left(
        Seq("Query parameter 'callback', if specified, cannot be specified more than once")
      ))
    }
  }

  "validate envelope" must {

    "accept valid" in {
      val request = rightOrErrors(
        ProxyRequest.validate(
          requestMethod = "GET",
          requestPath = "/users/",
          body = testBody,
          queryParameters = Map("envelope" -> Seq("request", "response")),
          headers = Headers()
        )
      )

      request.envelopes.contains(Envelope.Request) must be(true)
      request.envelopes.contains(Envelope.Response) must be(true)
      request.responseEnvelope must be(true)
    }

    "reject invalid" in {
      ProxyRequest.validate(
        requestMethod = "GET",
        requestPath = "/users/",
        body = testBody,
        queryParameters = Map("envelope" -> Seq("foo")),
        headers = Headers()
      ) must be(Left(
        Seq("Invalid value 'foo' for query parameter 'envelope' - must be one of request, response")
      ))

      ProxyRequest.validate(
        requestMethod = "GET",
        requestPath = "/users/",
        body = testBody,
        queryParameters = Map("envelope" -> Seq("foo", "bar", "request", "response")),
        headers = Headers()
      ) must be(Left(
        Seq("Invalid values 'foo', 'bar' for query parameter 'envelope' - must be one of request, response")
      ))
    }

    "merges duplicates" in {
      val request = rightOrErrors(
        ProxyRequest.validate(
          requestMethod = "GET",
          requestPath = "/users/",
          body = testBody,
          queryParameters = Map("envelope" -> Seq("response", "response")),
          headers = Headers()
        )
      )
      request.envelopes must be(Seq(Envelope.Response))
    }
  }

}
