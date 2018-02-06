package lib

import helpers.BasePlaySpec
import play.api.libs.json._

class LoggingUtilSpec extends BasePlaySpec {

  private[this] val logger = JsonSafeLogger(
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

  "safeJson when type is not known respects blacklistFields" in {
    logger.safeJson(JsNull) must equal(JsNull)
    logger.safeJson(JsString("a")) must equal(JsString("a"))

    logger.safeJson(Json.obj("foo" -> "bar")) must equal(Json.obj("foo" -> "bar"))

    logger.safeJson(Json.obj("foo" -> "bar", "cvv" -> "123", "number" -> "1234567890")) must equal(
      Json.obj("foo" -> "bar", "cvv" -> "xxx", "number" -> "xxx")
    )

    logger.safeJson(
      JsArray(
        Seq(
          Json.obj("foo" -> "bar"),
          Json.obj("cvv" -> "123"),
          Json.obj("number" -> "1234567890")
        )
      )
    ) must equal(
      JsArray(
        Seq(
          Json.obj("foo" -> "bar"),
          Json.obj("cvv" -> "xxx"),
          Json.obj("number" -> "xxx")
        )
      )

    )
  }

  "safeJson with nested types" in {
    logger.safeJson(
      Json.obj(
        "items" -> Json.obj("number" -> "1234567890")
      )
    ) must equal(
      Json.obj(
        "items" -> Json.obj("number" -> "xxx")
      )
    )
  }

  "safeJson with type whitelist" in {
    logger.safeJson(Json.obj("number" -> "1234567890"), typ = Some("order_form")) must equal(
      Json.obj("number" -> "1234567890")
    )

    logger.safeJson(
      Json.obj(
        "order_form" -> Json.obj("number" -> "1234567890")
      )
    ) must equal(
      Json.obj(
        "order_form" -> Json.obj("number" -> "1234567890")
      )
    )
  }

  "safeJson with blacklisted model" in {
    logger.safeJson(
      Json.obj(
        "current" -> "foo",
        "new" -> "bar"
      ),
      Some("password_change_form")
    ) must equal(
      Json.obj(
        "current" -> "xxx",
        "new" -> "xxx"
      )
    )
  }

  "safeJson with nested blacklisted model" in {
    logger.safeJson(
      Json.obj(
        "password_change_form" -> Json.obj(
          "current" -> "foo",
          "new" -> "bar"
        )
      )
    ) must equal(
      Json.obj(
        "password_change_form" -> Json.obj(
          "current" -> "xxx",
          "new" -> "xxx"
        )
      )
    )
  }

}
