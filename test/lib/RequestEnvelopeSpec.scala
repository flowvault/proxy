package lib

import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{JsArray, JsBoolean, JsNull, Json}
import play.api.mvc.Headers

class RequestEnvelopeSpec extends PlaySpec {

  "validateMethod" in {
    RequestEnvelope.validateMethod(Json.obj()) must equal(
      Left(Seq("Request envelope field 'method' is required"))
    )

    RequestEnvelope.validateMethod(Json.obj("method" -> " ")) must equal(
      Left(Seq("Request envelope field 'method' must be one of GET, POST, PUT, PATCH, DELETE, HEAD, CONNECT, OPTIONS, TRACE"))
    )

    RequestEnvelope.validateMethod(Json.obj("method" -> "foo")) must equal(
      Left(Seq("Request envelope field 'method' must be one of GET, POST, PUT, PATCH, DELETE, HEAD, CONNECT, OPTIONS, TRACE"))
    )


    RequestEnvelope.validateMethod(Json.obj("method" -> "post")) must equal(
      Right(Method.Post)
    )
    Method.all.forall { m =>
      RequestEnvelope.validateMethod(Json.obj("method" -> m.toString)).isRight
    } must be(true)
  }

  "validateHeaders" in {
    RequestEnvelope.validateHeaders(Json.obj()) must equal(Right(Headers()))
    RequestEnvelope.validateHeaders(Json.obj("headers" -> Json.obj())) must equal(
      Right(Headers())
    )
    RequestEnvelope.validateHeaders(Json.obj("headers" -> Json.obj(
      "foo" -> JsArray(Nil)
    ))) must equal(
      Right(Headers())
    )

    RequestEnvelope.validateHeaders(Json.obj(
      "headers" -> Json.obj(
        "foo" -> Seq("bar")
      )
    )) must equal(
      Right(Headers(("foo", "bar")))
    )

    RequestEnvelope.validateHeaders(Json.obj(
      "headers" -> Json.obj(
        "foo" -> Seq("bar"),
        "a" -> Seq("b"),
      )
    )) must equal(
      Right(Headers(("foo", "bar"), ("a", "b")))
    )

    RequestEnvelope.validateHeaders(Json.obj(
      "headers" -> Json.obj(
        "foo" -> Seq("bar", "baz")
      )
    )) must equal(
      Right(Headers(("foo", "bar"), ("foo", "baz")))
    )

    RequestEnvelope.validateHeaders(Json.obj(
      "headers" -> "a"
    )) must equal(
      Left(Seq("Request envelope field 'headers' must be an object of type map[string, [string]]"))
    )
  }

  "validateBody" in {
    RequestEnvelope.validateBody(JsNull) must be(Right(None))
    RequestEnvelope.validateBody(Json.obj()) must be(Right(None))
    RequestEnvelope.validateBody(Json.obj("body" -> Json.obj())) must equal(Right(Some(
      ProxyRequestBody.Json(Json.obj())
    )))
    RequestEnvelope.validateBody(Json.obj("body" -> Json.obj("a" -> "b"))) must equal(Right(Some(
      ProxyRequestBody.Json(Json.obj("a" -> "b"))
    )))
    RequestEnvelope.validateBody(Json.obj("body" -> JsBoolean(true))) must equal(Right(Some(
      ProxyRequestBody.Json(JsBoolean(true))
    )))
  }
}
