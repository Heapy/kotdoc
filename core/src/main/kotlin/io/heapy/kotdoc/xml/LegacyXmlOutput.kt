// xml 1.3.14 Text.XML.Light.Output, (c) 2007 Galois Inc.; BSD-3-Clause, licenses/.
package io.heapy.kotdoc.xml

import io.heapy.kotdoc.layout.CharacterString
import io.heapy.kotdoc.layout.CharacterStringChars
import io.heapy.kotdoc.parsing.RegexCharacters

data class LegacyXmlPrintConfig(val shortEmptyTag: (LegacyXmlName)->Boolean={true},val prettify: Boolean=false)

/** Complete finite output productions of the original String-based XML.Light. */
object LegacyXmlOutput {
    val header=legacyChars("<?xml version='1.0' ?>")
    val defaultConfig=LegacyXmlPrintConfig()
    val prettyConfig=LegacyXmlPrintConfig(prettify=true)
    fun showQName(name: LegacyXmlName)=CharacterStringChars.concat(listOfNotNull(name.prefix,name.prefix?.let {legacyChars(":")},name.local))
    fun tagEnd(name: LegacyXmlName,suffix: CharacterString=CharacterStringChars.empty)=CharacterStringChars.concat(listOf(legacyChars("</"),showQName(name),legacyChars(">"),suffix))
    fun showAttr(attribute: LegacyXmlAttribute)=CharacterStringChars.concat(listOf(showQName(attribute.name),legacyChars("=\""),escape(attribute.value),legacyChars("\"")))
    fun showElement(element: LegacyXmlElement)=printContent(element,defaultConfig)
    fun showContent(content: LegacyXmlContent)=printContent(content,defaultConfig)
    fun showCData(text: LegacyXmlText)=showContent(text)
    fun showTopElement(element: LegacyXmlElement)=CharacterStringChars.append(header,showElement(element))
    fun prettyElement(element: LegacyXmlElement)=printContent(element,prettyConfig)
    fun prettyContent(content: LegacyXmlContent)=printContent(content,prettyConfig)
    fun prettyTopElement(element: LegacyXmlElement)=printTopElement(element,prettyConfig)
    fun printTopElement(element: LegacyXmlElement,config: LegacyXmlPrintConfig)=CharacterStringChars.concat(listOf(header,legacyChars("\n"),printContent(element,config),legacyChars("\n")))
    fun escape(text: CharacterString): CharacterString {
        val out=mutableListOf<Int>()
        for(cp in text.points()) {
            val replacement=when(cp) {60->"&lt;";62->"&gt;";38->"&amp;";34->"&quot;";39->"&#39;";else->if(cp==10 || RegexCharacters.isPrint(cp)) null else "&#$cp;"}
            if(replacement==null) out.add(cp) else replacement.forEach {out.add(it.code)}
        }
        return CharacterString(out.toIntArray())
    }
    private class Content(val value: LegacyXmlContent,val indent: Int)
    private class RenderText(val value: LegacyXmlText,val indent: Int)
    private class Raw(val value: CharacterString)
    private class Indent(val count: Int)
    fun printContent(content: LegacyXmlContent,config: LegacyXmlPrintConfig): CharacterString {
        val chunks=mutableListOf<CharacterString>();val todo=ArrayDeque<Any>();todo.addLast(Content(content,0))
        fun emit(s: String) {chunks.add(legacyChars(s))}
        fun emit(s: CharacterString) {chunks.add(s)}
        fun indent(n: Int) {if(n>0) emit(" ".repeat(n))}
        while(todo.isNotEmpty()) when(val work=todo.removeLast()) {
            is Raw -> emit(work.value)
            is Indent -> indent(work.count)
            is RenderText -> {
                val t=work.value;indent(work.indent)
                when(t.kind) {
                    LegacyXmlTextKind.CDataRaw -> emit(t.text)
                    LegacyXmlTextKind.CDataVerbatim -> {
                        emit("<![CDATA[");val ps=t.text.points();val out=mutableListOf<Int>();var at=0
                        while(at<ps.size) {
                            if(at+2<ps.size && ps[at]==93 && ps[at+1]==93 && ps[at+2]==62) { "]]]]><![CDATA[>".forEach {out.add(it.code)};at+=3 }
                            else out.add(ps[at++])
                        }
                        emit(CharacterString(out.toIntArray()));emit("]]>")
                    }
                    LegacyXmlTextKind.CDataText -> {
                        val escaped=escape(t.text)
                        if(!config.prettify || work.indent==0) emit(escaped) else {
                            val out=mutableListOf<Int>()
                            escaped.points().forEach {cp->out.add(cp);if(cp==10) repeat(work.indent) {out.add(32)}}
                            emit(CharacterString(out.toIntArray()))
                        }
                    }
                }
            }
            is Content -> when(val c=work.value) {
                is LegacyXmlCRef -> {emit("&");emit(c.reference);emit(";")}
                is LegacyXmlText -> todo.addLast(RenderText(c,work.indent))
                is LegacyXmlElement -> {
                    indent(work.indent);emit("<");emit(showQName(c.name))
                    c.attributes.forEach {emit(" ");emit(showAttr(it))}
                    when {
                        c.content.isEmpty() && c.name.local.points().firstOrNull()==63 -> emit(" ?>")
                        c.content.isEmpty() && config.shortEmptyTag(c.name) -> emit(" />")
                        c.content.size==1 && c.content[0] is LegacyXmlText -> {emit(">");todo.addLast(Raw(tagEnd(c.name)));todo.addLast(RenderText(c.content[0] as LegacyXmlText,0))}
                        else -> {
                            emit(">");if(config.prettify) emit("\n")
                            todo.addLast(Raw(tagEnd(c.name)));todo.addLast(Indent(work.indent))
                            for(child in c.content.asReversed()) {
                                if(config.prettify) todo.addLast(Raw(legacyChars("\n")))
                                todo.addLast(Content(child,if(config.prettify) work.indent+2 else work.indent))
                            }
                        }
                    }
                }
            }
        }
        return CharacterStringChars.concat(chunks)
    }
}
