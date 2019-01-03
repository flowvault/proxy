package auth

import io.flow.log.RollbarLogger

trait LoggingHelper {
  def logger: RollbarLogger

  protected def log(requestId: String): RollbarLogger = {
    logger.
      fingerprint(getClass.getName).
      withKeyValue("log_source", "proxy").
      requestId(requestId)
  }
}
