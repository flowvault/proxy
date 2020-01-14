package helpers

import java.util.UUID

import cats.data.Validated.{Invalid, Valid}
import cats.data.ValidatedNec
import cats.implicits._
import io.flow.log.RollbarLogger
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.libs.ws.WSClient
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}

abstract class BasePlaySpec extends PlaySpec
  with GuiceOneServerPerSuite
  with FutureAwaits
  with DefaultAwaitTimeout {

  def wsClient: WSClient = app.injector.instanceOf[WSClient]
  def logger: RollbarLogger = app.injector.instanceOf[RollbarLogger]

  def validOrErrors[V, T](result: ValidatedNec[V, T]): T = {
    result match {
      case Invalid(errors) => sys.error(s"Expected Valid but got Invalid: " + errors.toList.mkString(", "))
      case Valid(obj) => obj
    }
  }

  def createTestId(): String = {
    "tst-" + UUID.randomUUID().toString
  }

}
