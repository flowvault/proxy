package lib

import play.api.libs.json._

/**
  * Convert query parameters, url form encoded parameters to a json object
  */
object FormData {

  private[this] val KnownStrings = Seq("number", "cvv")

  def parseEncoded(value: String): Map[String, Seq[String]] = {
    val data = scala.collection.mutable.Map[String, Seq[String]]()
    value.split("&").foreach { x =>
      x.split("=").toList match {
        case key :: value :: Nil => {
          val values = data.get(key) match {
            case None => Seq(value)
            case Some(existing) => existing ++ Seq(value)
          }
          data += (key -> values)
        }
        case _ => {
          // Ignore
        }
      }
    }
    data.toMap
  }

  def toJson(data: Map[String, Seq[String]]): JsValue = {
    val nested = data.map{ case (key, value) =>
      key.split("\\[").foldRight(
        if(key.contains("[]"))
          Json.toJson(value)  //take seq for arrays
        else
          Json.toJson(value.headOption.getOrElse(""))
      ){ case (newKey, v) =>
        val newVal = {
          val js = (v \ "").getOrElse(v)

          //convert '{key: val}' to '[{key: val}]' if previous key specifies array type, otherwise nothing
          if(newKey == "]"){
            if(!js.toString.startsWith("[")) {
              val s = (v \ "").getOrElse(v).toString.
                replaceFirst("\\{", "[{").
                reverse.
                replaceFirst("\\}", "]}").
                reverse

              Json.toJson(Json.parse(s))
            }
            else
              js
          }
          else {
            KnownStrings.contains(newKey) match {
              case true => js
              case false => {
                safeConvertToLong(js.toString.replace("\"", "")) match {
                  case Some(num) => JsNumber(num)
                  case _ => js
                }
              }
            }
          }
        }

        Json.obj(newKey.replace("]", "") -> newVal)
      }
    }

    Json.toJson(nested.foldLeft(Json.obj()){ case (a, b) => a.deepMerge(b.as[JsObject]) })
  }

  def safeConvertToLong(s: String): Option[Long] = {
    try {
      Some(s.toLong)
    } catch {
      case e: Exception => None
    }
  }
}
