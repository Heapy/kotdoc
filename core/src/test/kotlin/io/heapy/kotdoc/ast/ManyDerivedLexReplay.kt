package io.heapy.kotdoc.ast
import kotlinx.serialization.json.*
private fun lexInput(prefix:IntArray,mode:String):ManyReadInput {
    fun infinite():GenericList<Int> = GenericList.cons({33}){infinite()}
    fun suffix():GenericList<Int> = when(mode){"empty"->GenericList.empty();"bottom"->error("lex-tail");"bad-head"->GenericList.cons({error("lex-head")}){GenericList.empty()};"bad-next"->GenericList.cons({33}){error("lex-next")};"infinite"->infinite();else->error(mode)}
    fun at(i:Int):GenericList<Int> = GenericList.defer{if(i==prefix.size)suffix()else GenericList.cons({prefix[i]}){at(i+1)}}
    return ManyReadInput{at(0)}
}
fun replayManyDerivedLex(only:Int?=null){
    val rows=ManyDerivedProof.lines("lex-demand-native.jsonl")
    val dictionary=ManyReadDictionary(object:ManyElementRead<Unit>{override fun readPrec()=ManyReadPrec.fail<Unit>()})
    for(i in only?.let{listOf(it)}?:rows.indices.toList()){
        val row=Json.parseToJsonElement(rows[i]).jsonObject
        val prefix=row.getValue("prefix").jsonArray.map{it.jsonPrimitive.int}.toIntArray();val mode=row.getValue("tail").jsonPrimitive.content
        val actual=try{val empty=dictionary.readPrec(0,lexInput(prefix,mode)).step() is GenericList.Step.Nil;buildJsonObject{put("result",if(empty)"True"else"False")}}catch(e:IllegalStateException){buildJsonObject{put("exception",e.message!!.lineSequence().first())}}
        val expected=buildJsonObject{row["result"]?.let{put("result",it)};row["exception"]?.let{put("exception",it)}}
        check(actual==expected){"Lexer row$i ${row["name"]}:$actual !=$expected"}
    }
    if(only==null)println("Many full lexP demand original replay PASS ${rows.size}")
}
fun main()=replayManyDerivedLex()
