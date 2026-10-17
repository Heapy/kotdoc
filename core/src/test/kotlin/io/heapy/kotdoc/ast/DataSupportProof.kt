package io.heapy.kotdoc.ast
import kotlinx.serialization.json.*
import java.io.File
private class SupportTag
private class SupportConst<A>(val value:DataThunk<String>):DataKind<SupportTag,A>{override fun forceGenericRepRoot(){value()}}
private val ignore=object:DataUnfold<SupportTag> {
 override fun <A> inject(value:DataThunk<A>):DataKind<SupportTag,A> = SupportConst(DataThunk{"zero"})
 override fun <D,B> apply(evidence:DataEvidence<D>,function:DataThunk<DataKind<SupportTag,DataFunction<D,B>>>):DataKind<SupportTag,B> = SupportConst(DataThunk{"field"})
}
fun main(){
 val expected=DataProofFiles.lines("support-native.jsonl").map(Json::parseToJsonElement)
 val integer=DataDictionaries.int();val double=DataDictionaries.double();val character=DataDictionaries.char()
 val dictionaries=listOf(integer,double,character,DataDictionaries.boolean(),DataDictionaries.text(),
  DataDictionaries.list(integer.evidence),DataDictionaries.tuple2(integer.evidence,integer.evidence),
  DataDictionaries.tuple3(integer.evidence,integer.evidence,integer.evidence),DataDictionaries.maybe(integer.evidence),
  DataDictionaries.textMap(integer.evidence),DataDictionaries.sequence(integer.evidence))
 var index=0
 for(dictionary in dictionaries) {
  @Suppress("UNCHECKED_CAST") val data=(dictionary as DataDictionary<Any?>).dataTypeOf(DataThunk{error("unused root")})
  val value=buildJsonObject {
   put("name",data.name);put("rep",when(data.representation){is DataTypeRep.Algebraic->"AlgRep";DataTypeRep.Integral->"IntRep";DataTypeRep.Floating->"FloatRep";DataTypeRep.Character->"CharRep";DataTypeRep.None->"NoRep"})
   put("constructors",buildJsonArray{if(data.representation is DataTypeRep.Algebraic)for(constructor in data.constructors())add(buildJsonArray{
    add((constructor.representation as DataConstrRep.Algebraic).index);add(constructor.name);add(buildJsonArray{for(field in constructor.fields)add(field)});add(constructor.fixity.name)
   })})
  }
  check(value==expected[index]){"support metadata $index $value != ${expected[index]}"};index++
 }
 fun probe(name:String,body:()->String){val got=try{body()}catch(_:Throwable){"error"};val value=buildJsonArray{add(name);add(got)};check(value==expected[index]){"$value != ${expected[index]}"};index++}
 fun <A> primitive(name:String,dictionary:DataDictionary<A>,mode:Int) {
  probe("$name-${if(mode==0)"toConstr-WHNF-bottom"else"ConstrRep-WHNF-bottom"}"){
   val constructor=PandocDataClass.toConstr(dictionary){error("value")}()
   if(mode==0)"constructor"else{constructor.representation;"representation"}
  }
 }
 primitive("Int",integer,0);primitive("Double",double,0);primitive("Char",character,0)
 primitive("Int",integer,1);primitive("Double",double,1);primitive("Char",character,1)
 fun <A> unfold(name:String,dictionary:DataDictionary<A>)=probe("$name-gunfold-z-ignores-payload"){
  (PandocDataClass.gunfold(dictionary,ignore){PandocDataClass.toConstr(dictionary){error("value")}()}() as SupportConst<*>).value()
 }
 unfold("Int",integer);unfold("Double",double);unfold("Char",character)
 probe("Int-gunfold-wrong-tag"){(PandocDataClass.gunfold(integer,ignore){PandocDataClass.toConstr(character){97}()}() as SupportConst<*>).value()}
 probe("Int-gunfold-bottom-constr"){(PandocDataClass.gunfold(integer,ignore){error("constructor")}() as SupportConst<*>).value()}
 probe("Int-Constr-name-bottom"){PandocDataClass.toConstr(integer){error("value")}().name}
 probe("Double-Constr-name-bottom"){PandocDataClass.toConstr(double){error("value")}().name}
 probe("Char-Constr-name-bottom"){PandocDataClass.toConstr(character){error("value")}().name}
 check(index==expected.size)
 println("$index independent supporting metadata/primitive demand observations PASS")
}
