// Text.Blaze.Renderer.{String,Text,Utf8,Pretty} 0.8.3.0.
// Copyright (c) 2010 Jasper Van der Jeugt, BSD-3-Clause; licenses/Blaze-BSD-3-Clause.txt.
package io.heapy.kotdoc.markup

import io.heapy.kotdoc.layout.CharacterString
import io.heapy.kotdoc.layout.CharacterStringChars
import io.heapy.kotdoc.layout.TextChars
import io.heapy.kotdoc.parsing.Utf8Text
import java.io.ByteArrayOutputStream

class BlazeUtf8Exception(val byte: Int) : IllegalArgumentException(
    "Cannot decode byte '\\x${byte.toString(16)}': Data.Text.Encoding: Invalid UTF-8 stream")

/** Full outputs retain the independent String/Text/byte backends. */
object BlazeRenderer {
    private sealed interface Action {
        class Literal(val text: String) : Action
        class Static(val value: StaticString) : Action
        class Choice(val value: ChoiceString) : Action
    }
    private class Attributes(val parts: List<Action>,val next: Attributes?)
    private class Node(val markup: MarkupM<*>,val attributes: Attributes?,val indent: Long)
    private class Attrs(val value: Attributes?)
    private fun actions(markup: MarkupM<*>,pretty: Boolean): Sequence<Action> = sequence {
        val todo=ArrayDeque<Any>();todo.addLast(Node(markup,null,0))
        fun schedule(parts: List<Any>) { parts.asReversed().forEach(todo::addLast) }
        fun ind(i: Long)=Action.Literal(" ".repeat(i.coerceAtLeast(0).also { require(it <= Int.MAX_VALUE) }.toInt()))
        while(todo.isNotEmpty()) when(val task=todo.removeLast()) {
            is Action -> yield(task)
            is Attrs -> { var a=task.value;while(a != null) { a.parts.forEach { yield(it) };a=a.next } }
            is Node -> {
                val m=task.markup;val a=task.attributes;val i=task.indent
                when(m) {
                    is MarkupM.Deferred -> todo.addLast(Node(m.node.value,a,i))
                    is MarkupM.Append -> { todo.addLast(Node(m.right,a,i));todo.addLast(Node(m.left,a,i)) }
                    is MarkupM.Parent -> schedule(buildList {
                        if(pretty) add(ind(i));add(Action.Static(m.open));add(Attrs(a));add(Action.Literal(if(pretty) ">\n" else ">"))
                        add(Node(m.content,null,if(pretty) i+4 else i));if(pretty) add(ind(i));add(Action.Static(m.close));if(pretty) add(Action.Literal("\n"))
                    })
                    is MarkupM.CustomParent -> schedule(buildList {
                        if(pretty) add(ind(i));add(Action.Literal("<"));add(Action.Choice(m.tag));add(Attrs(a));add(Action.Literal(if(pretty) ">\n" else ">"))
                        add(Node(m.content,null,if(pretty) i+4 else i));if(pretty) add(ind(i));add(Action.Literal("</"));add(Action.Choice(m.tag));add(Action.Literal(if(pretty) ">\n" else ">"))
                    })
                    is MarkupM.Leaf -> schedule(buildList {
                        if(pretty) add(ind(i));add(Action.Static(m.open));add(Attrs(a));add(Action.Static(m.close));if(pretty) add(Action.Literal("\n"))
                    })
                    is MarkupM.CustomLeaf -> schedule(buildList {
                        if(pretty) add(ind(i));add(Action.Literal("<"));add(Action.Choice(m.tag));add(Attrs(a));add(Action.Literal((if(m.close) " />" else ">") + if(pretty) "\n" else ""))
                    })
                    is MarkupM.AddAttribute -> todo.addLast(Node(m.content,Attributes(listOf(Action.Static(m.key),Action.Choice(m.value),Action.Literal("\"")),a),i))
                    is MarkupM.AddCustomAttribute -> todo.addLast(Node(m.content,Attributes(listOf(Action.Literal(" "),Action.Choice(m.key),Action.Literal("=\""),Action.Choice(m.value),Action.Literal("\"")),a),i))
                    is MarkupM.Content -> schedule(buildList { if(pretty) add(ind(i));add(Action.Choice(m.content));if(pretty) add(Action.Literal("\n")) })
                    is MarkupM.Comment -> schedule(buildList { if(pretty) add(ind(i));add(Action.Literal("<!-- "));add(Action.Choice(m.comment));add(Action.Literal(if(pretty) " -->\n" else " -->")) })
                    is MarkupM.Empty -> Unit
                }
            }
        }
    }
    private sealed interface Fragment {
        class Static(val value: StaticString) : Fragment
        class Points(val value: CharacterString,val escaped: Boolean=false) : Fragment
        class Text(val value: String,val escaped: Boolean=false) : Fragment
        class Bytes(val value: ByteArray) : Fragment
    }
    private fun fragments(actions: Sequence<Action>): Sequence<Fragment> = sequence {
        val input=actions.iterator();val todo=ArrayDeque<Any>()
        while(todo.isNotEmpty() || input.hasNext()) when(val x=if(todo.isEmpty()) input.next() else todo.removeLast()) {
            is Action.Literal -> yield(Fragment.Text(x.text))
            is Action.Static -> yield(Fragment.Static(x.value))
            is Action.Choice -> todo.addLast(x.value)
            is ChoiceString.Deferred -> todo.addLast(x.value.value)
            is ChoiceString.Static -> yield(Fragment.Static(x.value))
            is ChoiceString.String -> yield(Fragment.Points(escape(x.value),true))
            is ChoiceString.Text -> yield(Fragment.Text(escapeText(x.value),true))
            is ChoiceString.Bytes -> yield(Fragment.Bytes(x.rawBytes))
            is ChoiceString.PreEscaped -> when(val s=x.value.force()) {
                is ChoiceString.String -> yield(Fragment.Points(s.value))
                is ChoiceString.Text -> yield(Fragment.Text(s.value))
                else -> todo.addLast(s)
            }
            is ChoiceString.External -> when(val s=x.value.force()) {
                is ChoiceString.String -> if(!containsEnd(s.value)) yield(Fragment.Points(s.value))
                is ChoiceString.Text -> if(!s.value.contains("</")) yield(Fragment.Text(s.value))
                is ChoiceString.Bytes -> if(!containsEnd(s.rawBytes)) yield(Fragment.Bytes(s.rawBytes))
                else -> todo.addLast(s)
            }
            is ChoiceString.Append -> { todo.addLast(x.right);todo.addLast(x.left) }
            ChoiceString.Empty -> Unit
        }
    }
    fun renderString(markup: MarkupM<*>): CharacterString = stringFragments(fragments(actions(markup,false)))
    fun renderPretty(markup: MarkupM<*>): CharacterString = stringFragments(fragments(actions(markup,true)))
    fun fromChoiceString(value: ChoiceString,suffix: CharacterString=CharacterStringChars.empty): CharacterString = stringFragments(fragments(sequenceOf(Action.Choice(value))),suffix)
    private fun stringFragments(xs: Sequence<Fragment>,suffix: CharacterString=CharacterStringChars.empty): CharacterString {
        val input=xs.iterator()
        fun render(): CharacterString {
            val chunks=ArrayList<CharacterString>()
            while(input.hasNext()) when(val x=input.next()) {
                is Fragment.Points -> chunks.add(x.value)
                is Fragment.Text -> chunks.add(CharacterStringChars.fromString(x.value))
                is Fragment.Bytes -> chunks.add(CharacterString(x.value.map { it.toInt() and 255 }.toIntArray()))
                is Fragment.Static -> if(x.value.prefix != null) chunks.add(x.value.prefix) else {
                    chunks.add(x.value.appendString(lazy { render() }));return CharacterStringChars.concat(chunks)
                }
            }
            chunks.add(suffix);return CharacterStringChars.concat(chunks)
        }
        return render()
    }
    fun renderText(markup: MarkupM<*>,decoder: (ByteArray)->String=::decodeUtf8): String = buildString {
        for(x in fragments(actions(markup,false))) append(when(x) {
            is Fragment.Static -> x.value.text
            is Fragment.Text -> x.value
            is Fragment.Points -> TextChars.fromPoints(x.value.points())
            is Fragment.Bytes -> TextChars.fromString(decoder(x.value.copyOf()))
        })
    }
    fun renderUtf8(markup: MarkupM<*>): ByteArray = ByteArrayOutputStream().also { out ->
        for(x in fragments(actions(markup,false))) out.write(when(x) {
            is Fragment.Static -> x.value.rawUtf8
            is Fragment.Text -> (if(x.escaped) stripHtmlControls(x.value) else x.value).toByteArray(Charsets.UTF_8)
            is Fragment.Points -> encodeString(if(x.escaped) CharacterString(x.value.points().filter(::allowedHtmlChar).toIntArray()) else x.value)
            is Fragment.Bytes -> x.value
        })
    }.toByteArray()
    // blaze-builder 0.4.4.1 Html.Utf8/Html.Word discard these controls only
    // in escaped String/Text builders; raw bytes, static and pre-escaped data retain them.
    private fun allowedHtmlChar(c: Int)=c != 127 && (c >= 32 || c == 9 || c == 10 || c == 13)
    private fun stripHtmlControls(s: String)=s.filter { allowedHtmlChar(it.code) }
    fun renderTextLenient(markup: MarkupM<*>)=renderText(markup,Utf8Text::decodeLenient)
    fun renderStringUtf8(markup: MarkupM<*>)=encodeString(renderString(markup))
    fun encodeString(value: CharacterString): ByteArray = ByteArrayOutputStream().also { out ->
        for(c in value.points()) when {
            c < 0x80 -> out.write(c)
            c < 0x800 -> { out.write(0xc0 or (c shr 6));out.write(0x80 or (c and 63)) }
            c < 0x10000 -> { out.write(0xe0 or (c shr 12));out.write(0x80 or ((c shr 6) and 63));out.write(0x80 or (c and 63)) }
            else -> { out.write(0xf0 or (c shr 18));out.write(0x80 or ((c shr 12) and 63));out.write(0x80 or ((c shr 6) and 63));out.write(0x80 or (c and 63)) }
        }
    }.toByteArray()
    fun decodeUtf8(bytes: ByteArray): String {
        var i=0
        while(i < bytes.size) {
            val lead=bytes[i].toInt() and 255
            if(lead < 128) { i++;continue }
            val n=when(lead) { in 0xc2..0xdf -> 2;in 0xe0..0xef -> 3;in 0xf0..0xf4 -> 4;else -> throw BlazeUtf8Exception(lead) }
            for(k in 1..<minOf(n,bytes.size-i)) {
                val c=bytes[i+k].toInt() and 255
                if(c !in 0x80..0xbf || k==1 && when(lead) { 0xe0 -> c < 0xa0;0xed -> c > 0x9f;0xf0 -> c < 0x90;0xf4 -> c > 0x8f;else -> false }) throw BlazeUtf8Exception(lead)
            }
            // text's skipIncomplete folds the partial code point with a strict
            // builder, so the last byte's error is forced first.
            if(n > bytes.size-i) throw BlazeUtf8Exception(bytes.last().toInt() and 255)
            i+=n
        }
        return bytes.toString(Charsets.UTF_8)
    }
    private fun containsEnd(value: CharacterString): Boolean { val a=value.points();return (0..<a.size-1).any { a[it]==60 && a[it+1]==47 } }
    private fun containsEnd(value: ByteArray): Boolean=(0..<value.size-1).any { value[it]==60.toByte() && value[it+1]==47.toByte() }
    fun escapeText(value: String): String=buildString { TextChars.fromString(value).codePoints().forEach { append(escapeCode(it) ?: String(Character.toChars(it))) } }
    private fun escape(value: CharacterString)=CharacterStringChars.concat(value.points().map { c -> escapeCode(c)?.let(CharacterStringChars::fromString) ?: CharacterString(intArrayOf(c)) })
    private fun escapeCode(c: Int): String?=when(c) { 60 -> "&lt;";62 -> "&gt;";38 -> "&amp;";34 -> "&quot;";39 -> "&#39;";else -> null }
}
