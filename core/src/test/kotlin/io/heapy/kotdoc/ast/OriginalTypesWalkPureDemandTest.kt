package io.heapy.kotdoc.ast

import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

private fun badInlineList(tail: Boolean = false): List<Inline> = object : AbstractList<Inline>() {
    override val size = if (tail) 2 else 1
    override fun get(index: Int): Inline = if (tail && index == 0) Inline.Str("a") else error(if(tail)"tail" else "child")
}
private fun <T> badSpine(message: String): List<T> = object : AbstractList<T>() {
    override val size: Int get() = error(message)
    override fun get(index: Int): T = error(message)
}
private fun <T> badTail(first: T, message: String): List<T> = object : AbstractList<T>() {
    override val size = 2
    override fun get(index: Int): T = if(index==0)first else error(message)
}
private fun definitionWalk(terms: List<Inline>, definitions: List<List<Block>>): Block.DefinitionList =
    Walk.inlineInBlock.walk({Inline.Space},Block.DefinitionList(listOf(terms to definitions))) as Block.DefinitionList
private object StopMonad : WalkMonad<StopMonad.Tag> {
    class Tag
    class Effect<A>(val value: A?, val error: String?) : WalkEffect<Tag, A>
    override fun <A> pure(value: A): WalkEffect<Tag, A> = Effect(value, null)
    override fun <A, B> flatMap(value: WalkEffect<Tag, A>, next: (A) -> WalkEffect<Tag, B>): WalkEffect<Tag, B> {
        value as Effect<A>
        @Suppress("UNCHECKED_CAST")
        return if (value.error != null) Effect(null, value.error) else next(value.value as A)
    }
}
private class DemandBox(value: () -> Inline) { val value by lazy(value) }
private object DemandBoxDictionary : Walkable<Inline, DemandBox> {
    override fun walk(transform: (Inline) -> Inline, value: DemandBox): DemandBox = walkLazy({ transform(it()) }, { value })
    override fun walkLazy(transform: (() -> Inline) -> Inline, value: () -> DemandBox): DemandBox = DemandBox { transform { value().value } }
    override fun <F> walkM(monad: WalkMonad<F>, transform: (Inline) -> WalkEffect<F, Inline>, value: DemandBox): WalkEffect<F, DemandBox> =
        monad.flatMap(transform(value.value)) { result -> monad.pure(DemandBox { result }) }
    override fun <C> query(transform: (Inline) -> C, value: DemandBox, monoid: WalkMonoid<C>): C = transform(value.value)
}
private class DemandShown(val text: String)
private fun demandResponse(probe: String): JsonElement = try {
    val bad = badInlineList()
    val value: Any = when (probe) {
        "walk-constant-bad-child" -> Walk.inlineInInline.walk({ Inline.Space }, Inline.Emph(bad))
        "walk-constant-bottom-root" -> Walk.inlineInInline.walkLazy({ Inline.Space }, { error("root") })
        "walk-first-bottom-inline" -> when(val r=Walk.inlineInInline.queryLazy({WalkOptional.Some("hit")},{error("root")},WalkMonoids.first())) { is WalkOptional.Some -> "Just ${AstShow.show(r.value)}"; WalkOptional.None -> "Nothing" }
        "walk-constructor-bad-child" -> Walk.inlineInInline.walk({ if(it is Inline.Emph)Inline.Space else it }, Inline.Emph(bad))
        "walk-as-pattern-drop-children" -> Walk.inlineInInline.walk({ if(it is Inline.Emph)it.copy(content=it.content.take(0)) else it }, Inline.Emph(bad))
        "walk-id-bad-child" -> Walk.inlineInInline.walk({it},Inline.Emph(bad))
        "walk-forcing-head" -> Walk.inlineInInline.walk({if(it is Inline.Emph && it.content.firstOrNull()==Inline.Space)Inline.Space else it},Inline.Emph(bad))
        "walk-list-constant-bad-child" -> Walk.inlinesInInline.walk({emptyList()},Inline.Emph(bad))
        "walk-list-prefix-bad-tail-element" -> Walk.inlinesInInline.walk({it.take(1)},Inline.Emph(badInlineList(true)))
        "walk-list-constant-bottom-spine" -> Walk.inlinesInInlines.walkLazy({emptyList()},{error("root")})
        "walk-list-constant-partial-spine" -> Walk.inlinesInInlines.walkLazy({emptyList()},{object:AbstractList<Inline>() {
            override val size:Int get()=error("tail")
            override fun get(index:Int):Inline=if(index==0)Inline.Space else error("tail")
        }})
        "walk-block-constant-bad-child", "walk-block-constructor-bad-child" -> Walk.blockInBlock.walk({if(probe=="walk-block-constant-bad-child" || it is Block.BlockQuote)Block.HorizontalRule else it},Block.BlockQuote(object:AbstractList<Block>() {
            override val size=1; override fun get(index:Int):Block=error("child")
        }))
        "walk-inline-in-block-constant" -> Walk.inlineInBlock.walkLazy({Inline.Space},{Block.Para(bad)})
        "walk-block-in-inline-constant" -> Walk.blockInInline.walkLazy({Block.HorizontalRule},{Inline.Note(object:AbstractList<Block>() {
            override val size=1;override fun get(index:Int):Block=error("child")
        })})
        "walk-meta-value-constant" -> Walk.metaValueInMetaValue.walk({MetaValue.MetaString("done")},MetaValue.MetaList(object:AbstractList<MetaValue>() {
            override val size=1;override fun get(index:Int):MetaValue=error("child")
        }))
        "walk-meta-inline-constant" -> Walk.inlineInMetaValue.walkLazy({Inline.Space},{MetaValue.MetaInlines(bad)})
        "walk-traversable-constant" -> Walk.traversable(Walk.inlineInInline,WalkContainers.list<Inline>()).walkLazy({Inline.Space},{bad})
        "walk-many-constant" -> DemandShown("Many {unMany = fromList " + AstShow.show(Walk.traversable(Walk.inlineInInline,WalkContainers.inlines).walkLazy({Inline.Space},{Inlines(bad)}).values) + "}")
        "walk-pair-first" -> Walk.lazyPair(Walk.inlineInInline,Walk.inlineInInline).walkLazy({Inline.Space},{WalkLazyPair({Inline.Space},{Inline.Emph(bad)})}).first
        "walk-meta-constant-bottom-root" -> DemandShown(AstShow.meta(Walk.metaInMeta.walkLazy({emptyMap()},{error("root")})))
        "walk-pandoc-constant-bottom-root" -> Walk.pandocInPandoc.walkLazy({Pandoc()},{error("root")})
        "walk-blocks-constant-bottom-root" -> Walk.blocksInBlocks.walkLazy({emptyList()},{error("root")})
        "walkM-pair-left-failure" -> {
            val dictionary=Walk.lazyPair(Walk.inlineInInline,Walk.inlineInInline)
            val result=dictionary.walkM(StopMonad,{StopMonad.Effect<Inline>(null,"stop")},WalkLazyPair({Inline.Space},{error("right")})) as StopMonad.Effect<*>
            DemandShown("Left " + AstShow.show(result.error!!))
        }
        "walk-large-list-prefix" -> Walk.traversable(Walk.inlineInInline,WalkContainers.list<Inline>()).walkLazy({Inline.Space},{object:AbstractList<Inline>() {
            override val size=1 shl 30;override fun get(index:Int):Inline=error("child")
        }}).take(2)
        "walk-pair-query-first" -> {
            val result=Walk.lazyPair(Walk.inlineInInline,Walk.inlineInInline).queryLazy({WalkOptional.Some("hit")},{WalkLazyPair({Inline.Space},{error("right")})},WalkMonoids.first())
            DemandShown(when(result){is WalkOptional.Some->"Just "+AstShow.show(result.value);WalkOptional.None->"Nothing"})
        }
        "walk-custom-pair-first" -> {
            val output=Walk.lazyPair(DemandBoxDictionary,Walk.inlineInInline).walkLazy({Inline.Space},{WalkLazyPair({DemandBox{error("child")}},{error("right")})}).first
            DemandShown("DemandBox "+AstShow.show(output.value,11))
        }
        "walk-pair-first-bottom-right" -> Walk.lazyPair(Walk.inlineInInline,Walk.inlineInInline).walk({it},WalkLazyPair({Inline.Space},{error("right")})).first
        "walk-custom-constant", "walk-custom-list-constant" -> {
            val box=DemandBox { error("child") }
            val output=if(probe=="walk-custom-constant")DemandBoxDictionary.walkLazy({Inline.Space},{box})
                else Walk.traversable(DemandBoxDictionary,WalkContainers.list<DemandBox>()).walkLazy({Inline.Space},{listOf(box)}).single()
            val shown="DemandBox " + AstShow.show(output.value,11)
            DemandShown(if(probe=="walk-custom-constant")shown else "[$shown]")
        }
        "walk-definition-outer-prefix", "walk-definition-force-outer-tail" -> {
            val input=badTail(listOf<Inline>(Inline.Str("a")) to emptyList<List<Block>>(),"tail")
            val output=(Walk.inlineInBlock.walk({Inline.Space},Block.DefinitionList(input)) as Block.DefinitionList).items
            if(probe=="walk-definition-outer-prefix")output.take(1) else output
        }
        "walk-definition-terms-ignore-defs" -> definitionWalk(listOf(Inline.Str("a")),badSpine("defs")).items.first().first
        "walk-definition-defs-ignore-terms" -> definitionWalk(badSpine("terms"),listOf(listOf(Block.Plain(listOf(Inline.Str("a")))))).items.first().second
        "walk-definition-terms-prefix" -> definitionWalk(badTail(Inline.Str("a") as Inline,"term-tail"),badSpine("defs")).items.first().first.take(1)
        "walk-definition-defs-prefix" -> definitionWalk(badSpine("terms"),badTail(listOf<Block>(Block.Plain(listOf(Inline.Str("a")))),"defs-tail")).items.first().second.take(1)
        "walk-definition-def-block-prefix" -> definitionWalk(badSpine("terms"),listOf(badTail(Block.Plain(listOf(Inline.Str("a"))) as Block,"block-tail"))).items.first().second.first().take(1)
        "walk-definition-force-defs" -> definitionWalk(listOf(Inline.Str("a")),badSpine("defs")).items.first().second
        "walk-definition-force-terms" -> definitionWalk(badSpine("terms"),emptyList()).items.first().first
        "walk-definition-empty-prefix" -> {
            val input=object:AbstractList<Pair<List<Inline>,List<List<Block>>>>() {override val size=1;override fun get(index:Int):Pair<List<Inline>,List<List<Block>>> = error("pair")}
            (Walk.inlineInBlock.walk({Inline.Space},Block.DefinitionList(input)) as Block.DefinitionList).items.take(0)
        }
        "walk-line-inner-spine-undemanded" -> (Walk.inlineInBlock.walk({Inline.Space},Block.LineBlock(listOf(badSpine("inner")))) as Block.LineBlock).lines.take(1).size.toLong()
        "walk-bullet-inner-spine-undemanded" -> (Walk.inlineInBlock.walk({Inline.Space},Block.BulletList(listOf(badSpine("inner")))) as Block.BulletList).items.take(1).size.toLong()
        "walk-ordered-inner-spine-undemanded" -> (Walk.inlineInBlock.walk({Inline.Space},Block.OrderedList(ListAttributes(),listOf(badSpine("inner")))) as Block.OrderedList).items.take(1).size.toLong()
        "walk-definition-def-inner-spine-undemanded" -> definitionWalk(badSpine("terms"),listOf(badSpine("inner"))).items.first().second.take(1).size.toLong()
        "walk-map-keys", "walk-map-first-value", "walk-map-all-values" -> {
            val dictionary=Walk.traversable(Walk.inlineInInline,WalkContainers.map<String,Inline>(naturalOrder()))
            val output=dictionary.walk({if(it is Inline.Str && (probe=="walk-map-keys" || it.text=="b"))error("callback") else it},mapOf("a" to Inline.Str("a"),"b" to Inline.Str("b")))
            when(probe) {"walk-map-keys"->output.keys.toList();"walk-map-first-value"->output.getValue("a");else->output.values.toList()}
        }
        else -> error("unknown probe $probe")
    }
    JsonPrimitive(if(value is DemandShown)value.text else if(probe=="walk-first-bottom-inline")value as String else AstShow.show(value))
} catch (e: IllegalStateException) { buildJsonObject { put("exception",e.message!!) } }

