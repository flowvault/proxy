package lib

import helpers.BasePlaySpec
import play.api.libs.json._

class LoggingUtilSpec extends BasePlaySpec {

  private[this] val jsonSafeLogger = JsonSafeLogger(
    rollbar = logger,
    config = JsonSafeLoggerConfig(
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
    jsonSafeLogger.safeJson(JsNull) must equal(JsNull)
    jsonSafeLogger.safeJson(JsString("a")) must equal(JsString("a"))

    jsonSafeLogger.safeJson(Json.obj("foo" -> "bar")) must equal(Json.obj("foo" -> "bar"))

    jsonSafeLogger.safeJson(Json.obj("foo" -> "bar", "cvv" -> "123", "number" -> "1234567890")) must equal(
      Json.obj("foo" -> "bar", "cvv" -> "xxx", "number" -> "xxx")
    )

    Seq("cvv", "CVV", "Cvv") foreach { cvv =>
      jsonSafeLogger.safeJson(
        JsArray(
          Seq(
            Json.obj("foo" -> "bar"),
            Json.obj("cvv" -> cvv),
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
  }

  "safeJson with nested types" in {
    jsonSafeLogger.safeJson(
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
    jsonSafeLogger.safeJson(Json.obj("number" -> "1234567890"), typ = Some("order_form")) must equal(
      Json.obj("number" -> "1234567890")
    )

    jsonSafeLogger.safeJson(
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
    Seq(
      "password_change_form",
      "Password_CHANGE_forM",
      ".password_change_form",
      "foo.password_change_form",
      "io.flow.user.v0.models.password_change_form",
      "iO.flOw.User.V0.mOdels.PasSword_CHANGE_forM"
    ) foreach { typ =>
      jsonSafeLogger.safeJson(
        Json.obj(
          "current" -> "foo",
          "new" -> "bar"
        ),
        Some(typ)
      ) must equal(
        Json.obj(
          "current" -> "xxx",
          "new" -> "xxx"
        )
      )
    }
  }

  "safeJson with nested blacklisted model" in {
    jsonSafeLogger.safeJson(
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
