package io.heapy.kotdoc.ast
import kotlinx.serialization.json.*
import java.io.File
import java.util.zip.GZIPInputStream
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

private object TraceMonad : WalkMonad<TraceMonad.Tag> {
    class Tag
    data class Result<A>(val value: A?, val trace: List<String>, val error: String? = null)
    class Effect<A>(val run: (List<String>) -> Result<A>) : WalkEffect<Tag, A>
    override fun <A> pure(value: A): WalkEffect<Tag, A> = Effect { Result(value, it) }
    override fun <A,B> flatMap(value:WalkEffect<Tag,A>,next:(A)->WalkEffect<Tag,B>):WalkEffect<Tag,B> = Effect { trace ->
        val result=(value as Effect<A>).run(trace)
        if(result.error!=null)Result(null,result.trace,result.error) else (next(result.value as A) as Effect<B>).run(result.trace)
    }
    override fun <S,A> tailRecM(initial:S,step:(S)->WalkEffect<Tag,WalkLoop<S,A>>):WalkEffect<Tag,A> = Effect { trace ->
        var state=initial;var output=trace
        var result:Result<A>?=null
        while(result==null) {
            val next=(step(state) as Effect<WalkLoop<S,A>>).run(output);output=next.trace
            if(next.error!=null)result=Result(null,output,next.error)
            else when(val loop=next.value!!) { is WalkLoop.Done->result=Result(loop.value,output);is WalkLoop.Continue->state=loop.state }
        }
        result
    }
}
private object CounterMonad:WalkMonad<CounterMonad.Tag> {
    class Tag
    class Effect<A>(val run:(Int)->Pair<A,Int>):WalkEffect<Tag,A>
    override fun <A> pure(value:A):WalkEffect<Tag,A> = Effect { value to it }
    override fun <A,B> flatMap(value:WalkEffect<Tag,A>,next:(A)->WalkEffect<Tag,B>):WalkEffect<Tag,B> = Effect { state -> val (a,s)=(value as Effect<A>).run(state);(next(a) as Effect<B>).run(s) }
    override fun <S,A> tailRecM(initial:S,step:(S)->WalkEffect<Tag,WalkLoop<S,A>>):WalkEffect<Tag,A> = Effect { start ->
        var state=initial;var counter=start
        while(true) {
            val (next,n)=(step(state) as Effect<WalkLoop<S,A>>).run(counter);counter=n
            when(next){is WalkLoop.Done->return@Effect next.value to counter;is WalkLoop.Continue->state=next.state}
        }
        error("unreachable")
    }
}
private object BranchMonad:WalkMonad<BranchMonad.Tag> {
    class Tag
    class Effect<A>(val values:Sequence<A>):WalkEffect<Tag,A>
    override fun <A> pure(value:A):WalkEffect<Tag,A> = Effect(sequenceOf(value))
    override fun <A,B> flatMap(value:WalkEffect<Tag,A>,next:(A)->WalkEffect<Tag,B>):WalkEffect<Tag,B> = Effect((value as Effect<A>).values.flatMap { (next(it) as Effect<B>).values })
    override fun <S,A> tailRecM(initial:S,step:(S)->WalkEffect<Tag,WalkLoop<S,A>>):WalkEffect<Tag,A> = Effect(sequence {
        val stack=ArrayDeque<Iterator<WalkLoop<S,A>>>()
        stack.addLast(sequenceOf<WalkLoop<S,A>>(WalkLoop.Continue(initial)).iterator())
        while(stack.isNotEmpty()) {
            val iterator=stack.last();if(!iterator.hasNext()){stack.removeLast();continue}
            when(val loop=iterator.next()) {is WalkLoop.Done->yield(loop.value);is WalkLoop.Continue->stack.addLast((step(loop.state) as Effect<WalkLoop<S,A>>).values.iterator())}
        }
    })
}
private object MetaBlockDictionary:Walkable<Map<String,MetaValue>,Block> {
    override fun <F> walkM(monad:WalkMonad<F>,transform:(Map<String,MetaValue>)->WalkEffect<F,Map<String,MetaValue>>,value:Block):WalkEffect<F,Block> =
        monad.flatMap(transform(mapOf("external" to MetaValue.MetaString("block")))) { result->monad.pure(Block.Plain(listOf(Inline.Str(result.keys.sortedWith(UnicodeTextOrder).joinToString(","))))) }
    override fun <C> query(transform:(Map<String,MetaValue>)->C,value:Block,monoid:WalkMonoid<C>):C=transform(mapOf("external" to MetaValue.MetaString("block")))
}
private data class Probe(val text:String)
@Suppress("UNCHECKED_CAST")
private fun probeDictionary(root:String):Walkable<Any,Any?> = object:Walkable<Any,Any?> {
    override fun walk(transform:(Any)->Any,value:Any?):Any? = when(root) {
        "inlines"->(value as List<Inline>)+Inline.Str((transform(Probe("pure-inlines")) as Probe).text)
        "blocks"->(value as List<Block>)+Block.Plain(listOf(Inline.Str((transform(Probe("pure-blocks")) as Probe).text)))
        else->super<Walkable>.walk(transform,value)
    }
    override fun <F> walkM(monad:WalkMonad<F>,transform:(Any)->WalkEffect<F,Any>,value:Any?):WalkEffect<F,Any?> = monad.flatMap(transform(Probe(root))) { probe ->
        val text=(probe as Probe).text
        val result:Any?=when(root) {
            "inlines"->(value as List<Inline>)+Inline.Str(text)
            "blocks"->(value as List<Block>)+Block.Plain(listOf(Inline.Str(text)))
            "citation"->(value as Citation).copy(citationId=text)
            "row"->(value as Row).copy(attr=Attr(text))
            "head"->(value as TableHead).copy(attr=Attr(text))
            "body"->(value as TableBody).copy(attr=Attr(text))
            "foot"->(value as TableFoot).copy(attr=Attr(text))
            "cell"->(value as Cell).copy(attr=Attr(text))
            "caption"->(value as Caption).copy(long=value.long+Block.Plain(listOf(Inline.Str(text))))
            "meta"->(value as Map<String,MetaValue>)+(text to MetaValue.MetaString(text))
            "metaValue"->MetaValue.MetaString(text)
            "shortCaption"->(value as List<Inline>?).orEmpty()+Inline.Str(text)
            else->error(root)
        }
        monad.pure(result)
    }
    override fun <C> query(transform:(Any)->C,value:Any?,monoid:WalkMonoid<C>):C=transform(Probe("query-"+root))
}
@Suppress("UNCHECKED_CAST")
private fun shown(kind:String,value:Any?):String=when(kind) {
    "probe"->"Probe "+AstShow.show((value as Probe).text)
    "meta"->AstShow.meta(value as Map<String,MetaValue>)
    "maybeBlock"->if(value==null)"Nothing" else "Just "+AstShow.show(value,11)
    "identityBlock"->"Identity "+AstShow.show(value!!,11)
    "manyInlines","manyBlocks"->"Many {unMany = fromList "+AstShow.show(if(value is Inlines)value.values else (value as Blocks).values)+"}"
    "seqBlock","seqInlines"->"fromList "+AstShow.show(value!!)
    "nestedBlock"->if(value==null)"Nothing" else "Just (fromList "+AstShow.show(value)+")"
    "mapBlock"->"fromList "+AstShow.show((value as Map<String,Block>).toSortedMap(UnicodeTextOrder).toList())
    "pairMetaBlockMixed"->{val p=value as Pair<Map<String,MetaValue>,Block>;"("+AstShow.meta(p.first)+","+AstShow.show(p.second)+")"}
    "pairMetaPandoc"->{val p=value as Pair<Map<String,MetaValue>,Pandoc>;"("+AstShow.meta(p.first)+","+AstShow.show(p.second)+")"}
    else->AstShow.show(value!!)
}

