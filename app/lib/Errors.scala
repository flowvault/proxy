package lib

import io.flow.error.v0.models.GenericError
import io.flow.error.v0.models.json._
import play.api.libs.json.{Json, JsValue}

trait Errors {

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

}

