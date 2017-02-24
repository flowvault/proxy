package clients

import io.flow.token.v0.interfaces.{Client => TokenClient}
import play.api.{Environment, Configuration, Mode}
import play.api.inject.Module

class TokenClientModule extends Module {

  def bindings(env: Environment, conf: Configuration) = {
    env.mode match {
      case Mode.Prod | Mode.Dev => Seq(
        bind[TokenClient].to[DefaultTokenClient]
      )
      case Mode.Test => Seq(
        // TODO: Add mock
        bind[TokenClient].to[DefaultTokenClient]
      )
    }
  }

}
