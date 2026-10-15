// Text.Pandoc.Readers.LaTeX, Copyright (C) 2006-2024 John MacFarlane.
// GPL-2.0-or-later; licenses/pandoc-GPL-2.0.md.
// text-2.1.2 case mappings: BSD-2-Clause, licenses/text-BSD-2-Clause.txt.
package io.heapy.kotdoc.latex

import io.heapy.kotdoc.ast.*
import io.heapy.kotdoc.parsing.ParserCharacters
import io.heapy.kotdoc.parsing.RegexCharacters
import io.heapy.kotdoc.options.Extensions
import io.heapy.kotdoc.options.ExtensionName
import kotlinx.serialization.json.*

object LaTeXReaderText {
    private val data by lazy { Json.parseToJsonElement(checkNotNull(javaClass.getResource("/io/heapy/kotdoc/text-2.1.2-case.json")).readText()).jsonObject }
    private fun mapping(kind: String) = data.getValue(kind).jsonObject.mapKeys { it.key.toInt(16) }.mapValues { it.value.jsonPrimitive.content }
    private val upper by lazy { mapping("upper") }
    private val lower by lazy { mapping("lower") }
    private val title by lazy { mapping("title") }
    private fun convert(text: String, table: Map<Int,String>): String = buildString {
        text.codePoints().forEach { cp -> table[cp]?.let(::append) ?: appendCodePoint(cp) }
    }
    fun toUpper(text: String) = convert(text,upper)
    fun toLower(text: String) = convert(text,lower)
    /** Shared.uniqueIdent instantiated with the full reader's text-2.1.2 and
     * GHC Unicode-16 dictionaries. Earlier independent dependency ports retain
     * their text-2.1.3/ICU profile in Identifiers. */
    fun uniqueIdent(inlines: List<Inline>,used: Set<String>,extensions: Extensions): String {
        val gfm=ExtensionName.GfmAutoIdentifiers in extensions
        val ascii=ExtensionName.AsciiIdentifiers in extensions
        val converted=if(gfm || ascii)object : PandocVisitor() {
            override fun transformInline(value: Inline): Inline = when {
                value is Inline.Span && value.attr.identifier.isEmpty() && value.attr.classes==listOf("emoji") && value.attr.keyValues.size==1 && value.attr.keyValues[0].first=="data-emoji"->Inline.Str(value.attr.keyValues[0].second)
                value is Inline.Str->Inline.Str(Emoji.replaceEmojis(value.text) { original,aliases->aliases.firstOrNull() ?: original })
                else->value
            }
        }.walkInlines(inlines) else inlines
        val filtered=buildString {
            toLower(DocumentShared.stringifyInlines(converted)).codePoints().forEach { original ->
                val cp=if(gfm && ParserCharacters.isSpace(original))45 else original
                val allowed=cp==45 || cp==95 || if(gfm)RegexCharacters.category(cp) in listOf(5,6,7,11) else cp==46
                if(ParserCharacters.isSpace(cp) || ParserCharacters.isAlphaNum(cp) || allowed)appendCodePoint(cp)
            }
        }
        var identifier=if(gfm)filtered else buildString {
            var pending=false
            filtered.codePoints().forEach { cp -> if(ParserCharacters.isSpace(cp))pending=isNotEmpty() else { if(pending)append('-');appendCodePoint(cp);pending=false } }
        }
        if(ascii)identifier=Asciify.toAsciiText(identifier)
        if(!gfm) {
            var at=0;while(at<identifier.length && !ParserCharacters.isAlpha(identifier.codePointAt(at)))at+=Character.charCount(identifier.codePointAt(at))
            identifier=identifier.substring(at)
        }
        val base=identifier.ifEmpty { "section" }
        if(base !in used)return base
        for(i in 1..60000) { val candidate="$base-$i";if(candidate !in used)return candidate }
        return base
    }
    fun titleCharacter(point: Int): String = if (ParserCharacters.isSpace(point)) String(Character.toChars(point)) else title[point] ?: String(Character.toChars(point))
    fun removeDoubleQuotes(text: String): String = if (text.length >= 2 && text.first()=='"' && text.last()=='"') text.substring(1,text.lastIndex) else text
    fun unescapeURL(text: String): String = buildString {
        var at=0
        while(at<text.length) {
            val c=text[at++]
            if(c=='\\' && at<text.length && text[at] in "#$%&~_^\\{}") append(text[at++]) else append(c)
        }
    }
    private fun children(x: Inline, mode: Boolean): List<Inline>? = when(x) {
        is Inline.Emph->x.content; is Inline.Strong->x.content; is Inline.Underline->x.content
        is Inline.SmallCaps->x.content; is Inline.Quoted->x.content; is Inline.Span->x.content; is Inline.Link->x.content
        is Inline.Strikeout->if(mode)x.content else null
        is Inline.Superscript->if(mode)x.content else null
        is Inline.Subscript->if(mode)x.content else null
        else->null
    }
    private fun rebuild(x: Inline, xs: List<Inline>): Inline = when(x) {
        is Inline.Emph->x.copy(content=xs); is Inline.Strong->x.copy(content=xs); is Inline.Underline->x.copy(content=xs)
        is Inline.SmallCaps->x.copy(content=xs); is Inline.Quoted->x.copy(content=xs); is Inline.Span->x.copy(content=xs); is Inline.Link->x.copy(content=xs)
        is Inline.Strikeout->x.copy(content=xs); is Inline.Superscript->x.copy(content=xs); is Inline.Subscript->x.copy(content=xs)
        else->error("non-container")
    }
    private class Frame(val input: List<Inline>,val parent: Inline?,var index: Int=0,val out: MutableList<Inline> = ArrayList())
    fun caseTransformWith(exclusions: Set<String>, f: (String)->String, value: Inlines): Inlines {
        val stack=ArrayDeque<Frame>();stack.addLast(Frame(value.values,null))
        while(true) {
            val frame=stack.last()
            if(frame.index==frame.input.size) {
                stack.removeLast()
                if(stack.isEmpty())return Inlines(frame.out)
                stack.last().out.add(rebuild(checkNotNull(frame.parent),frame.out));continue
            }
            val x=frame.input[frame.index++]
            if(x is Inline.Span && x.attr==Attr(classes=listOf("nocasechange"))) { frame.out.addAll(x.content);continue }
            if(x is Inline.Str) { frame.out.add(if(x.text in exclusions)x else Inline.Str(f(x.text)));continue }
            val xs=children(x,true)
            if(xs==null)frame.out.add(x) else stack.addLast(Frame(xs,x))
        }
    }
    fun makeTitlecase(value: Inlines): Inlines {
        val stack=ArrayDeque<Frame>();stack.addLast(Frame(value.values,null));var changed=false
        while(true) {
            val frame=stack.last()
            if(changed) { frame.out.addAll(frame.input.subList(frame.index,frame.input.size));frame.index=frame.input.size }
            if(frame.index==frame.input.size) {
                stack.removeLast();if(stack.isEmpty())return Inlines(frame.out)
                stack.last().out.add(rebuild(checkNotNull(frame.parent),frame.out));continue
            }
            val x=frame.input[frame.index++]
            if(x is Inline.Str && x.text.isNotEmpty()) {
                val cp=x.text.codePointAt(0);frame.out.add(Inline.Str(titleCharacter(cp)+x.text.substring(Character.charCount(cp))));changed=true
            } else {
                val xs=children(x,false)
                if(xs==null)frame.out.add(x) else stack.addLast(Frame(xs,x))
            }
        }
    }
    fun makeTitlecaseAll(exclusions: Set<String>,value: Inlines): Inlines {
        fun sep(x: Inline)=x===Inline.Space || x===Inline.SoftBreak || x===Inline.LineBreak
        val out=ArrayList<Inline>();var at=0;val xs=value.values
        while(at<xs.size) {
            val start=at;while(at<xs.size && !sep(xs[at]))at++
            val word=xs.subList(start,at)
            out.addAll(if(DocumentShared.stringifyInlines(word) in exclusions)word else makeTitlecase(Inlines(word)).values)
            while(at<xs.size && sep(xs[at]))out.add(xs[at++])
        }
        return Inlines(out)
    }
    fun fixPassthroughEscapes(value: Inlines): Inlines = Inlines(object : PandocVisitor() {
        override fun transformInline(value: Inline): Inline = if(value is Inline.Code) value.copy(text=buildString {
            var i=0;while(i<value.text.length) { val c=value.text[i++];if(c=='\\' && i<value.text.length && value.text[i] in "%{}\\")append(value.text[i++]) else append(c) }
        }) else value
    }.walkInlines(value.values))
    fun processHBox(value: Inlines): Inlines = Inlines(object : PandocVisitor() {
        override fun transformInline(value: Inline): Inline = when(value) { Inline.Space,Inline.SoftBreak->Inline.Str("\u00a0");Inline.LineBreak->Inline.Str("");else->value }
    }.walkInlines(value.values))
}
