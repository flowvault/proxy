package lib

object CheckoutHealthcheck {

  val Body: String = build(82000)

  def build(bytes: Int): String = {
    "abcdefghijklmnopqrstuvwxyz " * (bytes/27)
  }
}
