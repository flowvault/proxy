package helpers

import play.core.server.Server
import play.api.routing.sird._
import play.api.mvc._
import play.api.libs.json._
import play.api.libs.ws.WSClient
import play.api.test.WsTestClient

object MockStandaloneServer {

  def withTestClient[T](
    f: (WSClient, Int) => T
  ):T = {
    Server.withRouterFromComponents() { components =>
      import Results._
      import components.{defaultActionBuilder => Action}
      {
        case GET(p"/users/") => Action {
          Ok(
            Json.arr(
              Json.obj(
                "id" -> 1
              )
            )
          )
        }

        case GET(p"/redirect/example") => Action {
          Redirect("http://localhost/foo")
        }
      }
    } { implicit port =>
      WsTestClient.withClient { client =>
        f(client, port.value)
      }
    }
  }
}
