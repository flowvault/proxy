package lib

import io.flow.log.RollbarLogger
import javax.inject.Inject
import org.yaml.snakeyaml.Yaml

import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success, Try}

/**
  * Parses the contents of the configuration file from a URI
  */
class ConfigParser @Inject() (
  logger: RollbarLogger
) {

  private[this] val EmptyProxyConfig = InternalProxyConfig(
    uri = "",
    version = "",
    servers = Nil,
    operations = Nil,
    errors = Nil
  )

  def parse(
    sourceUri: String,
    contents: String
  ): InternalProxyConfig = {
    contents.trim match {
      case "" => EmptyProxyConfig

      case value => {
        val yaml = new Yaml()

        Try {
          val all = Option(yaml.loadAs(value, classOf[java.util.Map[String, Object]])) match {
            case None => Map.empty[String, Object]
            case Some(data) => toMap(data)
          }

          val version = getString(all, "version")

          val servers: Seq[InternalServer] = getArray(all, "servers").map { objJs =>
            val obj = toMap(objJs)

            /**
              * Currently uses global proxy logger.
              * TODO:
              * - Figure best way to use logger for the server actually fulfilling the request
              * - This probably happens in some logger service that reads from a config
              * - Or maybe have this in registry?
              */
            val serverLogger = logger

            InternalServer(
              name = getString(obj, "name"),
              host = getString(obj, "host"),
              logger = serverLogger
            )
          }

          val operations: Seq[InternalOperation] = getArray(all, "operations").map { objJs =>
            val obj = toMap(objJs)
            InternalOperation(
              method = getString(obj, "method"),
              path = getString(obj, "path"),
              server = getString(obj, "server")
            )
          }

          InternalProxyConfig(
            uri = sourceUri,
            version = version,
            servers = servers,
            operations = operations,
            errors = Nil
          )
        } match {
          case Success(result) => result
          case Failure(ex) => EmptyProxyConfig.copy(errors = Seq(ex.getMessage))
        }
      }
    }
  }

  def getString(obj: Map[String, Any], name: String): String = {
    obj.get(name).map(_.toString.trim).getOrElse("")
  }  

  def getArray(obj: Map[String, Any], name: String): Seq[Any] = {
    obj.get(name).map(_.asInstanceOf[java.util.List[Object]].asScala.toSeq).getOrElse(Nil)
  }  

  private[this] def toMap(value: Any): Map[String, Any] = {
    value match {
      case map: java.util.HashMap[_, _] => {
        map.asScala.map { case (k, v) =>
          k.toString -> v
        }.toMap
      }

      case _ => {
        Map.empty
      }
    }
  }
}
