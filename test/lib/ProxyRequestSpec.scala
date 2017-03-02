package lib

import org.scalatestplus.play._

class ProxyRequestSpec extends PlaySpec with OneServerPerSuite {

  "validate method" in {
    ProxyRequest.validate()
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

}
