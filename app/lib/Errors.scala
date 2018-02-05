package lib

import play.api.libs.json.{Json, JsValue}
import java.util.UUID

trait Errors {

  def generateErrorId(): String = {
    "proxy" + UUID.randomUUID.toString.replaceAll("-", "")
  }

  def genericError(message: String): JsValue = {
    genericErrors(Seq(message))
  }

  def genericErrors(messages: Seq[String]): JsValue = {
    jsonErrors("generic_error", messages)
  }

  def clientErrors(messages: Seq[String]): JsValue = {
    jsonErrors("client_error", messages)
  }

  def serverErrors(messages: Seq[String]): JsValue = {
    jsonErrors("server_error", messages)
  }

  private[this] def jsonErrors(code: String, messages: Seq[String]): JsValue = {
    Json.obj(
      "code" -> code,
      "messages" -> messages
    )
  }
}

