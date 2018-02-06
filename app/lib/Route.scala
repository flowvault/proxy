package lib

/**
  * Manages paths, matching an incoming request to a known path
  */
sealed trait Route {

  def method: Method

  def path: String

  /**
    * Returns true if this route matches the specified method and path
    */
  def matches(incomingMethod: Method, incomingPath: String): Boolean

  private[this] val InternalOrganization = "flow"

  private[this] val hasOrganizationPrefix: Boolean = path == "/:organization" || path.startsWith("/:organization/")
  // POST /organizations/:organization_id is reserved to create an org
  private[this] val hasOrganizationResourceId: Boolean = path.startsWith("/organizations/:organization_id") && !(
    path == "/organizations/:organization_id" && method == Method.Post
  )
  private[this] val hasPartner: Boolean = path == "/partners/:partner" || path.startsWith("/partners/:partner/")
  private[this] val isInternal: Boolean = path == "/internal" || path.startsWith("/internal/")

  private[this] val hasAnyOrganization = hasOrganizationPrefix || hasOrganizationResourceId

  assert(
    !hasOrganizationPrefix || !hasOrganizationResourceId,
    s"Route[$method $path] cannot both be hasOrganizationPrefix[$hasOrganizationPrefix] and hasOrganizationResourceId[$hasOrganizationResourceId]"
  )

  assert(
    (isInternal && !hasAnyOrganization) || !isInternal,
    s"Route[$method $path] cannot both be isInternal[$isInternal] and hasAnyOrganization[$hasAnyOrganization]"
  )

  assert(
    (isInternal && !hasPartner) || !isInternal,
    s"Route[$method $path] cannot both be isInternal[$isInternal] and hasPartner[$hasPartner]"
  )

  /**
    * By naming convention, if the path starts with /:organization, we
    * know that we need to authenticate that the requesting user has
    * access to that organization.
    */
  def organization(requestPath: String): Option[String] = {
    if (isInternal) {
      Some(InternalOrganization)

    } else if (hasOrganizationPrefix) {
      requestPath.split("/").toList match {
        case _ :: org :: _ => Some(org)
        case _ => sys.error(s"$method $requestPath: Could not extract organization from url")
      }

    } else if (hasOrganizationResourceId) {
      requestPath.split("/").toList match {
        case _ :: _ :: org :: _ => Some(org)
        case _ => sys.error(s"$method $requestPath: Could not extract organization from organization resource url")
      }

    } else {
      None
    }
  }

  /**
    * By naming convention, if the path starts with /partners/:partner, we
    * know that we need to authenticate that the requesting user has
    * access to that partner.
    */
  def partner(requestPath: String): Option[String] = {
    if (hasPartner) {
      requestPath.split("/").toList match {
        case _ :: _ :: partner :: _ => Some(partner)
        case _ => sys.error(s"$method $requestPath: Could not extract partner from url")
      }
    } else {
      None
    }
  }

}

object Route {

  /**
    * Represents a static route (e.g. /organizations) with no wildcards
    */
  case class Static(method: Method, path: String) extends Route {
    assert(path == path.toLowerCase.trim, s"path[$path] must be lower case trimmed")

    override def matches(incomingMethod: Method, incomingPath: String): Boolean = {
      method == incomingMethod && path == incomingPath
    }
  }

  /**
    * Represents a dynamic route (e.g. /organizations/:id) with
    * wildcards. We implement this by building a regular expression
    * that replaces any ":xxx" with a pattern of one or more
    * characters that are not a '/'
    */
  case class Dynamic(method: Method, path: String) extends Route {
    assert(path == path.toLowerCase.trim, s"path[$path] must be lower case trimmed")

    private[this] val isOrganizationPath = path.startsWith("/:organization/")

    private[this] val pattern = (
      "^" +
        path.split("/").map { p =>
          if (p.startsWith(":")) {
            """[^\/]+"""
          } else {
            p
          }
        }.mkString("""\/""") +
        "$"
    ).r

    override def matches(incomingMethod: Method, incomingPath: String): Boolean = {
      if (method == incomingMethod) {
        incomingPath match {
          case pattern() => {
            if (isOrganizationPath) {
              // Special case 'internal' so that it does not match an org
              !incomingPath.startsWith("/internal/")
            } else {
              true
            }
          }
          case _ => false
        }
      } else {
        false
      }
    }

  }

  def apply(method: Method, path: String): Route = {
    if (path.indexOf(":") >= 0) {
      Dynamic(method = method, path = path)
    } else {
      Static(method = method, path = path)
    }
  }

}
