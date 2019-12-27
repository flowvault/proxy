package lib

import helpers.BasePlaySpec

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

  "removeKeys" in {
    val parts = Util.removeKeys(
      Map[String, Seq[String]](
        "foo" -> Seq("bar"),
        "foo2" -> Seq("baz")
      ),
      Set("a", "foo2")
    )
    parts.keys.toSeq must equal(Seq("foo"))
  }

  "filterKeys" in {
    val parts = Util.filterKeys(
      Map[String, Seq[String]](
        "foo" -> Seq("bar"),
        "foo2" -> Seq("baz")
      ),
      Set("a", "foo2")
    )
    parts.keys.toSeq must equal(Seq("foo2"))
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
