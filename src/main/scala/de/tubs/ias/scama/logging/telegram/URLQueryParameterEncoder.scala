package de.tubs.ias.scama.logging.telegram

object URLQueryParameterEncoder {

  private val translation = Map(
    " " -> "%20",
    "!" -> "%21",
    "\"" -> "%22",
    "#" -> "%23",
    "$" -> "%24",
    "%" -> "%25",
    "&" -> "%26",
    "'" -> "%27",
    "(" -> "%28",
    ")" -> "%29",
    "*" -> "%2A",
    "+" -> "\\%2B",
    "," -> ",",
    "-" -> "\\%2D",
    "." -> "\\%2E",
    "/" -> "%2F",
    ":" -> "%3A",
    ";" -> "%3B",
    "<" -> "%3C",
    "=" -> "\\%3D",
    ">" -> "%3E",
    "?" -> "%3F",
    "@" -> "%40",
    "[" -> "%5B",
    "\\" -> "%5C",
    "]" -> "%5D",
    "^" -> "%5E",
    "_" -> "\\%5F",
    "`" -> "%60",
    "{" -> "%7B",
    "|" -> "%7C",
    "}" -> "%7D",
    "~" -> "%7E",
    "`" -> "%E2%82%AC",
    "‚" -> "%E2%80%9A",
  )

  def encodeParameter(parameter: String): String = {
    parameter.toList
      .map { char =>
        translation.getOrElse(char.toString, char.toString)
      }
      .mkString("")
  }

}
