package lib.timed

import scala.concurrent.{ExecutionContext, Future}

object TimedFuture {
  def apply[T](f: => Future[T])(implicit ec: ExecutionContext): Future[Timed[T]] = {
    val issuedAt = System.nanoTime()
    f.map(Timed(_, issuedAt, System.nanoTime()))
  }
}

case class Timed[T](value: T, issuedAt: Long, completedAt: Long) {
  lazy val durationNs: Long = completedAt - issuedAt
  lazy val durationMs: Long = durationNs / 1000000
}
