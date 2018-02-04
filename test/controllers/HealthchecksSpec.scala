package controllers

import helpers.BasePlaySpec

class HealthchecksSpec extends BasePlaySpec {

  override lazy val port = 9010

  "GET /_internal_/healthcheck" in {
    val result = await(
      wsClient.url(s"http://localhost:$port/_internal_/healthcheck").get()
    )
    result.status must equal(200)
    result.body.contains("healthy") must equal(true)
  }

}
