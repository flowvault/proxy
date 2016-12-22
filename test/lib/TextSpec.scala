package lib

import org.scalatestplus.play._

class TextSpec extends PlaySpec with OneServerPerSuite {

  "isAlphaNumeric" in {
    Text.isAlphaNumeric("abcdefghijklmnopqrstuvqxyz") must be(true)
    Text.isAlphaNumeric("abcdefghijklmnopqrstuvqxyz".toUpperCase) must be(true)
    Text.isAlphaNumeric("0123456789") must be(true)
    Text.isAlphaNumeric("!") must be(false)
    Text.isAlphaNumeric("a!") must be(false)
  }

}