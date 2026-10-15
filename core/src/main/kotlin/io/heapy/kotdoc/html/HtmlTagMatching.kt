// Text.HTML.TagSoup (~==), Type deriving Show; Neil Mitchell, BSD-3-Clause.
package io.heapy.kotdoc.html

import io.heapy.kotdoc.formats.HaskellString

object HtmlTagMatching {
    /** TagSoup's asymmetric (~==), including its comment/warning empty-left rule. */
    fun matches(actual: HtmlTag, expected: HtmlTag): Boolean = when {
        actual is HtmlTag.Open && expected is HtmlTag.Open ->
            (expected.name.isEmpty() || expected.name == actual.name) && expected.attributes.all { (name, value) ->
                when { name.isEmpty() -> actual.attributes.any { it.second == value }
                    value.isEmpty() -> actual.attributes.any { it.first == name }
                    else -> name to value in actual.attributes }
            }
        actual is HtmlTag.Close && expected is HtmlTag.Close -> expected.name.isEmpty() || actual.name == expected.name
        actual is HtmlTag.Text && expected is HtmlTag.Text -> expected.text.isEmpty() || actual.text == expected.text
        actual is HtmlTag.Comment && expected is HtmlTag.Comment -> actual.text.isEmpty() || actual.text == expected.text
        actual is HtmlTag.Warning && expected is HtmlTag.Warning -> actual.message.isEmpty() || actual.message == expected.message
        actual is HtmlTag.Position && expected is HtmlTag.Position -> actual == expected
        else -> false
    }
    fun show(tag: HtmlTag): String = when (tag) {
        is HtmlTag.Open -> "TagOpen ${HaskellString.quote(tag.name)} " + tag.attributes.joinToString(",", "[", "]") { (k, v) -> "(${HaskellString.quote(k)},${HaskellString.quote(v)})" }
        is HtmlTag.Close -> "TagClose ${HaskellString.quote(tag.name)}"
        is HtmlTag.Text -> "TagText ${HaskellString.quote(tag.text)}"
        is HtmlTag.Comment -> "TagComment ${HaskellString.quote(tag.text)}"
        is HtmlTag.Warning -> "TagWarning ${HaskellString.quote(tag.message)}"
        is HtmlTag.Position -> "TagPosition ${integerArgument(tag.row)} ${integerArgument(tag.column)}"
    }
    private fun integerArgument(n: Long) = if (n < 0) "($n)" else n.toString()
}
