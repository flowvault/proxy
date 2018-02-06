package lib

import helpers.BasePlaySpec
import scala.io.Source

class IndexSpec extends BasePlaySpec {

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
      Operation(Route(Method.Get, "/organizations"), org),
      Operation(Route(Method.Post, "/organizations"), org),
      Operation(Route(Method.Get, "/organizations/:id"), org),
      Operation(Route(Method.Put, "/organizations/:id"), org),
      Operation(Route(Method.Get, "/users"), user),
      Operation(Route(Method.Post, "/users"), user),
      Operation(Route(Method.Get, "/users/:id"), user),
      Operation(Route(Method.Put, "/users/:id"), user)
    )
    
    val s = Index(
      ProxyConfig(
        sources = Seq(source),
        servers = servers,
        operations = operations
      )
    )

    // Undefined
    s.resolve(Method.Get, "") must be(None)
    s.resolve(Method.Get, "/") must be(None)
    s.resolve(Method.Get, "/tmp") must be(None)

    // static
    s.resolve(Method.Get, "/organizations").map(_.server.host) must be(Some("https://organization.api.flow.io"))
    s.resolve(Method.Post, "/organizations").map(_.server.host) must be(Some("https://organization.api.flow.io"))
    s.resolve(Method.Get, "/users").map(_.server.host) must be(Some("https://user.api.flow.io"))
    s.resolve(Method.Post, "/users").map(_.server.host) must be(Some("https://user.api.flow.io"))

    // dynamic
    s.resolve(Method.Get, "/organizations/flow").map(_.server.host) must be(Some("https://organization.api.flow.io"))
    s.resolve(Method.Put, "/organizations/flow").map(_.server.host) must be(Some("https://organization.api.flow.io"))
    s.resolve(Method.Get, "/users/usr-201606-128367123").map(_.server.host) must be(Some("https://user.api.flow.io"))
    s.resolve(Method.Put, "/users/usr-201606-128367123").map(_.server.host) must be(Some("https://user.api.flow.io"))
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
      Operation(Route(Method.Get, "/:organization/currency/settings"), public),
      Operation(Route(Method.Get, "/:organization/currency/settings/:id"), public),
      Operation(Route(Method.Get, "/internal/currency/settings"), internal),
      Operation(Route(Method.Get, "/internal/currency/settings/:id"), internal)
    )
    
    val s = Index(
      ProxyConfig(
        sources = Seq(source),
        servers = servers,
        operations = operations
      )
    )

    s.resolve(Method.Get, "/demo/currency/settings").map(_.server.name) must be(Some("currency"))
    s.resolve(Method.Get, "/demo/currency/settings/50").map(_.server.name) must be(Some("currency"))
    
    s.resolve(Method.Get, "/internal/currency/settings").map(_.server.name) must be(Some("currency-internal"))
    s.resolve(Method.Get, "/internal/currency/settings/50").map(_.server.name) must be(Some("currency-internal"))
  }

  // We leave this here as a simple way to evaluate impact
  // on performance of changes in the path resolution libraries
  "performance measurement" in {
    val uri = "https://s3.amazonaws.com/io.flow.aws-s3-public/util/api-proxy/development.config"
    //val uri = "file:///tmp/api-proxy.development.config"
    val contents = Source.fromURL(uri).mkString
    val config = ConfigParser.parse(source.uri, contents).validate().right.get
    val index = Index(config)

    val ms = time(1000) { () =>
      index.resolve(Method.Get, "/flow/catalog/items")
      index.resolve(Method.Get, "/organizations")
      index.resolve(Method.Get, "/:organization/catalog/items")
      index.resolve(Method.Get, "/:organization/catalog/items/:number")
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
