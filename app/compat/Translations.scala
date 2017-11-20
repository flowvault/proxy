package compat

import javax.inject.{Inject, Singleton}

import play.api.Logger

@Singleton
class Translations @Inject() () {

  private[this] val DefaultTranslationCatalog = TranslationCatalog(
    locale = "en_US",
    keys = Map(
      "invalid_cvn" -> "CVN is not valid",
      "invalid_expiration" -> "Expiration date must be on or after {{mm_yyyy}}"
    ),
    fallbackCatalog = None
  )

  private[this] val catalogs = Seq[TranslationCatalog](
    DefaultTranslationCatalog,
    TranslationCatalog(
      locale = "en_FR",
      keys = Map(
        "invalid_cvn" -> "Le code de securite n'est pas valide",
        "invalid_expiration" -> "La date d'expiration doit etre apres {{mm_yyyy}}"
      ),
      fallbackCatalog = Some(DefaultTranslationCatalog)
    )
  )

  private[this] val catalogMap: Map[String, TranslationCatalog] = Map(
    catalogs.map { c =>
      c.locale -> c
    }: _*
  )

  def locale(locale: String): TranslationCatalog = {
    catalogMap.getOrElse(
      locale,
      {
        Logger.warn(s"[Translations] No catalog found for locale[$locale] - using default[${DefaultTranslationCatalog.locale}]")
        DefaultTranslationCatalog
      }
    )
  }

}

case class TranslationTarget(
  key: String,
  template: String
) {

  private[this] val variables: Seq[String] = Seq(
    "mm_yyyy"
  )

  def substitute(values: Map[String, String]): String = {
    val missing = variables.filterNot { key => values.isDefinedAt(key) }
    assert(
      missing.isEmpty,
      s"TranslationTarget[$key] with template[$template]: Missing translation keys: ${missing.mkString(",")}"
    )

    variables.foldLeft(template) { case (value, k) =>
      value.replace(s"{{$k}}", values(k))
    }
  }

}

case class TranslationCatalog(
  locale: String,
  keys: Map[String, String],
  fallbackCatalog: Option[TranslationCatalog]
) {

  private[this] val translationTargets: Map[String, TranslationTarget] = keys.map { case (k, v) =>
      k -> TranslationTarget(k, v)
  }

  private def lookup(key: String): Option[TranslationTarget] = {
    translationTargets.get(key) match {
      case Some(value) => Some(value)
      case None => {
        Logger.warn(s"[TranslationCatalog] Key[$key] not found for locale[$locale]")
        fallbackCatalog.flatMap(_.lookup(key))
      }
    }
  }

  def translate(key: String): Option[String] = {
    lookup(key).map { target =>
      target.substitute(Map("mm_yyyy" -> "11/2017"))
    }
  }

}



