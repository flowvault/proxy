package lib

import akka.util.ByteString
import helpers.BasePlaySpec
import play.api.mvc.Headers

class ProxyRequestSpec extends BasePlaySpec {

  private[this] val testBody = Some(ProxyRequestBody.Bytes(ByteString("test".getBytes())))

  "parses ContentType" in {
    ContentType.fromString("application/json") must be(Some(ContentType.ApplicationJson))
    ContentType.fromString("application/json; charset=UTF-8") must be(Some(ContentType.ApplicationJson))
    ContentType.fromString("application/JSON;charset=utf-8") must be(Some(ContentType.ApplicationJson))

    ContentType.fromString("foo") must be(None)
    ContentType.fromString(";") must be(None)
    ContentType.fromString("foo;") must be(None)
  }

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
    request.method must be(Method.Get)
    request.pathWithQuery must be("/users/?foo=1&foo=2")
    request.path must be("/users/")
    request.bodyUtf8 must be(Some("test"))
    request.jsonpCallback must be(Some("jj"))
    request.responseEnvelope must be(true)
    request.envelopes must be(Nil)
    request.queryParameters must be(query - "callback")
    request.toString must be(s"id:${request.requestId} GET /users/?foo=1&foo=2")
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
      ).method must be(Method.Post)
    }

    "reject invalid" in {
      ProxyRequest.validate(
        requestMethod = "GET",
        requestPath = "/users/",
        body = testBody,
        queryParameters = Map("method" -> Seq("foo")),
        headers = Headers()
      ) must be(Left(
        Seq(
          "Invalid value 'foo' for query parameter 'method' - must be one of GET, POST, PUT, PATCH, DELETE, HEAD, CONNECT, OPTIONS, TRACE"
        )
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
