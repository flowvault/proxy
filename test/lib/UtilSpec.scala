package lib

import helpers.BasePlaySpec
import play.api.mvc.Headers

class UtilSpec extends BasePlaySpec {

  "toFlatSeq" in {
    Util.toFlatSeq(Map[String, Seq[String]]()) must be(Nil)

    val parts = Util.toFlatSeq(
      Map[String, Seq[String]](
        "foo" -> Seq("bar"),
        "foo2" -> Seq("baz")
      )
    )
    parts.size must be(2)
    parts.contains(("foo", "bar")) must be(true)
    parts.contains(("foo2", "baz")) must be(true)
  }

  "removeKey" in {
    val parts = Util.removeKey(
      Map[String, Seq[String]](
        "foo" -> Seq("bar"),
        "foo2" -> Seq("baz")
      ),
      "foo2"
    )
    parts.keys.toSeq must equal(Seq("foo"))
  }

  "removeHeaders" in {
    val parts = Util.removeHeaders(
      Headers(
        "foo" -> "bar",
        "foo2" -> "baz",
      ),
      Set("a", "Foo2")
    )
    parts.keys.toSeq must equal(Seq("foo"))
  }

  "filterHeaders" in {
    val parts = Util.filterHeaders(
      Headers(
        "foo" -> "bar",
        "foo2" -> "baz",
      ),
      Set("a", "Foo2")
    )
    parts.keys.toSeq must equal(Seq("Foo2"))
  }

  "query with multiple values" in {
    val parts = Util.toFlatSeq(
      Map[String, Seq[String]](
        "foo" -> Seq("a", "b"),
        "foo2" -> Seq("c")
      )
    )
    parts.contains(("foo", "a")) must be(true)
    parts.contains(("foo", "b")) must be(true)
    parts.contains(("foo2", "c")) must be(true)
    parts.size must be(3)
  }

}
