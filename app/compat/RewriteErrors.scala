package compat

import javax.inject.{Inject, Singleton}
import io.apibuilder.spec.v0.models.Response
import play.api.libs.json._

sealed trait RewriteHandler {
  def rewrite(incoming: JsObject): JsObject
}

object RewriteHandler {

  case object PassthroughHandler extends RewriteHandler {
    override def rewrite(incoming: JsObject): JsObject = {
      incoming
    }
  }

  case class TranslationHandler(
    response: Response,
    catalog: TranslationCatalog
  ) extends RewriteHandler {

    override def rewrite(incoming: JsObject): JsObject = {
      println(s"response.`type`: ${response.`type`}")

      Json.toJson(
        incoming.value.map { case (key, v) =>
          key -> (
            key match {
              case "errors" => {
                v match {
                  case a: JsArray => translateErrors(a.value)
                  case _ => v
                }
              }
              case _ => v
            }
            )
        }
      ).asInstanceOf[JsObject]
    }

    private[this] def translateErrors(errors: Seq[JsValue]): JsValue = {
      JsArray(
        errors.map {
          case o: JsObject => translateError(o)
          case j => j
        }
      )
    }

    private[this] def translateError(js: JsObject): JsObject = {
      (js \ "code").asOpt[String] match {
        case None => jsInternalD
        case Some(errorCode) => {
          // Inject our translated message here
          Json.toJson(
            js.value ++ Map(
              "message" -> JsString(
                catalog.translate(errorCode).getOrElse {
                  (js \ "message").asOpt[String].getOrElse(errorCode)
                }
              )
            )
          ).asInstanceOf[JsObject]
        }
      }
    }

  }

}

@Singleton
class RewriteErrors @Inject() (
  translations: Translations
) {

  def rewrite(
    response: Response,
    locale: String,
    incoming: JsValue
  ): JsValue = {
    incoming.asOpt[JsObject] match {
      case None => incoming
      case Some(o) => {
        val handlers = Seq(
          RewriteHandler.TranslationHandler(response, translations.locale(locale))
        )

        handlers.foldLeft(o) { case (js, handler) =>
          handler.rewrite(js)
        }
      }
    }
  }

}