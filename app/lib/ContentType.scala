package lib

import scala.annotation.tailrec

sealed trait ContentType extends Product with Serializable {
  val toStringWithEncoding: String = s"$toString; charset=utf-8"
}

object ContentType {

  case object ApplicationJavascript extends ContentType { override def toString = "application/javascript" }
  case object ApplicationJson extends ContentType { override def toString = "application/json" }
  case object TextHtml extends ContentType { override def toString = "text/html" }
  case object UrlFormEncoded extends ContentType { override def toString = "application/x-www-form-urlencoded" }
  case class Other(name: String) extends ContentType {
    override def toString: String = name

    override val toStringWithEncoding: String = {
      val index = name.indexOf(";")
      if (index < 0) {
        s"$toString; charset=utf-8"
      } else {
        // don't append another ;
        name
      }
    }
  }

  val all = Seq(ApplicationJavascript, ApplicationJson, UrlFormEncoded)

  private[this]
  val byName = all.map(x => x.toString.toLowerCase -> x).toMap

  def apply(value: String): ContentType = {
    fromString(value).getOrElse {
      value.trim.toLowerCase match {
        case "none/none" | "application/octet-stream" => ContentType.ApplicationJson
        case _ => ContentType.Other(value)
      }
    }
  }

  @tailrec
  def fromString(value: String): Option[ContentType] = {
    byName.get(value.toLowerCase.trim) match {
      case Some(ct) => Some(ct)
      case None => {
        // check for charset
        val index = value.indexOf(";")
        if (index > 0 ) {
          fromString(value.substring(0, index))
        } else {
          None
        }
      }
    }
  }
}