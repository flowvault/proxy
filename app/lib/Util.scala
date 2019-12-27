package lib

object Util {

  def toFlatSeq(data: Map[String, Seq[String]]): Seq[(String, String)] =
    data.map { case (k, vs) =>
      vs.map(k -> _)
    }.flatten.toSeq

}
