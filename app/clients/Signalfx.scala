package clients

@javax.inject.Singleton
class DefaultSignalfxClient @javax.inject.Inject() (
  ws: play.api.libs.ws.WSClient
) extends io.flow.signalfx.v0.Client(ws)
