package lib

import org.scalatestplus.play._

class ConfigSpec extends PlaySpec with OneServerPerSuite {

  private[this] lazy val config = play.api.Play.current.injector.instanceOf[Config]

  "isVerboseLogEnabled" in {
    config.isVerboseLogEnabled("/foo") must be(true)
    config.isVerboseLogEnabled("/foo/a/b/c") must be(true)
    config.isVerboseLogEnabled("/bar") must be(true)
    config.isVerboseLogEnabled("/bar/") must be(true)
    config.isVerboseLogEnabled("/") must be(false)
    config.isVerboseLogEnabled("/other") must be(false)
  }

}
