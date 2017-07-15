package lib

import play.api.Logger
import play.api.libs.json._

object LoggingUtil {

  val logger = JsonSafeLogger(
    JsonSafeLoggerConfig(
      blacklistFields = Set("cvv", "number", "token", "email", "password"),
      blacklistModels = Set("password_change_form"),
      whitelistModelFields = Map(
        "item_form" -> Set("number"),
        "harmonized_item_form" -> Set("number"),
        "order_form" -> Set("number"),
        "order_put_form" -> Set("number")
      )
    )
  )

}

/**
  * @param blacklistFields Any value for a field with this name will be redacted
  * @param whitelistFields Any value for a field with this name will be redacted
  * @param whitelistModelFields A Map from `type name` to list of fields to white
  *        list of fields to allow in the output
  */
case class JsonSafeLoggerConfig(
  blacklistFields: Set[String] = Set(),
  blacklistModels: Set[String] = Set(),
  whitelistModelFields: Map[String, Set[String]] = Map()
)

object JsonSafeLogger {

  val DefaultConfig = JsonSafeLoggerConfig(
    blacklistFields = Set("cvv", "password", "email", "token", "credit_card_number"),
    blacklistModels = Set("password_form")
  )

  val default = JsonSafeLogger(DefaultConfig)

}

/**
  * 
  */
case class JsonSafeLogger(config: JsonSafeLoggerConfig) {

  /**
    * Accepts a JsValue, redacting any fields that may contain sensitive data
    * @param body The JsValue itself
    * @param typ The type represented by the JsValue if resolved from the apidoc specification
    */
  def safeJson(
    body: JsValue,
    typ: Option[String] = None
  ): JsValue = {
    val isModelBlacklisted = typ.map(config.blacklistModels.contains).getOrElse(false)
    val allFieldsToReplace = typ.flatMap(config.whitelistModelFields.get) match {
      case None => config.blacklistFields
      case Some(whitelist) => config.blacklistFields.diff(whitelist)
    }

    body match {
      case o: JsObject => JsObject(
        o.value.map { case (k, v) =>
          if (isModelBlacklisted || allFieldsToReplace.contains(k.toLowerCase.trim)) {
            val redactedValue = v match {
              case JsNull => JsNull
              case _: JsBoolean => JsBoolean(false)
              case _: JsString => JsString("xxx")
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

}
