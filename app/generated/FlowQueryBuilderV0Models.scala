/**
 * Generated by API Builder - https://www.apibuilder.io
 * Service version: 0.7.58
 * apibuilder 0.14.93 app.apibuilder.io/flow/query-builder/latest/play_2_x_json
 */
package io.flow.query.builder.v0.models {

  sealed trait AvailableFilter extends _root_.scala.Product with _root_.scala.Serializable

  /**
   * Defines the valid discriminator values for the type AvailableFilter
   */
  sealed trait AvailableFilterDiscriminator extends _root_.scala.Product with _root_.scala.Serializable

  object AvailableFilterDiscriminator {

    case object AvailableFilterStructured extends AvailableFilterDiscriminator { override def toString = "structured" }
    case object AvailableFilterUnstructured extends AvailableFilterDiscriminator { override def toString = "unstructured" }

    final case class UNDEFINED(override val toString: String) extends AvailableFilterDiscriminator

    val all: scala.List[AvailableFilterDiscriminator] = scala.List(AvailableFilterStructured, AvailableFilterUnstructured)

    private[this] val byName: Map[String, AvailableFilterDiscriminator] = all.map(x => x.toString.toLowerCase -> x).toMap

    def apply(value: String): AvailableFilterDiscriminator = fromString(value).getOrElse(UNDEFINED(value))

    def fromString(value: String): _root_.scala.Option[AvailableFilterDiscriminator] = byName.get(value.toLowerCase)

  }

  sealed trait QueryBuilderForm extends _root_.scala.Product with _root_.scala.Serializable

  /**
   * Defines the valid discriminator values for the type QueryBuilderForm
   */
  sealed trait QueryBuilderFormDiscriminator extends _root_.scala.Product with _root_.scala.Serializable

  object QueryBuilderFormDiscriminator {

    case object QueryBuilderFilterForm extends QueryBuilderFormDiscriminator { override def toString = "filter" }
    case object QueryBuilderQueryForm extends QueryBuilderFormDiscriminator { override def toString = "query" }

    final case class UNDEFINED(override val toString: String) extends QueryBuilderFormDiscriminator

    val all: scala.List[QueryBuilderFormDiscriminator] = scala.List(QueryBuilderFilterForm, QueryBuilderQueryForm)

    private[this] val byName: Map[String, QueryBuilderFormDiscriminator] = all.map(x => x.toString.toLowerCase -> x).toMap

    def apply(value: String): QueryBuilderFormDiscriminator = fromString(value).getOrElse(UNDEFINED(value))

    def fromString(value: String): _root_.scala.Option[QueryBuilderFormDiscriminator] = byName.get(value.toLowerCase)

  }

  sealed trait QueryFilter extends _root_.scala.Product with _root_.scala.Serializable

  /**
   * Defines the valid discriminator values for the type QueryFilter
   */
  sealed trait QueryFilterDiscriminator extends _root_.scala.Product with _root_.scala.Serializable

  object QueryFilterDiscriminator {

    case object QueryFilterStructured extends QueryFilterDiscriminator { override def toString = "structured" }
    case object QueryFilterUnstructured extends QueryFilterDiscriminator { override def toString = "unstructured" }

    final case class UNDEFINED(override val toString: String) extends QueryFilterDiscriminator

    val all: scala.List[QueryFilterDiscriminator] = scala.List(QueryFilterStructured, QueryFilterUnstructured)

    private[this] val byName: Map[String, QueryFilterDiscriminator] = all.map(x => x.toString.toLowerCase -> x).toMap

    def apply(value: String): QueryFilterDiscriminator = fromString(value).getOrElse(UNDEFINED(value))

    def fromString(value: String): _root_.scala.Option[QueryFilterDiscriminator] = byName.get(value.toLowerCase)

  }

  sealed trait QueryFilterForm extends _root_.scala.Product with _root_.scala.Serializable

  /**
   * Defines the valid discriminator values for the type QueryFilterForm
   */
  sealed trait QueryFilterFormDiscriminator extends _root_.scala.Product with _root_.scala.Serializable

  object QueryFilterFormDiscriminator {

    case object QueryFilterStructuredForm extends QueryFilterFormDiscriminator { override def toString = "structured" }
    case object QueryFilterUnstructuredForm extends QueryFilterFormDiscriminator { override def toString = "unstructured" }

    final case class UNDEFINED(override val toString: String) extends QueryFilterFormDiscriminator

    val all: scala.List[QueryFilterFormDiscriminator] = scala.List(QueryFilterStructuredForm, QueryFilterUnstructuredForm)

    private[this] val byName: Map[String, QueryFilterFormDiscriminator] = all.map(x => x.toString.toLowerCase -> x).toMap

    def apply(value: String): QueryFilterFormDiscriminator = fromString(value).getOrElse(UNDEFINED(value))

    def fromString(value: String): _root_.scala.Option[QueryFilterFormDiscriminator] = byName.get(value.toLowerCase)

  }

  /**
   * @param validValues If specified, the list of valid string values that will be accepted
   * @param placeholder Example placeholder text
   */
  final case class AvailableFilterStructured(
    field: String,
    operators: Seq[String],
    format: io.flow.query.builder.v0.models.AvailableFilterFormat,
    validValues: _root_.scala.Option[Seq[String]] = None,
    placeholder: _root_.scala.Option[String] = None
  ) extends AvailableFilter

  /**
   * @param placeholder Example placeholder text
   */
  final case class AvailableFilterUnstructured(
    placeholder: _root_.scala.Option[String] = None
  ) extends AvailableFilter

  /**
   * The query model is used to present a query to a user, containing both the raw
   * query as well as the filter representation of the query.
   */
  final case class Query(
    q: String,
    filters: Seq[io.flow.query.builder.v0.models.QueryFilter]
  )

  /**
   * The query builder model is used to build a query using structured filters. The
   * end result is a single 'q' string - e.g. 'category:jewelry and brand:Flow'
   */
  final case class QueryBuilder(
    q: String,
    filters: Seq[io.flow.query.builder.v0.models.QueryFilter],
    available: Seq[io.flow.query.builder.v0.models.AvailableFilter]
  )

  final case class QueryBuilderFilterForm(
    filters: Seq[io.flow.query.builder.v0.models.QueryFilterForm]
  ) extends QueryBuilderForm

  final case class QueryBuilderQueryForm(
    q: String
  ) extends QueryBuilderForm

  /**
   * @param q A string representation of this query filter
   */
  final case class QueryFilterStructured(
    q: String,
    field: String,
    operator: String,
    values: Seq[String]
  ) extends QueryFilter

  final case class QueryFilterStructuredForm(
    field: String,
    operator: String,
    values: Seq[String]
  ) extends QueryFilterForm

  /**
   * @param q A keyword search query
   */
  final case class QueryFilterUnstructured(
    q: String
  ) extends QueryFilter

  final case class QueryFilterUnstructuredForm(
    q: String
  ) extends QueryFilterForm

  /**
   * Provides future compatibility in clients - in the future, when a type is added
   * to the union AvailableFilter, it will need to be handled in the client code.
   * This implementation will deserialize these future types as an instance of this
   * class.
   *
   * @param description Information about the type that we received that is undefined in this version of
   *        the client.
   */
  final case class AvailableFilterUndefinedType(
    description: String
  ) extends AvailableFilter

  /**
   * Provides future compatibility in clients - in the future, when a type is added
   * to the union QueryBuilderForm, it will need to be handled in the client code.
   * This implementation will deserialize these future types as an instance of this
   * class.
   *
   * @param description Information about the type that we received that is undefined in this version of
   *        the client.
   */
  final case class QueryBuilderFormUndefinedType(
    description: String
  ) extends QueryBuilderForm

  /**
   * Provides future compatibility in clients - in the future, when a type is added
   * to the union QueryFilter, it will need to be handled in the client code. This
   * implementation will deserialize these future types as an instance of this class.
   *
   * @param description Information about the type that we received that is undefined in this version of
   *        the client.
   */
  final case class QueryFilterUndefinedType(
    description: String
  ) extends QueryFilter

  /**
   * Provides future compatibility in clients - in the future, when a type is added
   * to the union QueryFilterForm, it will need to be handled in the client code.
   * This implementation will deserialize these future types as an instance of this
   * class.
   *
   * @param description Information about the type that we received that is undefined in this version of
   *        the client.
   */
  final case class QueryFilterFormUndefinedType(
    description: String
  ) extends QueryFilterForm

  /**
   * The Available Filter Format defines the type of data that is expected in a
   * single filter
   */
  sealed trait AvailableFilterFormat extends _root_.scala.Product with _root_.scala.Serializable

  object AvailableFilterFormat {

    case object Boolean extends AvailableFilterFormat { override def toString = "boolean" }
    /**
     * Expects date in format YYYY-MM-DD
     */
    case object Date extends AvailableFilterFormat { override def toString = "date" }
    case object Money extends AvailableFilterFormat { override def toString = "money" }
    case object Decimal extends AvailableFilterFormat { override def toString = "decimal" }
    case object String extends AvailableFilterFormat { override def toString = "string" }
    case object UnitOfLength extends AvailableFilterFormat { override def toString = "unit_of_length" }
    case object UnitOfMass extends AvailableFilterFormat { override def toString = "unit_of_mass" }

    /**
     * UNDEFINED captures values that are sent either in error or
     * that were added by the server after this library was
     * generated. We want to make it easy and obvious for users of
     * this library to handle this case gracefully.
     *
     * We use all CAPS for the variable name to avoid collisions
     * with the camel cased values above.
     */
    final case class UNDEFINED(override val toString: String) extends AvailableFilterFormat

    /**
     * all returns a list of all the valid, known values. We use
     * lower case to avoid collisions with the camel cased values
     * above.
     */
    val all: scala.List[AvailableFilterFormat] = scala.List(Boolean, Date, Money, Decimal, String, UnitOfLength, UnitOfMass)

    private[this]
    val byName: Map[String, AvailableFilterFormat] = all.map(x => x.toString.toLowerCase -> x).toMap

    def apply(value: String): AvailableFilterFormat = fromString(value).getOrElse(UNDEFINED(value))

    def fromString(value: String): _root_.scala.Option[AvailableFilterFormat] = byName.get(value.toLowerCase)

  }

}

