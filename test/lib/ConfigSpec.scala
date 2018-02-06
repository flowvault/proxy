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

}
