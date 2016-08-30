package controllers

import play.api.test._
import play.api.test.Helpers._
import org.scalatest._
import org.scalatestplus.play._

class ServerProxySpec extends PlaySpec with OneServerPerSuite {

  "query" in {
    ServerProxy.query(Map[String, Seq[String]]()) must be(Nil)

    ServerProxy.query(
      Map[String, Seq[String]](
        "foo" -> Seq("bar"),
        "foo2" -> Seq("baz")
      )
    ) must be(
      Seq(
        ("foo", "bar"),
        ("foo2", "baz")
      )
    )

    ServerProxy.query(
      Map[String, Seq[String]](
        "foo" -> Seq("a", "b"),
        "foo2" -> Seq("c")
      )
    ) must be(
      Seq(
        ("foo", "a"),
        ("foo", "b"),
        ("foo2", "c")
      )
    )
  }

}
