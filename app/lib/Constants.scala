package lib

object Constants {

  object Headers {

    val FlowAuth = "X-Flow-Auth"
    val FlowRequestId = "X-Flow-Request-Id"
    val FlowService = "X-Flow-Service"
    val FlowHost = "X-Flow-Host"

    val Host = "Host"
    val ForwardedHost = "X-Forwarded-Host"

    val namesToRemove = Seq(FlowAuth, FlowService, FlowHost, Host)

  }

  /**
    * For some features (like specifying explicitly to which service
    * to route the request), we verify that the requesting user is a
    * member of this organization.
    */
  val FlowOrganizationId = "flow"
  
}
