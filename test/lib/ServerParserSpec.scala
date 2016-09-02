package lib

import controllers.{ServerProxy, ServerProxyDefinition, ServerProxyImpl}
import org.scalatest._
import org.scalatestplus.play._
import play.api.test._
import play.api.test.Helpers._
import scala.io.Source

class ServerParserSpec extends PlaySpec with OneServerPerSuite {

  private[this] lazy val serverProxyFactory = play.api.Play.current.injector.instanceOf[ServerProxy.Factory]

  val uri = "file:///test"

  val source = ProxyConfigSource(
    uri = uri,
    version = "0.0.1"
  )

  "empty" in {
    ConfigParser.parse(uri, "   ").validate() must be(
      Left(Seq("Missing uri", "Missing version"))
    )
  }

  "hostHeaderValue" in {
    Seq("http://user.api.flow.io", "https://user.api.flow.io").foreach { host =>
      ServerProxyDefinition(Server("user", host), ApidocServices.Empty).hostHeaderValue must be("user.api.flow.io")
    }
  }

  "single server w/ no operations" in {
    val spec = """
version: 0.0.1

servers:
  - name: test
    host: https://test.api.flow.io
"""
    ConfigParser.parse(uri, spec).validate() must be(
      Right(
        ProxyConfig(
          sources = Seq(source),
          servers = Seq(
            Server("test", "https://test.api.flow.io")
          ),
          operations = Nil
        )
      )
    )
  }

  "single server w/ operations" in {
    val spec = """
version: 1.2.3

servers:
  - name: user
    host: https://user.api.flow.io

operations:
  - method: GET
    path: /users
    server: user
  - method: POST
    path: /users
    server: user
  - method: GET
    path: /users/:id
    server: user
"""
    val user = Server(
      "user",
      "https://user.api.flow.io"
    )

    val cfg = ConfigParser.parse(uri, spec).validate.right.get
    cfg.sources must be(Seq(source.copy(version = "1.2.3")))
    cfg.servers must be(Seq(user))
    cfg.operations must be(
      Seq(
        Operation(Route("GET", "/users"), user),
        Operation(Route("POST", "/users"), user),
        Operation(Route("GET", "/users/:id"), user)
      )
    )
  }

  "latest production config" in {
    val uri = "https://s3.amazonaws.com/io.flow.aws-s3-public/util/api-proxy/production.config"
    val contents = Source.fromURL(uri).mkString
    ConfigParser.parse(uri, contents).validate match {
      case Left(errors) => {
        sys.error(s"Failed to parse config at URI[$uri]: $errors")
      }

      case Right(config) => {
        Seq("user", "organization", "catalog").foreach { name =>
          val server = config.servers.find(_.name == name).getOrElse {
            sys.error(s"Failed to find server[$name]")
          }
          server.host must be(s"https://$name.api.flow.io")
        }

        val index = Index(config)
        Seq(
          ("GET", "/users", "user"),
          ("GET", "/organizations", "organization"),
          ("GET", "/:organization/catalog", "catalog")
        ).foreach { case (method, path, server) =>
          val op = index.resolve(method, path).getOrElse {
            sys.error(s"Failed to resolve path[$path]")
          }
          op.server.name must be(server)
          op.route.method must be(method)
          op.route.path must be(path)
        }

        // make sure all servers have a defined execution context
        config.servers.filter { server =>
          serverProxyFactory(
            ServerProxyDefinition(server, ApidocServices.Empty)
          ).asInstanceOf[ServerProxyImpl].executionContextName == ServerProxy.DefaultContextName
        }.map(_.name).toList match {
          case Nil => {}
          case names => {
            sys.error("All servers must have their own execution context. Please update conf/base.conf to add contexts named: " + names.map { n => s"$n-context" }.sorted.mkString(", "))
          }
        }
      }
    }
  }

  "latest development config" in {
    val uri = "https://s3.amazonaws.com/io.flow.aws-s3-public/util/api-proxy/development.config"
    val contents = Source.fromURL(uri).mkString
    ConfigParser.parse(uri, contents).validate() match {
      case Left(errors) => {
        sys.error(s"Failed to parse config at URI[$uri]: $errors")
      }

      case Right(config) => {
        Map(
          "user" -> "http://localhost:6021",
          "organization" -> "http://localhost:6081",
          "catalog" -> "http://localhost:6071"
        ).foreach { case (name, host) =>
          val server = config.servers.find(_.name == name).getOrElse {
            sys.error(s"Failed to find server[$name]")
          }
          server.host must be(host)
        }

        val index = Index(config)
        Seq(
          ("GET", "/users", "user"),
          ("GET", "/organizations", "organization"),
          ("GET", "/:organization/catalog", "catalog")
        ).foreach { case (method, path, server) =>
          val op = index.resolve(method, path).getOrElse {
            sys.error(s"Failed to resolve path[$path]")
          }
          op.server.name must be(server)
          op.route.method must be(method)
          op.route.path must be(path)
        }
      }
    }
  }

  "internal routes" in {
    val uris = Seq(
      "https://s3.amazonaws.com/io.flow.aws-s3-public/util/api-proxy/development.config",
      "https://s3.amazonaws.com/io.flow.aws-s3-public/util/api-internal-proxy/development.config"
    )
    val proxyConfigFetcher = play.api.Play.current.injector.instanceOf[ProxyConfigFetcher]
    val config = proxyConfigFetcher.load(uris).right.get

    Seq("currency", "currency-internal").foreach { name =>
      config.servers.find(_.name == name).getOrElse {
        sys.error(s"Failed to find server[$name]")
      }
    }

    val index = Index(config)
    val op1 = index.resolve("GET", "/test/currency/rates").get
    op1.server.name must be("currency")
    op1.route.method must be("GET")
    op1.route.path must be("/:organization/currency/rates")

    val op2 = index.resolve("GET", "/internal/currency/rates").get
    op2.server.name must be("currency-internal")
    op2.route.method must be("GET")
    op2.route.path must be("/internal/currency/rates")
  }
  
}
