package lib

object Util {

  def toFlatSeq(data: Map[String, Seq[String]]): Seq[(String, String)] = {
    data.map { case (k, vs) =>
      vs.map(k -> _)
    }.flatten.toSeq
  }

  def removeKeys(
    data: Map[String, Seq[String]],
    keys: Set[String],
  ): Map[String, Seq[String]] =
    data -- keys

  def filterKeys(
    data: Map[String, Seq[String]],
    keys: Set[String]
  ): Map[String, Seq[String]] =
    data.filterKeys(keys)

}
