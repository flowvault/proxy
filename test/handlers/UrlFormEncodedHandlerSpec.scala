package handlers

import helpers.HandlerBasePlaySpec
import lib.{ContentType, Method}
import play.api.libs.json.Json

class UrlFormEncodedHandlerSpec extends HandlerBasePlaySpec {

  import scala.concurrent.ExecutionContext.Implicits.global

  private[this] def urlFormEncodedHandler = app.injector.instanceOf[UrlFormEncodedHandler]

  "converts url form encoded to application/json" in {
    val response = simulate(
      urlFormEncodedHandler,
      Method.Post,
      "/users",
      body = Some("email=joe@test.flow.io")
    )
    response.status must equal(201)
    response.contentType must equal(Some("application/json; charset=utf-8"))
    response.bodyAsJson must equal(
      Json.obj("id" -> 1)
    )
  }

  "validates form" in {
    val response = simulate(
      urlFormEncodedHandler,
      Method.Post,
      "/users",
      body = Some("name=joe")
    )
    (response.bodyAsJson \ "messages").as[Seq[String]] must equal(
      Seq("user_form.name must be an object and not a string")
    )
    response.status must equal(422)

    simulate(urlFormEncodedHandler,
      Method.Post,
      "/users",
      body = Some("name[first]=joe&name[last]=Smith")
    ).status must equal(201)
  }

  "validates the content type matches the body" in {
    val response = simulate(
      urlFormEncodedHandler,
      Method.Post,
      "/users",
      body = Some(
        """
          |{ "email": "joe@test.flow.io" }
        """.stripMargin
      )
    )
    response.status must equal(422)
    (response.bodyAsJson \ "messages").as[Seq[String]] must equal(
      Seq(
        s"The content type you specified '${ContentType.UrlFormEncoded.toString}' does not match the body. " +
        "Please specify 'Content-type: application/json' when providing a JSON Body."
      )
    )
  }
}
