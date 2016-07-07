package lib

/**
  * Manages paths, matching an incoming request to a known path
  */
sealed trait InternalRoute {

  def host: String
  def method: String
  def path: String

  private[this] val hasOrganization: Boolean = path == "/:organization" || path.startsWith("/:organization/")

  /**
    * By naming convention, if the path starts with /:organization, we
    * know that we need to authenticate that the requesting user has
    * access to that organization.
    */
  def organization(requestPath: String): Option[String] = {
    hasOrganization match {
      case false => None
      case true => {
        requestPath.split("/").toList match {
          case empty :: org :: rest => Some(org)
          case _ => sys.error(s"$method $host$requestPath: Could not extract organization")
        }
      }
    }
  }

}

object InternalRoute {

  /**
    * Represents a static route (e.g. /organizations) with no wildcards
    */
  case class Static(host: String, method: String, path: String) extends InternalRoute {
    assert(method == method.toUpperCase.trim, s"Method[$method] must be upper case trimmed")
    assert(path == path.toLowerCase.trim, s"path[$path] must be lower case trimmed")
  }

  /**
    * Represents a dynamic route (e.g. /organizations/:id) with
    * wildcards. We implement this be building a regular expression
    * that replaces any ":xxx" with a pattern of one or more
    * characters that are not a '/'
    */
  case class Dynamic(host: String, method: String, path: String) extends InternalRoute {
    assert(method == method.toUpperCase.trim, s"Method[$method] must be upper case trimmed")
    assert(path == path.toLowerCase.trim, s"path[$path] must be lower case trimmed")

    private[this] val pattern = (
      "^" +
        path.split("/").map { p =>
          p.startsWith(":") match {
            case true => {
              """[^\/]+"""
            }
            case false => {
              p
            }
          }
        }.mkString("""\/""") +
        "$"
    ).r

    def matches(incomingMethod: String, incomingPath: String): Boolean = {
      method == incomingMethod match {
        case true => incomingPath match {
          case pattern() => true
          case _ => false
        }
        case false => {
          false
        }
      }
    }

  }

  def apply(route: Route, host: String): InternalRoute = {
    route.path.indexOf(":") >= 0 match {
      case true => Dynamic(host = host, method = route.method, path = route.path)
      case false => Static(host = host, method = route.method, path = route.path)
    }
  }

}
