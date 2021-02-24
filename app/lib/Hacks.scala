package lib

object Hacks {

  def shouldConvertToLatencyMetric(method: String, path: String): Boolean = {
    logToMetricEndpoints.contains(s"${method} ${path}")
  }

  val logToMetricEndpoints = Seq(
    "GET /:organization/experiences/:experience_key/conversions/price",
    "GET /:organization/experiences/:experience_key/items/query",
    "GET /:organization/experiences/:experience_key/local/items",
    "GET /:organization/experiences/:experience_key/price/books",
    "GET /:organization/experiences/:key/items/:number/price",
    "GET /:organization/experiences/items",
    "GET /:organization/orders",
    "GET /:organization/orders/:number",
    "GET /:organization/orders/:number/allocations",
    "GET /:organization/orders/:number/status/fraud",
    "GET /:organization/orders/allocations/:number",
    "GET /:organization/orders/versions",
    "GET /:organization/price/books",
    "GET /:organization/refunds/summary/:order_number",
    "GET /:organization/shopify/localized/variants/experience/:experience_key/map",
    "GET /shopify/carts/:id.html",
    "POST /:organization/experiences/:experience_key/shopify/cart/conversions",
    "POST /:organization/experiences/items/filters/:filter",
    "POST /:organization/order/builders",
    "POST /:organization/orders",
    "POST /:organization/orders/submissions",
    "POST /bundles/checkout/:org/orders",
    "POST /checkouts",
    "PUT /:organization/order-identifiers/:identifier",
    "PUT /:organization/orders/:number",
    "PUT /:organization/orders/:number/destination",
    "PUT /:organization/orders/:number/submissions"
  )

}
