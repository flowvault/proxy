package auth

import io.flow.customer.v0.models.Customer
import io.flow.customer.v0.{Client => CustomerClient}
import lib.ResolvedToken

import scala.concurrent.{ExecutionContext, Future}

trait CustomerAuthHelper extends LoggingHelper {

  def customerClient: CustomerClient
  def requestHeadersUtil: RequestHeadersUtil

  private[auth] def getCustomerResolvedToken(
    requestId: String,
    customerNumber: String,
    sessionResolvedTokenOption: Option[ResolvedToken]
  )(implicit ec: ExecutionContext): Future[Option[ResolvedToken]] = {
    sessionResolvedTokenOption.map { t =>
      getCustomerResolvedToken(
        requestId = requestId,
        customerNumber = customerNumber,
        sessionResolvedToken = t
      )
    }.getOrElse(Future.successful(None))
  }

  private[this] def getCustomerResolvedToken(
    requestId: String,
    customerNumber: String,
    sessionResolvedToken: ResolvedToken
  )(implicit ec: ExecutionContext): Future[Option[ResolvedToken]] = {
    sessionResolvedToken.organizationId.map { organizationId =>
      getCustomer(
        requestId = requestId,
        organizationId = organizationId,
        customerNumber = customerNumber
      ).map { customer =>
        Some(
          sessionResolvedToken.copy(
            customerNumber = customer.map(_.number)
          )
        )
      }
    }.getOrElse(Future.successful(None))
  }

  private[this] def getCustomer(
    requestId: String,
    organizationId: String,
    customerNumber: String
  )(implicit ec: ExecutionContext): Future[Option[Customer]] = {
    customerClient.customers.getByNumber(
      organization = organizationId,
      number = customerNumber,
      requestHeaders = requestHeadersUtil.organizationAsSystemUser(
        organizationId = organizationId,
        requestId = requestId
      )
    ).map { customer =>
      Some(customer)
    }.recover {
      case io.flow.customer.v0.errors.UnitResponse(_) => None
      case ex: Throwable => {
        val msg = "Error communication with customer service"
        log(requestId).
          withKeyValue("customer_number", customerNumber).
          error(msg, ex)
        throw new RuntimeException(msg, ex)
      }
    }
  }

}