package io.flow.query.builder.v0.models {

  package object json {
    import play.api.libs.json.__
    import play.api.libs.json.JsString
    import play.api.libs.json.Writes
    import play.api.libs.functional.syntax._
    import io.flow.query.builder.v0.models.json._

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

    implicit val jsonReadsQueryBuilderAvailableFilterFormat = new play.api.libs.json.Reads[io.flow.query.builder.v0.models.AvailableFilterFormat] {
      def reads(js: play.api.libs.json.JsValue): play.api.libs.json.JsResult[io.flow.query.builder.v0.models.AvailableFilterFormat] = {
        js match {
          case v: play.api.libs.json.JsString => play.api.libs.json.JsSuccess(io.flow.query.builder.v0.models.AvailableFilterFormat(v.value))
          case _ => {
            (js \ "value").validate[String] match {
              case play.api.libs.json.JsSuccess(v, _) => play.api.libs.json.JsSuccess(io.flow.query.builder.v0.models.AvailableFilterFormat(v))
              case err: play.api.libs.json.JsError =>
                (js \ "available_filter_format").validate[String] match {
                  case play.api.libs.json.JsSuccess(v, _) => play.api.libs.json.JsSuccess(io.flow.query.builder.v0.models.AvailableFilterFormat(v))
                  case err: play.api.libs.json.JsError => err
                }
            }
          }
        }
      }
    }

    def jsonWritesQueryBuilderAvailableFilterFormat(obj: io.flow.query.builder.v0.models.AvailableFilterFormat) = {
      play.api.libs.json.JsString(obj.toString)
    }

    def jsObjectAvailableFilterFormat(obj: io.flow.query.builder.v0.models.AvailableFilterFormat) = {
      play.api.libs.json.Json.obj("value" -> play.api.libs.json.JsString(obj.toString))
    }

    implicit def jsonWritesQueryBuilderAvailableFilterFormat: play.api.libs.json.Writes[AvailableFilterFormat] = {
      new play.api.libs.json.Writes[io.flow.query.builder.v0.models.AvailableFilterFormat] {
        def writes(obj: io.flow.query.builder.v0.models.AvailableFilterFormat) = {
          jsonWritesQueryBuilderAvailableFilterFormat(obj)
        }
      }
    }

    implicit def jsonReadsQueryBuilderAvailableFilterStructured: play.api.libs.json.Reads[AvailableFilterStructured] = {
      for {
        field <- (__ \ "field").read[String]
        operators <- (__ \ "operators").read[Seq[String]]
        format <- (__ \ "format").read[io.flow.query.builder.v0.models.AvailableFilterFormat]
        validValues <- (__ \ "valid_values").readNullable[Seq[String]]
        placeholder <- (__ \ "placeholder").readNullable[String]
      } yield AvailableFilterStructured(field, operators, format, validValues, placeholder)
    }

    def jsObjectAvailableFilterStructured(obj: io.flow.query.builder.v0.models.AvailableFilterStructured): play.api.libs.json.JsObject = {
      play.api.libs.json.Json.obj(
        "field" -> play.api.libs.json.JsString(obj.field),
        "operators" -> play.api.libs.json.Json.toJson(obj.operators),
        "format" -> play.api.libs.json.JsString(obj.format.toString)
      ) ++ (obj.validValues match {
        case None => play.api.libs.json.Json.obj()
        case Some(x) => play.api.libs.json.Json.obj("valid_values" -> play.api.libs.json.Json.toJson(x))
      }) ++
      (obj.placeholder match {
        case None => play.api.libs.json.Json.obj()
        case Some(x) => play.api.libs.json.Json.obj("placeholder" -> play.api.libs.json.JsString(x))
      }) ++ play.api.libs.json.Json.obj("discriminator" -> "structured")
    }

    implicit def jsonWritesQueryBuilderAvailableFilterStructured: play.api.libs.json.Writes[AvailableFilterStructured] = {
      new play.api.libs.json.Writes[io.flow.query.builder.v0.models.AvailableFilterStructured] {
        def writes(obj: io.flow.query.builder.v0.models.AvailableFilterStructured) = {
          jsObjectAvailableFilterStructured(obj)
        }
      }
    }

    implicit def jsonReadsQueryBuilderAvailableFilterUnstructured: play.api.libs.json.Reads[AvailableFilterUnstructured] = {
      (__ \ "placeholder").readNullable[String].map { x => new AvailableFilterUnstructured(placeholder = x) }
    }

    def jsObjectAvailableFilterUnstructured(obj: io.flow.query.builder.v0.models.AvailableFilterUnstructured): play.api.libs.json.JsObject = {
      (obj.placeholder match {
        case None => play.api.libs.json.Json.obj()
        case Some(x) => play.api.libs.json.Json.obj("placeholder" -> play.api.libs.json.JsString(x))
      }) ++ play.api.libs.json.Json.obj("discriminator" -> "unstructured")
    }

    implicit def jsonWritesQueryBuilderAvailableFilterUnstructured: play.api.libs.json.Writes[AvailableFilterUnstructured] = {
      new play.api.libs.json.Writes[io.flow.query.builder.v0.models.AvailableFilterUnstructured] {
        def writes(obj: io.flow.query.builder.v0.models.AvailableFilterUnstructured) = {
          jsObjectAvailableFilterUnstructured(obj)
        }
      }
    }

    implicit def jsonReadsQueryBuilderQuery: play.api.libs.json.Reads[Query] = {
      for {
        q <- (__ \ "q").read[String]
        filters <- (__ \ "filters").read[Seq[io.flow.query.builder.v0.models.QueryFilter]]
      } yield Query(q, filters)
    }

    def jsObjectQuery(obj: io.flow.query.builder.v0.models.Query): play.api.libs.json.JsObject = {
      play.api.libs.json.Json.obj(
        "q" -> play.api.libs.json.JsString(obj.q),
        "filters" -> play.api.libs.json.Json.toJson(obj.filters)
      )
    }

    implicit def jsonWritesQueryBuilderQuery: play.api.libs.json.Writes[Query] = {
      new play.api.libs.json.Writes[io.flow.query.builder.v0.models.Query] {
        def writes(obj: io.flow.query.builder.v0.models.Query) = {
          jsObjectQuery(obj)
        }
      }
    }

    implicit def jsonReadsQueryBuilderQueryBuilder: play.api.libs.json.Reads[QueryBuilder] = {
      for {
        q <- (__ \ "q").read[String]
        filters <- (__ \ "filters").read[Seq[io.flow.query.builder.v0.models.QueryFilter]]
        available <- (__ \ "available").read[Seq[io.flow.query.builder.v0.models.AvailableFilter]]
      } yield QueryBuilder(q, filters, available)
    }

    def jsObjectQueryBuilder(obj: io.flow.query.builder.v0.models.QueryBuilder): play.api.libs.json.JsObject = {
      play.api.libs.json.Json.obj(
        "q" -> play.api.libs.json.JsString(obj.q),
        "filters" -> play.api.libs.json.Json.toJson(obj.filters),
        "available" -> play.api.libs.json.Json.toJson(obj.available)
      )
    }

    implicit def jsonWritesQueryBuilderQueryBuilder: play.api.libs.json.Writes[QueryBuilder] = {
      new play.api.libs.json.Writes[io.flow.query.builder.v0.models.QueryBuilder] {
        def writes(obj: io.flow.query.builder.v0.models.QueryBuilder) = {
          jsObjectQueryBuilder(obj)
        }
      }
    }

    implicit def jsonReadsQueryBuilderQueryBuilderFilterForm: play.api.libs.json.Reads[QueryBuilderFilterForm] = {
      (__ \ "filters").read[Seq[io.flow.query.builder.v0.models.QueryFilterForm]].map { x => new QueryBuilderFilterForm(filters = x) }
    }

    def jsObjectQueryBuilderFilterForm(obj: io.flow.query.builder.v0.models.QueryBuilderFilterForm): play.api.libs.json.JsObject = {
      play.api.libs.json.Json.obj(
        "filters" -> play.api.libs.json.Json.toJson(obj.filters)
      ) ++ play.api.libs.json.Json.obj("discriminator" -> "filter")
    }

    implicit def jsonWritesQueryBuilderQueryBuilderFilterForm: play.api.libs.json.Writes[QueryBuilderFilterForm] = {
      new play.api.libs.json.Writes[io.flow.query.builder.v0.models.QueryBuilderFilterForm] {
        def writes(obj: io.flow.query.builder.v0.models.QueryBuilderFilterForm) = {
          jsObjectQueryBuilderFilterForm(obj)
        }
      }
    }

    implicit def jsonReadsQueryBuilderQueryBuilderQueryForm: play.api.libs.json.Reads[QueryBuilderQueryForm] = {
      (__ \ "q").read[String].map { x => new QueryBuilderQueryForm(q = x) }
    }

    def jsObjectQueryBuilderQueryForm(obj: io.flow.query.builder.v0.models.QueryBuilderQueryForm): play.api.libs.json.JsObject = {
      play.api.libs.json.Json.obj(
        "q" -> play.api.libs.json.JsString(obj.q)
      ) ++ play.api.libs.json.Json.obj("discriminator" -> "query")
    }

    implicit def jsonWritesQueryBuilderQueryBuilderQueryForm: play.api.libs.json.Writes[QueryBuilderQueryForm] = {
      new play.api.libs.json.Writes[io.flow.query.builder.v0.models.QueryBuilderQueryForm] {
        def writes(obj: io.flow.query.builder.v0.models.QueryBuilderQueryForm) = {
          jsObjectQueryBuilderQueryForm(obj)
        }
      }
    }

    implicit def jsonReadsQueryBuilderQueryFilterStructured: play.api.libs.json.Reads[QueryFilterStructured] = {
      for {
        q <- (__ \ "q").read[String]
        field <- (__ \ "field").read[String]
        operator <- (__ \ "operator").read[String]
        values <- (__ \ "values").read[Seq[String]]
      } yield QueryFilterStructured(q, field, operator, values)
    }

    def jsObjectQueryFilterStructured(obj: io.flow.query.builder.v0.models.QueryFilterStructured): play.api.libs.json.JsObject = {
      play.api.libs.json.Json.obj(
        "q" -> play.api.libs.json.JsString(obj.q),
        "field" -> play.api.libs.json.JsString(obj.field),
        "operator" -> play.api.libs.json.JsString(obj.operator),
        "values" -> play.api.libs.json.Json.toJson(obj.values)
      ) ++ play.api.libs.json.Json.obj("discriminator" -> "structured")
    }

    implicit def jsonWritesQueryBuilderQueryFilterStructured: play.api.libs.json.Writes[QueryFilterStructured] = {
      new play.api.libs.json.Writes[io.flow.query.builder.v0.models.QueryFilterStructured] {
        def writes(obj: io.flow.query.builder.v0.models.QueryFilterStructured) = {
          jsObjectQueryFilterStructured(obj)
        }
      }
    }

    implicit def jsonReadsQueryBuilderQueryFilterStructuredForm: play.api.libs.json.Reads[QueryFilterStructuredForm] = {
      for {
        field <- (__ \ "field").read[String]
        operator <- (__ \ "operator").read[String]
        values <- (__ \ "values").read[Seq[String]]
      } yield QueryFilterStructuredForm(field, operator, values)
    }

    def jsObjectQueryFilterStructuredForm(obj: io.flow.query.builder.v0.models.QueryFilterStructuredForm): play.api.libs.json.JsObject = {
      play.api.libs.json.Json.obj(
        "field" -> play.api.libs.json.JsString(obj.field),
        "operator" -> play.api.libs.json.JsString(obj.operator),
        "values" -> play.api.libs.json.Json.toJson(obj.values)
      ) ++ play.api.libs.json.Json.obj("discriminator" -> "structured")
    }

    implicit def jsonWritesQueryBuilderQueryFilterStructuredForm: play.api.libs.json.Writes[QueryFilterStructuredForm] = {
      new play.api.libs.json.Writes[io.flow.query.builder.v0.models.QueryFilterStructuredForm] {
        def writes(obj: io.flow.query.builder.v0.models.QueryFilterStructuredForm) = {
          jsObjectQueryFilterStructuredForm(obj)
        }
      }
    }

    implicit def jsonReadsQueryBuilderQueryFilterUnstructured: play.api.libs.json.Reads[QueryFilterUnstructured] = {
      (__ \ "q").read[String].map { x => new QueryFilterUnstructured(q = x) }
    }

    def jsObjectQueryFilterUnstructured(obj: io.flow.query.builder.v0.models.QueryFilterUnstructured): play.api.libs.json.JsObject = {
      play.api.libs.json.Json.obj(
        "q" -> play.api.libs.json.JsString(obj.q)
      ) ++ play.api.libs.json.Json.obj("discriminator" -> "unstructured")
    }

    implicit def jsonWritesQueryBuilderQueryFilterUnstructured: play.api.libs.json.Writes[QueryFilterUnstructured] = {
      new play.api.libs.json.Writes[io.flow.query.builder.v0.models.QueryFilterUnstructured] {
        def writes(obj: io.flow.query.builder.v0.models.QueryFilterUnstructured) = {
          jsObjectQueryFilterUnstructured(obj)
        }
      }
    }

    implicit def jsonReadsQueryBuilderQueryFilterUnstructuredForm: play.api.libs.json.Reads[QueryFilterUnstructuredForm] = {
      (__ \ "q").read[String].map { x => new QueryFilterUnstructuredForm(q = x) }
    }

    def jsObjectQueryFilterUnstructuredForm(obj: io.flow.query.builder.v0.models.QueryFilterUnstructuredForm): play.api.libs.json.JsObject = {
      play.api.libs.json.Json.obj(
        "q" -> play.api.libs.json.JsString(obj.q)
      ) ++ play.api.libs.json.Json.obj("discriminator" -> "unstructured")
    }

    implicit def jsonWritesQueryBuilderQueryFilterUnstructuredForm: play.api.libs.json.Writes[QueryFilterUnstructuredForm] = {
      new play.api.libs.json.Writes[io.flow.query.builder.v0.models.QueryFilterUnstructuredForm] {
        def writes(obj: io.flow.query.builder.v0.models.QueryFilterUnstructuredForm) = {
          jsObjectQueryFilterUnstructuredForm(obj)
        }
      }
    }

    implicit def jsonReadsQueryBuilderAvailableFilter: play.api.libs.json.Reads[AvailableFilter] = new play.api.libs.json.Reads[AvailableFilter] {
      def reads(js: play.api.libs.json.JsValue): play.api.libs.json.JsResult[AvailableFilter] = {
        (js \ "discriminator").asOpt[String].getOrElse { sys.error("Union[AvailableFilter] requires a discriminator named 'discriminator' - this field was not found in the Json Value") } match {
          case "structured" => js.validate[io.flow.query.builder.v0.models.AvailableFilterStructured]
          case "unstructured" => js.validate[io.flow.query.builder.v0.models.AvailableFilterUnstructured]
          case other => play.api.libs.json.JsSuccess(io.flow.query.builder.v0.models.AvailableFilterUndefinedType(other))
        }
      }
    }

    def jsObjectAvailableFilter(obj: io.flow.query.builder.v0.models.AvailableFilter): play.api.libs.json.JsObject = {
      obj match {
        case x: io.flow.query.builder.v0.models.AvailableFilterStructured => jsObjectAvailableFilterStructured(x)
        case x: io.flow.query.builder.v0.models.AvailableFilterUnstructured => jsObjectAvailableFilterUnstructured(x)
        case other => {
          sys.error(s"The type[${other.getClass.getName}] has no JSON writer")
        }
      }
    }

    implicit def jsonWritesQueryBuilderAvailableFilter: play.api.libs.json.Writes[AvailableFilter] = {
      new play.api.libs.json.Writes[io.flow.query.builder.v0.models.AvailableFilter] {
        def writes(obj: io.flow.query.builder.v0.models.AvailableFilter) = {
          jsObjectAvailableFilter(obj)
        }
      }
    }

    implicit def jsonReadsQueryBuilderQueryBuilderForm: play.api.libs.json.Reads[QueryBuilderForm] = new play.api.libs.json.Reads[QueryBuilderForm] {
      def reads(js: play.api.libs.json.JsValue): play.api.libs.json.JsResult[QueryBuilderForm] = {
        (js \ "discriminator").asOpt[String].getOrElse { sys.error("Union[QueryBuilderForm] requires a discriminator named 'discriminator' - this field was not found in the Json Value") } match {
          case "filter" => js.validate[io.flow.query.builder.v0.models.QueryBuilderFilterForm]
          case "query" => js.validate[io.flow.query.builder.v0.models.QueryBuilderQueryForm]
          case other => play.api.libs.json.JsSuccess(io.flow.query.builder.v0.models.QueryBuilderFormUndefinedType(other))
        }
      }
    }

    def jsObjectQueryBuilderForm(obj: io.flow.query.builder.v0.models.QueryBuilderForm): play.api.libs.json.JsObject = {
      obj match {
        case x: io.flow.query.builder.v0.models.QueryBuilderFilterForm => jsObjectQueryBuilderFilterForm(x)
        case x: io.flow.query.builder.v0.models.QueryBuilderQueryForm => jsObjectQueryBuilderQueryForm(x)
        case other => {
          sys.error(s"The type[${other.getClass.getName}] has no JSON writer")
        }
      }
    }

    implicit def jsonWritesQueryBuilderQueryBuilderForm: play.api.libs.json.Writes[QueryBuilderForm] = {
      new play.api.libs.json.Writes[io.flow.query.builder.v0.models.QueryBuilderForm] {
        def writes(obj: io.flow.query.builder.v0.models.QueryBuilderForm) = {
          jsObjectQueryBuilderForm(obj)
        }
      }
    }

    implicit def jsonReadsQueryBuilderQueryFilter: play.api.libs.json.Reads[QueryFilter] = new play.api.libs.json.Reads[QueryFilter] {
      def reads(js: play.api.libs.json.JsValue): play.api.libs.json.JsResult[QueryFilter] = {
        (js \ "discriminator").asOpt[String].getOrElse("structured") match {
          case "structured" => js.validate[io.flow.query.builder.v0.models.QueryFilterStructured]
          case "unstructured" => js.validate[io.flow.query.builder.v0.models.QueryFilterUnstructured]
          case other => play.api.libs.json.JsSuccess(io.flow.query.builder.v0.models.QueryFilterUndefinedType(other))
        }
      }
    }

    def jsObjectQueryFilter(obj: io.flow.query.builder.v0.models.QueryFilter): play.api.libs.json.JsObject = {
      obj match {
        case x: io.flow.query.builder.v0.models.QueryFilterStructured => jsObjectQueryFilterStructured(x)
        case x: io.flow.query.builder.v0.models.QueryFilterUnstructured => jsObjectQueryFilterUnstructured(x)
        case other => {
          sys.error(s"The type[${other.getClass.getName}] has no JSON writer")
        }
      }
    }

    implicit def jsonWritesQueryBuilderQueryFilter: play.api.libs.json.Writes[QueryFilter] = {
      new play.api.libs.json.Writes[io.flow.query.builder.v0.models.QueryFilter] {
        def writes(obj: io.flow.query.builder.v0.models.QueryFilter) = {
          jsObjectQueryFilter(obj)
        }
      }
    }

    implicit def jsonReadsQueryBuilderQueryFilterForm: play.api.libs.json.Reads[QueryFilterForm] = new play.api.libs.json.Reads[QueryFilterForm] {
      def reads(js: play.api.libs.json.JsValue): play.api.libs.json.JsResult[QueryFilterForm] = {
        (js \ "discriminator").asOpt[String].getOrElse("structured") match {
          case "structured" => js.validate[io.flow.query.builder.v0.models.QueryFilterStructuredForm]
          case "unstructured" => js.validate[io.flow.query.builder.v0.models.QueryFilterUnstructuredForm]
          case other => play.api.libs.json.JsSuccess(io.flow.query.builder.v0.models.QueryFilterFormUndefinedType(other))
        }
      }
    }

    def jsObjectQueryFilterForm(obj: io.flow.query.builder.v0.models.QueryFilterForm): play.api.libs.json.JsObject = {
      obj match {
        case x: io.flow.query.builder.v0.models.QueryFilterStructuredForm => jsObjectQueryFilterStructuredForm(x)
        case x: io.flow.query.builder.v0.models.QueryFilterUnstructuredForm => jsObjectQueryFilterUnstructuredForm(x)
        case other => {
          sys.error(s"The type[${other.getClass.getName}] has no JSON writer")
        }
      }
    }

    implicit def jsonWritesQueryBuilderQueryFilterForm: play.api.libs.json.Writes[QueryFilterForm] = {
      new play.api.libs.json.Writes[io.flow.query.builder.v0.models.QueryFilterForm] {
        def writes(obj: io.flow.query.builder.v0.models.QueryFilterForm) = {
          jsObjectQueryFilterForm(obj)
        }
      }
    }
  }
}

