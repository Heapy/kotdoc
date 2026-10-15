// Text.HTML.TagSoup.Render/canonicalizeTags, © 2006-2019 Neil Mitchell.
// BSD-3-Clause, licenses/tagsoup-BSD-3-Clause.txt.
// Pandoc Shared.renderTags' options: © 2006-2024 John MacFarlane, GPL-2.0-or-later.
package io.heapy.kotdoc.html

import io.heapy.kotdoc.parsing.RegexCharacters
import io.heapy.kotdoc.latex.LaTeXReaderText

object HtmlRendering {
    data class Options(val escape: (String)->String = ::escapeHTML,
                       val minimize: (String)->Boolean = { it=="br" },
                       val rawTag: (String)->Boolean = { it=="script" })
    private fun simple(text: String,upper: Boolean)=buildString { text.codePoints().forEach { appendCodePoint(if(upper)RegexCharacters.toUpper(it) else RegexCharacters.toLower(it)) } }
    fun canonicalize(tags: List<HtmlTag>): List<HtmlTag> = tags.map { when(it) {
        is HtmlTag.Open->if(it.name.startsWith('!'))it.copy(name="!"+simple(it.name.drop(1),true)) else it.copy(name=simple(it.name,false),attributes=it.attributes.map { (k,v)->simple(k,false) to v })
        is HtmlTag.Close->it.copy(name=simple(it.name,false))
        else->it
    } }
    fun escapeHTML(text: String)=buildString { for(c in text)append(when(c) { '&'->"&amp;";'"'->"&quot;";'<'->"&lt;";'>'->"&gt;";else->c.toString() }) }
    fun render(tags: List<HtmlTag>,options: Options=Options()): String = buildString {
        fun open(t: HtmlTag.Open,shut: String) {
            append('<').append(t.name)
            for((k,v) in t.attributes)when {
                k.isEmpty() && v.isEmpty()->append(" \"\"")
                v.isEmpty()->append(' ').append(k)
                k.isEmpty()->append(" \"").append(options.escape(v)).append('"')
                else->append(' ').append(k).append("=\"").append(options.escape(v)).append('"')
            }
            append(shut).append('>')
        }
        fun tag(t: HtmlTag,raw: Boolean=false) { when(t) {
            is HtmlTag.Open->open(t,"")
            is HtmlTag.Close->append("</").append(t.name).append('>')
            is HtmlTag.Text->append(if(raw)t.text else options.escape(t.text))
            is HtmlTag.Comment->append("<!--").append(t.text.replace("-->","-- >")).append("-->")
            else->Unit
        } }
        var at=0
        while(at<tags.size) {
            val t=tags[at++]
            if(t is HtmlTag.Open) {
                if(at<tags.size && tags[at]==HtmlTag.Close(t.name) && options.minimize(t.name)) { open(t," /");at++;continue }
                if(t.name.startsWith('?')) { open(t," ?");continue }
                if(options.rawTag(t.name)) {
                    tag(t)
                    while(at<tags.size && tags[at]!=HtmlTag.Close(t.name))tag(tags[at++],true)
                    continue
                }
            }
            tag(t)
        }
    }
    fun renderPandoc(tags: List<HtmlTag>)=render(tags,Options(minimize={ LaTeXReaderText.toLower(it) in setOf("hr","br","img","meta","link","col","use","path","rect") },rawTag={ LaTeXReaderText.toLower(it) in setOf("script","style") }))
}
