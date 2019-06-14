package lib

import helpers.BasePlaySpec

class ConfigSpec extends BasePlaySpec {

  private[this] lazy val config = app.injector.instanceOf[Config]

  "isVerboseLogEnabled" in {
    config.isVerboseLogEnabled("/foo") must be(true)
    config.isVerboseLogEnabled("/foo/a/b/c") must be(true)
    config.isVerboseLogEnabled("/bar") must be(true)
    config.isVerboseLogEnabled("/bar/") must be(true)
    config.isVerboseLogEnabled("/") must be(false)
    config.isVerboseLogEnabled("/other") must be(false)
  }

  "toList" in {
    config.toList("") must be(Nil)
    config.toList("  ") must be(Nil)
    config.toList(" , ") must be(Nil)
    config.toList("/foo,/a") must be(Seq("/foo", "/a"))
    config.toList(" /foo , /a ") must be(Seq("/foo", "/a"))
    config.toList(" : ") must be(Nil)
    config.toList("/foo:/a") must be(Seq("/foo", "/a"))
    config.toList(" /foo : /a ") must be(Seq("/foo", "/a"))
  }

  "toList preserves protocol" in {
    // make sure https:// is preserved
    config.toList(
      "https://s3.amazonaws.com/io.flow.aws-s3-public/util/api-proxy/latest/development.config.yml:https://s3.amazonaws.com/io.flow.aws-s3-public/util/api-internal-proxy/latest/development.config.yml"
    ) must be(
      Seq(
        "https://s3.amazonaws.com/io.flow.aws-s3-public/util/api-proxy/latest/development.config.yml",
        "https://s3.amazonaws.com/io.flow.aws-s3-public/util/api-internal-proxy/latest/development.config.yml"
      )
    )
  }

}
