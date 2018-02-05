package lib

sealed trait Method extends Product with Serializable

object Method {

  case object Get extends Method { override def toString = "GET" }
  case object Post extends Method { override def toString = "POST" }
  case object Put extends Method { override def toString = "PUT" }
  case object Patch extends Method { override def toString = "PATCH" }
  case object Delete extends Method { override def toString = "DELETE" }
  case object Head extends Method { override def toString = "HEAD" }
  case object Connect extends Method { override def toString = "CONNECT" }
  case object Options extends Method { override def toString = "OPTIONS" }
  case object Trace extends Method { override def toString = "TRACE" }

  case class UNDEFINED(override val toString: String) extends Method

  val all: scala.List[Method] = scala.List(Get, Post, Put, Patch, Delete, Head, Connect, Options, Trace)

  private[this]
  val byName: Map[String, Method] = all.map(x => x.toString.toLowerCase -> x).toMap

  def apply(value: String): Method = fromString(value).getOrElse(UNDEFINED(value))

  def fromString(value: String): _root_.scala.Option[Method] = byName.get(value.toLowerCase)

}