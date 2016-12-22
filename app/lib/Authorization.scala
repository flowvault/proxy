package lib

import javax.inject.{Inject, Singleton}

import authentikat.jwt.{JsonWebToken, JwtClaimsSetJValue}
import org.apache.commons.codec.binary.{Base64, StringUtils}

sealed trait Authorization

object Authorization {

  /**
    * Indicates no auth credentials were present
    */
  case object NoCredentials extends Authorization

  /**
    * Indicates authorization header was present but was not a
    * recognized type (e.g. Basic, Bearer)
    */
  case object Unrecognized extends Authorization {
    def valid = Seq("Basic", "Bearer")
  }

  /**
    * Indicates API token was presented as basic auth; but API token
    * was not valid.
    */
  case object InvalidApiToken extends Authorization

  /**
    * Indicates JWT Bearer data was presented as authorization
    * header; but data was not valid.
    */
  case class InvalidJwt(missing: Seq[String]) extends Authorization

  /**
    * Indicates JWT Bearer data was presented as authorization
    * header; but data was not valid.
    */
  case object InvalidBearer extends Authorization

  /**
    * Indicates valid API Token for a given user.
    */
  case class Token(token: String) extends Authorization

  /**
    * Indicates valid user ID was parsed from JWT token
    */
  case class User(id: String) extends Authorization

}

/**
  * Responsible for parsing the authorization header, returning a
  * specific Authorization that can be used to clearly identify
  * whether or not authorization succeeded, and if not why.
  */
@Singleton
class AuthorizationParser @Inject() (
  config: Config
) {

  /**
    * Parses the value fro the authorization header, handling case
    * where no authorization was present
   */
  def parse(value: Option[String]): Authorization = {
    value match {
      case None => Authorization.NoCredentials
      case Some(value) => parse(value)
    }
  }

  /**
    * Parses the actual authorization header value. Acceptable types are:
    * - Basic - the API Token for the user
    * - Bearer - the JWT Token for the user with that contains an id field representing the user id in the database
   */
  def parse(headerValue: String): Authorization = {
    headerValue.split(" ").toList match {
      case "Basic" :: value :: Nil => {

        new String(Base64.decodeBase64(StringUtils.getBytesUsAscii(value))).split(":").toList match {
          case Nil => Authorization.InvalidApiToken
          case token :: _ => Authorization.Token(token)
        }
      }

      case "Bearer" :: value :: Nil => {
        value match {
          case JsonWebToken(header, claimsSet, signature) if jwtIsValid(value) => parseJwtToken(claimsSet)
          case _ => Authorization.InvalidBearer
        }
      }

      case _ => Authorization.Unrecognized
    }
  }

  private[this] def jwtIsValid(token: String): Boolean = JsonWebToken.validate(token, config.jwtSalt)

  private[this] def parseJwtToken(claimsSet: JwtClaimsSetJValue): Authorization = {
    claimsSet.asSimpleMap.toOption match {
      case Some(claims) => {
        claims.get("id") match {
          case None => Authorization.InvalidJwt(Seq("id"))
          case Some(userId) => Authorization.User(userId)
        }
      }

      case _ => Authorization.InvalidBearer
    }
  }

}
