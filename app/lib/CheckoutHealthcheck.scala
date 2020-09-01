package lib

object CheckoutHealthcheck {

  val Body: String = build(82000)

  private[this] def build(approximateNumberOfBytes: Int): String = {
    "abcdefghijklmnopqrstuvwxyz " * (approximateNumberOfBytes/27)
  }

}
