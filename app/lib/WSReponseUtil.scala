package lib

import play.api.Logger
import play.api.libs.ws.WSResponse

import scala.util.{Failure, Success, Try}

object WSReponseUtil {

  private def EmptyBody = ""

  implicit class WSReponseWrapper(val response: WSResponse) extends AnyVal {
    def safeBody: String = {
      Try(response.body) match {
        case Success(b) => b
        case Failure(e) =>
          Logger.warn("Error while retrieving response body. Returning empty body.", e)
          EmptyBody
      }
    }
  }

}
