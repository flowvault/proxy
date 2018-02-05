package helpers

import play.api.http.Port
import play.core.server.Server
import play.api.routing.sird._
import play.api.mvc._
import play.api.libs.json._
import play.api.libs.ws.WSClient
import play.api.test.WsTestClient

object MockStandaloneServer {

  def withServer[T](
    f: (lib.Server, WSClient) => T
  ): T = {
    withTestClient { (port, client) =>
      f(
        lib.Server(
          name = "Test",
          host = s"http://localhost:${port.value}"
        ),
        client
      )
    }
  }

  private[this] def withTestClient[T](
    f: (Port, WSClient) => T
  ):T = {
    Server.withRouterFromComponents() { components =>
      import Results._
      import components.{defaultActionBuilder => Action}
      {
        case GET(p"/users/") => Action {
          Ok(
            Json.arr(
              Json.obj(
                "id" -> 1,
                "name" -> "Joe Smith"
              )
            )
          )
        }
      }
    } { implicit port =>
      WsTestClient.withClient { client =>
        f(port, client)
      }
    }
  }
}
