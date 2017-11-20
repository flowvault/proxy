package compat

import java.io.File

import play.api.libs.json.{JsObject, Json}

case class FixtureInput(
  method: String,
  path: String,
  responseCode: Int,
  body: JsObject
)

case class FixtureTestCase(
  locale: String,
  expected: JsObject
)

case class Fixture(
  input: FixtureInput,
  testCases: Seq[FixtureTestCase]
)

object Fixture {

  private[this] val CommentCharacter = "#"

  def load(file: File): Fixture = {
    scala.io.Source.fromFile(file).getLines.
      map(_.trim).
      filter { l => !l.startsWith(CommentCharacter) }.
      mkString("\n").
      trim.
      split("\n\n").
      toList match {
        case src :: rest => {
          val input = parseInput(src)
          Fixture(
            input = input,
            testCases = rest.map(parseTestCase)
          )
        }
        case _ => sys.error(s"File[$file] Could not parse contents - no newline found")
    }
  }

  private[this] def parseInput(value: String): FixtureInput = {
    value.trim.split("\n").toList match {
      case pathLine :: rest => {
        pathLine.split(" ").toList match {
          case method :: path :: code :: Nil => {
            FixtureInput(
              method = method,
              path = path,
              responseCode = code.toInt,
              Json.parse(rest.mkString("\n")).asInstanceOf[JsObject]
            )
          }

          case _ => sys.error(s"Invalid path line: $pathLine")
        }
      }

      case _ => sys.error(s"Could not parse input: $value")
    }
  }

  private[this] def parseTestCase(value: String): FixtureTestCase = {
    value.trim.split("\n").toList match {
      case localeLine :: json => {
        if (!localeLine.startsWith("locale:")) {
          sys.error(s"Expected first line of test case to start with locale: $localeLine")
        }
        val locale: String = localeLine.split(":").last.trim
        FixtureTestCase(
          locale = locale,
          expected = Json.parse(json.mkString("\n")).as[JsObject]
        )
      }

      case _ => sys.error(s"Missing locale for test case: $value")
    }
  }

}