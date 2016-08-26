package lib

object Constants {

  object Headers {

    val FlowAuth = "X-Flow-Auth"
    val FlowRequestId = "X-Flow-Request-Id"
    val FlowService = "X-Flow-Service"
    val FlowHost = "X-Flow-Host"
    val FlowIp = "X-Flow-Ip"

    val Host = "Host"
    val ForwardedHost = "X-Forwarded-Host"
    val Origin = "Origin"
    val ForwardedOrigin = "X-Forwarded-Origin"
    val ForwardedMethod = "X-Forwarded-Method"

    val namesToRemove = Seq(FlowAuth, FlowRequestId, FlowService, FlowHost, Host, Origin, ForwardedOrigin, ForwardedMethod)
  }

  /**
    * For some features (like specifying explicitly to which service
    * to route the request), we verify that the requesting user is a
    * member of this organization.
    */
  val FlowOrganizationId = "flow"
  
}
