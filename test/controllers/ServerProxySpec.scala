package controllers

import play.api.test._
import play.api.test.Helpers._
import org.scalatest._
import org.scalatestplus.play._

class ServerProxySpec extends PlaySpec with OneServerPerSuite {
/*
  "query" in {
    ServerProxy.query(Map[String, Seq[String]]()) must be(Nil)

    val parts = ServerProxy.query(
      Map[String, Seq[String]](
        "foo" -> Seq("bar"),
        "foo2" -> Seq("baz")
      )
    )
    parts.size must be(2)
    parts.contains(("foo", "bar")) must be(true)
    parts.contains(("foo2", "baz")) must be(true)
  }
 */
  "query with multivalues" in {
    val parts = ServerProxy.query(
      Map[String, Seq[String]](
        "foo" -> Seq("a", "b"),
        "foo2" -> Seq("c")
      )
    )
    println(parts)
    parts.size must be(3)
    parts.contains(("foo", "a")) must be(true)
    parts.contains(("foo", "b")) must be(true)
    parts.contains(("foo2", "c")) must be(true)
  }

}
