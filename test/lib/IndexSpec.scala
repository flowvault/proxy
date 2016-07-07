package lib

import play.api.test._
import play.api.test.Helpers._
import org.scalatest._
import org.scalatestplus.play._
import scala.io.Source

class IndexSpec extends PlaySpec with OneServerPerSuite {

  "resolves route" in {
    val services = Seq(
      Service(
        "organization",
        "https://organization.api.flow.io",
        routes = Seq(
          Route("GET", "/organizations"),
          Route("POST", "/organizations"),
          Route("GET", "/organizations/:id"),
          Route("PUT", "/organizations/:id")
        )
      ),
      Service(
        "user",
        "https://user.api.flow.io",
        routes = Seq(
          Route("GET", "/users"),
          Route("POST", "/users"),
          Route("GET", "/users/:id"),
          Route("PUT", "/users/:id")
        )
      )
    )

    val s = Index(
      ProxyConfig(
        version = "0.0.1",
        services = services
      )
    )

    // Undefined
    s.resolve("GET", "") must be(None)
    s.resolve("GET", "/") must be(None)
    s.resolve("GET", "/tmp") must be(None)

    // static
    s.resolve("GET", "/organizations").map(_.host) must be(Some("https://organization.api.flow.io"))
    s.resolve("POST", "/organizations").map(_.host) must be(Some("https://organization.api.flow.io"))
    s.resolve("GET", "/users").map(_.host) must be(Some("https://user.api.flow.io"))
    s.resolve("POST", "/users").map(_.host) must be(Some("https://user.api.flow.io"))

    // dynamic
    s.resolve("GET", "/organizations/flow").map(_.host) must be(Some("https://organization.api.flow.io"))
    s.resolve("PUT", "/organizations/flow").map(_.host) must be(Some("https://organization.api.flow.io"))
    s.resolve("GET", "/users/usr-201606-128367123").map(_.host) must be(Some("https://user.api.flow.io"))
    s.resolve("PUT", "/users/usr-201606-128367123").map(_.host) must be(Some("https://user.api.flow.io"))
  }

  // We leave this here as a simple way to evaluate impact
  // on peformance of changes in the path resolution libraries
  "performance measurement" in {
    val uri = "https://s3.amazonaws.com/io.flow.aws-s3-public/util/api-proxy/development.config"
    val contents = Source.fromURL(uri).mkString
    ServiceParser.parse(contents) match {
      case Left(errors) => {
        sys.error(s"Failed to parse config at URI[$uri]: $errors")
      }
      case Right(config) => {
        val index = Index(config)

        val ms = time(100) { () =>
          index.resolve("GET", "/flow/catalog/items")
          index.resolve("GET", "/organizations")
        }
        //println(s"ms: $ms")
      }
    }
  }

  def time(numberIterations: Int = 10000)(f: () => Unit): Long = {
    val start = System.currentTimeMillis
    (1 to numberIterations).foreach { _ =>
      f
    }
    System.currentTimeMillis - start
  }

}
