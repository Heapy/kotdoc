// Port of Text.Blaze.Tests 0.8.3.0's ten properties.
// Copyright (c) 2010 Jasper Van der Jeugt; BSD-3-Clause.
package io.heapy.kotdoc.markup

import io.heapy.kotdoc.layout.DocLayout
import io.heapy.kotdoc.layout.CharacterString
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import kotlin.random.Random
import kotlin.test.*

class BlazePropertiesTest {
    private fun same(x: MarkupM<*>, y: MarkupM<*>) {
        val xs = BlazeRenderer.renderStringUtf8(x)
        val xt = BlazeRenderer.renderText(x).toByteArray(Charsets.UTF_8)
        val xu = BlazeRenderer.renderUtf8(x)
        val ys = BlazeRenderer.renderStringUtf8(y)
        val yt = BlazeRenderer.renderText(y).toByteArray(Charsets.UTF_8)
        val yu = BlazeRenderer.renderUtf8(y)
        assertContentEquals(xs, ys)
        assertContentEquals(xt, yt)
        assertContentEquals(xu, yu)
        assertContentEquals(xs, yt)
        assertContentEquals(xt, yu)
    }

    private fun string(r: Random): CharacterString = CharacterString(IntArray(r.nextInt(40)) {
        // Like the upstream Markup generator, avoid control characters. Include
        // supplementary scalars as well as all punctuation relevant to escaping.
        if (r.nextBoolean()) r.nextInt(32, 127) else listOf(0xe9, 0x3bb, 0x20ac, 0x1f600, 0x10ffff).random(r)
    })

    private fun markup(r: Random, depth: Int): Markup = concatMarkup(List(r.nextInt(4)) {
        var child = when (r.nextInt(if (depth > 0) 4 else 3)) {
            0 -> Blaze.customLeaf(Blaze.stringTag(listOf("img", "br", "area").random(r)), false)
            1 -> Blaze.string(string(r))
            2 -> Blaze.empty
            else -> Blaze.customParent(Blaze.stringTag(listOf("p", "div", "table").random(r)), markup(r, depth - 1))
        }
        repeat(r.nextInt(5)) {
            val key = listOf("id", "class", "name").random(r)
            child = child.with(Blaze.attribute(Blaze.stringTag(key), Blaze.stringTag(" $key=\""), Blaze.stringValue(string(r))))
        }
        child
    }, Unit)

    // Preserve the source property's intentionally permissive UTF-8 predicate;
    // it checks byte lengths/continuations, not scalar validity or overlong forms.
    private fun sourceUtf8Predicate(bytes: ByteArray): Boolean {
        var at = 0
        while (at < bytes.size) {
            val b = bytes[at].toInt() and 255
            val n = when (b) {
                in 0..0x7f -> 1
                in 0xc0..0xdf -> 2
                in 0xe0..0xef -> 3
                in 0xf0..0xf7 -> 4
                else -> return false
            }
            if (n > bytes.size - at) return false
            for (i in 1..<n) if ((bytes[at + i].toInt() and 255) !in 0x80..0xbf) return false
            at += n
        }
        return true
    }

    @TestFactory fun originalProperties(): List<DynamicTest> {
        val properties: List<Pair<String, (Random) -> Unit>> = listOf(
            "left identity Monoid law" to { r -> val h = markup(r, 4); same(Blaze.empty.then(h), h) },
            "right identity Monoid law" to { r -> val h = markup(r, 4); same(h.then(Blaze.empty), h) },
            "associativity Monoid law" to { r ->
                val x = markup(r, 4); val y = markup(r, 4); val z = markup(r, 4)
                same(x.then(y.then(z)), x.then(y).then(z))
            },
            "mconcat Monoid law" to { r ->
                val xs = List(r.nextInt(10)) { markup(r, 4) }
                same(concatMarkup(xs, Unit), xs.asReversed().fold(Blaze.empty) { tail, x -> x.then(tail) })
            },
            "identity Applicative law" to { r ->
                val x = markup(r, 4)
                same(pureMarkup<(Lazy<Unit>) -> Unit> { it.value }.applyLazy(x), x)
            },
            "post escaping characters" to { r ->
                val bytes = BlazeRenderer.renderUtf8(Blaze.string(string(r)))
                assertTrue(bytes.none { (it.toInt() and 255) in listOf(34, 39, 60, 62) })
            },
            "valid UTF-8" to { r -> assertTrue(sourceUtf8Predicate(BlazeRenderer.renderUtf8(markup(r, 4)))) },
            "external </ sequence" to { r ->
                val bytes = BlazeRenderer.renderUtf8(Blaze.string(string(r)).external())
                assertFalse(bytes.toString(Charsets.ISO_8859_1).contains("</"))
            },
            "well nested <>" to { r ->
                var open = false
                for (byte in BlazeRenderer.renderUtf8(markup(r, 4))) when (byte.toInt() and 255) {
                    60 -> { assertFalse(open); open = true }
                    62 -> { assertTrue(open); open = false }
                }
                assertFalse(open)
            },
            "unsafeByteString id" to { r ->
                val bytes = r.nextBytes(r.nextInt(80))
                assertContentEquals(bytes, BlazeRenderer.renderUtf8(Blaze.unsafeByteString(bytes)))
            },
        )
        return properties.mapIndexed { i, (name, property) -> DynamicTest.dynamicTest(name) {
            val random = Random(0xB1A2E + i)
            repeat(1_000) { property(random) }
        } }
    }

    @Test fun attributeCompositionRetainsApplicationOrderAndDeepTrees() {
        val a=Blaze.customAttribute(Blaze.stringTag("a"),Blaze.textValue("1"))
        val b=Blaze.customAttribute(Blaze.stringTag("b"),Blaze.textValue("2"))
        val leaf=Blaze.customLeaf(Blaze.stringTag("br"),false)
        assertEquals("<br a=\"1\" b=\"2\">",BlazeRenderer.renderText(leaf.with(a+b)))
        assertEquals(BlazeRenderer.renderText(leaf.with(a).with(b)),BlazeRenderer.renderText(leaf.with(a+b)))
        var attrs=Attribute.Empty
        repeat(10_000) { attrs += a }
        val tree=leaf.with(attrs)
        assertEquals("<br"+" a=\"1\"".repeat(10_000)+">",BlazeRenderer.renderText(tree))
        assertEquals(BlazeRenderer.renderText(tree),DocLayout.Text.render(null,BlazeLayout.layoutMarkup(tree)))
    }

    @Test fun lazyChunksAreMemoizedAndIgnoredAttributesStayUnevaluated() {
        var reads = 0
        val chunks = sequence { reads++; yield("a"); reads++; yield("b"); reads++ }
        val m = Blaze.lazyText(chunks)
        assertEquals(0, reads)
        assertFalse(m.isEmpty())
        assertEquals(1, reads)
        assertEquals("ab", BlazeRenderer.renderText(m))
        assertEquals(3, reads)
        assertEquals("ab", BlazeRenderer.renderText(m))
        assertEquals(3, reads)
        val unused = Blaze.lazyTextValue(sequence { error("unused attribute") })
        val content = Blaze.text("text").with(Blaze.customAttribute(Blaze.stringTag("x"), unused))
        assertEquals("text", BlazeRenderer.renderText(content))
        assertEquals("text", BlazeRenderer.renderUtf8(content).toString(Charsets.UTF_8))
        assertFalse(content.isEmpty())
        var choiceReads = 0
        val value = Blaze.lazyTextValue(sequence { choiceReads++; yield("a"); error("unneeded tail") })
        assertFalse(value.value.isEmpty())
        assertEquals(1, choiceReads)
    }
}
