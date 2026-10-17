// Port of Text.Pandoc.Builder ToMetaValue instances.
// Copyright (c) 2010-2023 John MacFarlane. BSD-3-Clause; see licenses/.
// Text.pack scalar normalization: ©2009–2012 Bryan O'Sullivan; ©2009 Duncan
// Coutts; ©2008–2009 Tom Harper; ©2021 Andrew Lelechenko; BSD-style.
package io.heapy.kotdoc.ast

fun interface ToMetaValue<A> {
 fun toMetaValue(value:()->A):MetaValue
}
object MetadataConversions {
 val metaValue=ToMetaValue<MetaValue>{it()}
 val bool=ToMetaValue<Boolean>{MetaValue.MetaBool(it())}
 val text=ToMetaValue<String>{MetaValue.MetaString(it())}
 /** Haskell String is a list of Char code points; it is not Text nor JVM UTF-16. */
 val string=ToMetaValue<GenericList<Int>>{input->val out=StringBuilder();var chars=input();while(true)when(val cell=chars.step()){
  GenericList.Step.Nil->break
  is GenericList.Step.Cons->{val point=cell.head();require(point in 0..0x10ffff);out.appendCodePoint(if(point in 0xd800..0xdfff)0xfffd else point);chars=cell.tail()}
 };MetaValue.MetaString(out.toString())}
 val inlines=ToMetaValue<Many<Inline>>{val values=it().toSourceList();values.step();MetaValue.MetaInlines(values)}
 val blocks=ToMetaValue<Many<Block>>{val values=it().toSourceList();values.step();MetaValue.MetaBlocks(values)}
 fun <A> list(element:ToMetaValue<A>)=ToMetaValue<List<A>>{input->val values=metadataMapList(input,element::toMetaValue);values.step();MetaValue.MetaList(values)}
 fun <A> mapText(element:ToMetaValue<A>)=ToMetaValue<MetadataMap<A>>{input->MetaValue.MetaMap(input().mapped(element::toMetaValue))}
 /** mapKeys Text.pack is applied before mapping values; largest original String key wins a collision. */
 fun <A> mapString(element:ToMetaValue<A>)=ToMetaValue<List<Pair<GenericList<Int>,()->A>>>{input->
  val entries=input().sortedWith{a,b->compareChars(a.first,b.first)}
  val packed=MetadataMap.fromEntries(entries.map{(key,value)->(string.toMetaValue{key}as MetaValue.MetaString).value to value})
  MetaValue.MetaMap(packed.mapped(element::toMetaValue))
 }
 private fun compareChars(a:GenericList<Int>,b:GenericList<Int>):Int {var x=a;var y=b;while(true){val l=x.step();val r=y.step();if(l is GenericList.Step.Nil)return if(r is GenericList.Step.Nil)0 else -1;if(r is GenericList.Step.Nil)return 1;l as GenericList.Step.Cons;r as GenericList.Step.Cons;val d=l.head().compareTo(r.head());if(d!=0)return d;x=l.tail();y=r.tail()}}
}
