package io.heapy.kotdoc.ast
import kotlinx.serialization.json.*
import java.io.File
private fun jsonCharacters(values:List<String>):String=values.joinToString(""){text->buildString{text.codePoints().forEach{cp->appendCodePoint(if(cp in 0xD800..0xDFFF)0xFFFD else cp)}}}
private object ConstructorState:WalkMonad<ConstructorState.Tag> {
 class Tag
 class Effect<A>(val run:(List<String>)->Pair<A,List<String>>):WalkEffect<Tag,A>
 override fun <A> pure(value:A):WalkEffect<Tag,A> = Effect{value to it}
 override fun <A,B> flatMap(value:WalkEffect<Tag,A>,next:(A)->WalkEffect<Tag,B>):WalkEffect<Tag,B> = Effect{initial->val (a,state)=(value as Effect<A>).run(initial);(next(a) as Effect<B>).run(state)}
 override fun <S,A> tailRecM(initial:S,step:(S)->WalkEffect<Tag,WalkLoop<S,A>>):WalkEffect<Tag,A> = Effect{start->
  var current=initial;var state=start
  while(true){val (loop,next)=(step(current) as Effect<WalkLoop<S,A>>).run(state);state=next
   when(loop){is WalkLoop.Continue->current=loop.state;is WalkLoop.Done->return@Effect loop.value to state}}
  error("unreachable")
 }
}