internal fun checkWalkPureDemand(lines: List<String>): Int {
    var count=0
    for(line in lines) {
        val record=Json.parseToJsonElement(line).jsonObject
        val probe=record.getValue("request").jsonObject.getValue("probe").jsonPrimitive.content
        if(!probe.startsWith("walk"))continue
        check(demandResponse(probe)==record.getValue("response")){"$probe: ${demandResponse(probe)} != ${record["response"]}"};count++
    }
    return count
}
class OriginalTypesWalkPureDemandTest {
    @TestFactory fun `lazy adapter memoization and immutable collection contracts`(): List<DynamicTest> = listOf(
        DynamicTest.dynamicTest("deferred values share one transformed result") {
            var calls=0
            val output=Walk.traversable(Walk.inlineInInline,WalkContainers.list<Inline>()).walk({ calls++;Inline.Str("result") },listOf(Inline.Space))
            kotlin.test.assertEquals(0,calls)
            kotlin.test.assertSame(output[0],output[0]);kotlin.test.assertEquals(1,calls)
        },
        DynamicTest.dynamicTest("failed deferred values share the original exception") {
            var calls=0
            val failure=IllegalStateException("child")
            val input=object:AbstractList<Inline>() {override val size=1;override fun get(index:Int):Inline {calls++;throw failure}}
            val output=(Walk.inlineInInline.walk({it},Inline.Emph(input)) as Inline.Emph).content
            repeat(2){kotlin.test.assertSame(failure,kotlin.test.assertFailsWith<IllegalStateException>{output[0]})}
            kotlin.test.assertEquals(1,calls)
            var extents=0
            val badExtent=object:AbstractList<List<Block>>() {
                override val size:Int get(){extents++;throw failure}
                override fun get(index:Int):List<Block> = error("unrequested element")
            }
            val definitions=definitionWalk(emptyList(),badExtent).items.first().second
            repeat(2){kotlin.test.assertSame(failure,kotlin.test.assertFailsWith<IllegalStateException>{definitions.size})}
            kotlin.test.assertEquals(1,extents)
        },
        DynamicTest.dynamicTest("deferred lists reject mutation") {
            val output=(Walk.inlineInInline.walk({it},Inline.Emph(listOf(Inline.Space))) as Inline.Emph).content
            @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN", "UNCHECKED_CAST")
            kotlin.test.assertFailsWith<UnsupportedOperationException>{(output as java.util.List<Inline>).add(Inline.Space)}
        },
        DynamicTest.dynamicTest("deferred Map entries retain equality and hashing") {
            val input=mapOf("a" to Inline.Space,"b" to Inline.Str("b"))
            val output=Walk.traversable(Walk.inlineInInline,WalkContainers.map<String,Inline>(naturalOrder())).walk({it},input)
            kotlin.test.assertEquals(input,output);kotlin.test.assertEquals(output,input)
            kotlin.test.assertEquals(input.entries,output.entries);kotlin.test.assertEquals(input.hashCode(),output.hashCode())
        },
        DynamicTest.dynamicTest("lazy pair fields share results without touching the other field") {
            var first=0;var second=0
            val value=WalkLazyPair<Inline,Inline>({first++;Inline.Str("first")},{second++;error("second")})
            val output=Walk.lazyPair(Walk.inlineInInline,Walk.inlineInInline).walk({it},value)
            kotlin.test.assertEquals(0,first);kotlin.test.assertEquals(0,second)
            kotlin.test.assertSame(output.first,output.first);kotlin.test.assertEquals(1,first);kotlin.test.assertEquals(0,second)
        },
    )

    @TestFactory fun `original pure Walk demand observations`(): List<DynamicTest> =
        javaClass.getResourceAsStream("/pandoc-types/walk-pure/demand-probes.jsonl")!!.bufferedReader().use { it.readLines() }.mapNotNull { line ->
            val record=Json.parseToJsonElement(line).jsonObject
            val probe=record.getValue("request").jsonObject.getValue("probe").jsonPrimitive.content
            if(!probe.startsWith("walk"))null else DynamicTest.dynamicTest(probe) {
                kotlin.test.assertEquals(record.getValue("response"), demandResponse(probe))
            }
        }
}
