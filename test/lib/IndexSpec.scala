package lib

import play.api.test._
import play.api.test.Helpers._
import org.scalatest._
import org.scalatestplus.play._
import scala.io.Source

class IndexSpec extends PlaySpec with OneServerPerSuite {

  val source = ProxyConfigSource(
    uri = "file:///test",
    version = "0.0.1"
  )

  "resolves route" in {
    val org = Server(
      "organization",
      "https://organization.api.flow.io"
    )
    val user = Server(
      "user",
      "https://user.api.flow.io"
    )
    val servers = Seq(org, user)

    val operations = Seq(
      Operation(Route("GET", "/organizations"), org),
      Operation(Route("POST", "/organizations"), org),
      Operation(Route("GET", "/organizations/:id"), org),
      Operation(Route("PUT", "/organizations/:id"), org),
      Operation(Route("GET", "/users"), user),
      Operation(Route("POST", "/users"), user),
      Operation(Route("GET", "/users/:id"), user),
      Operation(Route("PUT", "/users/:id"), user)
    )
    
    val s = Index(
      ProxyConfig(
        sources = Seq(source),
        servers = servers,
        operations = operations
      )
    )

    // Undefined
    s.resolve("GET", "") must be(None)
    s.resolve("GET", "/") must be(None)
    s.resolve("GET", "/tmp") must be(None)

    // static
    s.resolve("GET", "/organizations").map(_.server.host) must be(Some("https://organization.api.flow.io"))
    s.resolve("POST", "/organizations").map(_.server.host) must be(Some("https://organization.api.flow.io"))
    s.resolve("GET", "/users").map(_.server.host) must be(Some("https://user.api.flow.io"))
    s.resolve("POST", "/users").map(_.server.host) must be(Some("https://user.api.flow.io"))

    // dynamic
    s.resolve("GET", "/organizations/flow").map(_.server.host) must be(Some("https://organization.api.flow.io"))
    s.resolve("PUT", "/organizations/flow").map(_.server.host) must be(Some("https://organization.api.flow.io"))
    s.resolve("GET", "/users/usr-201606-128367123").map(_.server.host) must be(Some("https://user.api.flow.io"))
    s.resolve("PUT", "/users/usr-201606-128367123").map(_.server.host) must be(Some("https://user.api.flow.io"))
  }

  "internal routes that match :org routes" in {
    val public = Server(
      "currency",
      "https://currency.api.flow.io"
    )
    val internal = Server(
      "currency-internal",
      "https://currency.api.flow.io"
    )
    val servers = Seq(public, internal)

    val operations = Seq(
      Operation(Route("GET", "/:organization/currency/settings"), public),
      Operation(Route("GET", "/:organization/currency/settings/:id"), public),
      Operation(Route("GET", "/internal/currency/settings"), internal),
      Operation(Route("GET", "/internal/currency/settings/:id"), internal)
    )
    
    val s = Index(
      ProxyConfig(
        sources = Seq(source),
        servers = servers,
        operations = operations
      )
    )

    s.resolve("GET", "/demo/currency/settings").map(_.server.name) must be(Some("currency"))
    s.resolve("GET", "/demo/currency/settings/50").map(_.server.name) must be(Some("currency"))
    
    s.resolve("GET", "/internal/currency/settings").map(_.server.name) must be(Some("currency-internal"))
    s.resolve("GET", "/internal/currency/settings/50").map(_.server.name) must be(Some("currency-internal"))
  }

  // We leave this here as a simple way to evaluate impact
  // on peformance of changes in the path resolution libraries
  "performance measurement" in {
    val uri = "https://s3.amazonaws.com/io.flow.aws-s3-public/util/api-proxy/development.config"
    //val uri = "file:///tmp/api-proxy.development.config"
    val contents = Source.fromURL(uri).mkString
    val config = ConfigParser.parse(source.uri, contents).validate().right.get
    val index = Index(config)

    val ms = time(1000) { () =>
      index.resolve("GET", "/flow/catalog/items")
      index.resolve("GET", "/organizations")
      index.resolve("GET", "/:organization/catalog/items")
      index.resolve("GET", "/:organization/catalog/items/:number")
    }
    println(s"1000 path lookups took $ms ms")
    ms < 50 must be(true)
  }

  def time(numberIterations: Int = 10000)(f: () => Unit): Long = {
    val start = System.currentTimeMillis
    (1 to numberIterations).foreach { _ =>
      f
    }
    System.currentTimeMillis - start
  }

}
