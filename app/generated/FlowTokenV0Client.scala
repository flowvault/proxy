/**
 * Generated by apidoc - http://www.apidoc.me
 * Service version: 0.1.29
 * apidoc:0.11.36 http://www.apidoc.me/flow/token/0.1.29/play_2_5_client
 */
package io.flow.token.v0.models {

  /**
   * The actual value of the API token. This is modeled as a separate resource as it
   * is fetched only on demand.
   */
  case class Cleartext(
    value: String
  )

  /**
   * All of the metadata associated with a given token.
   */
  case class Token(
    id: String,
    user: io.flow.common.v0.models.UserReference,
    partial: String,
    createdAt: _root_.org.joda.time.DateTime,
    description: _root_.scala.Option[String] = None
  )

  /**
   * Used to authenticate a given token.
   */
  case class TokenAuthenticationForm(
    token: String
  )

  /**
   * Used to create a new token for the user authorized by the request. You can only
   * create an API token for your own account.
   */
  case class TokenForm(
    description: _root_.scala.Option[String] = None
  )

  /**
   * Summary data for a given token
   */
  case class TokenReference(
    id: String,
    user: io.flow.common.v0.models.UserReference
  )

  case class TokenVersion(
    id: String,
    timestamp: _root_.org.joda.time.DateTime,
    `type`: io.flow.common.v0.models.ChangeType,
    token: io.flow.token.v0.models.Token
  )

  /**
   * Model used to report whether or not a given token is valid
   */
  case class Validation(
    status: String
  )

  /**
   * Defines the payload of a request to validate a token, with primary goal of
   * preventing the token from being included in an HTTP GET.
   */
  case class ValidationForm(
    token: String
  )

}