private fun change(kind:String,n:Int,value:Any):Any {
    val marker=if(n<0)"pure" else "visit$n"
    return when(kind) {
        "probe"->Probe((value as Probe).text+marker)
        "inline"->when(value){is Inline.Str->value.copy(text=value.text+marker);is Inline.Emph->Inline.Strong(value.content+Inline.Str(marker));else->value}
        "inlines"->(value as List<Inline>)+Inline.Str(marker)
        "block"->when(value){is Block.Plain->Block.Para(value.content+Inline.Str(marker));is Block.BlockQuote->Block.Div(Attr(),value.blocks+Block.Plain(listOf(Inline.Str(marker))));else->value}
        "blocks"->(value as List<Block>)+Block.Plain(listOf(Inline.Str(marker)))
        "meta"->(value as Map<String,MetaValue>)+(marker to MetaValue.MetaString(marker))
        "metaValue"->when(value){is MetaValue.MetaString->value.copy(value=value.value+marker);is MetaValue.MetaBool->value.copy(value=!value.value);is MetaValue.MetaList->value.copy(values=value.values+MetaValue.MetaString(marker));else->value}
        "pandoc"->(value as Pandoc).copy(blocks=value.blocks+Block.RawBlock("walk",marker))
        else->error(kind)
    }
}
private interface TestWalk<A,B> {
    fun <F> walkM(monad:WalkMonad<F>, transform:(A)->WalkEffect<F,A>,value:B):WalkEffect<F,B>
    fun <C> query(transform:(A)->C,value:B,monoid:WalkMonoid<C>):C
    fun walk(transform:(A)->A,value:B):B=(walkM(WalkIdentity,{WalkIdentity.Value(transform(it))},value)as WalkIdentity.Value<B>).value
}
private fun <A,B> check(dictionary:Walkable<A,B>,value:B,request:JsonObject):JsonElement=check(object:TestWalk<A,B>{
    override fun walk(transform:(A)->A,value:B)=dictionary.walk(transform,value)
    override fun <F> walkM(monad:WalkMonad<F>, transform:(A)->WalkEffect<F,A>,value:B)=dictionary.walkM(monad,transform,value)
    override fun <C> query(transform:(A)->C,value:B,monoid:WalkMonoid<C>)=dictionary.query(transform,value,monoid)
},value,request)
@Suppress("UNCHECKED_CAST")
private fun <A,B> check(dictionary:TestWalk<A,B>,value:B,request:JsonObject):JsonElement {
    val target=request.getValue("target").jsonPrimitive.content;val root=request.getValue("root").jsonPrimitive.content
    fun transform(n:Int,a:A):A=change(target,n,a as Any) as A
    return when(request.getValue("method").jsonPrimitive.content) {
        "walk"->JsonPrimitive(shown(root,dictionary.walk({transform(-1,it)},value)))
        "walkM","failure"->{
            val failure=request.getValue("method").jsonPrimitive.content=="failure";val stop=request.getValue("stop").jsonPrimitive.int
            val result=(dictionary.walkM(TraceMonad,{a->TraceMonad.Effect { trace ->
                val next=trace+shown(target,a as Any)
                if(failure && trace.size==stop)TraceMonad.Result(null,next,"stop${trace.size}")else TraceMonad.Result(transform(trace.size,a),next)
            }},value) as TraceMonad.Effect<B>).run(emptyList())
            buildJsonObject { if(failure){put("result",result.error?:shown(root,result.value));put("failed",result.error!=null)}else put("value",shown(root,result.value));put("trace",JsonArray(result.trace.map(::JsonPrimitive))) }
        }
        "branching"->{val values=(dictionary.walkM(BranchMonad,{a->BranchMonad.Effect(sequenceOf(transform(0,a),transform(1,a)))},value)as BranchMonad.Effect<B>).values.take(5).toList()
            buildJsonObject {put("values",JsonArray(values.take(4).map{JsonPrimitive(shown(root,it))}));put("truncated",values.size>4)} }
        "query"->JsonArray(dictionary.query({listOf(JsonPrimitive(shown(target,it as Any)))},value,WalkMonoids.list()))
        "first"->dictionary.query({WalkOptional.Some(shown(target,it as Any)) as WalkOptional<String>},value,WalkMonoids.first()).let { if(it is WalkOptional.Some)JsonPrimitive(it.value)else JsonNull }
        "last"->dictionary.query({WalkOptional.Some(shown(target,it as Any)) as WalkOptional<String>},value,WalkMonoids.last()).let { if(it is WalkOptional.Some)JsonPrimitive(it.value)else JsonNull }
        "count"->JsonPrimitive(dictionary.query({1L},value,WalkMonoids.sumLong))
        else->error("method")
    }
}
@Suppress("UNCHECKED_CAST")
private fun dictionary(target:String,root:String):Walkable<Any,Any> = if(target=="probe")probeDictionary(root) as Walkable<Any,Any> else when(target+":"+root) {
    "meta:block"->MetaBlockDictionary as Walkable<Any,Any>
    "inline:inline"->Walk.inlineInInline as Walkable<Any,Any>
    "inlines:inlines"->Walk.inlinesInInlines as Walkable<Any,Any>
    "inlines:inline"->Walk.inlinesInInline as Walkable<Any,Any>
    "inline:block"->Walk.inlineInBlock as Walkable<Any,Any>
    "inlines:block"->Walk.inlinesInBlock as Walkable<Any,Any>
    "block:block"->Walk.blockInBlock as Walkable<Any,Any>
    "blocks:block"->Walk.blocksInBlock as Walkable<Any,Any>
    "blocks:blocks"->Walk.blocksInBlocks as Walkable<Any,Any>
    "block:inline"->Walk.blockInInline as Walkable<Any,Any>
    "blocks:inline"->Walk.blocksInInline as Walkable<Any,Any>
    "block:pandoc"->Walk.blockInPandoc as Walkable<Any,Any>
    "blocks:pandoc"->Walk.blocksInPandoc as Walkable<Any,Any>
    "inline:pandoc"->Walk.inlineInPandoc as Walkable<Any,Any>
    "inlines:pandoc"->Walk.inlinesInPandoc as Walkable<Any,Any>
    "meta:pandoc"->Walk.metaInPandoc as Walkable<Any,Any>
    "metaValue:pandoc"->Walk.metaValueInPandoc as Walkable<Any,Any>
    "pandoc:pandoc"->Walk.pandocInPandoc as Walkable<Any,Any>
    "meta:meta"->Walk.metaInMeta as Walkable<Any,Any>
    "inline:meta"->Walk.inlineInMeta as Walkable<Any,Any>
    "inlines:meta"->Walk.inlinesInMeta as Walkable<Any,Any>
    "block:meta"->Walk.blockInMeta as Walkable<Any,Any>
    "blocks:meta"->Walk.blocksInMeta as Walkable<Any,Any>
    "metaValue:meta"->Walk.metaValueInMeta as Walkable<Any,Any>
    "metaValue:metaValue"->Walk.metaValueInMetaValue as Walkable<Any,Any>
    "inline:metaValue"->Walk.inlineInMetaValue as Walkable<Any,Any>
    "inlines:metaValue"->Walk.inlinesInMetaValue as Walkable<Any,Any>
    "block:metaValue"->Walk.blockInMetaValue as Walkable<Any,Any>
    "blocks:metaValue"->Walk.blocksInMetaValue as Walkable<Any,Any>
    "inline:row"->Walk.inlineInRow as Walkable<Any,Any>
    "inlines:row"->Walk.inlinesInRow as Walkable<Any,Any>
    "block:row"->Walk.blockInRow as Walkable<Any,Any>
    "blocks:row"->Walk.blocksInRow as Walkable<Any,Any>
    "inline:head"->Walk.inlineInHead as Walkable<Any,Any>
    "inlines:head"->Walk.inlinesInHead as Walkable<Any,Any>
    "block:head"->Walk.blockInHead as Walkable<Any,Any>
    "blocks:head"->Walk.blocksInHead as Walkable<Any,Any>
    "inline:body"->Walk.inlineInBody as Walkable<Any,Any>
    "inlines:body"->Walk.inlinesInBody as Walkable<Any,Any>
    "block:body"->Walk.blockInBody as Walkable<Any,Any>
    "blocks:body"->Walk.blocksInBody as Walkable<Any,Any>
    "inline:foot"->Walk.inlineInFoot as Walkable<Any,Any>
    "inlines:foot"->Walk.inlinesInFoot as Walkable<Any,Any>
    "block:foot"->Walk.blockInFoot as Walkable<Any,Any>
    "blocks:foot"->Walk.blocksInFoot as Walkable<Any,Any>
    "inline:caption"->Walk.inlineInCaption as Walkable<Any,Any>
    "inlines:caption"->Walk.inlinesInCaption as Walkable<Any,Any>
    "block:caption"->Walk.blockInCaption as Walkable<Any,Any>
    "blocks:caption"->Walk.blocksInCaption as Walkable<Any,Any>
    "inline:cell"->Walk.inlineInCell as Walkable<Any,Any>
    "inlines:cell"->Walk.inlinesInCell as Walkable<Any,Any>
    "block:cell"->Walk.blockInCell as Walkable<Any,Any>
    "blocks:cell"->Walk.blocksInCell as Walkable<Any,Any>
    "inline:citation"->Walk.inlineInCitation as Walkable<Any,Any>
    "inlines:citation"->Walk.inlinesInCitation as Walkable<Any,Any>
    "block:citation"->Walk.blockInCitation as Walkable<Any,Any>
    "blocks:citation"->Walk.blocksInCitation as Walkable<Any,Any>
    else->when(root) {
        "inlines"->Walk.traversable(dictionary(target,"inline"),WalkContainers.list()) as Walkable<Any,Any>
        "blocks"->Walk.traversable(dictionary(target,"block"),WalkContainers.list()) as Walkable<Any,Any>
        else->error("dictionary $target $root")
    }
}
private fun readRoot(root:String,input:String):Any = when(root) {
    "inline"->PandocRead.Inline.read(input)
    "inlines"->PandocRead.Inline.readList(input).single().first
    "block"->PandocRead.Block.read(input)
    "blocks"->PandocRead.Block.readList(input).single().first
    "meta"->PandocRead.Meta.read(input)
    "metaValue"->PandocRead.MetaValue.read(input)
    "pandoc"->PandocRead.Pandoc.read(input)
    "citation"->PandocRead.Citation.read(input)
    "caption"->PandocRead.Caption.read(input)
    "cell"->PandocRead.Cell.read(input)
    "row"->PandocRead.Row.read(input)
    "head"->PandocRead.TableHead.read(input)
    "body"->PandocRead.TableBody.read(input)
    "foot"->PandocRead.TableFoot.read(input)
    "metaValuePrime"->PandocRead.MetaValue.read(input)
    else->error(root)
}
@Suppress("UNCHECKED_CAST")
private fun genericExecute(r:JsonObject):JsonElement {
    val target=r.getValue("target").jsonPrimitive.content
    val root=r.getValue("root").jsonPrimitive.content
    val input=r.getValue("input").jsonPrimitive.content
    val block=if(target in listOf("inline","inlines","block","blocks"))dictionary(target,"block") as Walkable<Any,Block> else null
    val blockShape=ReadShape.Root.BLOCK
    return when(root) {
        "pairMetaBlockMixed"->check(Walk.pair(dictionary(target,"meta"),dictionary(target,"block")),ReadDictionary<Pair<Any,Any>>(ReadShape.Tuple(listOf(ReadShape.Root.META,ReadShape.Root.BLOCK))).read(input),r)
        "manyInlines"->check(Walk.traversable(dictionary(target,"inline") as Walkable<Any,Inline>,WalkContainers.inlines),PandocRead.Inlines.read(input),r)
        "manyBlocks"->check(Walk.traversable(dictionary(target,"block") as Walkable<Any,Block>,WalkContainers.blocks),PandocRead.Blocks.read(input),r)
        "seqInlines"->check(Walk.traversable(probeDictionary("inlines"),WalkContainers.list()),ReadDictionary<List<List<Inline>>>(ReadShape.Sequence(ReadShape.Items(ReadShape.Root.INLINE))).read(input),r)
        "pairInlinesBlocks"->check(Walk.pair(probeDictionary("inlines"),probeDictionary("blocks")),ReadDictionary<Pair<List<Inline>,List<Block>>>(ReadShape.Tuple(listOf(ReadShape.Items(ReadShape.Root.INLINE),ReadShape.Items(ReadShape.Root.BLOCK)))).read(input),r)
        "maybeBlock"->check(Walk.traversable(block!!,WalkContainers.nullable()),ReadDictionary<Block?>(ReadShape.Optional(blockShape)).read(input),r)
        "seqBlock"->check(Walk.traversable(block!!,WalkContainers.list()),ReadDictionary<List<Block>>(ReadShape.Sequence(blockShape)).read(input),r)
        "identityBlock"->check(block!!,PandocRead.Block.read(input.removePrefix("Identity ")),r)
        "nestedBlock"->check(Walk.traversable(Walk.traversable(block!!,WalkContainers.list()),WalkContainers.nullable()),ReadDictionary<List<Block>?>(ReadShape.Optional(ReadShape.Sequence(blockShape))).read(input),r)
        "mapBlock"->check(Walk.traversable(block!!,WalkContainers.map(UnicodeTextOrder)),ReadDictionary<List<Pair<String,Block>>>(ReadShape.Sequence(ReadShape.Tuple(listOf(ReadShape.Prim.TEXT,blockShape)))).read(input).toMap(),r)
        "pairInlineBlock"->check(Walk.pair(dictionary(target,"inline"),dictionary(target,"block")),ReadDictionary<Pair<Any,Any>>(ReadShape.Tuple(listOf(ReadShape.Root.INLINE,blockShape))).read(input),r)
        "pairMetaPandoc"->check(Walk.pair(dictionary(target,"meta"),dictionary(target,"pandoc")),ReadDictionary<Pair<Any,Any>>(ReadShape.Tuple(listOf(ReadShape.Root.META,ReadShape.Root.PANDOC))).read(input),r)
        "pairPandoc"->check(Walk.pair(dictionary(target,"pandoc"),dictionary(target,"pandoc")),ReadDictionary<Pair<Any,Any>>(ReadShape.Tuple(listOf(ReadShape.Root.PANDOC,ReadShape.Root.PANDOC))).read(input),r)
        else->error(root)
    }
}
@Suppress("UNCHECKED_CAST")
private fun helperExecute(r:JsonObject):JsonElement {
    val target=r.getValue("target").jsonPrimitive.content
    val root=r.getValue("root").jsonPrimitive.content
    val input=r.getValue("input").jsonPrimitive.content
    return when(root) {
        "inline"->{
            val citation=dictionary(target,"citation") as Walkable<Any,Citation>
            val blocks=dictionary(target,"blocks") as Walkable<Any,List<Block>>
            val inlines=dictionary(target,"inlines") as Walkable<Any,List<Inline>>
            check(object:TestWalk<Any,Inline> {
                override fun <F> walkM(monad:WalkMonad<F>,transform:(Any)->WalkEffect<F,Any>,value:Inline)=WalkHelpers.walkInlineM(monad,transform,value,citation,blocks,inlines)
                override fun <C> query(transform:(Any)->C,value:Inline,monoid:WalkMonoid<C>)=WalkHelpers.queryInline(transform,value,monoid,citation,blocks,inlines)
            },readRoot(root,input) as Inline,r)
        }
        "block"->{
            val blocks=dictionary(target,"blocks") as Walkable<Any,List<Block>>
            val inlines=dictionary(target,"inlines") as Walkable<Any,List<Inline>>
            val row=dictionary(target,"row") as Walkable<Any,Row>
            val caption=dictionary(target,"caption") as Walkable<Any,Caption>
            val head=dictionary(target,"head") as Walkable<Any,TableHead>
            val body=dictionary(target,"body") as Walkable<Any,TableBody>
            val foot=dictionary(target,"foot") as Walkable<Any,TableFoot>
            val citation=dictionary(target,"citation") as Walkable<Any,Citation>
            check(object:TestWalk<Any,Block> {
                override fun <F> walkM(monad:WalkMonad<F>,transform:(Any)->WalkEffect<F,Any>,value:Block)=WalkHelpers.walkBlockM(monad,transform,value,blocks,inlines,row,caption,head,body,foot,citation)
                override fun <C> query(transform:(Any)->C,value:Block,monoid:WalkMonoid<C>)=WalkHelpers.queryBlock(transform,value,monoid,blocks,inlines,row,caption,head,body,foot,citation)
            },readRoot(root,input) as Block,r)
        }
        "metaValue"->{
            val metaValue=dictionary(target,"metaValue") as Walkable<Any,MetaValue>
            val blocks=dictionary(target,"blocks") as Walkable<Any,List<Block>>
            val inlines=dictionary(target,"inlines") as Walkable<Any,List<Inline>>
            check(object:TestWalk<Any,MetaValue> {
                override fun <F> walkM(monad:WalkMonad<F>,transform:(Any)->WalkEffect<F,Any>,value:MetaValue)=WalkHelpers.walkMetaValueM(monad,transform,value,metaValue,blocks,inlines)
                override fun <C> query(transform:(Any)->C,value:MetaValue,monoid:WalkMonoid<C>)=WalkHelpers.queryMetaValue(transform,value,monoid,metaValue,blocks,inlines)
            },readRoot(root,input) as MetaValue,r)
        }
        "citation"->{
            val inlines=dictionary(target,"inlines") as Walkable<Any,List<Inline>>
            check(object:TestWalk<Any,Citation> {
                override fun <F> walkM(monad:WalkMonad<F>,transform:(Any)->WalkEffect<F,Any>,value:Citation)=WalkHelpers.walkCitationM(monad,transform,value,inlines)
                override fun <C> query(transform:(Any)->C,value:Citation,monoid:WalkMonoid<C>)=WalkHelpers.queryCitation(transform,value,monoid,inlines)
            },readRoot(root,input) as Citation,r)
        }
        "row"->{
            val cell=dictionary(target,"cell") as Walkable<Any,Cell>
            check(object:TestWalk<Any,Row> {
                override fun <F> walkM(monad:WalkMonad<F>,transform:(Any)->WalkEffect<F,Any>,value:Row)=WalkHelpers.walkRowM(monad,transform,value,cell)
                override fun <C> query(transform:(Any)->C,value:Row,monoid:WalkMonoid<C>)=WalkHelpers.queryRow(transform,value,monoid,cell)
            },readRoot(root,input) as Row,r)
        }
        "head"->{
            val row=dictionary(target,"row") as Walkable<Any,Row>
            check(object:TestWalk<Any,TableHead> {
                override fun <F> walkM(monad:WalkMonad<F>,transform:(Any)->WalkEffect<F,Any>,value:TableHead)=WalkHelpers.walkTableHeadM(monad,transform,value,row)
                override fun <C> query(transform:(Any)->C,value:TableHead,monoid:WalkMonoid<C>)=WalkHelpers.queryTableHead(transform,value,monoid,row)
            },readRoot(root,input) as TableHead,r)
        }
        "body"->{
            val row=dictionary(target,"row") as Walkable<Any,Row>
            check(object:TestWalk<Any,TableBody> {
                override fun <F> walkM(monad:WalkMonad<F>,transform:(Any)->WalkEffect<F,Any>,value:TableBody)=WalkHelpers.walkTableBodyM(monad,transform,value,row)
                override fun <C> query(transform:(Any)->C,value:TableBody,monoid:WalkMonoid<C>)=WalkHelpers.queryTableBody(transform,value,monoid,row)
            },readRoot(root,input) as TableBody,r)
        }
        "foot"->{
            val row=dictionary(target,"row") as Walkable<Any,Row>
            check(object:TestWalk<Any,TableFoot> {
                override fun <F> walkM(monad:WalkMonad<F>,transform:(Any)->WalkEffect<F,Any>,value:TableFoot)=WalkHelpers.walkTableFootM(monad,transform,value,row)
                override fun <C> query(transform:(Any)->C,value:TableFoot,monoid:WalkMonoid<C>)=WalkHelpers.queryTableFoot(transform,value,monoid,row)
            },readRoot(root,input) as TableFoot,r)
        }
        "cell"->{
            val blocks=dictionary(target,"blocks") as Walkable<Any,List<Block>>
            check(object:TestWalk<Any,Cell> {
                override fun <F> walkM(monad:WalkMonad<F>,transform:(Any)->WalkEffect<F,Any>,value:Cell)=WalkHelpers.walkCellM(monad,transform,value,blocks)
                override fun <C> query(transform:(Any)->C,value:Cell,monoid:WalkMonoid<C>)=WalkHelpers.queryCell(transform,value,monoid,blocks)
            },readRoot(root,input) as Cell,r)
        }
        "caption"->{
            val blocks=dictionary(target,"blocks") as Walkable<Any,List<Block>>
            val inlines=dictionary(target,"inlines") as Walkable<Any,List<Inline>>
            check(object:TestWalk<Any,Caption> {
                override fun <F> walkM(monad:WalkMonad<F>,transform:(Any)->WalkEffect<F,Any>,value:Caption)=WalkHelpers.walkCaptionM(monad,transform,value,blocks,inlines)
                override fun <C> query(transform:(Any)->C,value:Caption,monoid:WalkMonoid<C>)=WalkHelpers.queryCaption(transform,value,monoid,blocks,inlines)
            },readRoot(root,input) as Caption,r)
        }
        "pandoc"->{
            val meta=dictionary(target,"meta") as Walkable<Any,Map<String,MetaValue>>
            val blocks=dictionary(target,"blocks") as Walkable<Any,List<Block>>
            check(object:TestWalk<Any,Pandoc> {
                override fun <F> walkM(monad:WalkMonad<F>,transform:(Any)->WalkEffect<F,Any>,value:Pandoc)=WalkHelpers.walkPandocM(monad,transform,value,meta,blocks)
                override fun <C> query(transform:(Any)->C,value:Pandoc,monoid:WalkMonoid<C>)=WalkHelpers.queryPandoc(transform,value,monoid,meta,blocks)
            },readRoot(root,input) as Pandoc,r)
        }
        "metaValuePrime"->check(object:TestWalk<MetaValue,MetaValue> {
            override fun <F> walkM(monad:WalkMonad<F>,transform:(MetaValue)->WalkEffect<F,MetaValue>,value:MetaValue)=WalkHelpers.walkMetaValueMPrime(monad,transform,value)
            override fun <C> query(transform:(MetaValue)->C,value:MetaValue,monoid:WalkMonoid<C>)=WalkHelpers.queryMetaValuePrime(transform,value,monoid)
        },PandocRead.MetaValue.read(input),r)
        else->error(root)
    }
}
private fun demandExecute(r:JsonObject):JsonElement {
    fun bad(values:List<Inline?>)=object:AbstractList<Inline>() {
        override val size=values.size
        override fun get(index:Int):Inline=values[index]?:error(if(index==0)"head" else "tail")
    }
    fun showOptional(value:WalkOptional<String>):String=when(value) {WalkOptional.None->"Nothing";is WalkOptional.Some->"Just "+AstShow.show(value.value)}
    val list=Walk.traversable(Walk.inlineInInline,WalkContainers.list())
    return try {
        when(r.getValue("probe").jsonPrimitive.content) {
            "list-prefix-bottom-inline"->JsonArray(Walk.inlineInInline.queryLazy({sequenceOf("hit")},{error("root")},WalkMonoids.sequence()).take(1).map(::JsonPrimitive).toList())
            "list-prefix-bad-tail"->JsonArray(list.query({sequenceOf(AstShow.show(it))},bad(listOf(Inline.Str("first"),null)),WalkMonoids.sequence()).take(1).map(::JsonPrimitive).toList())
            "list-prefix-nested"->JsonArray(Walk.inlineInInline.query({if(it is Inline.Str)sequenceOf(it.text)else emptySequence()},Inline.Emph(bad(listOf(Inline.Str("first"),null))),WalkMonoids.sequence()).take(1).map(::JsonPrimitive).toList())
            "unit-ignores-callback"->{Walk.inlineInInline.queryLazy({error("callback")},{error("root")},WalkMonoids.unit);JsonPrimitive("()")}
            "first-bottom-inline"->JsonPrimitive(showOptional(Walk.inlineInInline.queryLazy({WalkOptional.Some("hit")},{error("root")},WalkMonoids.first())))
            "first-constant-bad-head"->JsonPrimitive(showOptional(list.queryLazy({WalkOptional.Some("hit")},{bad(listOf(null))},WalkMonoids.first())))
            "first-bad-tail"->JsonPrimitive(showOptional(list.query({WalkOptional.Some(AstShow.show(it))},bad(listOf(Inline.Str("first"),null)),WalkMonoids.first())))
            "last-bad-head"->JsonPrimitive(showOptional(list.query({WalkOptional.Some(AstShow.show(it))},bad(listOf(null,Inline.Str("last"))),WalkMonoids.last())))
            "last-constant-bad-head"->JsonPrimitive(showOptional(list.queryLazy({WalkOptional.Some("hit")},{bad(listOf(null))},WalkMonoids.last())))
            "pandoc-constant-bottom"->JsonPrimitive(showOptional(Walk.pandocInPandoc.queryLazy({WalkOptional.Some("hit")},{error("root")},WalkMonoids.first())))
            "unit-ignores-bottom"->{Walk.inlineInInline.queryLazy({Unit},{error("root")},WalkMonoids.unit);JsonPrimitive("()")}
            "any-ignores-bottom"->JsonPrimitive(Walk.inlineInInline.queryLazy({true},{error("root")},WalkMonoids.any))
            "all-ignores-bottom"->JsonPrimitive(Walk.inlineInInline.queryLazy({false},{error("root")},WalkMonoids.all))
            "meta-skips-block-elements","meta-value-skips-block-elements"->{
                val blocks=object:AbstractList<Block>() {override val size:Int get()=1;override fun get(index:Int):Block=error("block")}
                val value=Pandoc(mapOf("a" to MetaValue.MetaString("x")),blocks)
                if(r.getValue("probe").jsonPrimitive.content=="meta-skips-block-elements")JsonPrimitive(Walk.metaInPandoc.query({1L},value,WalkMonoids.sumLong))
                else JsonPrimitive(Walk.metaValueInPandoc.query({1L},value,WalkMonoids.sumLong))
            }
            "failure-before-tail"->{val result=(list.walkM(TraceMonad,{_:Inline->TraceMonad.Effect<Inline>{trace->TraceMonad.Result(null,trace,"stop")}},bad(listOf(Inline.Str("first"),null)))as TraceMonad.Effect<List<Inline>>).run(emptyList());JsonPrimitive("Left "+AstShow.show(result.error!!))}
            "deep-walk"->{var value:Inline=Inline.Str("leaf");repeat(10000){value=Inline.Emph(listOf(value))};val result=Walk.inlineInInline.walk({if(it is Inline.Str)it.copy(text=it.text+"done")else it},value)
                buildJsonObject{put("count",Walk.inlineInInline.query({1L},result,WalkMonoids.sumLong));put("leaf",Walk.inlineInInline.queryValues(result).filterIsInstance<Inline.Str>().joinToString(""){it.text})}}
            "deep-state"->{var value:Inline=Inline.Str("leaf");repeat(10000){value=Inline.Emph(listOf(value))};val (result,n)=(Walk.inlineInInline.walkM(CounterMonad,{x->CounterMonad.Effect{state->x to state+1}},value)as CounterMonad.Effect<Inline>).run(0)
                JsonArray(listOf(JsonPrimitive(n),JsonPrimitive(Walk.inlineInInline.query({1L},result,WalkMonoids.sumLong))))}
            "deep-count"->{var value:Inline=Inline.Str("leaf");repeat(10000){value=Inline.Emph(listOf(value))};JsonPrimitive(Walk.inlineInInline.query({1L},value,WalkMonoids.sumLong))}
            else->error("probe")
        }
    }catch(e:Exception){buildJsonObject{put("exception",e.message?.lineSequence()?.firstOrNull()?:e.javaClass.simpleName)}}
}
private fun execute(r:JsonObject):JsonElement {
    if(r["demand"]!=null)return demandExecute(r)
    if(r["generic"]!=null)return genericExecute(r)
    if(r["helper"]!=null)return helperExecute(r)
    val input=r.getValue("input").jsonPrimitive.content
    val target=r.getValue("target").jsonPrimitive.content
    val root=r.getValue("root").jsonPrimitive.content
    return check(dictionary(target,root),readRoot(root,input),r)
}

