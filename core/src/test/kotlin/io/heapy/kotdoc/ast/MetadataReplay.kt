package io.heapy.kotdoc.ast
import kotlinx.serialization.json.*
import java.nio.file.Path
import kotlin.io.path.*
private class MetadataDecoder(val conversion:ToMetaValue<Any?>,val read:(JsonElement)->Any?)
@Suppress("UNCHECKED_CAST") private fun <A> decoded(conversion:ToMetaValue<A>,read:(JsonElement)->A)=MetadataDecoder(conversion as ToMetaValue<Any?>,read)
private fun points(value:JsonElement)=value.jsonArray.map{it.jsonPrimitive.int}
private fun chars(value:JsonElement)=GenericList.view(points(value))
private fun text(value:JsonElement):String=buildString{for(cp in points(value))appendCodePoint(if(cp in 0xd800..0xdfff)0xfffd else cp)}
private fun decoder(spec:JsonElement):MetadataDecoder {val s=spec.jsonObject;return when(s.getValue("type").jsonPrimitive.content){
 "meta"->decoded(MetadataConversions.metaValue){PandocRead.MetaValue.read(it.jsonPrimitive.content)}
 "bool"->decoded(MetadataConversions.bool){it.jsonPrimitive.boolean}
 "text"->decoded(MetadataConversions.text,::text)
 "string"->decoded(MetadataConversions.string,::chars)
 "inlines"->decoded(MetadataConversions.inlines){Many.fromList(PandocRead.Inline.readList(it.jsonPrimitive.content).single().first)}
 "blocks"->decoded(MetadataConversions.blocks){Many.fromList(PandocRead.Block.readList(it.jsonPrimitive.content).single().first)}
 "list"->{val child=decoder(s.getValue("element"));decoded(MetadataConversions.list(child.conversion)){it.jsonArray.map(child.read)}}
 "mapText"->{val child=decoder(s.getValue("element"));decoded(MetadataConversions.mapText(child.conversion)){value->MetadataMap.fromEntries(value.jsonArray.map{entry->val p=entry.jsonArray;text(p[0]) to {child.read(p[1])}})}}
 "mapString"->{val child=decoder(s.getValue("element"));decoded(MetadataConversions.mapString(child.conversion)){value->value.jsonArray.map{entry->val p=entry.jsonArray;chars(p[0]) to {child.read(p[1])}}}}
 else->error("type")
}}
internal fun metadataExecute(row:JsonObject):JsonElement=try {
 fun raw(name:String)=row.getValue(name).jsonPrimitive.content
 fun meta(name:String="meta")=MetadataMap.fromMap(PandocRead.Meta.read(raw(name)))
 fun document(name:String="document")=MetadataDocument.fromAst(PandocRead.Pandoc.read(raw(name)))
 fun inlines()=Many.fromList(PandocRead.Inline.readList(raw("inlines")).single().first)
 fun converted():MetaValue {val d=decoder(row.getValue("spec"));return d.conversion.toMetaValue{d.read(row.getValue("value"))}}
 val key={text(row.getValue("key"))}
 val actual=when(raw("operation")){
 "convert"->AstShow.show(converted())
 "setMetaMeta"->AstShow.meta(PandocMetadata.meta.setMeta(key,::converted,MetadataConversions.metaValue){meta()}())
 "setMetaPandoc"->AstShow.show(PandocMetadata.pandoc.setMeta(key,::converted,MetadataConversions.metaValue){document()}().toAst())
 "deleteMetaMeta"->AstShow.meta(PandocMetadata.meta.deleteMeta(key){meta()}())
 "deleteMetaPandoc"->AstShow.show(PandocMetadata.pandoc.deleteMeta(key){document()}().toAst())
 "nullMeta","metaEmpty"->AstShow.meta(PandocMetadata.nullMeta())
 "isNullMeta"->if(PandocMetadata.isNullMeta{meta()})"True"else"False"
 "lookupMeta"->when(val value=PandocMetadata.lookupMeta(key){meta()}){GenericMaybe.None->"Nothing";is GenericMaybe.Some->"Just ("+AstShow.show(value.value)+")"}
 "docTitle"->AstShow.show(PandocMetadata.docTitle{meta()})
 "docAuthors"->AstShow.show(PandocMetadata.docAuthors{meta()})
 "docDate"->AstShow.show(PandocMetadata.docDate{meta()})
 "doc"->AstShow.show(PandocMetadata.doc{Many.fromList(PandocRead.Block.readList(raw("blocks")).single().first)}.toAst())
 "setTitle"->AstShow.show(PandocMetadata.setTitle(::inlines){document()}().toAst())
 "setDate"->AstShow.show(PandocMetadata.setDate(::inlines){document()}().toAst())
 "setAuthors"->{val lists=ReadDictionary<List<List<Inline>>>(ReadShape.Items(ReadShape.Items(ReadShape.Root.INLINE))).read(raw("authors"));AstShow.show(PandocMetadata.setAuthors({lists.map{Many.fromList(it)}}){document()}().toAst())}
 "metaMappend"->AstShow.meta(MetadataAlgebra.meta.mappend({meta()},{meta("otherMeta")}))
 "pandocMappend"->AstShow.show(MetadataAlgebra.pandoc.mappend({document()},{document("otherDocument")}).toAst())
 "metaAppend"->AstShow.meta(PandocMetadata.appendMeta({meta()},{meta("otherMeta")}))
 "pandocAppend"->AstShow.show(PandocMetadata.appendPandoc({document()},{document("otherDocument")}).toAst())
 "pandocEmpty"->AstShow.show(PandocMetadata.doc{Many.empty()}.toAst())
 "metaStimes"->AstShow.meta(MetadataAlgebra.meta.stimes(java.math.BigInteger(raw("count"))){meta()})
 "pandocStimes"->AstShow.show(MetadataAlgebra.pandoc.stimes(java.math.BigInteger(raw("count"))){document()}.toAst())
 "metaConcat","metaSconcat"->{val values=ReadDictionary<List<Map<String,MetaValue>>>(ReadShape.Items(ReadShape.Root.META)).read(raw("metas")).map{MetadataMap.fromMap(it)};AstShow.meta(if(raw("operation")=="metaConcat")MetadataAlgebra.meta.mconcat{GenericList.view(values)}else MetadataAlgebra.meta.sconcat({meta()}){GenericList.view(values)})}
 "pandocConcat","pandocSconcat"->{val values=ReadDictionary<List<Pandoc>>(ReadShape.Items(ReadShape.Root.PANDOC)).read(raw("documents")).map{MetadataDocument.fromAst(it)};AstShow.show((if(raw("operation")=="pandocConcat")MetadataAlgebra.pandoc.mconcat{GenericList.view(values)}else MetadataAlgebra.pandoc.sconcat({document()}){GenericList.view(values)}).toAst())}
 else->error("operation")
 }
 JsonPrimitive(actual)
}catch(e:RuntimeException){buildJsonObject{put("exception",e.message.orEmpty())}}
fun main(){val root=Path.of("tools/haskell-types-metadata-reference");val requests=root.resolve("requests.jsonl").readLines();val expected=root.resolve("responses.jsonl").readLines();requests.zip(expected).forEachIndexed{i,(r,e)->val row=Json.parseToJsonElement(r).jsonObject;val actual=metadataExecute(row);check(actual==Json.parseToJsonElement(e)){"$i ${row["case"]}: $actual != $e"}};println("${requests.size} original metadata responses PASS")}