package io.flow.token.v0.models {

  package object json {
    import play.api.libs.functional.syntax._
    import play.api.libs.json.__
    import play.api.libs.json.JsString
    import play.api.libs.json.Writes
    import play.api.libs.json.Reads.DefaultJodaDateReads
    import io.flow.common.v0.models.json._
    import io.flow.token.v0.models.json._

    private[v0] implicit val jsonReadsUUID = __.read[String].map(java.util.UUID.fromString)

    private[v0] implicit val jsonWritesUUID = new Writes[java.util.UUID] {
      def writes(x: java.util.UUID) = JsString(x.toString)
    }

    private[v0] implicit val jsonWritesJodaDateTime = new Writes[org.joda.time.DateTime] {
      def writes(x: org.joda.time.DateTime) = {
        import org.joda.time.format.ISODateTimeFormat.dateTime
        val str = dateTime.print(x)
        JsString(str)
      }
    }

    implicit def jsonReadsTokenCleartext: play.api.libs.json.Reads[Cleartext] = {
      (__ \ "value").read[String].map { x => new Cleartext(value = x) }
    }

    def jsObjectCleartext(obj: io.flow.token.v0.models.Cleartext) = {
      play.api.libs.json.Json.obj(
        "value" -> play.api.libs.json.JsString(obj.value)
      )
    }

    implicit def jsonWritesTokenCleartext: play.api.libs.json.Writes[Cleartext] = {
      new play.api.libs.json.Writes[io.flow.token.v0.models.Cleartext] {
        def writes(obj: io.flow.token.v0.models.Cleartext) = {
          jsObjectCleartext(obj)
        }
      }
    }

    implicit def jsonReadsTokenToken: play.api.libs.json.Reads[Token] = {
      (
        (__ \ "id").read[String] and
        (__ \ "user").read[io.flow.common.v0.models.UserReference] and
        (__ \ "partial").read[String] and
        (__ \ "created_at").read[_root_.org.joda.time.DateTime] and
        (__ \ "description").readNullable[String]
      )(Token.apply _)
    }

    def jsObjectToken(obj: io.flow.token.v0.models.Token) = {
      play.api.libs.json.Json.obj(
        "id" -> play.api.libs.json.JsString(obj.id),
        "user" -> io.flow.common.v0.models.json.jsObjectUserReference(obj.user),
        "partial" -> play.api.libs.json.JsString(obj.partial),
        "created_at" -> play.api.libs.json.JsString(_root_.org.joda.time.format.ISODateTimeFormat.dateTime.print(obj.createdAt))
      ) ++ (obj.description match {
        case None => play.api.libs.json.Json.obj()
        case Some(x) => play.api.libs.json.Json.obj("description" -> play.api.libs.json.JsString(x))
      })
    }

    implicit def jsonWritesTokenToken: play.api.libs.json.Writes[Token] = {
      new play.api.libs.json.Writes[io.flow.token.v0.models.Token] {
        def writes(obj: io.flow.token.v0.models.Token) = {
          jsObjectToken(obj)
        }
      }
    }

    implicit def jsonReadsTokenTokenAuthenticationForm: play.api.libs.json.Reads[TokenAuthenticationForm] = {
      (__ \ "token").read[String].map { x => new TokenAuthenticationForm(token = x) }
    }

    def jsObjectTokenAuthenticationForm(obj: io.flow.token.v0.models.TokenAuthenticationForm) = {
      play.api.libs.json.Json.obj(
        "token" -> play.api.libs.json.JsString(obj.token)
      )
    }

    implicit def jsonWritesTokenTokenAuthenticationForm: play.api.libs.json.Writes[TokenAuthenticationForm] = {
      new play.api.libs.json.Writes[io.flow.token.v0.models.TokenAuthenticationForm] {
        def writes(obj: io.flow.token.v0.models.TokenAuthenticationForm) = {
          jsObjectTokenAuthenticationForm(obj)
        }
      }
    }

    implicit def jsonReadsTokenTokenForm: play.api.libs.json.Reads[TokenForm] = {
      (__ \ "description").readNullable[String].map { x => new TokenForm(description = x) }
    }

    def jsObjectTokenForm(obj: io.flow.token.v0.models.TokenForm) = {
      (obj.description match {
        case None => play.api.libs.json.Json.obj()
        case Some(x) => play.api.libs.json.Json.obj("description" -> play.api.libs.json.JsString(x))
      })
    }

    implicit def jsonWritesTokenTokenForm: play.api.libs.json.Writes[TokenForm] = {
      new play.api.libs.json.Writes[io.flow.token.v0.models.TokenForm] {
        def writes(obj: io.flow.token.v0.models.TokenForm) = {
          jsObjectTokenForm(obj)
        }
      }
    }

    implicit def jsonReadsTokenTokenReference: play.api.libs.json.Reads[TokenReference] = {
      (
        (__ \ "id").read[String] and
        (__ \ "user").read[io.flow.common.v0.models.UserReference]
      )(TokenReference.apply _)
    }

    def jsObjectTokenReference(obj: io.flow.token.v0.models.TokenReference) = {
      play.api.libs.json.Json.obj(
        "id" -> play.api.libs.json.JsString(obj.id),
        "user" -> io.flow.common.v0.models.json.jsObjectUserReference(obj.user)
      )
    }

    implicit def jsonWritesTokenTokenReference: play.api.libs.json.Writes[TokenReference] = {
      new play.api.libs.json.Writes[io.flow.token.v0.models.TokenReference] {
        def writes(obj: io.flow.token.v0.models.TokenReference) = {
          jsObjectTokenReference(obj)
        }
      }
    }

    implicit def jsonReadsTokenTokenVersion: play.api.libs.json.Reads[TokenVersion] = {
      (
        (__ \ "id").read[String] and
        (__ \ "timestamp").read[_root_.org.joda.time.DateTime] and
        (__ \ "type").read[io.flow.common.v0.models.ChangeType] and
        (__ \ "token").read[io.flow.token.v0.models.Token]
      )(TokenVersion.apply _)
    }

    def jsObjectTokenVersion(obj: io.flow.token.v0.models.TokenVersion) = {
      play.api.libs.json.Json.obj(
        "id" -> play.api.libs.json.JsString(obj.id),
        "timestamp" -> play.api.libs.json.JsString(_root_.org.joda.time.format.ISODateTimeFormat.dateTime.print(obj.timestamp)),
        "type" -> play.api.libs.json.JsString(obj.`type`.toString),
        "token" -> jsObjectToken(obj.token)
      )
    }

    implicit def jsonWritesTokenTokenVersion: play.api.libs.json.Writes[TokenVersion] = {
      new play.api.libs.json.Writes[io.flow.token.v0.models.TokenVersion] {
        def writes(obj: io.flow.token.v0.models.TokenVersion) = {
          jsObjectTokenVersion(obj)
        }
      }
    }

    implicit def jsonReadsTokenValidation: play.api.libs.json.Reads[Validation] = {
      (__ \ "status").read[String].map { x => new Validation(status = x) }
    }

    def jsObjectValidation(obj: io.flow.token.v0.models.Validation) = {
      play.api.libs.json.Json.obj(
        "status" -> play.api.libs.json.JsString(obj.status)
      )
    }

    implicit def jsonWritesTokenValidation: play.api.libs.json.Writes[Validation] = {
      new play.api.libs.json.Writes[io.flow.token.v0.models.Validation] {
        def writes(obj: io.flow.token.v0.models.Validation) = {
          jsObjectValidation(obj)
        }
      }
    }

    implicit def jsonReadsTokenValidationForm: play.api.libs.json.Reads[ValidationForm] = {
      (__ \ "token").read[String].map { x => new ValidationForm(token = x) }
    }

    def jsObjectValidationForm(obj: io.flow.token.v0.models.ValidationForm) = {
      play.api.libs.json.Json.obj(
        "token" -> play.api.libs.json.JsString(obj.token)
      )
    }

    implicit def jsonWritesTokenValidationForm: play.api.libs.json.Writes[ValidationForm] = {
      new play.api.libs.json.Writes[io.flow.token.v0.models.ValidationForm] {
        def writes(obj: io.flow.token.v0.models.ValidationForm) = {
          jsObjectValidationForm(obj)
        }
      }
    }
  }
}

