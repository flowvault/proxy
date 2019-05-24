/**
 * Generated by API Builder - https://www.apibuilder.io
 * Service version: 0.6.40
 * apibuilder 0.14.75 app.apibuilder.io/flow/checkout-configuration/0.6.40/play_2_x_json
 */
package io.flow.checkout.configuration.v0.models {

  final case class CheckoutBehavior(
    shippingAddress: io.flow.checkout.configuration.v0.models.CheckoutBehaviorShippingAddress,
    shippingMethod: io.flow.checkout.configuration.v0.models.CheckoutBehaviorShippingMethod,
    customerInfo: io.flow.checkout.configuration.v0.models.CheckoutBehaviorCustomerInfo
  )

  final case class CheckoutBehaviorCustomerInfo(
    email: io.flow.checkout.configuration.v0.models.CheckoutBehaviorCustomerInfoEmail
  )

  final case class CheckoutBehaviorCustomerInfoEmail(
    prompt: io.flow.checkout.configuration.v0.models.CheckoutPromptBehavior = io.flow.checkout.configuration.v0.models.CheckoutPromptBehavior.Incomplete
  )

  final case class CheckoutBehaviorShippingAddress(
    prompt: io.flow.checkout.configuration.v0.models.CheckoutPromptBehavior
  )

  final case class CheckoutBehaviorShippingMethod(
    prompt: io.flow.checkout.configuration.v0.models.CheckoutShippingMethodPromptBehavior
  )

  final case class CheckoutConfiguration(
    id: String,
    behavior: io.flow.checkout.configuration.v0.models.CheckoutBehavior,
    settings: io.flow.checkout.configuration.v0.models.CheckoutSettings
  )

  final case class CheckoutConfigurationReference(
    id: String
  )

  /**
   * @param sessionPersistenceTimeout The session persistence timeout controls how long we persist data in checkout
   *        (e.g. shipping address) as users navigate between cart and checkout. If set to 1
   *        week, for example, then when a user starts a new checkout session, we will
   *        default their order to the last checkout they had in their session, pre
   *        populating data like the shipping address.
   * @param sessionPersistenceAttributeKeys If specified, we require an opt in with the provided attribute key in order to
   *        enable session persistence
   */
  final case class CheckoutSettings(
    sessionPersistenceTimeout: _root_.scala.Option[io.flow.common.v0.models.Duration] = None,
    sessionPersistenceAttributeKeys: _root_.scala.Option[Seq[String]] = None
  )

  /**
   * The checkout prompt behavior specifies when or if a user should by prompted to
   * provided certain information. For example, if the shipping info prompt is
   * 'incomplete', this means that as a user enters Checkout, we will present the
   * form to specify a shipping address only if we do not have a complete shipping
   * address. If the shipping address was already present (e.g. for a repeat
   * customer), this step of Checkout can be skipped.
   */
  sealed trait CheckoutPromptBehavior extends _root_.scala.Product with _root_.scala.Serializable

  object CheckoutPromptBehavior {

    case object Always extends CheckoutPromptBehavior { override def toString = "always" }
    /**
     * Only prompt if the order does not have a complete shipping address field present
     */
    case object Incomplete extends CheckoutPromptBehavior { override def toString = "incomplete" }

    /**
     * UNDEFINED captures values that are sent either in error or
     * that were added by the server after this library was
     * generated. We want to make it easy and obvious for users of
     * this library to handle this case gracefully.
     *
     * We use all CAPS for the variable name to avoid collisions
     * with the camel cased values above.
     */
    final case class UNDEFINED(override val toString: String) extends CheckoutPromptBehavior

    /**
     * all returns a list of all the valid, known values. We use
     * lower case to avoid collisions with the camel cased values
     * above.
     */
    val all: scala.List[CheckoutPromptBehavior] = scala.List(Always, Incomplete)

    private[this]
    val byName: Map[String, CheckoutPromptBehavior] = all.map(x => x.toString.toLowerCase -> x).toMap

    def apply(value: String): CheckoutPromptBehavior = fromString(value).getOrElse(UNDEFINED(value))

    def fromString(value: String): _root_.scala.Option[CheckoutPromptBehavior] = byName.get(value.toLowerCase)

  }

  /**
   * Controls when we display the 'choose your shipping method' step of checkout
   */
  sealed trait CheckoutShippingMethodPromptBehavior extends _root_.scala.Product with _root_.scala.Serializable

  object CheckoutShippingMethodPromptBehavior {

    case object Always extends CheckoutShippingMethodPromptBehavior { override def toString = "always" }
    /**
     * Prompt the user to select a shipping method only if there are more than 1
     * shipping method options available for this order
     */
    case object Multiple extends CheckoutShippingMethodPromptBehavior { override def toString = "multiple" }

    /**
     * UNDEFINED captures values that are sent either in error or
     * that were added by the server after this library was
     * generated. We want to make it easy and obvious for users of
     * this library to handle this case gracefully.
     *
     * We use all CAPS for the variable name to avoid collisions
     * with the camel cased values above.
     */
    final case class UNDEFINED(override val toString: String) extends CheckoutShippingMethodPromptBehavior

    /**
     * all returns a list of all the valid, known values. We use
     * lower case to avoid collisions with the camel cased values
     * above.
     */
    val all: scala.List[CheckoutShippingMethodPromptBehavior] = scala.List(Always, Multiple)

    private[this]
    val byName: Map[String, CheckoutShippingMethodPromptBehavior] = all.map(x => x.toString.toLowerCase -> x).toMap

    def apply(value: String): CheckoutShippingMethodPromptBehavior = fromString(value).getOrElse(UNDEFINED(value))

    def fromString(value: String): _root_.scala.Option[CheckoutShippingMethodPromptBehavior] = byName.get(value.toLowerCase)

  }

}

