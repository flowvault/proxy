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
        case GET(p"/users/1") => Action {
          Ok(
            Json.obj(
              "id" -> 1
            )
          )
        }

        case POST(p"/users") => Action {
          Created(
            Json.obj(
              "id" -> 1
            )
          )
        }

        case GET(p"/redirect/example") => Action {
          Redirect("http://localhost/foo")
        }

        case GET(p"/file.pdf") => Action {
          Ok("file.pdf").as("application/pdf")
        }

      }
    } { implicit port =>
      WsTestClient.withClient { client =>
        f(client, port.value)
      }
    }
  }
}
