package lib

object Text {

  /**
    * Tokens at Flow are random alphanumerics - if there is a binary encoding problem in the
    * auth headers, we can cut off the request immediately knowing the token is not valid.
    */
  def isAlphaNumeric(s: String): Boolean = {
    s.toList.forall(_.isLetterOrDigit)
  }

}