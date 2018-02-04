package helpers

import java.util.UUID

import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.libs.ws.WSClient
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}

abstract class BasePlaySpec extends PlaySpec
  with GuiceOneServerPerSuite
  with FutureAwaits
  with DefaultAwaitTimeout {

  def wsClient: WSClient = app.injector.instanceOf[WSClient]

  def rightOrErrors[K, V](result: Either[K, V]): V = {
    result match {
      case Left(error) => sys.error(s"Expected right but got left: $error")
      case Right(r) => r
    }
  }

  def createTestId(): String = {
    "tst-" + UUID.randomUUID().toString
  }

}
