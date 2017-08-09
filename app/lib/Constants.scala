package lib

object Constants {

  object Headers {

    val FlowAuth = "X-Flow-Auth"
    val FlowRequestId = "X-Flow-Request-Id"
    val FlowServer = "X-Flow-Server"
    val FlowHost = "X-Flow-Host"
    val FlowIp = "X-Flow-Ip"

    val Host = "Host"
    val ForwardedHost = "X-Forwarded-Host"
    val Origin = "Origin"
    val ForwardedOrigin = "X-Forwarded-Origin"
    val ForwardedMethod = "X-Forwarded-Method"

    val CfRay = "CF-RAY"

    val namesToRemove = Seq(FlowAuth, FlowRequestId, FlowServer, FlowHost, Host, Origin, ForwardedOrigin, ForwardedMethod, CfRay)
  }

  /**
    * For some features (like specifying explicitly to which server
    * to route the request), we verify that the requesting user is a
    * member of this organization.
    */
  val FlowOrganizationId = "flow"
  
}
