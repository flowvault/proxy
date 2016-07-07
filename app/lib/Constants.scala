package lib

object Constants {

  object Headers {

    val FlowAuth = "X-Flow-Auth"
    val FlowService = "X-Flow-Service"
    val FlowHost = "X-Flow-Host"

    val all = Seq(FlowAuth, FlowService, FlowHost)

  }

  /**
    * For some features (like specifying explicitly to which service
    * to route the request), we verify that the requesting user is a
    * member of this organization.
    */
  val FlowOrganizationId = "flow"
  
}
