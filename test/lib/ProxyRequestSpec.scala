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

  "validate w/ default values succeeds" in {
    val query = Map(
      "q" -> Seq("baz"),
      "callback" -> Seq("jj")
    )

    val request = rightOrErrors(
      ProxyRequest.validate(
        requestMethod = "get",
        requestPath = "/users/",
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
    request.path must be("/users/")
    request.bodyUtf8 must be(Some("test"))
    request.jsonpCallback must be(Some("jj"))
    request.responseEnvelope must be(true)
    request.envelopes must be(Nil)
    request.queryParameters must be(query - "callback")
    request.toString must be("GET /users/")
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
        Seq("Invalid value for query parameter 'method' - must be one of POST, PUT, PATCH, DELETE")
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
      rightOrErrors(
        ProxyRequest.validate(
          requestMethod = "GET",
          requestPath = "/users/",
          body = testBody,
          queryParameters = Map("callback" -> Seq("my_json.Callback")),
          headers = Headers()
        )
      ).jsonpCallback must be(Some("my_json.Callback"))
    }

    "reject invalid" in {
      ProxyRequest.validate(
        requestMethod = "GET",
        requestPath = "/users/",
        body = testBody,
        queryParameters = Map("callback" -> Seq("!!!")),
        headers = Headers()
      ) must be(Left(
        Seq("Callback parameter, if specified, must contain only alphanumerics, '_' and '.' characters")
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

}
