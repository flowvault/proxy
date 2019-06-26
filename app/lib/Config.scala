package lib

import javax.inject.{Inject, Singleton}
import play.api.Configuration

import scala.annotation.tailrec

@Singleton
class Config @Inject() (
  configuration: Configuration
) {

  private[this] object Names {
    val JwtSalt = "jwt.salt"
    val VerboseLogPrefixes = "integration.path.prefixes"
    val Required: Seq[String] = Seq(JwtSalt)
  }

  lazy val jwtSalt: String = requiredString(Names.JwtSalt)

  private[this] lazy val VerboseLogPrefixes: Seq[String] = optionalString(Names.VerboseLogPrefixes).getOrElse("").
    split(",").
    map(_.trim).
    filterNot(_.isEmpty)

  def requiredString(name: String): String = {
    optionalString(name).getOrElse {
      sys.error(s"Missing configuration parameter named[$name]")
    }
  }

  def optionalString(name: String): Option[String] = {
    val trimmed = name.trim
    assert(
      trimmed.nonEmpty,
      "Configuration parameter name cannot be empty"
    )
    configuration.getOptional[String](trimmed).map(_.trim).filter(_.nonEmpty)
  }

  def missing(): Seq[String] = {
    Names.Required.filter { optionalString(_).isEmpty }
  }

  def isVerboseLogEnabled(path: String): Boolean = {
    VerboseLogPrefixes.exists { p =>
      path.startsWith(p)
    }
  }

  def optionalList(name: String): List[String] = {
    optionalString(name).map(toList).getOrElse(Nil)
  }

  def nonEmptyList(name: String): List[String] = {
    toList(requiredString(name)) match {
      case Nil =>  sys.error(s"Configuration parameter named[$name] must not be empty")
      case values => values
    }
  }

  /**
    * Splits string on delimiter (either ',' or ':') returning a list
    * of values which if present are guaranteed to be non empty
    */
  private[lib] def toList(value: String): List[String] = {
    reassembleProtocol(
      Nil,
      value.split(delim(value)).map(_.trim).toList.filter(_.nonEmpty)
    )
  }

  /**
    * Migrating from comma to : as delimiter due to a bug
    * in our dev tools. we need to fix the underlying bug
    * but for now we add complexity here to support ":"
    * as a delimiter, taking care not to lose urls
    */
  private[this] def delim(str: String): String = {
    val i = str.indexOf(",")
    if (i > 0) {
      ","
    } else {
      ":"
    }
  }

  @tailrec
  private[this] def reassembleProtocol(completed: List[String], remaining: List[String]): List[String] = {
    remaining match {
      case Nil => completed
      case a :: b :: rest if isProtocol(a) => reassembleProtocol(
        completed ++ List(s"$a:$b"),
        rest
      )
      case a :: rest => reassembleProtocol(completed ++ List(a), rest)
    }
  }

  private[this] val Protocols = Set("http", "https", "ftp", "file")
  private[this] def isProtocol(value: String) = {
    Protocols.contains(value.toLowerCase().trim)
  }
}
