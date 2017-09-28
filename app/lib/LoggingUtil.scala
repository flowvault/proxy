package lib

import play.api.Logger
import play.api.libs.json._

object LoggingUtil {

  val logger = JsonSafeLogger(
    JsonSafeLoggerConfig(
      blacklistFields = Set(
        "cvv", "number", "token", "email", "email_address",
        "password", "name", "first_name", "last_name", "streets", "phone", "phone_number"
      ),
      blacklistModels = Set("password_change_form"),
      whitelistModelFields = Map(
        "customer" -> Set("number"),
        "harmonized_item_form" -> Set("number"),
        "hs_code" -> Set("code"),
        "hs6" -> Set("code"),
        "hs10" -> Set("code"),
        "item_form" -> Set("number"),
        "line_item_form" -> Set("number"),
        "packaging" -> Set("number"),
        "partner_order_identifier_form" -> Set("number"),
        "organization_put_form" -> Set("name"),
        "tariff_code" -> Set("code")
      )
    )
  )

}

/**
  * @param blacklistFields Any value for a field with this name will be redacted
  * @param blacklistModels All fields for these models will be redacted
  * @param whitelistModelFields A Map from `type name` to list of fields to white
  *        list of fields to allow in the output
  */
case class JsonSafeLoggerConfig(
  blacklistFields: Set[String] = Set(),
  blacklistModels: Set[String] = Set(),
  whitelistModelFields: Map[String, Set[String]] = Map()
)

/**
  * Helpers to use a default logger configuration
  */
object JsonSafeLogger {

  val DefaultConfig = JsonSafeLoggerConfig(
    blacklistFields = Set("cvv", "password", "email", "token", "credit_card_number"),
    blacklistModels = Set("password_form")
  )

  val default = JsonSafeLogger(DefaultConfig)

}

/**
  * Configures the white lists and black lists that are used to determine
  * exactly which field values are redacted in the log output
  */
case class JsonSafeLogger(config: JsonSafeLoggerConfig) {

  /**
    * Accepts a JsValue, redacting any fields that may contain sensitive data
    * @param value The JsValue itself
    * @param typ The type represented by the JsValue if resolved from the API Builder specification
    */
  def safeJson(
    value: JsValue,
    typ: Option[String] = None
  ): JsValue = {
    if (typ.exists(config.blacklistFields) || typ.exists(isTypeBlacklisted)) {
      redact(value)

    } else {
      value match {
        case o: JsObject => JsObject(
          o.value.map {
            case (k, v) if isFieldBlacklisted(k, typ) => k -> redact(v)
            case (k, v) => {
              v match {
                case _: JsObject => {
                  // Hack to use the key value as the new type name. TODO: Lookup from spec
                  k -> safeJson(v, Some(k))
                }
                case _ => k -> safeJson(v, typ)
              }
            }
          }
        )

        case ar: JsArray => JsArray(ar.value.map { v => safeJson(v) })

        case _ => value
      }
    }
  }

  private[this] def isTypeBlacklisted(typ: String): Boolean = {
    config.blacklistModels.contains(typ.toLowerCase.trim)
  }

  private[this] def isFieldBlacklisted(field: String, typ: Option[String]): Boolean = {
    val whiteList = typ.map(_.toLowerCase.trim) match {
      case None => Set.empty[String]
      case Some(t) => config.whitelistModelFields.getOrElse(t, Set.empty[String])
    }
    config.blacklistFields.diff(whiteList).contains(field)
  }

  private[this] def redact(value: JsValue): JsValue = {
    value match {
      case JsNull => JsNull
      case v: JsBoolean => v
      case _: JsString => JsString("xxx")
      case _: JsNumber => JsNumber(123)
      case ar: JsArray => JsArray(ar.value.map(redact))
      case o: JsObject => JsObject(o.value.map { case (k, v) => k -> redact(v) })
      case _ => {
        Logger.warn(s"Do not know how to redact values for json type[${value.getClass.getName}] - Returning {}")
        Json.obj()
      }
    }
  }

}
