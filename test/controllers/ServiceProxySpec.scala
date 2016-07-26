package controllers

import play.api.test._
import play.api.test.Helpers._
import org.scalatest._
import org.scalatestplus.play._

class ServiceProxySpec extends PlaySpec with OneServerPerSuite {

  "query" in {
    ServiceProxy.query(Map[String, Seq[String]]()) must be(Nil)

    ServiceProxy.query(
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

    ServiceProxy.query(
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
