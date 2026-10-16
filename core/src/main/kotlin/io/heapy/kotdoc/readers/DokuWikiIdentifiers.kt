// Text.Pandoc.Shared textToIdentifier/inlineListToIdentifier/uniqueIdent.
// Copyright (C) 2006-2024 John MacFarlane; GPL-2.0-or-later.
package io.heapy.kotdoc.readers

import io.heapy.kotdoc.ast.*
import io.heapy.kotdoc.options.ExtensionName
import io.heapy.kotdoc.options.Extensions

/** GHC's pinned Unicode categories, independent of the host ICU version. */
internal object DokuWikiIdentifiers {
    private val rows = checkNotNull(javaClass.getResource("/io/heapy/kotdoc/dokuwiki-identifier-flags.tsv"))
        .readText().lineSequence().filter(String::isNotEmpty).map { it.split('\t').map(String::toInt) }.toList()
    private val starts = IntArray(rows.size) { rows[it][0] }
    private val flags = IntArray(rows.size) { rows[it][1] }
    fun flags(point: Int): Int {
        val index=starts.binarySearch(point)
        return flags[if(index>=0)index else -index-2]
    }
    fun unique(inlines: List<Inline>, used: Set<String>, extensions: Extensions): String {
        val unemoji = if(ExtensionName.GfmAutoIdentifiers in extensions || ExtensionName.AsciiIdentifiers in extensions)
            object : PandocVisitor() {
                override fun transformInline(value: Inline): Inline = when {
                    value is Inline.Span && value.attr.identifier.isEmpty() && value.attr.classes==listOf("emoji") && value.attr.keyValues.size==1 && value.attr.keyValues[0].first=="data-emoji" -> Inline.Str(value.attr.keyValues[0].second)
                    value is Inline.Str -> Inline.Str(Emoji.replaceEmojis(value.text) { original,aliases -> aliases.firstOrNull() ?: original })
                    else -> value
                }
            }.walkInlines(inlines) else inlines
        val gfm=ExtensionName.GfmAutoIdentifiers in extensions
        val filtered=buildString {
            for(original in UnicodeText.toLower(DocumentShared.stringifyInlines(unemoji)).codePoints().toArray()) {
                val point=if(gfm && flags(original) and 4 != 0)45 else original
                val bits=flags(point)
                if(bits and 6 != 0 || point==45 || point==95 || if(gfm)bits and 8 != 0 else point==46)appendCodePoint(point)
            }
        }
        val separated=if(gfm)filtered else buildString {
            var pending=false
            for(point in filtered.codePoints().toArray()) {
                if(flags(point) and 4 != 0)pending=isNotEmpty()
                else {if(pending)append('-');appendCodePoint(point);pending=false}
            }
        }
        val ascii=if(ExtensionName.AsciiIdentifiers in extensions)Asciify.toAsciiText(separated) else separated
        var start=0
        if(!gfm)while(start<ascii.length && flags(ascii.codePointAt(start)) and 1 == 0)start+=Character.charCount(ascii.codePointAt(start))
        val base=ascii.drop(start).ifEmpty { "section" }
        if(base !in used)return base
        for(number in 1..60000)if("$base-$number" !in used)return "$base-$number"
        return base
    }
}
