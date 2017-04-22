package lib

import play.api.Logger

/**
  * Given a particular configuration for a proxy, builds an in memory
  * index of the routes to make for efficient lookup of the route (and
  * service) ased on an incoming request method and path.
  * 
  * Stategy:
  *   - Use a hash map lookup for all static routes (no variables)
  *   - For paths with variables
  *     - First segment by the HTTP Method
  *     - Iterate through list to call matches on each route
  */
case class Index(config: ProxyConfig) {

  /**
    * Create two indexes of the routes:
    *   - static routes are simple lookups by path (Map[String, Route])
    *   - dynamic routes is a map from the HTTP Method to a list of routes to try (Seq[Route])
    */
  private[this] val (staticRouteMap, dynamicRoutes) = {
    val all: Seq[Operation] = config.operations

    val dynamicRoutes = all.flatMap { op =>
      op.route match {
        case r: Route.Dynamic => Some(op)
        case r: Route.Static => None
      }
    }

    // Map from method name to list of internal routes
    var dynamicRouteMap = scala.collection.mutable.Map[String, Seq[Operation]]()
    all.foreach { op =>
      op.route match {
        case r: Route.Dynamic => {
          dynamicRouteMap.get(r.method) match {
            case None => {
              dynamicRouteMap += (r.method -> Seq(op))
            }
            case Some(operations) => {
              dynamicRouteMap += (r.method -> (operations ++ Seq(op)))
            }
          }
        }
        case r: Route.Static => // no-op
      }
    }

    val staticRoutes = all.flatMap { op =>
      op.route match {
        case r: Route.Dynamic => None
        case r: Route.Static => Some(op)
      }
    }

    val staticRouteMap = Map(
      staticRoutes.map { op =>
        routeKey(op.route.method, op.route.path) -> op
      }: _*
    )

    Logger.info(s"Index: staticRoutes[${staticRouteMap.size}] dynamicRoutes[${dynamicRoutes.size}]")

    (staticRouteMap, dynamicRouteMap.toMap)
  }

  final def resolve(method: String, path: String): Option[Operation] = {
    staticRouteMap.get(routeKey(method, path)) match {
      case None => {
        dynamicRoutes.get(method.toUpperCase) match {
          case None => {
            None
          }
          case Some(routes) => {
            routes.find(_.route.matches(method.toUpperCase, path.toLowerCase.trim))
          }
        }
      }
      case Some(op) => {
        Some(op)
      }
    }
  }

  private[this] def routeKey(method: String, path: String): String = {
    s"$method:$path".toLowerCase
  }
}
