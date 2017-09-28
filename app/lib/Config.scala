package lib

import collection.JavaConverters._
import javax.inject.{Inject, Singleton}
import play.api.Configuration

@Singleton
class Config @Inject() (
  configuration: Configuration
) {

  private[this] object Names {
    val JwtSalt = "jwt.salt"
    val VerboseLogPrefixes = "integration.path.prefixes"
    val All = Seq(JwtSalt)
  }

  lazy val jwtSalt = requiredString(Names.JwtSalt)
  private[this] lazy val VerboseLogPrefixes: Seq[String] = requiredString(Names.VerboseLogPrefixes).
    split(",").
    map(_.trim).
    filterNot(_.isEmpty)

  def requiredString(name: String): String = {
    configuration.getString(name).getOrElse {
      sys.error(s"Missing configuration parameter[$name]")
    }
  }

  def missing(): Seq[String] = {
    Names.All.filter { configuration.getString(_).isEmpty }
  }

  def isVerboseLogEnabled(path: String): Boolean = {
    VerboseLogPrefixes.exists { p =>
      path.startsWith(p)
    }
  }

}
