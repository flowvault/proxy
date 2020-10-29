package controllers

import helpers.BasePlaySpec
import lib.Constants
import org.scalatest.OptionValues

class HealthchecksSpec extends BasePlaySpec with OptionValues {

  "GET /_internal_/healthcheck" in {
    val result = await(
      wsClient.url(s"http://localhost:$port/_internal_/healthcheck").get()
    )
    result.status must equal(200)
    result.body.contains("healthy") must equal(true)
    result.header(Constants.Headers.FlowProxyResponseTime).value must fullyMatch regex "\\d+"
  }

}