internal fun runOriginalTypesWalkReplay(directory:File) {
    val requests=File(directory,"walk-requests.jsonl").readLines();val responses=File(directory,"walk-responses.jsonl").readLines()
    var count=0
    requests.zip(responses).forEach { (line,expected) ->
        val request=Json.parseToJsonElement(line).jsonObject
        if(request["probe"]?.jsonPrimitive?.content !in setOf("meta-skips-blocks","meta-value-skips-blocks")) {
            val actual=execute(request);val wanted=Json.parseToJsonElement(expected)
            check(actual==wanted){"${request["id"]}: expected $wanted, actual $actual"};count++
        }
    }
    println("$count actual original Walk observations passed")
}

class OriginalTypesWalkClosureTest {
    @TestFactory
    fun `original 58 Walk dictionaries and 24 helper exports`(): List<DynamicTest> {
        fun lines(name:String)=GZIPInputStream(javaClass.getResourceAsStream("/pandoc-types/walk-closure/$name.gz")!!).bufferedReader().use{it.readLines()}
        val requests=lines("walk-requests.jsonl");val responses=lines("walk-responses.jsonl")
        check(requests.size==responses.size)
        return requests.zip(responses).mapNotNull { (line,response) ->
            val request=Json.parseToJsonElement(line).jsonObject
            // StrictData constructor failure before Walk, retained and independently
            // replayed in the source oracle; no undefined List spine is claimed here.
            if(request["probe"]?.jsonPrimitive?.content in setOf("meta-skips-blocks","meta-value-skips-blocks"))null
            else DynamicTest.dynamicTest(request.getValue("id").jsonPrimitive.content) {
                val actual=execute(request);val expected=Json.parseToJsonElement(response)
                kotlin.test.assertEquals(expected,actual)
            }
        }
    }
}
