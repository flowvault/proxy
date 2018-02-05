package lib

import scala.annotation.tailrec

sealed trait ContentType extends Product with Serializable

object ContentType {

  case object ApplicationJson extends ContentType { override def toString = "application/json" }
  case object UrlFormEncoded extends ContentType { override def toString = "application/x-www-form-urlencoded" }
  case class Other(name: String) extends ContentType { override def toString: String = name }

  val all = Seq(ApplicationJson, UrlFormEncoded)

  private[this]
  val byName = all.map(x => x.toString.toLowerCase -> x).toMap

  def apply(value: String): ContentType = fromString(value).getOrElse(Other(value))

  @tailrec
  def fromString(value: String): Option[ContentType] = {
    byName.get(value.toLowerCase) match {
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