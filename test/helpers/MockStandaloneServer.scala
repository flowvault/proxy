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
        println(s"CREATED SERVER ON PORT[${port.value}]")
        val r = f(port, client)
        Thread.sleep(1000)
        println(s"CLOSED SERVER ON PORT[${port.value}]")
        r
      }
    }
  }

  def embed(
    name: String,
    port: Int = 15432
  )(
    f: lib.Server => Any
  ): Unit = {
    import play.api.inject.guice.GuiceApplicationBuilder
    import play.api.mvc._
    import play.api.routing.SimpleRouterImpl
    import play.api.routing.sird._
    import play.core.server.{AkkaHttpServer, ServerConfig}

    val server = AkkaHttpServer.fromApplication(GuiceApplicationBuilder().router(new SimpleRouterImpl({
      case GET(p"/users/") => Action {
        Results.Ok(s"Hello users")
      }
    })).build(), ServerConfig(
      port = Some(port),
      address = "127.0.0.1"
    ))

    try {
      println(s"Created server on port[$port]")
      f(
        lib.Server(
          name = name,
          host = s"http://127.0.0.1:$port"
        )
      )
    } finally {
      println(s"Stopping server on port[$port]")
      server.stop()
    }
  }
}
