package lib

import play.api.Logger
import play.api.libs.json._

import scala.annotation.tailrec

object LoggingUtil {

  // All fields in this array will have their values redacted
  private[this] val GlobalFieldsToReplace = Set("cvv", "number", "token", "email", "password")

  // Map from apidoc model type to list of fields to whitelist
  private[this] val WhiteListByType = Map(
    "item_form" -> Set("number"),
    "harmonized_item_form" -> Set("number"),
    "order_form" -> Set("number"),
    "order_put_form" -> Set("number")
  )

  // Map from apidoc model type to list of fields to whitelist
  private[this] val BlacklistedModels = Set(
    "password_change_form"
  )

  /**
    * Accepts a JsValue, redacting any fields that may contain sensitive data
    * @param body The JsValue itself
    * @param typ The type represented by the JsValue if resolved from the apidoc specification
    */
  def safeJson(
    body: JsValue,
    typ: Option[String] = None
  ): JsValue = {
    val isModelBlacklisted = typ.map(BlacklistedModels.contains).getOrElse(false)
    val allFieldsToReplace = typ.flatMap(WhiteListByType.get) match {
      case None => GlobalFieldsToReplace
      case Some(whitelist) => GlobalFieldsToReplace.diff(whitelist)
    }

    body match {
      case o: JsObject => JsObject(
        o.value.map { case (k, v) =>
          if (isModelBlacklisted || allFieldsToReplace.contains(k.toLowerCase.trim)) {
            val redactedValue = v match {
              case JsNull => JsNull
              case _: JsBoolean => JsBoolean(false)
              case _: JsString => JsString("x" * stringLength(v))
              case _: JsNumber => JsNumber(123)
              case _: JsArray => JsArray(Nil)
              case _: JsObject => Json.obj()
              case other => {
                Logger.warn(s"Do not know how to redact values for json type[${v.getClass.getName}] - Returning empty json object")
                Json.obj()
              }
            }
            k -> redactedValue
          } else {
            k -> safeJson(v)
          }
        }
      )

      case a: JsArray => JsArray(
        a.value.map { v => safeJson(v) }
      )

      case _ => body
    }
  }

  private[this] def stringLength(js: JsValue): Int = {
    js match {
      case JsNull => 0
      case v: JsString => v.value.length
      case v: JsNumber => v.value.toString().length
      case _ => js.toString().length
    }
  }
}
