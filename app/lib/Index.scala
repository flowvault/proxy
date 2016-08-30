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
    val all: Seq[Route] = config.operations.map { _.route }

    val dynamicRoutes = all.flatMap {
      case r: Route.Dynamic => Some(r)
      case r: Route.Static => None
    }

    // Map from method name to list of internal routes
    var dynamicRouteMap = scala.collection.mutable.Map[String, Seq[Route.Dynamic]]()
    all.foreach {
      case r: Route.Dynamic => {
        dynamicRouteMap.get(r.method) match {
          case None => {
            dynamicRouteMap += (r.method -> Seq(r))
          }
          case Some(els) => {
            dynamicRouteMap += (r.method -> (els ++ Seq(r)))
          }
        }
      }
      case r: Route.Static => {
      }
    }

    val staticRoutes = all.flatMap {
      case r: Route.Dynamic => None
      case r: Route.Static => Some(r)
    }

    val staticRouteMap = Map(
      staticRoutes.map { ir =>
        (routeKey(ir.method, ir.path) -> ir)
      }: _*
    )

    Logger.info(s"Index: staticRoutes[${staticRouteMap.size}] dynamicRoutes[${dynamicRoutes.size}]")

    (staticRouteMap, dynamicRouteMap.toMap)
  }

  final def resolve(method: String, path: String): Option[Route] = {
    staticRouteMap.get(routeKey(method, path)) match {
      case None => {
        dynamicRoutes.get(method.toUpperCase) match {
          case None => {
            None
          }
          case Some(routes) => {
            routes.find(_.matches(method.toUpperCase, path.toLowerCase.trim))
          }
        }
      }
      case Some(ir) => {
        Some(ir)
      }
    }
  }

  private[this] def routeKey(method: String, path: String): String = {
    s"$method:$path".toLowerCase
  }
}
