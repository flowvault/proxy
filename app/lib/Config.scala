package lib

import javax.inject.{Inject, Singleton}
import play.api.Configuration

@Singleton
class Config @Inject() (
  configuration: Configuration
) {

  private[this] object Names {
    val JwtSalt = "jwt.salt"
    val All = Seq(JwtSalt)
  }

  lazy val jwtSalt = requiredString(Names.JwtSalt)

  def requiredString(name: String): String = {
    configuration.getString(name).getOrElse {
      sys.error(s"Missing configuration parameter[$name]")
    }
  }

  def missing(): Seq[String] = {
    Names.All.filter { configuration.getString(_).isEmpty }
  }

}