package io.flow.checkout.configuration.v0.models {

  package object json {
    import play.api.libs.json.__
    import play.api.libs.json.JsString
    import play.api.libs.json.Writes
    import play.api.libs.functional.syntax._
    import io.flow.checkout.configuration.v0.models.json._
    import io.flow.common.v0.models.json._
    import io.flow.error.v0.models.json._
    import io.flow.permission.v0.models.json._

    private[v0] implicit val jsonReadsUUID = __.read[String].map { str =>
      _root_.java.util.UUID.fromString(str)
    }

    private[v0] implicit val jsonWritesUUID = new Writes[_root_.java.util.UUID] {
      def writes(x: _root_.java.util.UUID) = JsString(x.toString)
    }

    private[v0] implicit val jsonReadsJodaDateTime = __.read[String].map { str =>
      _root_.org.joda.time.format.ISODateTimeFormat.dateTimeParser.parseDateTime(str)
    }

    private[v0] implicit val jsonWritesJodaDateTime = new Writes[_root_.org.joda.time.DateTime] {
      def writes(x: _root_.org.joda.time.DateTime) = {
        JsString(_root_.org.joda.time.format.ISODateTimeFormat.dateTime.print(x))
      }
    }

    private[v0] implicit val jsonReadsJodaLocalDate = __.read[String].map { str =>
      _root_.org.joda.time.format.ISODateTimeFormat.dateTimeParser.parseLocalDate(str)
    }

    private[v0] implicit val jsonWritesJodaLocalDate = new Writes[_root_.org.joda.time.LocalDate] {
      def writes(x: _root_.org.joda.time.LocalDate) = {
        JsString(_root_.org.joda.time.format.ISODateTimeFormat.date.print(x))
      }
    }

    implicit val jsonReadsCheckoutConfigurationCheckoutPromptBehavior = new play.api.libs.json.Reads[io.flow.checkout.configuration.v0.models.CheckoutPromptBehavior] {
      def reads(js: play.api.libs.json.JsValue): play.api.libs.json.JsResult[io.flow.checkout.configuration.v0.models.CheckoutPromptBehavior] = {
        js match {
          case v: play.api.libs.json.JsString => play.api.libs.json.JsSuccess(io.flow.checkout.configuration.v0.models.CheckoutPromptBehavior(v.value))
          case _ => {
            (js \ "value").validate[String] match {
              case play.api.libs.json.JsSuccess(v, _) => play.api.libs.json.JsSuccess(io.flow.checkout.configuration.v0.models.CheckoutPromptBehavior(v))
              case err: play.api.libs.json.JsError => err
            }
          }
        }
      }
    }

    def jsonWritesCheckoutConfigurationCheckoutPromptBehavior(obj: io.flow.checkout.configuration.v0.models.CheckoutPromptBehavior) = {
      play.api.libs.json.JsString(obj.toString)
    }

    def jsObjectCheckoutPromptBehavior(obj: io.flow.checkout.configuration.v0.models.CheckoutPromptBehavior) = {
      play.api.libs.json.Json.obj("value" -> play.api.libs.json.JsString(obj.toString))
    }

    implicit def jsonWritesCheckoutConfigurationCheckoutPromptBehavior: play.api.libs.json.Writes[CheckoutPromptBehavior] = {
      new play.api.libs.json.Writes[io.flow.checkout.configuration.v0.models.CheckoutPromptBehavior] {
        def writes(obj: io.flow.checkout.configuration.v0.models.CheckoutPromptBehavior) = {
          jsonWritesCheckoutConfigurationCheckoutPromptBehavior(obj)
        }
      }
    }

    implicit val jsonReadsCheckoutConfigurationCheckoutShippingMethodPromptBehavior = new play.api.libs.json.Reads[io.flow.checkout.configuration.v0.models.CheckoutShippingMethodPromptBehavior] {
      def reads(js: play.api.libs.json.JsValue): play.api.libs.json.JsResult[io.flow.checkout.configuration.v0.models.CheckoutShippingMethodPromptBehavior] = {
        js match {
          case v: play.api.libs.json.JsString => play.api.libs.json.JsSuccess(io.flow.checkout.configuration.v0.models.CheckoutShippingMethodPromptBehavior(v.value))
          case _ => {
            (js \ "value").validate[String] match {
              case play.api.libs.json.JsSuccess(v, _) => play.api.libs.json.JsSuccess(io.flow.checkout.configuration.v0.models.CheckoutShippingMethodPromptBehavior(v))
              case err: play.api.libs.json.JsError => err
            }
          }
        }
      }
    }

    def jsonWritesCheckoutConfigurationCheckoutShippingMethodPromptBehavior(obj: io.flow.checkout.configuration.v0.models.CheckoutShippingMethodPromptBehavior) = {
      play.api.libs.json.JsString(obj.toString)
    }

    def jsObjectCheckoutShippingMethodPromptBehavior(obj: io.flow.checkout.configuration.v0.models.CheckoutShippingMethodPromptBehavior) = {
      play.api.libs.json.Json.obj("value" -> play.api.libs.json.JsString(obj.toString))
    }

    implicit def jsonWritesCheckoutConfigurationCheckoutShippingMethodPromptBehavior: play.api.libs.json.Writes[CheckoutShippingMethodPromptBehavior] = {
      new play.api.libs.json.Writes[io.flow.checkout.configuration.v0.models.CheckoutShippingMethodPromptBehavior] {
        def writes(obj: io.flow.checkout.configuration.v0.models.CheckoutShippingMethodPromptBehavior) = {
          jsonWritesCheckoutConfigurationCheckoutShippingMethodPromptBehavior(obj)
        }
      }
    }

    implicit def jsonReadsCheckoutConfigurationCheckoutBehavior: play.api.libs.json.Reads[CheckoutBehavior] = {
      for {
        shippingAddress <- (__ \ "shipping_address").read[io.flow.checkout.configuration.v0.models.CheckoutBehaviorShippingAddress]
        shippingMethod <- (__ \ "shipping_method").read[io.flow.checkout.configuration.v0.models.CheckoutBehaviorShippingMethod]
        customerInfo <- (__ \ "customer_info").read[io.flow.checkout.configuration.v0.models.CheckoutBehaviorCustomerInfo]
      } yield CheckoutBehavior(shippingAddress, shippingMethod, customerInfo)
    }

    def jsObjectCheckoutBehavior(obj: io.flow.checkout.configuration.v0.models.CheckoutBehavior): play.api.libs.json.JsObject = {
      play.api.libs.json.Json.obj(
        "shipping_address" -> jsObjectCheckoutBehaviorShippingAddress(obj.shippingAddress),
        "shipping_method" -> jsObjectCheckoutBehaviorShippingMethod(obj.shippingMethod),
        "customer_info" -> jsObjectCheckoutBehaviorCustomerInfo(obj.customerInfo)
      )
    }

    implicit def jsonWritesCheckoutConfigurationCheckoutBehavior: play.api.libs.json.Writes[CheckoutBehavior] = {
      new play.api.libs.json.Writes[io.flow.checkout.configuration.v0.models.CheckoutBehavior] {
        def writes(obj: io.flow.checkout.configuration.v0.models.CheckoutBehavior) = {
          jsObjectCheckoutBehavior(obj)
        }
      }
    }

    implicit def jsonReadsCheckoutConfigurationCheckoutBehaviorCustomerInfo: play.api.libs.json.Reads[CheckoutBehaviorCustomerInfo] = {
      (__ \ "email").read[io.flow.checkout.configuration.v0.models.CheckoutBehaviorCustomerInfoEmail].map { x => new CheckoutBehaviorCustomerInfo(email = x) }
    }

    def jsObjectCheckoutBehaviorCustomerInfo(obj: io.flow.checkout.configuration.v0.models.CheckoutBehaviorCustomerInfo): play.api.libs.json.JsObject = {
      play.api.libs.json.Json.obj(
        "email" -> jsObjectCheckoutBehaviorCustomerInfoEmail(obj.email)
      )
    }

    implicit def jsonWritesCheckoutConfigurationCheckoutBehaviorCustomerInfo: play.api.libs.json.Writes[CheckoutBehaviorCustomerInfo] = {
      new play.api.libs.json.Writes[io.flow.checkout.configuration.v0.models.CheckoutBehaviorCustomerInfo] {
        def writes(obj: io.flow.checkout.configuration.v0.models.CheckoutBehaviorCustomerInfo) = {
          jsObjectCheckoutBehaviorCustomerInfo(obj)
        }
      }
    }

    implicit def jsonReadsCheckoutConfigurationCheckoutBehaviorCustomerInfoEmail: play.api.libs.json.Reads[CheckoutBehaviorCustomerInfoEmail] = {
      (__ \ "prompt").read[io.flow.checkout.configuration.v0.models.CheckoutPromptBehavior].map { x => new CheckoutBehaviorCustomerInfoEmail(prompt = x) }
    }

    def jsObjectCheckoutBehaviorCustomerInfoEmail(obj: io.flow.checkout.configuration.v0.models.CheckoutBehaviorCustomerInfoEmail): play.api.libs.json.JsObject = {
      play.api.libs.json.Json.obj(
        "prompt" -> play.api.libs.json.JsString(obj.prompt.toString)
      )
    }

    implicit def jsonWritesCheckoutConfigurationCheckoutBehaviorCustomerInfoEmail: play.api.libs.json.Writes[CheckoutBehaviorCustomerInfoEmail] = {
      new play.api.libs.json.Writes[io.flow.checkout.configuration.v0.models.CheckoutBehaviorCustomerInfoEmail] {
        def writes(obj: io.flow.checkout.configuration.v0.models.CheckoutBehaviorCustomerInfoEmail) = {
          jsObjectCheckoutBehaviorCustomerInfoEmail(obj)
        }
      }
    }

    implicit def jsonReadsCheckoutConfigurationCheckoutBehaviorShippingAddress: play.api.libs.json.Reads[CheckoutBehaviorShippingAddress] = {
      (__ \ "prompt").read[io.flow.checkout.configuration.v0.models.CheckoutPromptBehavior].map { x => new CheckoutBehaviorShippingAddress(prompt = x) }
    }

    def jsObjectCheckoutBehaviorShippingAddress(obj: io.flow.checkout.configuration.v0.models.CheckoutBehaviorShippingAddress): play.api.libs.json.JsObject = {
      play.api.libs.json.Json.obj(
        "prompt" -> play.api.libs.json.JsString(obj.prompt.toString)
      )
    }

    implicit def jsonWritesCheckoutConfigurationCheckoutBehaviorShippingAddress: play.api.libs.json.Writes[CheckoutBehaviorShippingAddress] = {
      new play.api.libs.json.Writes[io.flow.checkout.configuration.v0.models.CheckoutBehaviorShippingAddress] {
        def writes(obj: io.flow.checkout.configuration.v0.models.CheckoutBehaviorShippingAddress) = {
          jsObjectCheckoutBehaviorShippingAddress(obj)
        }
      }
    }

    implicit def jsonReadsCheckoutConfigurationCheckoutBehaviorShippingMethod: play.api.libs.json.Reads[CheckoutBehaviorShippingMethod] = {
      (__ \ "prompt").read[io.flow.checkout.configuration.v0.models.CheckoutShippingMethodPromptBehavior].map { x => new CheckoutBehaviorShippingMethod(prompt = x) }
    }

    def jsObjectCheckoutBehaviorShippingMethod(obj: io.flow.checkout.configuration.v0.models.CheckoutBehaviorShippingMethod): play.api.libs.json.JsObject = {
      play.api.libs.json.Json.obj(
        "prompt" -> play.api.libs.json.JsString(obj.prompt.toString)
      )
    }

    implicit def jsonWritesCheckoutConfigurationCheckoutBehaviorShippingMethod: play.api.libs.json.Writes[CheckoutBehaviorShippingMethod] = {
      new play.api.libs.json.Writes[io.flow.checkout.configuration.v0.models.CheckoutBehaviorShippingMethod] {
        def writes(obj: io.flow.checkout.configuration.v0.models.CheckoutBehaviorShippingMethod) = {
          jsObjectCheckoutBehaviorShippingMethod(obj)
        }
      }
    }

    implicit def jsonReadsCheckoutConfigurationCheckoutConfiguration: play.api.libs.json.Reads[CheckoutConfiguration] = {
      for {
        id <- (__ \ "id").read[String]
        behavior <- (__ \ "behavior").read[io.flow.checkout.configuration.v0.models.CheckoutBehavior]
        settings <- (__ \ "settings").read[io.flow.checkout.configuration.v0.models.CheckoutSettings]
      } yield CheckoutConfiguration(id, behavior, settings)
    }

    def jsObjectCheckoutConfiguration(obj: io.flow.checkout.configuration.v0.models.CheckoutConfiguration): play.api.libs.json.JsObject = {
      play.api.libs.json.Json.obj(
        "id" -> play.api.libs.json.JsString(obj.id),
        "behavior" -> jsObjectCheckoutBehavior(obj.behavior),
        "settings" -> jsObjectCheckoutSettings(obj.settings)
      )
    }

    implicit def jsonWritesCheckoutConfigurationCheckoutConfiguration: play.api.libs.json.Writes[CheckoutConfiguration] = {
      new play.api.libs.json.Writes[io.flow.checkout.configuration.v0.models.CheckoutConfiguration] {
        def writes(obj: io.flow.checkout.configuration.v0.models.CheckoutConfiguration) = {
          jsObjectCheckoutConfiguration(obj)
        }
      }
    }

    implicit def jsonReadsCheckoutConfigurationCheckoutConfigurationReference: play.api.libs.json.Reads[CheckoutConfigurationReference] = {
      (__ \ "id").read[String].map { x => new CheckoutConfigurationReference(id = x) }
    }

    def jsObjectCheckoutConfigurationReference(obj: io.flow.checkout.configuration.v0.models.CheckoutConfigurationReference): play.api.libs.json.JsObject = {
      play.api.libs.json.Json.obj(
        "id" -> play.api.libs.json.JsString(obj.id)
      )
    }

    implicit def jsonWritesCheckoutConfigurationCheckoutConfigurationReference: play.api.libs.json.Writes[CheckoutConfigurationReference] = {
      new play.api.libs.json.Writes[io.flow.checkout.configuration.v0.models.CheckoutConfigurationReference] {
        def writes(obj: io.flow.checkout.configuration.v0.models.CheckoutConfigurationReference) = {
          jsObjectCheckoutConfigurationReference(obj)
        }
      }
    }

    implicit def jsonReadsCheckoutConfigurationCheckoutSettings: play.api.libs.json.Reads[CheckoutSettings] = {
      for {
        sessionPersistenceTimeout <- (__ \ "session_persistence_timeout").readNullable[io.flow.common.v0.models.Duration]
        sessionPersistenceAttributeKeys <- (__ \ "session_persistence_attribute_keys").readNullable[Seq[String]]
      } yield CheckoutSettings(sessionPersistenceTimeout, sessionPersistenceAttributeKeys)
    }

    def jsObjectCheckoutSettings(obj: io.flow.checkout.configuration.v0.models.CheckoutSettings): play.api.libs.json.JsObject = {
      (obj.sessionPersistenceTimeout match {
        case None => play.api.libs.json.Json.obj()
        case Some(x) => play.api.libs.json.Json.obj("session_persistence_timeout" -> io.flow.common.v0.models.json.jsObjectDuration(x))
      }) ++
      (obj.sessionPersistenceAttributeKeys match {
        case None => play.api.libs.json.Json.obj()
        case Some(x) => play.api.libs.json.Json.obj("session_persistence_attribute_keys" -> play.api.libs.json.Json.toJson(x))
      })
    }

    implicit def jsonWritesCheckoutConfigurationCheckoutSettings: play.api.libs.json.Writes[CheckoutSettings] = {
      new play.api.libs.json.Writes[io.flow.checkout.configuration.v0.models.CheckoutSettings] {
        def writes(obj: io.flow.checkout.configuration.v0.models.CheckoutSettings) = {
          jsObjectCheckoutSettings(obj)
        }
      }
    }
  }
}

