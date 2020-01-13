package lib

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

  def removeKeys(
    data: Map[String, Seq[String]],
    keys: Set[String],
  ): Map[String, Seq[String]] = {
    data.filter { case (k, _) =>
      !keys.contains(k)
    }
  }

  def filterKeys(
    data: Map[String, Seq[String]],
    keys: Set[String]
  ): Map[String, Seq[String]] = {
    data.filter { case (k, _) =>
      keys.contains(k)
    }
  }

}
