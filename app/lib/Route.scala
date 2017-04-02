package lib

/**
  * Manages paths, matching an incoming request to a known path
  */
sealed trait Route {

  def method: String
  def path: String

  /**
    * Returns true if this route matches the specified method and path
    */
  def matches(incomingMethod: String, incomingPath: String): Boolean

  private[this] val InternalOrganization = "flow"

  private[this] val hasOrganization: Boolean = path == "/:organization" || path.startsWith("/:organization/")
  private[this] val hasPartner: Boolean = path == "/partners/:partner" || path.startsWith("/partners/:partner/")
  private[this] val isInternal: Boolean = path == "/internal" || path.startsWith("/internal/")

  assert(
    (isInternal && !hasOrganization) || !isInternal,
    s"Route cannot both be internal and have an organization: $method $path"
  )

  assert(
    (isInternal && !hasPartner) || !isInternal,
    s"Route cannot both be internal and have a partner: $method $path"
  )

  /**
    * By naming convention, if the path starts with /:organization, we
    * know that we need to authenticate that the requesting user has
    * access to that organization.
    */
  def organization(requestPath: String): Option[String] = {
    isInternal match {
      case true => Some(InternalOrganization)
      case false => {
        hasOrganization match {
          case false => None
          case true => {
            requestPath.split("/").toList match {
              case empty :: org :: rest => Some(org)
              case _ => sys.error(s"$method $requestPath: Could not extract organization from url")
            }
          }
        }
      }
    }
  }

  /**
    * By naming convention, if the path starts with /partners/:partner, we
    * know that we need to authenticate that the requesting user has
    * access to that partner.
    */
  def partner(requestPath: String): Option[String] = {
    hasPartner match {
      case false => None
      case true => {
        requestPath.split("/").toList match {
          case empty :: partners :: partner :: rest => Some(partner)
          case _ => sys.error(s"$method $requestPath: Could not extract partner from url")
        }
      }
    }
  }
  
}

object Route {

  /**
    * Represents a static route (e.g. /organizations) with no wildcards
    */
  case class Static(method: String, path: String) extends Route {
    assert(method == method.toUpperCase.trim, s"Method[$method] must be upper case trimmed")
    assert(path == path.toLowerCase.trim, s"path[$path] must be lower case trimmed")

    override def matches(incomingMethod: String, incomingPath: String) = {
      method == incomingMethod && path == incomingPath
    }
  }

  /**
    * Represents a dynamic route (e.g. /organizations/:id) with
    * wildcards. We implement this be building a regular expression
    * that replaces any ":xxx" with a pattern of one or more
    * characters that are not a '/'
    */
  case class Dynamic(method: String, path: String) extends Route {
    assert(method == method.toUpperCase.trim, s"Method[$method] must be upper case trimmed")
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

    override def matches(incomingMethod: String, incomingPath: String): Boolean = {
      method == incomingMethod match {
        case true => incomingPath match {
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
        case false => {
          false
        }
      }
    }

  }

  def apply(method: String, path: String): Route = {
    path.indexOf(":") >= 0 match {
      case true => Dynamic(method = method, path = path)
      case false => Static(method = method, path = path)
    }
  }

}