package io.flow.checkout.configuration.v0 {

  object Bindables {

    import play.api.mvc.{PathBindable, QueryStringBindable}

    // import models directly for backwards compatibility with prior versions of the generator
    import Core._
    import Models._

    object Core {
      implicit def pathBindableDateTimeIso8601(implicit stringBinder: QueryStringBindable[String]): PathBindable[_root_.org.joda.time.DateTime] = ApibuilderPathBindable(ApibuilderTypes.dateTimeIso8601)
      implicit def queryStringBindableDateTimeIso8601(implicit stringBinder: QueryStringBindable[String]): QueryStringBindable[_root_.org.joda.time.DateTime] = ApibuilderQueryStringBindable(ApibuilderTypes.dateTimeIso8601)

      implicit def pathBindableDateIso8601(implicit stringBinder: QueryStringBindable[String]): PathBindable[_root_.org.joda.time.LocalDate] = ApibuilderPathBindable(ApibuilderTypes.dateIso8601)
      implicit def queryStringBindableDateIso8601(implicit stringBinder: QueryStringBindable[String]): QueryStringBindable[_root_.org.joda.time.LocalDate] = ApibuilderQueryStringBindable(ApibuilderTypes.dateIso8601)
    }

    object Models {
      import io.flow.checkout.configuration.v0.models._

      val checkoutPromptBehaviorConverter: ApibuilderTypeConverter[io.flow.checkout.configuration.v0.models.CheckoutPromptBehavior] = new ApibuilderTypeConverter[io.flow.checkout.configuration.v0.models.CheckoutPromptBehavior] {
        override def convert(value: String): io.flow.checkout.configuration.v0.models.CheckoutPromptBehavior = io.flow.checkout.configuration.v0.models.CheckoutPromptBehavior(value)
        override def convert(value: io.flow.checkout.configuration.v0.models.CheckoutPromptBehavior): String = value.toString
        override def example: io.flow.checkout.configuration.v0.models.CheckoutPromptBehavior = io.flow.checkout.configuration.v0.models.CheckoutPromptBehavior.Always
        override def validValues: Seq[io.flow.checkout.configuration.v0.models.CheckoutPromptBehavior] = io.flow.checkout.configuration.v0.models.CheckoutPromptBehavior.all
      }
      implicit def pathBindableCheckoutPromptBehavior(implicit stringBinder: QueryStringBindable[String]): PathBindable[io.flow.checkout.configuration.v0.models.CheckoutPromptBehavior] = ApibuilderPathBindable(checkoutPromptBehaviorConverter)
      implicit def queryStringBindableCheckoutPromptBehavior(implicit stringBinder: QueryStringBindable[String]): QueryStringBindable[io.flow.checkout.configuration.v0.models.CheckoutPromptBehavior] = ApibuilderQueryStringBindable(checkoutPromptBehaviorConverter)

      val checkoutShippingMethodPromptBehaviorConverter: ApibuilderTypeConverter[io.flow.checkout.configuration.v0.models.CheckoutShippingMethodPromptBehavior] = new ApibuilderTypeConverter[io.flow.checkout.configuration.v0.models.CheckoutShippingMethodPromptBehavior] {
        override def convert(value: String): io.flow.checkout.configuration.v0.models.CheckoutShippingMethodPromptBehavior = io.flow.checkout.configuration.v0.models.CheckoutShippingMethodPromptBehavior(value)
        override def convert(value: io.flow.checkout.configuration.v0.models.CheckoutShippingMethodPromptBehavior): String = value.toString
        override def example: io.flow.checkout.configuration.v0.models.CheckoutShippingMethodPromptBehavior = io.flow.checkout.configuration.v0.models.CheckoutShippingMethodPromptBehavior.Always
        override def validValues: Seq[io.flow.checkout.configuration.v0.models.CheckoutShippingMethodPromptBehavior] = io.flow.checkout.configuration.v0.models.CheckoutShippingMethodPromptBehavior.all
      }
      implicit def pathBindableCheckoutShippingMethodPromptBehavior(implicit stringBinder: QueryStringBindable[String]): PathBindable[io.flow.checkout.configuration.v0.models.CheckoutShippingMethodPromptBehavior] = ApibuilderPathBindable(checkoutShippingMethodPromptBehaviorConverter)
      implicit def queryStringBindableCheckoutShippingMethodPromptBehavior(implicit stringBinder: QueryStringBindable[String]): QueryStringBindable[io.flow.checkout.configuration.v0.models.CheckoutShippingMethodPromptBehavior] = ApibuilderQueryStringBindable(checkoutShippingMethodPromptBehaviorConverter)
    }

    trait ApibuilderTypeConverter[T] {

      def convert(value: String): T

      def convert(value: T): String

      def example: T

      def validValues: Seq[T] = Nil

      def errorMessage(key: String, value: String, ex: java.lang.Exception): String = {
        val base = s"Invalid value '$value' for parameter '$key'. "
        validValues.toList match {
          case Nil => base + "Ex: " + convert(example)
          case values => base + ". Valid values are: " + values.mkString("'", "', '", "'")
        }
      }
    }

    object ApibuilderTypes {
      val dateTimeIso8601: ApibuilderTypeConverter[_root_.org.joda.time.DateTime] = new ApibuilderTypeConverter[_root_.org.joda.time.DateTime] {
        override def convert(value: String): _root_.org.joda.time.DateTime = _root_.org.joda.time.format.ISODateTimeFormat.dateTimeParser.parseDateTime(value)
        override def convert(value: _root_.org.joda.time.DateTime): String = _root_.org.joda.time.format.ISODateTimeFormat.dateTime.print(value)
        override def example: _root_.org.joda.time.DateTime = _root_.org.joda.time.DateTime.now
      }

      val dateIso8601: ApibuilderTypeConverter[_root_.org.joda.time.LocalDate] = new ApibuilderTypeConverter[_root_.org.joda.time.LocalDate] {
        override def convert(value: String): _root_.org.joda.time.LocalDate = _root_.org.joda.time.format.ISODateTimeFormat.dateTimeParser.parseLocalDate(value)
        override def convert(value: _root_.org.joda.time.LocalDate): String = _root_.org.joda.time.format.ISODateTimeFormat.date.print(value)
        override def example: _root_.org.joda.time.LocalDate = _root_.org.joda.time.LocalDate.now
      }
    }

    final case class ApibuilderQueryStringBindable[T](
      converters: ApibuilderTypeConverter[T]
    ) extends QueryStringBindable[T] {

      override def bind(key: String, params: Map[String, Seq[String]]): _root_.scala.Option[_root_.scala.Either[String, T]] = {
        params.getOrElse(key, Nil).headOption.map { v =>
          try {
            Right(
              converters.convert(v)
            )
          } catch {
            case ex: java.lang.Exception => Left(
              converters.errorMessage(key, v, ex)
            )
          }
        }
      }

      override def unbind(key: String, value: T): String = {
        s"$key=${converters.convert(value)}"
      }
    }

    final case class ApibuilderPathBindable[T](
      converters: ApibuilderTypeConverter[T]
    ) extends PathBindable[T] {

      override def bind(key: String, value: String): _root_.scala.Either[String, T] = {
        try {
          Right(
            converters.convert(value)
          )
        } catch {
          case ex: java.lang.Exception => Left(
            converters.errorMessage(key, value, ex)
          )
        }
      }

      override def unbind(key: String, value: T): String = {
        converters.convert(value)
      }
    }

  }

}