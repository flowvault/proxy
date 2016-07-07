package lib

import play.api.test._
import play.api.test.Helpers._
import org.scalatest._
import org.scalatestplus.play._

class FlowAuthSpec extends PlaySpec with OneServerPerSuite {

  "map contains only values" in {
    FlowAuthData(
      userId = "5",
      organization = None,
      role = None
    ).toMap must be(Map("user_id" -> "5"))

    FlowAuthData(
      userId = "5",
      organization = Some("flow"),
      role = None
    ).toMap must be(Map("user_id" -> "5", "organization" -> "flow"))

    FlowAuthData(
      userId = "5",
      organization = Some("flow"),
      role = Some("member")
    ).toMap must be(Map("user_id" -> "5", "organization" -> "flow", "role" -> "member"))
  }

}
