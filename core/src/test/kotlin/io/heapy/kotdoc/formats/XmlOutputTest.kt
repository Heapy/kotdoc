package io.heapy.kotdoc.formats

import io.heapy.kotdoc.xml.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class XmlOutputTest {
    @Test fun `empty element differs from a single empty text node`() {
        assertEquals("<p />",XmlOutput.showElement(XmlElement(XmlName("p"),emptyList(),emptyList())))
        assertEquals("<p></p>",XmlOutput.showElement(XmlElement(XmlName("p"),emptyList(),listOf(XmlText("")))))
    }
    @Test fun `escapes text and attributes while preserving prefixes`() {
        val value=XmlElement(XmlName("p","urn:p","q"),listOf(XmlAttribute(XmlName("a"),"<>&\"'")),listOf(XmlText("<>&\"'")))
        assertEquals("<q:p a=\"&lt;&gt;&amp;&quot;&#39;\">&lt;&gt;&amp;&quot;&#39;</q:p>",XmlOutput.showElement(value))
    }
    @Test fun `CDATA splits terminators and raw content and references remain literal`() {
        assertEquals("<![CDATA[a]]]]><![CDATA[>b]]>",XmlOutput.showContent(XmlText("a]]>b",XmlTextKind.Verbatim)))
        assertEquals("<raw>",XmlOutput.showContent(XmlText("<raw>",XmlTextKind.Raw)))
        assertEquals("&name;",XmlOutput.showContent(XmlCRef("name")))
    }
    @Test fun `pretty inline tags retain text and indent nested block content`() {
        val element=XmlLight.parseElement("<root><p>a<b>b</b>c\nd</p><block><x/></block></root>")
        val config=XmlPrintConfig(inlineTag={it.local in listOf("p","b")},prettify=true)
        assertEquals("<root>\n  <p>a<b>b</b>c\n  d</p>\n  <block>\n    <x />\n  </block>\n</root>",XmlOutput.printContent(element,config))
    }
}