@Suppress("UNCHECKED_CAST")
private fun dictionary(type:GenericType):GenericData<Any?> {
 val args=type.arguments.map(::dictionary)
 val result:GenericData<*> = when(type.name){
 "[]"->GenericDatas.list(args.single())
 "(,)"->GenericDatas.tuple2(args[0],args[1])
 "(,,)"->GenericDatas.tuple3(args[0],args[1],args[2])
 "Maybe"->GenericDatas.maybe(args.single())
 "Seq"->GenericDatas.sequence(args.single())
 "Many"->GenericDatas.many(args.single())
 "Map"->GenericDatas.map(args[0],args[1],Comparator<Any?>{a,b->UnicodeTextOrder.compare(a as String,b as String)})
 else->GenericDefinitionData.named(type.name)
 }
 return result as GenericData<Any?>
}
@Suppress("UNCHECKED_CAST")
private fun decode(data:GenericData<Any?>,json:JsonObject):GenericValue<Any?> = GenericValue(data){
 val fields=json.getValue("fields").jsonArray.map{it.jsonObject};val ctor=json.getValue("constructor").jsonPrimitive.content
 fun child(index:Int,type:GenericType)=decode(dictionary(type),fields[index])
 when(data.type.name){
 "Int"->json.getValue("scalar").jsonPrimitive.long
 "Double"->json.getValue("scalar").jsonPrimitive.content.toDouble()
 "Char"->json.getValue("scalar").jsonPrimitive.int
 "Text"->json.getValue("scalar").jsonPrimitive.content
 "Bool"->ctor=="True"
 "[]"->if(fields.isEmpty())GenericList.empty<Any?>() else GenericList.cons({child(0,data.type.arguments[0]).value()},{child(1,data.type).value() as GenericList<Any?>})
 "(,)"->GenericTuple2({child(0,data.type.arguments[0]).value()},{child(1,data.type.arguments[1]).value()})
 "(,,)"->GenericTuple3({child(0,data.type.arguments[0]).value()},{child(1,data.type.arguments[1]).value()},{child(2,data.type.arguments[2]).value()})
 "Maybe"->if(fields.isEmpty())GenericMaybe.None else GenericMaybe.Some{child(0,data.type.arguments[0]).value()}
 "Seq"->if(fields.isEmpty())GenericSequence.empty<Any?>() else GenericSequence.cons({child(0,data.type.arguments[0]).value()},{child(1,data.type).value() as GenericSequence<Any?>})
 "Many"->{val seq=GenericDatas.sequence(dictionary(data.type.arguments[0])) as GenericData<Any?>;decode(seq,fields.single()).value()}
 "Map"->{val layer=data.layer{emptyMap<Any?,Any?>()};layer.rebuild(listOf(decode(layer.fields.single().data as GenericData<Any?>,fields.single())))}
 else->{val declaration=GenericDefinitionData.declarations.single{it.name==data.type.name};val spec=declaration.constructors.single{it.name==ctor};GenericDefinitionData.node(declaration.name,ctor,spec.fields.mapIndexed{i,t->decode(GenericDefinitionData.resolve(t),fields[i])})}
 }
}
private fun root(kind:String):GenericData<Any?> {
 val name=mapOf("pandoc" to "Pandoc","meta" to "Meta","metaValue" to "MetaValue","inline" to "Inline","block" to "Block","style" to "ListNumberStyle","delim" to "ListNumberDelim","format" to "Format","rowHeadColumns" to "RowHeadColumns","alignment" to "Alignment","colWidth" to "ColWidth","row" to "Row","tableHead" to "TableHead","tableBody" to "TableBody","tableFoot" to "TableFoot","caption" to "Caption","cell" to "Cell","rowSpan" to "RowSpan","colSpan" to "ColSpan","quote" to "QuoteType","math" to "MathType","citation" to "Citation","citationMode" to "CitationMode","attr" to "Attr","target" to "Target","listAttributes" to "ListAttributes","colSpec" to "ColSpec","shortCaption" to "ShortCaption","char" to "Char")[kind]
 @Suppress("UNCHECKED_CAST")
 return if(kind=="inlines"||kind=="blocks")GenericDatas.many(GenericDefinitionData.named(if(kind=="inlines")"Inline"else"Block")) as GenericData<Any?> else GenericDefinitionData.named(name?:error(kind))
}
@Suppress("UNCHECKED_CAST")
private fun verifyTypes(value:GenericValue<*>,shape:JsonObject):Int {
 val actual=value.data.type;val expected=shape.getValue("typeInfo").jsonObject
 check(actual.module==expected.getValue("module").jsonPrimitive.content){"module $actual $expected"}
 check(actual.tyConName==expected.getValue("name").jsonPrimitive.content){"name $actual $expected"}
 val sourcePackage=expected.getValue("package").jsonPrimitive.content
 check(sourcePackage==actual.library || sourcePackage.startsWith(actual.library+"-")){"package $actual $sourcePackage"}
 fun arguments(actual:GenericType,expected:JsonObject) {
  check(actual.module==expected.getValue("module").jsonPrimitive.content)
  check(actual.tyConName==expected.getValue("name").jsonPrimitive.content)
  val packageName=expected.getValue("package").jsonPrimitive.content
  check(packageName==actual.library || packageName.startsWith(actual.library+"-"))
  val args=expected.getValue("arguments").jsonArray
  check(actual.arguments.size==args.size)
  for((argument,json)in actual.arguments.zip(args))arguments(argument,json.jsonObject)
 }
 arguments(actual,expected)
 val fields=(value.data as GenericData<Any?>).layer(value::erased).fields
 val children=shape.getValue("fields").jsonArray
 check(fields.size==children.size){"arity $actual"}
 return 1+fields.zip(children).sumOf{(child,json)->verifyTypes(child,json.jsonObject)}
}
private fun existing(kind:String,input:String):Any?=when(kind){
 "pandoc"->PandocRead.Pandoc.read(input)
 "meta"->PandocRead.Meta.read(input)
 "metaValue"->PandocRead.MetaValue.read(input)
 "inline"->PandocRead.Inline.read(input)
 "block"->PandocRead.Block.read(input)
 "style"->PandocRead.ListNumberStyle.read(input)
 "delim"->PandocRead.ListNumberDelim.read(input)
 "format"->PandocRead.Format.read(input)
 "rowHeadColumns"->PandocRead.RowHeadColumns.read(input)
 "alignment"->PandocRead.Alignment.read(input)
 "colWidth"->PandocRead.ColWidth.read(input)
 "row"->PandocRead.Row.read(input)
 "tableHead"->PandocRead.TableHead.read(input)
 "tableBody"->PandocRead.TableBody.read(input)
 "tableFoot"->PandocRead.TableFoot.read(input)
 "caption"->PandocRead.Caption.read(input)
 "cell"->PandocRead.Cell.read(input)
 "rowSpan"->PandocRead.RowSpan.read(input)
 "colSpan"->PandocRead.ColSpan.read(input)
 "quote"->PandocRead.QuoteType.read(input)
 "math"->PandocRead.MathType.read(input)
 "citation"->PandocRead.Citation.read(input)
 "citationMode"->PandocRead.CitationMode.read(input)
 "attr"->PandocRead.Attr.read(input)
 "target"->PandocRead.Target.read(input)
 "listAttributes"->PandocRead.ListAttributes.read(input)
 "colSpec"->PandocRead.ColSpec.read(input)
 "shortCaption"->PandocRead.ShortCaption.read(input)
 "inlines"->PandocRead.Inlines.read(input)
 "char"->PandocRead.Char.read(input)
 "blocks"->PandocRead.Blocks.read(input)
 else->error(kind)
}
internal fun genericConstructorCase(index:Int){
 val requests=GenericProofFixtures.rows("constructor-requests.jsonl")
 val responses=GenericProofFixtures.rows("constructor-responses.jsonl")
 var checks=0;var typeChecks=0
 val request=requests[index];val response=responses[index]

  val kind=request.getValue("kind").jsonPrimitive.content
  val data=root(kind);val input=decode(data,response.getValue("inputShape").jsonObject)
  typeChecks+=verifyTypes(input,response.getValue("inputShape").jsonObject)
  @Suppress("UNCHECKED_CAST") val bridge=(when(kind){"inlines"->GenericAstData.inlines;"blocks"->GenericAstData.blocks;else->GenericAstData.named<Any?>(data.type.name.let{if(kind in setOf("attr","target","listAttributes","colSpec","shortCaption"))mapOf("attr" to "Attr","target" to "Target","listAttributes" to "ListAttributes","colSpec" to "ColSpec","shortCaption" to "ShortCaption").getValue(kind)else it})}) as GenericData<Any?>
  val ast=existing(kind,request.getValue("input").jsonPrimitive.content)
  fun change(c:Int)=if(c==97)88 else c
  for((method,result)in listOf("bottomUp" to PandocGeneric.bottomUp(GenericDatas.char,data,::change,input.value()),"topDown" to PandocGeneric.topDown(GenericDatas.char,data,::change,input.value()))){
   val shown=genericShow(GenericValue(data){result});check(shown==response.getValue(method).jsonPrimitive.content){"$request $method\n$shown\n${response[method]}"};checks++
  }
  val (monadic,effects)=(PandocGeneric.bottomUpM(ConstructorState,GenericDatas.char,data,{c->ConstructorState.Effect{change(c) to (it+String(Character.toChars(c)))}},input.value()) as ConstructorState.Effect<Any?>).run(emptyList())
  check(genericShow(GenericValue(data){monadic})==response.getValue("bottomUpM").jsonPrimitive.content){"bottomUpM $request"};checks++
  check(jsonCharacters(effects)==response.getValue("effects").jsonPrimitive.content){"effects $request"};checks++
  for((method,result)in listOf("bottomUp" to PandocGeneric.bottomUp(GenericDatas.char,bridge,::change,ast),"topDown" to PandocGeneric.topDown(GenericDatas.char,bridge,::change,ast))){
   val converted=GenericValue(bridge){result}.cast(data)
   check(genericShow(GenericValue(data){converted})==response.getValue(method).jsonPrimitive.content){"AST bridge $method $request"};checks++
  }
  val(astMonadic,astEffects)=(PandocGeneric.bottomUpM(ConstructorState,GenericDatas.char,bridge,{c->ConstructorState.Effect{change(c) to (it+String(Character.toChars(c)))}},ast) as ConstructorState.Effect<Any?>).run(emptyList())
  check(genericShow(GenericValue(data){GenericValue(bridge){astMonadic}.cast(data)})==response.getValue("bottomUpM").jsonPrimitive.content){"AST bottomUpM $request"};checks++
  check(jsonCharacters(astEffects)==response.getValue("effects").jsonPrimitive.content){"AST effects $request"};checks++
  check(PandocGeneric.queryWith(GenericDatas.char,bridge,{sequenceOf(it)},ast,GenericMonoids.sequence()).toList()==response.getValue("queryWith").jsonArray.map{it.jsonPrimitive.int}){"AST query $request"};checks++
  val chars=PandocGeneric.queryWith(GenericDatas.char,data,{sequenceOf(it)},input.value(),GenericMonoids.sequence()).toList()
  check(chars==response.getValue("queryWith").jsonArray.map{it.jsonPrimitive.int}){"query $request"};checks++

}
internal fun genericTargetCase(index:Int){
 val requests=GenericProofFixtures.rows("constructor-requests.jsonl")
 val responses=GenericProofFixtures.rows("constructor-responses.jsonl")
 var checks=0;var typeChecks=0
 val request=GenericProofFixtures.rows("target-requests.jsonl")[index];val response=GenericProofFixtures.rows("target-responses.jsonl")[index]

  val example=request.getValue("example").jsonPrimitive.int
  val data=root(request.getValue("kind").jsonPrimitive.content)
  val input=decode(data,responses[example].getValue("inputShape").jsonObject)
  val name=request.getValue("target").jsonPrimitive.content
  @Suppress("UNCHECKED_CAST") val target=(when(name){"char-surrogate"->GenericDatas.char;"text","text-collapse"->GenericDatas.text;"int"->GenericDatas.int;"bool"->GenericDatas.boolean;"string"->GenericDatas.list(GenericDatas.char);"target"->GenericDefinitionData.named("Target");else->error(name)}) as GenericData<Any?>
  @Suppress("UNCHECKED_CAST") fun change(value:Any?):Any?=when(name){"char-surrogate"->55296;"text"->(value as String)+"!";"text-collapse"->"a";"int"->(value as Long)+1L;"bool"->!(value as Boolean);"string"->(value as List<Int>).reversed();"target"->{val pair=value as GenericTuple2<String,String>;GenericTuple2({pair.first+"!"},{pair.second})};else->error(name)}
  for((method,result)in listOf("bottomUp" to PandocGeneric.bottomUp(target,data,::change,input.value()),"topDown" to PandocGeneric.topDown(target,data,::change,input.value()))){
   val shown=genericShow(GenericValue(data){result});check(shown==response.getValue(method).jsonPrimitive.content){"$request $method\n$shown\n${response[method]}"};checks++
  }
  val (monadic,effects)=(PandocGeneric.bottomUpM(ConstructorState,target,data,{v->ConstructorState.Effect{change(v) to (it+genericShow(GenericValue(target){v}))}},input.value()) as ConstructorState.Effect<Any?>).run(emptyList())
  check(genericShow(GenericValue(data){monadic})==response.getValue("bottomUpM").jsonPrimitive.content){"target bottomUpM $request"};checks++
  check(effects==response.getValue("effects").jsonArray.map{it.jsonPrimitive.content}){"target effects $request"};checks++
  val kind=request.getValue("kind").jsonPrimitive.content
  @Suppress("UNCHECKED_CAST") val bridge=(when(kind){"inlines"->GenericAstData.inlines;"blocks"->GenericAstData.blocks;else->GenericAstData.named<Any?>(data.type.name.let{if(kind in setOf("attr","target","listAttributes","colSpec","shortCaption"))mapOf("attr" to "Attr","target" to "Target","listAttributes" to "ListAttributes","colSpec" to "ColSpec","shortCaption" to "ShortCaption").getValue(kind)else it})}) as GenericData<Any?>
  val ast=existing(kind,request.getValue("input").jsonPrimitive.content)
  for((method,result)in listOf("bottomUp" to PandocGeneric.bottomUp(target,bridge,::change,ast),"topDown" to PandocGeneric.topDown(target,bridge,::change,ast))){
   check(genericShow(GenericValue(data){GenericValue(bridge){result}.cast(data)})==response.getValue(method).jsonPrimitive.content){"target AST $method $request"};checks++
  }
  val(astMonadic,astEffects)=(PandocGeneric.bottomUpM(ConstructorState,target,bridge,{v->ConstructorState.Effect{change(v) to (it+genericShow(GenericValue(target){v}))}},ast) as ConstructorState.Effect<Any?>).run(emptyList())
  check(genericShow(GenericValue(data){GenericValue(bridge){astMonadic}.cast(data)})==response.getValue("bottomUpM").jsonPrimitive.content){"target AST bottomUpM $request"};checks++
  check(astEffects==response.getValue("effects").jsonArray.map{it.jsonPrimitive.content}){"target AST effects $request"};checks++
  check(PandocGeneric.queryWith(target,bridge,{sequenceOf(genericShow(GenericValue(target){it}))},ast,GenericMonoids.sequence()).toList()==response.getValue("queryWith").jsonArray.map{it.jsonPrimitive.content}){"target AST query $request"};checks++
  val queried=PandocGeneric.queryWith(target,data,{sequenceOf(genericShow(GenericValue(target){it}))},input.value(),GenericMonoids.sequence()).toList()
  check(queried==response.getValue("queryWith").jsonArray.map{it.jsonPrimitive.content}){"target query $request"};checks++

}
fun genericConstructorReplayMain(){
 for(i in GenericProofFixtures.rows("constructor-requests.jsonl").indices)genericConstructorCase(i)
 for(i in GenericProofFixtures.rows("target-requests.jsonl").indices)genericTargetCase(i)
 println("Original constructor/target cases PASS, allfourfunctions throughcanonical andexistingAST adapters")
}
