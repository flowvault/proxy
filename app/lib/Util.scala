package lib

import play.api.mvc.Headers

object Util {

  def toFlatSeq(data: Map[String, Seq[String]]): Seq[(String, String)] = {
    data.map { case (k, vs) =>
      vs.map(k -> _)
    }.flatten.toSeq
  }

  def removeKey(
    data: Map[String, Seq[String]],
    key: String
  ): Map[String, Seq[String]] = {
    data.filter { case (k, _) =>
      k != key
    }
  }

  def removeHeaders(
    headers: Headers,
    toRemove: Set[String],
  ): Headers = {
    headers.remove(toRemove.toSeq: _*)
  }

  def filterHeaders(
    data: Headers,
    toKeep: Set[String]
  ): Map[String, Seq[String]] = {
    (for {
      key <- toKeep
      if data.hasHeader(key)
    } yield key -> data.getAll(key)).toMap
  }

}
