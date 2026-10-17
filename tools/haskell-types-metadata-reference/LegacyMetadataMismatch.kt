package io.heapy.kotdoc.ast
/** Run against immutable pre-fix core jar; the corresponding expected values are
 * direct original MetadataDemand.hs expressions, not reconstructed expectations. */
fun main(){
 fun blocks():List<Block> = GenericList.cons({error("head")},{error("tail")})
 for((name,f)in listOf<Pair<String,()->Any?>>(
  "doc-title-block-head-vs-tail" to {docTitle(mapOf("title" to MetaValue.MetaBlocks(blocks())))[0]},
  "doc-date-block-head-vs-tail" to {docDate(mapOf("date" to MetaValue.MetaBlocks(blocks())))[0]},
  "doc-authors-first-group" to {val values=GenericList.cons({MetaValue.MetaString("s")},{GenericList.cons({MetaValue.MetaInlines(listOf(Inline.Str("i")))},{GenericList.cons<MetaValue>({error("tail-value")},{GenericList.empty()})})});listOf(docAuthors(mapOf("author" to MetaValue.MetaList(values)))[0])}
 ))println(name+":"+try{AstShow.show(f()!!)}catch(e:RuntimeException){"exception:"+e.message})
}