package io.flow.token.v0 {

  object Bindables {

    import play.api.mvc.{PathBindable, QueryStringBindable}
    import org.joda.time.{DateTime, LocalDate}
    import org.joda.time.format.ISODateTimeFormat
    import io.flow.token.v0.models._

    // Type: date-time-iso8601
    implicit val pathBindableTypeDateTimeIso8601 = new PathBindable.Parsing[org.joda.time.DateTime](
      ISODateTimeFormat.dateTimeParser.parseDateTime(_), _.toString, (key: String, e: _root_.java.lang.Exception) => s"Error parsing date time $key. Example: 2014-04-29T11:56:52Z"
    )

    implicit val queryStringBindableTypeDateTimeIso8601 = new QueryStringBindable.Parsing[org.joda.time.DateTime](
      ISODateTimeFormat.dateTimeParser.parseDateTime(_), _.toString, (key: String, e: _root_.java.lang.Exception) => s"Error parsing date time $key. Example: 2014-04-29T11:56:52Z"
    )

    // Type: date-iso8601
    implicit val pathBindableTypeDateIso8601 = new PathBindable.Parsing[org.joda.time.LocalDate](
      ISODateTimeFormat.yearMonthDay.parseLocalDate(_), _.toString, (key: String, e: _root_.java.lang.Exception) => s"Error parsing date $key. Example: 2014-04-29"
    )

    implicit val queryStringBindableTypeDateIso8601 = new QueryStringBindable.Parsing[org.joda.time.LocalDate](
      ISODateTimeFormat.yearMonthDay.parseLocalDate(_), _.toString, (key: String, e: _root_.java.lang.Exception) => s"Error parsing date $key. Example: 2014-04-29"
    )



  }

}


