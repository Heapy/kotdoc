// Port of Text.Pandoc.Builder HasMeta and Definition metadata operations.
// Copyright (c) 2006-2023 John MacFarlane. BSD-3-Clause; see licenses/.
package io.heapy.kotdoc.ast

interface HasMeta<A> {
 fun <B> setMeta(key:()->String,value:()->B,conversion:ToMetaValue<B>,input:()->A):()->A
 fun deleteMeta(key:()->String,input:()->A):()->A
}
object PandocMetadata {
 val meta:HasMeta<MetadataMap<MetaValue>> = object:HasMeta<MetadataMap<MetaValue>> {
  override fun <B> setMeta(key:()->String,value:()->B,conversion:ToMetaValue<B>,input:()->MetadataMap<MetaValue>)=MetadataMap.memo{input().inserted(key()){conversion.toMetaValue(value)}}
  override fun deleteMeta(key:()->String,input:()->MetadataMap<MetaValue>)=MetadataMap.memo{input().deleted(key())}
 }
 val pandoc:HasMeta<MetadataDocument> = object:HasMeta<MetadataDocument> {
  override fun <B> setMeta(key:()->String,value:()->B,conversion:ToMetaValue<B>,input:()->MetadataDocument)=MetadataMap.memo{val source=input();MetadataDocument({source.meta.inserted(key()){conversion.toMetaValue(value)}},{source.blocks})}
  override fun deleteMeta(key:()->String,input:()->MetadataDocument)=MetadataMap.memo{val source=input();MetadataDocument({source.meta.deleted(key())},{source.blocks})}
 }
 fun doc(blocks:()->Many<Block>):MetadataDocument=MetadataDocument({MetadataMap.empty()},{blocks().toSourceList()})
 fun setTitle(title:()->Many<Inline>,input:()->MetadataDocument)=pandoc.setMeta({"title"},title,MetadataConversions.inlines,input)
 fun setAuthors(authors:()->List<Many<Inline>>,input:()->MetadataDocument)=pandoc.setMeta({"author"},authors,MetadataConversions.list(MetadataConversions.inlines),input)
 fun setDate(date:()->Many<Inline>,input:()->MetadataDocument)=pandoc.setMeta({"date"},date,MetadataConversions.inlines,input)
 fun nullMeta():MetadataMap<MetaValue> = MetadataMap.empty()
 fun isNullMeta(input:()->MetadataMap<MetaValue>)=input().isEmpty()
 fun lookupMeta(key:()->String,input:()->MetadataMap<MetaValue>):GenericMaybe<MetaValue>{val map=input();val k=key();return if(map.containsKey(k))GenericMaybe.Some{map.getValue(k)}else GenericMaybe.None}
 fun docTitle(input:()->MetadataMap<MetaValue>):GenericList<Inline> = textField("title",input)
 fun docDate(input:()->MetadataMap<MetaValue>):GenericList<Inline> = textField("date",input)
 private fun textField(key:String,input:()->MetadataMap<MetaValue>):GenericList<Inline> = GenericList.defer{val value=input()[key];when(value){
  is MetaValue.MetaString->GenericList.cons({Inline.Str(value.value)},{GenericList.empty()})
  is MetaValue.MetaInlines->GenericList.view(value.content)
  is MetaValue.MetaBlocks->{val first=GenericList.view(value.content).step();if(first is GenericList.Step.Cons)when(val b=first.head()){is Block.Plain->if(first.tail().step()is GenericList.Step.Nil)GenericList.view(b.content)else GenericList.empty();is Block.Para->if(first.tail().step()is GenericList.Step.Nil)GenericList.view(b.content)else GenericList.empty();else->GenericList.empty()}else GenericList.empty()}
  else->GenericList.empty()
 }}
 fun docAuthors(input:()->MetadataMap<MetaValue>):GenericList<List<Inline>> = GenericList.defer{when(val value=input()["author"]){
  is MetaValue.MetaString->GenericList.cons({listOf(Inline.Str(value.value))},{GenericList.empty()})
  is MetaValue.MetaInlines->GenericList.cons({value.content},{GenericList.empty()})
  is MetaValue.MetaList->groups(value.values,0)
  else->GenericList.empty()
 }}
 private fun groups(values:List<MetaValue>,group:Int):GenericList<List<Inline>> {
  if(group==4)return GenericList.empty()
  fun scan(input:()->List<MetaValue>):GenericList<List<Inline>> = GenericList.defer{
   var current=input
   while(true)when(val node=GenericList.view(current()).step()){
    GenericList.Step.Nil->return@defer groups(values,group+1)
    is GenericList.Step.Cons->{val value=node.head();val result:List<Inline>?=when(group){
     0->(value as? MetaValue.MetaInlines)?.content
     1,2->if(value is MetaValue.MetaBlocks){val head=GenericList.view(value.content).step();if(head is GenericList.Step.Cons){val b=head.head();val payload=if(group==1)(b as? Block.Plain)?.content else(b as? Block.Para)?.content;if(payload!=null&&head.tail().step()is GenericList.Step.Nil)payload else null}else null}else null
     else->(value as? MetaValue.MetaString)?.let{listOf(Inline.Str(it.value))}
    };if(result!=null)return@defer GenericList.cons({result},{scan(node::tail)});current=node::tail}
   }
   @Suppress("UNREACHABLE_CODE") GenericList.empty()
  }
  return scan{values}
 }
 fun appendMeta(left:()->MetadataMap<MetaValue>,right:()->MetadataMap<MetaValue>):MetadataMap<MetaValue>{val r=right();val l=left();return l.unionRight(r)}
 fun appendPandoc(left:()->MetadataDocument,right:()->MetadataDocument):MetadataDocument {val l=left();val r=right();return MetadataDocument({appendMeta({l.meta},{r.meta})},{appendList({l.blocks},{r.blocks})})}
 private fun <A> appendList(left:()->GenericList<A>,right:()->GenericList<A>):GenericList<A> = GenericList.defer{when(val step=left().step()){GenericList.Step.Nil->right();is GenericList.Step.Cons->GenericList.cons(step::head,{appendList(step::tail,right)})}}
}
