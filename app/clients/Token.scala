package clients

import play.api.Configuration

@javax.inject.Singleton
class DefaultTokenClient @javax.inject.Inject() (
  ws: play.api.libs.ws.WSClient
) extends io.flow.token.v0.Client(ws, baseUrl = "https://token.api.flow.io")
