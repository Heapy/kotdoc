package io.heapy.kotdoc.ast
import kotlinx.serialization.json.*
import java.nio.file.Path
import kotlin.io.path.readLines
internal fun metadataDemandExecute(name:String):JsonElement=try {
 fun <A> bad(message:String):A=error(message)
 fun root(value:Any?)="root"
 fun map(value:()->MetaValue)=MetadataMap.fromEntries(listOf("x" to value))
 fun empty()=MetadataMap.empty<MetaValue>()
 fun key(k:String,value:MetadataMap<MetaValue>):String=if(!value.containsKey(k))"Nothing"else"Just ("+AstShow.show(value.getValue(k))+")"
 fun title(field:String,value:MetaValue)=MetadataMap.fromEntries(listOf(field to {value}))
 fun partialBlocks():GenericList<Block> = GenericList.cons({bad("head")},{bad("tail")})
 fun <A> takeOne(input:List<A>):List<A> = when(val node=GenericList.view(input).step()){GenericList.Step.Nil->emptyList();is GenericList.Step.Cons->listOf(node.head())}
 fun poisonInline()=Many.singleton<Inline>{bad("inline")}
 val result=when(name){
 "convert-bool-root"->root(MetadataConversions.bool.toMetaValue{bad("bool")})
 "convert-text-root"->root(MetadataConversions.text.toMetaValue{bad("text")})
 "convert-string-root"->root(MetadataConversions.string.toMetaValue{bad("string")})
 "convert-string-head"->root(MetadataConversions.string.toMetaValue{GenericList.cons({bad("char")},{GenericList.empty()})})
 "convert-list-root"->root(MetadataConversions.list(MetadataConversions.bool).toMetaValue{bad("list")})
 "convert-list-head"->root(MetadataConversions.list(MetadataConversions.bool).toMetaValue{GenericList.cons({bad("head")},{GenericList.empty()})})
 "convert-list-tail"->root(MetadataConversions.list(MetadataConversions.bool).toMetaValue{GenericList.cons({true},{bad("tail")})})
 "convert-list-demand-head"->AstShow.show((MetadataConversions.list(MetadataConversions.bool).toMetaValue{GenericList.cons({bad("head")},{GenericList.empty()})}as MetaValue.MetaList).values.first())
 "convert-map-value"->root(MetadataConversions.mapText(MetadataConversions.bool).toMetaValue{MetadataMap.fromEntries(listOf("x" to {bad<Boolean>("value")}))})
 "convert-map-keys"->{val value=MetadataConversions.mapText(MetadataConversions.bool).toMetaValue{MetadataMap.fromEntries(listOf("x" to {bad<Boolean>("value")}))}as MetaValue.MetaMap;AstShow.show(value.values.keys.toList())}
 "convert-map-demand-value"->AstShow.show(MetadataConversions.mapText(MetadataConversions.bool).toMetaValue{MetadataMap.fromEntries(listOf("x" to {bad<Boolean>("value")}))})
 "convert-inlines-head"->root(MetadataConversions.inlines.toMetaValue{poisonInline()})
 "convert-blocks-head"->root(MetadataConversions.blocks.toMetaValue{Many.singleton<Block>{bad("block")}})
 "convert-inlines-root"->root(MetadataConversions.inlines.toMetaValue{bad("many")})
 "convert-blocks-root"->root(MetadataConversions.blocks.toMetaValue{bad("many")})
 "meta-set-unused"->key("y",PandocMetadata.meta.setMeta({"x"},{bad<Boolean>("value")},MetadataConversions.bool,::empty)())
 "meta-set-null"->if(PandocMetadata.isNullMeta(PandocMetadata.meta.setMeta({"x"},{bad<Boolean>("value")},MetadataConversions.bool,::empty)))"True"else"False"
 "meta-set-overwrite"->key("x",PandocMetadata.meta.setMeta({"x"},{true},MetadataConversions.bool){map{bad("old")}}())
 "meta-delete-poison"->AstShow.meta(PandocMetadata.meta.deleteMeta({"x"}){map{bad("old")}}())
 "meta-delete-key-empty"->AstShow.meta(PandocMetadata.meta.deleteMeta({bad("key")},::empty)())
 "meta-lookup-key-empty"->root(PandocMetadata.lookupMeta({bad("key")},::empty))
 "meta-insert-key-empty"->root(PandocMetadata.meta.setMeta({bad("key")},{true},MetadataConversions.bool,::empty)())
 "meta-lookup-found-lazy"->root(PandocMetadata.lookupMeta({"x"}){map{bad("value")}})
 "doc-title-list-tail"->AstShow.show(takeOne(PandocMetadata.docTitle{title("title",MetaValue.MetaInlines(GenericList.cons({Inline.Str("a")},{bad("tail")})))}))
 "doc-title-block-head-vs-tail"->root(GenericList.view(PandocMetadata.docTitle{title("title",MetaValue.MetaBlocks(partialBlocks()))}).step())
 "doc-date-block-head-vs-tail"->root(GenericList.view(PandocMetadata.docDate{title("date",MetaValue.MetaBlocks(partialBlocks()))}).step())
 "doc-title-single-payload"->AstShow.show(takeOne(PandocMetadata.docTitle{title("title",MetaValue.MetaBlocks(listOf(Block.Plain(GenericList.cons({Inline.Str("a")},{bad("tail")})))))}))
 "doc-authors-first-group"->AstShow.show(takeOne(PandocMetadata.docAuthors{title("author",MetaValue.MetaList(GenericList.cons({MetaValue.MetaString("s")},{GenericList.cons({MetaValue.MetaInlines(listOf(Inline.Str("i")))},{GenericList.cons({bad("tail-value")},{GenericList.empty()})})})))}))
 "doc-authors-early-string"->AstShow.show(takeOne(PandocMetadata.docAuthors{title("author",MetaValue.MetaList(GenericList.cons({MetaValue.MetaString("s")},{GenericList.cons({bad("later")},{GenericList.empty()})})))}))
 "doc-author-block-head-vs-tail"->root(GenericList.view(PandocMetadata.docAuthors{title("author",MetaValue.MetaList(listOf(MetaValue.MetaBlocks(partialBlocks()))))}).step())
 "pandoc-set-unused"->{val doc=MetadataDocument(::empty){GenericList.view(listOf(Block.Plain(emptyList())))};AstShow.show(PandocMetadata.pandoc.setMeta({"x"},{bad<Boolean>("value")},MetadataConversions.bool){doc}().blocks)}
 "pandoc-delete-preserve-head"->root(PandocMetadata.pandoc.deleteMeta({"x"}){MetadataDocument(::empty){GenericList.cons({bad("block")},{GenericList.empty()})}}().blocks)
 "doc-ignore-block-head"->root(PandocMetadata.doc{Many.singleton<Block>{bad("block")}})
 "doc-force-block-root"->root(PandocMetadata.doc{bad("many")})
 "set-title-unused"->key("other",PandocMetadata.setTitle({bad("title")}){MetadataDocument(::empty){GenericList.empty()}}().meta)
 "set-authors-unused"->key("other",PandocMetadata.setAuthors({bad("authors")}){MetadataDocument(::empty){GenericList.empty()}}().meta)
 "set-date-unused"->key("other",PandocMetadata.setDate({bad("date")}){MetadataDocument(::empty){GenericList.empty()}}().meta)
 "meta-append-winning-value"->key("x",PandocMetadata.appendMeta({map{bad("old")}},{map{MetaValue.MetaBool(true)}}))
 "meta-append-order"->root(PandocMetadata.appendMeta({bad("left")},{bad("right")}))
 "pandoc-append-order"->root(PandocMetadata.appendPandoc({bad("left")},{bad("right")}))
 "meta-concat-order"->root(MetadataAlgebra.meta.mconcat{GenericList.view(listOf({bad<MetadataMap<MetaValue>>("left")},{bad<MetadataMap<MetaValue>>("right")})).let{metadataMapList({it}){f->f()()}}})
 "pandoc-concat-order"->root(MetadataAlgebra.pandoc.mconcat{GenericList.cons({bad("left")},{GenericList.cons({bad("right")},{GenericList.empty()})})})
 "meta-sconcat-tail-first"->root(MetadataAlgebra.meta.sconcat({bad("head")},{bad("tail")}))
 "pandoc-sconcat-tail-first"->root(MetadataAlgebra.pandoc.sconcat({bad("head")},{bad("tail")}))
 "pandoc-sconcat-head-before-later-tail"->root(MetadataAlgebra.pandoc.sconcat({bad("head")},{GenericList.cons({MetadataDocument(::empty){GenericList.empty()}},{bad("tail")})}))
 "meta-stimes-zero"->root(MetadataAlgebra.meta.stimes(java.math.BigInteger.ZERO){bad("value")})
 "pandoc-stimes-negative"->root(MetadataAlgebra.pandoc.stimes(java.math.BigInteger.valueOf(-1)){bad("value")})
 "meta-stimes-one"->root(MetadataAlgebra.meta.stimes(java.math.BigInteger.ONE){bad("value")})
 "pandoc-stimes-two-lazy-head"->root(MetadataAlgebra.pandoc.stimes(java.math.BigInteger.TWO){MetadataDocument(::empty){GenericList.cons({bad("head")},{GenericList.empty()})}}.blocks)
 "custom-list-ignore-value"->AstShow.show(MetadataConversions.list(ToMetaValue<Any?>{MetaValue.MetaBool(true)}).toMetaValue{GenericList.cons({bad("ignored")},{GenericList.empty()})})
 "custom-hasmeta-ignore-value"->key("x",PandocMetadata.meta.setMeta({"x"},{bad<Any?>("ignored")},ToMetaValue{MetaValue.MetaBool(true)},::empty)())
 "string-map-discarded-value"->AstShow.show(MetadataConversions.mapString(MetadataConversions.bool).toMetaValue{listOf(GenericList.view(listOf(0xd800)) to {bad<Boolean>("lost")},GenericList.view(listOf(0xfffd)) to {true})})
 "meta-stimes-count-vs-value"->root(MetadataAlgebra.meta.stimes({bad("count")},{bad("value")}))
 "pandoc-stimes-count"->root(MetadataAlgebra.pandoc.stimes({bad("count")},{MetadataDocument(::empty){GenericList.empty()}}))
 "meta-append-unused-value"->if(PandocMetadata.isNullMeta{PandocMetadata.appendMeta({map{bad("value")}},::empty)})"True"else"False"
 else->error("probe:$name")
 };JsonPrimitive(result)
}catch(e:RuntimeException){buildJsonObject{put("exception",e.message.orEmpty())}}
fun main(){val lines=Path.of("tools/haskell-types-metadata-reference/demand-native.jsonl").readLines();lines.chunked(2).forEach{(n,e)->val name=Json.parseToJsonElement(n).jsonPrimitive.content;val actual=metadataDemandExecute(name);check(actual==Json.parseToJsonElement(e)){"$name: $actual != $e"}};println("${lines.size/2} original metadata demand responses PASS")}
