package lib

import org.joda.time.format.ISODateTimeFormat.dateTime
import org.scalatest._
import org.scalatestplus.play._
import play.api.test._
import play.api.test.Helpers._

class FlowAuthSpec extends PlaySpec with OneServerPerSuite {

  "map contains only values" in {
    val d = FlowAuthData(
      userId = "5",
      organization = None,
      role = None
    )
    d.toMap must be(Map("user_id" -> "5", "created_at" -> dateTime.print(d.createdAt)))

    val d2 = FlowAuthData(
      userId = "5",
      organization = Some("flow"),
      role = None
    )
    d2.toMap must be(Map("user_id" -> "5", "created_at" -> dateTime.print(d2.createdAt), "organization" -> "flow"))

    val d3 = FlowAuthData(
      userId = "5",
      organization = Some("flow"),
      role = Some("member")
    )
    d3.toMap must be(Map("user_id" -> "5", "created_at" -> dateTime.print(d3.createdAt), "organization" -> "flow", "role" -> "member"))
  }

}
