package handlers

import helpers.BasePlaySpec


class UrlFormEncodedHandlerSpec extends BasePlaySpec {

  import scala.concurrent.ExecutionContext.Implicits.global

  private[this] def urlFormEncodedHandler = app.injector.instanceOf[UrlFormEncodedHandler]

  "converts url form encoded to application/json" in {
  }
}
