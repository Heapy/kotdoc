package io.heapy.kotdoc.ast

import kotlinx.serialization.json.*

private class CastTag
fun main() {
    val expected=DataProofFiles.lines("cast-native.jsonl").map {Json.parseToJsonElement(it).jsonArray}
    val integer=DataDictionaries.int();val text=DataDictionaries.text()
    val list=DataDictionaries.list(integer.evidence);val maybe=DataDictionaries.maybe(integer.evidence)
    val seq=DataDictionaries.sequence(integer.evidence)
    val tuple=DataDictionaries.tuple2(integer.evidence,integer.evidence)
    val map=DataDictionaries.textMap(integer.evidence)
    val tuple3=DataDictionaries.tuple3(integer.evidence,integer.evidence,integer.evidence)
    val one=object:DataCast1<CastTag>{override fun <A> value(argument:DataEvidence<A>):DataKind<CastTag,Any?> = error("payload")}
    val two=object:DataCast2<CastTag>{override fun <A,B> value(left:DataEvidence<A>,right:DataEvidence<B>):DataKind<CastTag,Any?> = error("payload")}
    var index=0
    fun probe(name:String,body:()->String) {
        val got=try{body()}catch(_:Exception){"error"}
        check(buildJsonArray{add(name);add(got)}==expected[index++]){"$name: $got"}
    }
    fun present(value:GenericMaybe<*>)=if(value is GenericMaybe.Some)"True"else"False"
    probe("list-positive-ignores-payload"){present(PandocDataClass.dataCast1(list,{list.representation.type},one)())}
    probe("list-negative-ignores-payload"){present(PandocDataClass.dataCast1(list,{maybe.representation.type},one)())}
    probe("maybe-positive-ignores-payload"){present(PandocDataClass.dataCast1(maybe,{maybe.representation.type},one)())}
    probe("seq-positive-ignores-payload"){present(PandocDataClass.dataCast1(seq,{seq.representation.type},one)())}
    probe("tuple-positive-ignores-payload"){present(PandocDataClass.dataCast2(tuple,{tuple.representation.type},two)())}
    probe("map-positive-ignores-payload"){present(PandocDataClass.dataCast2(map,{map.representation.type},two)())}
    probe("tuple-negative-ignores-payload"){present(PandocDataClass.dataCast2(tuple,{map.representation.type},two)())}
    probe("int-default-ignores-payload"){present(PandocDataClass.dataCast1(integer,{error("constructor")},one)())}
    probe("text-default-ignores-payload"){present(PandocDataClass.dataCast1(text,{error("constructor")},one)())}
    probe("tuple3-default-ignores-payload"){present(PandocDataClass.dataCast2(tuple3,{error("constructor")},two)())}
    probe("list-positive-demands-payload"){(PandocDataClass.dataCast1(list,{list.representation.type},one)() as GenericMaybe.Some).value;"payload"}
    probe("tuple-positive-demands-payload"){(PandocDataClass.dataCast2(tuple,{tuple.representation.type},two)() as GenericMaybe.Some).value;"payload"}
    check(index==expected.size)
    println("$index independent supporting cast result/payload observations PASS")
}
