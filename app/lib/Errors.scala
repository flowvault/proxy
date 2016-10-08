package lib

import io.flow.error.v0.models.GenericError
import io.flow.error.v0.models.json._
import play.api.libs.json.{Json, JsValue}
import java.util.UUID

trait Errors {

  def generateErrorId(): String = {
    "err" + UUID.randomUUID.toString.replaceAll("-", "")
  }

  def genericError(message: String): JsValue = {
    genericErrors(Seq(message))
  }

  def genericErrors(messages: Seq[String]): JsValue = {
    Json.toJson(
      GenericError(
        messages = messages
      )
    )
  }

  def clientErrors(messages: Seq[String]): JsValue = {
    Json.obj(
      "code" -> "client_error",
      "messages" -> messages
    )
  }

  def serverErrors(messages: Seq[String]): JsValue = {
    Json.obj(
      "code" -> "server_error",
      "messages" -> messages
    )
  }

}

