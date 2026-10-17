// Source Builder representation adapters. ©2010–2023 John MacFarlane.
// BSD-3-Clause; original notices retained in licenses/.
package io.heapy.kotdoc.ast

/** Source aliases have distinct Kotlin carriers; their payload is the actual Many model. */
class BuilderInlines(val source:Many<GenericNode>)
class BuilderBlocks(val source:Many<GenericNode>)

internal object BuilderStreams {
 val inline=GenericDefinitionData.named("Inline")
 val block=GenericDefinitionData.named("Block")
 fun named(name:String,value:()->Any?)=GenericValue(GenericDefinitionData.named(name),value)
 fun text(value:()->String)=GenericValue(GenericDatas.text,value)
 fun integer(value:()->Long)=GenericValue(GenericDatas.int,value)
 fun <A> ast(name:String,value:()->A)=GenericValue(GenericAstData.named<A>(name),value)
 fun list(name:String,value:()->List<GenericNode>):GenericValue<List<Any?>> = GenericValue(GenericDatas.list(GenericDefinitionData.named(name))){value()}
 fun nodes(type:String,constructor:String,fields:List<GenericValue<*>>):GenericNode {
  val spec=GenericDefinitionData.declarations.single{it.name==type}.constructors.single{it.name==constructor}
  val canonical=fields.zip(spec.fields).map{(field,expression)->val source=GenericDefinitionData.resolve(expression);GenericValue(source){field.cast(source)}}
  return GenericDefinitionData.named(type).force{GenericDefinitionData.node(type,constructor,canonical)} as GenericNode
 }
 fun inline(constructor:String,vararg fields:GenericValue<*>):BuilderInlines = BuilderInlines(Many.singleton{nodes("Inline",constructor,fields.toList())})
 fun block(constructor:String,vararg fields:GenericValue<*>):BuilderBlocks = BuilderBlocks(Many.singleton{nodes("Block",constructor,fields.toList())})
 fun enum(name:String,constructor:String)=named(name){GenericDefinitionData.node(name,constructor,emptyList())}
 fun target(url:()->String,title:()->String)=GenericValue(GenericDatas.tuple2(GenericDatas.text,GenericDatas.text)){GenericTuple2(url,title)}
 fun <A,B> map(input:()->List<A>,f:(()->A)->B):GenericList<B> = GenericList.defer{when(val n=GenericList.view(input()).step()){
  GenericList.Step.Nil->GenericList.empty()
  is GenericList.Step.Cons->GenericList.cons({f(n::head)},{map(n::tail,f)})
 }}
 fun inlines(input:()->BuilderInlines)=list("Inline"){input().source.toSourceList()}
 fun blocks(input:()->BuilderBlocks)=list("Block"){input().source.toSourceList()}
 fun inlineList(input:()->List<BuilderInlines>)=GenericValue(GenericDatas.list(GenericDatas.list(inline))){map(input){it().source.toSourceList()}}
 fun blockList(input:()->List<BuilderBlocks>)=GenericValue(GenericDatas.list(GenericDatas.list(block))){map(input){it().source.toSourceList()}}
}