package io.flow.token.v0 {

  object Constants {

    val BaseUrl = "https://token.api.flow.io"
    val Namespace = "io.flow.token.v0"
    val UserAgent = "apidoc:0.11.36 http://www.apidoc.me/flow/token/0.1.29/play_2_5_client"
    val Version = "0.1.29"
    val VersionMajor = 0

  }

  class Client(
    ws: play.api.libs.ws.WSClient,
    val baseUrl: String = "https://token.api.flow.io",
    auth: scala.Option[io.flow.token.v0.Authorization] = None,
    defaultHeaders: Seq[(String, String)] = Nil
  ) extends interfaces.Client {
    import io.flow.common.v0.models.json._
    import io.flow.token.v0.models.json._

    private[this] val logger = play.api.Logger("io.flow.token.v0.Client")

    logger.info(s"Initializing io.flow.token.v0.Client for url $baseUrl")

    def tokens: Tokens = Tokens

    def validations: Validations = Validations

    object Tokens extends Tokens {
      override def get(
        id: _root_.scala.Option[Seq[String]] = None,
        token: _root_.scala.Option[String] = None,
        limit: Long = 25,
        offset: Long = 0,
        sort: String = "-created_at",
        requestHeaders: Seq[(String, String)] = Nil
      )(implicit ec: scala.concurrent.ExecutionContext): scala.concurrent.Future[Seq[io.flow.token.v0.models.Token]] = {
        val queryParameters = Seq(
          token.map("token" -> _),
          Some("limit" -> limit.toString),
          Some("offset" -> offset.toString),
          Some("sort" -> sort)
        ).flatten ++
          id.getOrElse(Nil).map("id" -> _)

        _executeRequest("GET", s"/tokens", queryParameters = queryParameters, requestHeaders = requestHeaders).map {
          case r if r.status == 200 => _root_.io.flow.token.v0.Client.parseJson("Seq[io.flow.token.v0.models.Token]", r, _.validate[Seq[io.flow.token.v0.models.Token]])
          case r if r.status == 401 => throw new io.flow.token.v0.errors.UnitResponse(r.status)
          case r => throw new io.flow.token.v0.errors.FailedRequest(r.status, s"Unsupported response code[${r.status}]. Expected: 200, 401")
        }
      }

      override def getVersions(
        id: _root_.scala.Option[Seq[String]] = None,
        tokenId: _root_.scala.Option[Seq[String]] = None,
        limit: Long = 25,
        offset: Long = 0,
        sort: String = "journal_timestamp",
        requestHeaders: Seq[(String, String)] = Nil
      )(implicit ec: scala.concurrent.ExecutionContext): scala.concurrent.Future[Seq[io.flow.token.v0.models.TokenVersion]] = {
        val queryParameters = Seq(
          Some("limit" -> limit.toString),
          Some("offset" -> offset.toString),
          Some("sort" -> sort)
        ).flatten ++
          id.getOrElse(Nil).map("id" -> _) ++
          tokenId.getOrElse(Nil).map("token_id" -> _)

        _executeRequest("GET", s"/tokens/versions", queryParameters = queryParameters, requestHeaders = requestHeaders).map {
          case r if r.status == 200 => _root_.io.flow.token.v0.Client.parseJson("Seq[io.flow.token.v0.models.TokenVersion]", r, _.validate[Seq[io.flow.token.v0.models.TokenVersion]])
          case r if r.status == 401 => throw new io.flow.token.v0.errors.UnitResponse(r.status)
          case r => throw new io.flow.token.v0.errors.FailedRequest(r.status, s"Unsupported response code[${r.status}]. Expected: 200, 401")
        }
      }

      override def getById(
        id: String,
        requestHeaders: Seq[(String, String)] = Nil
      )(implicit ec: scala.concurrent.ExecutionContext): scala.concurrent.Future[io.flow.token.v0.models.Token] = {
        _executeRequest("GET", s"/tokens/${play.utils.UriEncoding.encodePathSegment(id, "UTF-8")}", requestHeaders = requestHeaders).map {
          case r if r.status == 200 => _root_.io.flow.token.v0.Client.parseJson("io.flow.token.v0.models.Token", r, _.validate[io.flow.token.v0.models.Token])
          case r if r.status == 401 => throw new io.flow.token.v0.errors.UnitResponse(r.status)
          case r if r.status == 404 => throw new io.flow.token.v0.errors.UnitResponse(r.status)
          case r => throw new io.flow.token.v0.errors.FailedRequest(r.status, s"Unsupported response code[${r.status}]. Expected: 200, 401, 404")
        }
      }

      override def getCleartextById(
        id: String,
        requestHeaders: Seq[(String, String)] = Nil
      )(implicit ec: scala.concurrent.ExecutionContext): scala.concurrent.Future[io.flow.token.v0.models.Cleartext] = {
        _executeRequest("GET", s"/tokens/${play.utils.UriEncoding.encodePathSegment(id, "UTF-8")}/cleartext", requestHeaders = requestHeaders).map {
          case r if r.status == 200 => _root_.io.flow.token.v0.Client.parseJson("io.flow.token.v0.models.Cleartext", r, _.validate[io.flow.token.v0.models.Cleartext])
          case r if r.status == 401 => throw new io.flow.token.v0.errors.UnitResponse(r.status)
          case r if r.status == 404 => throw new io.flow.token.v0.errors.UnitResponse(r.status)
          case r => throw new io.flow.token.v0.errors.FailedRequest(r.status, s"Unsupported response code[${r.status}]. Expected: 200, 401, 404")
        }
      }

      override def postAuthentications(
        tokenAuthenticationForm: io.flow.token.v0.models.TokenAuthenticationForm,
        requestHeaders: Seq[(String, String)] = Nil
      )(implicit ec: scala.concurrent.ExecutionContext): scala.concurrent.Future[io.flow.token.v0.models.TokenReference] = {
        val payload = play.api.libs.json.Json.toJson(tokenAuthenticationForm)

        _executeRequest("POST", s"/tokens/authentications", body = Some(payload), requestHeaders = requestHeaders).map {
          case r if r.status == 200 => _root_.io.flow.token.v0.Client.parseJson("io.flow.token.v0.models.TokenReference", r, _.validate[io.flow.token.v0.models.TokenReference])
          case r if r.status == 401 => throw new io.flow.token.v0.errors.UnitResponse(r.status)
          case r if r.status == 404 => throw new io.flow.token.v0.errors.UnitResponse(r.status)
          case r if r.status == 422 => throw new io.flow.token.v0.errors.ErrorsResponse(r)
          case r => throw new io.flow.token.v0.errors.FailedRequest(r.status, s"Unsupported response code[${r.status}]. Expected: 200, 401, 404, 422")
        }
      }

      override def post(
        tokenForm: io.flow.token.v0.models.TokenForm,
        requestHeaders: Seq[(String, String)] = Nil
      )(implicit ec: scala.concurrent.ExecutionContext): scala.concurrent.Future[io.flow.token.v0.models.Token] = {
        val payload = play.api.libs.json.Json.toJson(tokenForm)

        _executeRequest("POST", s"/tokens", body = Some(payload), requestHeaders = requestHeaders).map {
          case r if r.status == 200 => _root_.io.flow.token.v0.Client.parseJson("io.flow.token.v0.models.Token", r, _.validate[io.flow.token.v0.models.Token])
          case r if r.status == 401 => throw new io.flow.token.v0.errors.UnitResponse(r.status)
          case r if r.status == 422 => throw new io.flow.token.v0.errors.ErrorsResponse(r)
          case r => throw new io.flow.token.v0.errors.FailedRequest(r.status, s"Unsupported response code[${r.status}]. Expected: 200, 401, 422")
        }
      }

      override def deleteById(
        id: String,
        requestHeaders: Seq[(String, String)] = Nil
      )(implicit ec: scala.concurrent.ExecutionContext): scala.concurrent.Future[Unit] = {
        _executeRequest("DELETE", s"/tokens/${play.utils.UriEncoding.encodePathSegment(id, "UTF-8")}", requestHeaders = requestHeaders).map {
          case r if r.status == 204 => ()
          case r if r.status == 401 => throw new io.flow.token.v0.errors.UnitResponse(r.status)
          case r if r.status == 404 => throw new io.flow.token.v0.errors.UnitResponse(r.status)
          case r => throw new io.flow.token.v0.errors.FailedRequest(r.status, s"Unsupported response code[${r.status}]. Expected: 204, 401, 404")
        }
      }
    }

    object Validations extends Validations {
      override def post(
        validationForm: io.flow.token.v0.models.ValidationForm,
        requestHeaders: Seq[(String, String)] = Nil
      )(implicit ec: scala.concurrent.ExecutionContext): scala.concurrent.Future[io.flow.token.v0.models.Validation] = {
        val payload = play.api.libs.json.Json.toJson(validationForm)

        _executeRequest("POST", s"/token-validations", body = Some(payload), requestHeaders = requestHeaders).map {
          case r if r.status == 200 => _root_.io.flow.token.v0.Client.parseJson("io.flow.token.v0.models.Validation", r, _.validate[io.flow.token.v0.models.Validation])
          case r if r.status == 401 => throw new io.flow.token.v0.errors.UnitResponse(r.status)
          case r if r.status == 422 => throw new io.flow.token.v0.errors.ErrorsResponse(r)
          case r => throw new io.flow.token.v0.errors.FailedRequest(r.status, s"Unsupported response code[${r.status}]. Expected: 200, 401, 422")
        }
      }
    }

    def _requestHolder(path: String): play.api.libs.ws.WSRequest = {
      
      val holder = ws.url(baseUrl + path).withHeaders(
        "User-Agent" -> Constants.UserAgent,
        "X-Apidoc-Version" -> Constants.Version,
        "X-Apidoc-Version-Major" -> Constants.VersionMajor.toString
      ).withHeaders(defaultHeaders : _*)
      auth.fold(holder) {
        case Authorization.Basic(username, password) => {
          holder.withAuth(username, password.getOrElse(""), play.api.libs.ws.WSAuthScheme.BASIC)
        }
        case a => sys.error("Invalid authorization scheme[" + a.getClass + "]")
      }
    }

    def _logRequest(method: String, req: play.api.libs.ws.WSRequest)(implicit ec: scala.concurrent.ExecutionContext): play.api.libs.ws.WSRequest = {
      val queryComponents = for {
        (name, values) <- req.queryString
        value <- values
      } yield s"$name=$value"
      val url = s"${req.url}${queryComponents.mkString("?", "&", "")}"
      auth.fold(logger.info(s"curl -X $method $url")) { _ =>
        logger.info(s"curl -X $method -u '[REDACTED]:' $url")
      }
      req
    }

    def _executeRequest(
      method: String,
      path: String,
      queryParameters: Seq[(String, String)] = Nil,
      requestHeaders: Seq[(String, String)] = Nil,
      body: Option[play.api.libs.json.JsValue] = None
    )(implicit ec: scala.concurrent.ExecutionContext): scala.concurrent.Future[play.api.libs.ws.WSResponse] = {
      method.toUpperCase match {
        case "GET" => {
          _logRequest("GET", _requestHolder(path).withHeaders(requestHeaders:_*).withQueryString(queryParameters:_*)).get()
        }
        case "POST" => {
          _logRequest("POST", _requestHolder(path).withHeaders(_withJsonContentType(requestHeaders):_*).withQueryString(queryParameters:_*)).post(body.getOrElse(play.api.libs.json.Json.obj()))
        }
        case "PUT" => {
          _logRequest("PUT", _requestHolder(path).withHeaders(_withJsonContentType(requestHeaders):_*).withQueryString(queryParameters:_*)).put(body.getOrElse(play.api.libs.json.Json.obj()))
        }
        case "PATCH" => {
          _logRequest("PATCH", _requestHolder(path).withHeaders(requestHeaders:_*).withQueryString(queryParameters:_*)).patch(body.getOrElse(play.api.libs.json.Json.obj()))
        }
        case "DELETE" => {
          _logRequest("DELETE", _requestHolder(path).withHeaders(requestHeaders:_*).withQueryString(queryParameters:_*)).delete()
        }
         case "HEAD" => {
          _logRequest("HEAD", _requestHolder(path).withHeaders(requestHeaders:_*).withQueryString(queryParameters:_*)).head()
        }
         case "OPTIONS" => {
          _logRequest("OPTIONS", _requestHolder(path).withHeaders(requestHeaders:_*).withQueryString(queryParameters:_*)).options()
        }
        case _ => {
          _logRequest(method, _requestHolder(path).withHeaders(requestHeaders:_*).withQueryString(queryParameters:_*))
          sys.error("Unsupported method[%s]".format(method))
        }
      }
    }

    /**
     * Adds a Content-Type: application/json header unless the specified requestHeaders
     * already contain a Content-Type header
     */
    def _withJsonContentType(headers: Seq[(String, String)]): Seq[(String, String)] = {
      headers.find { _._1.toUpperCase == "CONTENT-TYPE" } match {
        case None => headers ++ Seq(("Content-Type" -> "application/json; charset=UTF-8"))
        case Some(_) => headers
      }
    }

  }

  object Client {

    def parseJson[T](
      className: String,
      r: play.api.libs.ws.WSResponse,
      f: (play.api.libs.json.JsValue => play.api.libs.json.JsResult[T])
    ): T = {
      f(play.api.libs.json.Json.parse(r.body)) match {
        case play.api.libs.json.JsSuccess(x, _) => x
        case play.api.libs.json.JsError(errors) => {
          throw new io.flow.token.v0.errors.FailedRequest(r.status, s"Invalid json for class[" + className + "]: " + errors.mkString(" "))
        }
      }
    }

  }

  sealed trait Authorization
  object Authorization {
    case class Basic(username: String, password: Option[String] = None) extends Authorization
  }

  package interfaces {

    trait Client {
      def baseUrl: String
      def tokens: io.flow.token.v0.Tokens
      def validations: io.flow.token.v0.Validations
    }

  }

  trait Tokens {
    /**
     * Get all tokens that you are authorized to view. Note that the cleartext token
     * value is never sent. To view the API token itself, see the resource path
     * /:id/cleartext
     */
    def get(
      id: _root_.scala.Option[Seq[String]] = None,
      token: _root_.scala.Option[String] = None,
      limit: Long = 25,
      offset: Long = 0,
      sort: String = "-created_at",
      requestHeaders: Seq[(String, String)] = Nil
    )(implicit ec: scala.concurrent.ExecutionContext): scala.concurrent.Future[Seq[io.flow.token.v0.models.Token]]

    def getVersions(
      id: _root_.scala.Option[Seq[String]] = None,
      tokenId: _root_.scala.Option[Seq[String]] = None,
      limit: Long = 25,
      offset: Long = 0,
      sort: String = "journal_timestamp",
      requestHeaders: Seq[(String, String)] = Nil
    )(implicit ec: scala.concurrent.ExecutionContext): scala.concurrent.Future[Seq[io.flow.token.v0.models.TokenVersion]]

    /**
     * Get metadata for the token with this ID
     */
    def getById(
      id: String,
      requestHeaders: Seq[(String, String)] = Nil
    )(implicit ec: scala.concurrent.ExecutionContext): scala.concurrent.Future[io.flow.token.v0.models.Token]

    /**
     * Retrieves the token with the actual string token in cleartext
     */
    def getCleartextById(
      id: String,
      requestHeaders: Seq[(String, String)] = Nil
    )(implicit ec: scala.concurrent.ExecutionContext): scala.concurrent.Future[io.flow.token.v0.models.Cleartext]

    /**
     * Preferred method to validate a token, obtaining user information if the token is
     * valid (or a 404 if the token does not exist). We use an HTTP POST with a form
     * body to enusre that the token itself is not logged in the request logs.
     */
    def postAuthentications(
      tokenAuthenticationForm: io.flow.token.v0.models.TokenAuthenticationForm,
      requestHeaders: Seq[(String, String)] = Nil
    )(implicit ec: scala.concurrent.ExecutionContext): scala.concurrent.Future[io.flow.token.v0.models.TokenReference]

    /**
     * Create a new token for the requesting user
     */
    def post(
      tokenForm: io.flow.token.v0.models.TokenForm,
      requestHeaders: Seq[(String, String)] = Nil
    )(implicit ec: scala.concurrent.ExecutionContext): scala.concurrent.Future[io.flow.token.v0.models.Token]

    def deleteById(
      id: String,
      requestHeaders: Seq[(String, String)] = Nil
    )(implicit ec: scala.concurrent.ExecutionContext): scala.concurrent.Future[Unit]
  }

  trait Validations {
    def post(
      validationForm: io.flow.token.v0.models.ValidationForm,
      requestHeaders: Seq[(String, String)] = Nil
    )(implicit ec: scala.concurrent.ExecutionContext): scala.concurrent.Future[io.flow.token.v0.models.Validation]
  }

  package errors {

    import io.flow.common.v0.models.json._
    import io.flow.token.v0.models.json._

    case class ErrorsResponse(
      response: play.api.libs.ws.WSResponse,
      message: Option[String] = None
    ) extends Exception(message.getOrElse(response.status + ": " + response.body)){
      lazy val errors = _root_.io.flow.token.v0.Client.parseJson("Seq[io.flow.common.v0.models.Error]", response, _.validate[Seq[io.flow.common.v0.models.Error]])
    }

    case class UnitResponse(status: Int) extends Exception(s"HTTP $status")

    case class FailedRequest(responseCode: Int, message: String, requestUri: Option[_root_.java.net.URI] = None) extends _root_.java.lang.Exception(s"HTTP $responseCode: $message")

  }

}