package io.flow.query.builder.v0 {

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
      import io.flow.query.builder.v0.models._

      val availableFilterFormatConverter: ApibuilderTypeConverter[io.flow.query.builder.v0.models.AvailableFilterFormat] = new ApibuilderTypeConverter[io.flow.query.builder.v0.models.AvailableFilterFormat] {
        override def convert(value: String): io.flow.query.builder.v0.models.AvailableFilterFormat = io.flow.query.builder.v0.models.AvailableFilterFormat(value)
        override def convert(value: io.flow.query.builder.v0.models.AvailableFilterFormat): String = value.toString
        override def example: io.flow.query.builder.v0.models.AvailableFilterFormat = io.flow.query.builder.v0.models.AvailableFilterFormat.Boolean
        override def validValues: Seq[io.flow.query.builder.v0.models.AvailableFilterFormat] = io.flow.query.builder.v0.models.AvailableFilterFormat.all
      }
      implicit def pathBindableAvailableFilterFormat(implicit stringBinder: QueryStringBindable[String]): PathBindable[io.flow.query.builder.v0.models.AvailableFilterFormat] = ApibuilderPathBindable(availableFilterFormatConverter)
      implicit def queryStringBindableAvailableFilterFormat(implicit stringBinder: QueryStringBindable[String]): QueryStringBindable[io.flow.query.builder.v0.models.AvailableFilterFormat] = ApibuilderQueryStringBindable(availableFilterFormatConverter)
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
