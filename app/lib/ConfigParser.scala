package lib

import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.scanner.ScannerException
import play.api.Logger
import scala.collection.JavaConverters._
import scala.util.{Failure, Success, Try}

/**
  * Parses the contents of the configuration file from a URI
  */
object ConfigParser {

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
          val all = Option(yaml.load(contents)) match {
            case None => Map[String, Object]()
            case Some(data) => toMap(data)
          }

          val version = getString(all, "version")

          val servers: Seq[InternalServer] = toArray(all.get("servers")).map { objJs =>
            val obj = toMap(objJs)
            InternalServer(
              name = getString(obj, "name"),
              host = getString(obj, "host")
            )
          }

          val operations: Seq[InternalOperation] = toArray(all.get("operations")).map { objJs =>
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
          case Success(result) => {
            result
          }

          case Failure(ex) => {
            EmptyProxyConfig.copy(errors = Seq(ex.getMessage))
          }
        }
      }
    }
  }

  def getString(obj: Map[String, Any], name: String): String = {
    obj.get(name).map(_.toString.trim).getOrElse("")
  }  

  private[this] def toMap(value: Any): Map[String, Any] = {
    value match {
      case map: java.util.HashMap[_, _] => {
        map.asScala.map { case (key, value) =>
          (key.toString -> value)
        }.toMap
      }

      case _ => {
        Map.empty
      }
    }
  }

  private[this] def toArray(value: Any): Seq[Any] = {
    value match {
      case list: java.util.List[_] => {
        list.asScala.toSeq
      }

      case _ => {
        Nil
      }
    }
  }
}
