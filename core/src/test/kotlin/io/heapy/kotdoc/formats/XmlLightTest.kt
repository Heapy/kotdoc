package io.heapy.kotdoc.formats

import io.heapy.kotdoc.xml.*
import org.junit.jupiter.api.Test
import kotlin.test.*

class XmlLightTest {
    @Test
    fun `QName equality compares URI and only unbound prefixes`() {
        assertEquals(XmlName("a", "urn:a", "x"), XmlName("a", "urn:a", "y"))
        assertEquals(XmlName("a", "urn:a", "x").hashCode(), XmlName("a", "urn:a", "y").hashCode())
        assertNotEquals(XmlName("a", prefix = "x"), XmlName("a", prefix = "y"))
        assertNotEquals(XmlName("a", "urn:a"), XmlName("a"))
        assertNotEquals(XmlName("a", ""), XmlName("a"))
    }

    @Test
    fun `immediate text merges across ignored nodes CDATA and entities`() {
        val e = XmlLight.parseElement("<x>a<!--ignored-->b<![CDATA[c]]>&amp;<y>ignored</y>d<?test ?>e</x>")
        assertEquals("abc&de", e.strContent)
        assertEquals(listOf(XmlText("abc&"), XmlElement(XmlName("y"), emptyList(), listOf(XmlText("ignored"))), XmlText("de")), e.content)
    }

    @Test
    fun `content forests retain text and normalize a single root`() {
        assertEquals(listOf(XmlText("a"), XmlElement(XmlName("x"), emptyList(), emptyList()), XmlText("b")), XmlLight.parseContents("a<x/>b"))
        assertEquals(2, XmlLight.parseContents("<x/><y/>").size)
        assertEquals(listOf(XmlElement(XmlName("x"), emptyList(), emptyList())), XmlLight.parseContents(" \n<x/>\t"))
        assertFailsWith<XmlParseException> { XmlLight.parseElement("<x/><y/>") }
        assertFailsWith<XmlParseException> { XmlLight.parseElement("text") }
    }

    @Test
    fun `namespace declarations are retained and apply to the whole start tag`() {
        val e = XmlLight.parseElement("<p:x p:a='one' a='unqualified' xmlns:p='urn:p' xmlns='urn:default'><x/></p:x>")
        assertEquals(XmlName("x", "urn:p"), e.name)
        assertEquals("one", e.attr(XmlName("a", "urn:p")))
        assertEquals("unqualified", e.attr("a"))
        assertEquals("urn:p", e.attr(XmlName("p", prefix = "xmlns")))
        assertEquals("urn:default", e.attr("xmlns"))
        assertEquals(XmlName("x", "urn:default"), e.children.single().name)
    }

    @Test
    fun `custom entities remain text even when they contain markup`() {
        val e = XmlLight.parseElement("<x a='&custom;'>&custom;</x>", mapOf("custom" to "<y>&z;"))
        assertEquals("<y>&z;", e.strContent)
        assertEquals("<y>&z;", e.attr("a"))
        assertTrue(e.children.isEmpty())
        assertFailsWith<XmlParseException> { XmlLight.parseElement("<x>&custom;</x>") }
    }

    @Test
    fun `XML line endings normalize but CDATA line endings remain literal`() {
        val e = XmlLight.parseElement("<x a='one\r\ntwo\rthree\tfour'>one\r\ntwo\rthree<![CDATA[\rfour]]></x>")
        assertEquals("one\ntwo\nthree\tfour", e.attr("a"))
        assertEquals("one\ntwo\nthree\rfour", e.strContent)
    }
}
