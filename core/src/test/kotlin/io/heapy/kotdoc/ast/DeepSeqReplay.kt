package io.heapy.kotdoc.ast
import kotlinx.serialization.json.*
import java.io.File
@Suppress("UNCHECKED_CAST")
internal fun deepSeqExecute(request:JsonObject):JsonObject = try {
    val name=request.getValue("kind").jsonPrimitive.content
    val dictionary=PandocRead::class.java.getMethod("get"+name).invoke(PandocRead) as ReadDictionary<Any?>
    val input=DeepSeqData.fromAst(name){dictionary.read(request.getValue("input").jsonPrimitive.content)}
    val result:JsonElement=when(request.getValue("operation").jsonPrimitive.content) {
        "rnf"->{PandocDeepSeq.rnfLazy(input.dictionary){input.value()}();JsonPrimitive("()")}
        "force"->{PandocDeepSeq.force(input.dictionary){input.value()}();JsonPrimitive("forced")}
        "deepseq"->JsonPrimitive(PandocDeepSeq.deepseq(input.dictionary,{input.value()}){7}())
        "rwhnf"->{PandocDeepSeq.rwhnfLazy(input)();JsonPrimitive("()")}
        else->error("Operation")
    }
    buildJsonObject{put("result",result)}
} catch(e:Exception) {buildJsonObject{put("exception",e.message?.lineSequence()?.firstOrNull()?:e.javaClass.name)}}
@Suppress("UNCHECKED_CAST")
internal fun deepSeqTypedExecute(request:JsonObject):JsonObject = try {
    val name=request.getValue("kind").jsonPrimitive.content
    val read=PandocRead::class.java.getMethod("get"+name).invoke(PandocRead) as ReadDictionary<Any?>
    val dictionary=PandocNFData::class.java.getMethod("get"+name).invoke(PandocNFData) as NFData<Any?>
    val input=GenericMemo { val parsed=read.read(request.getValue("input").jsonPrimitive.content)
        when(name){"RowSpan"->RowSpan(parsed as Long);"ColSpan"->ColSpan(parsed as Long);"RowHeadColumns"->RowHeadColumns(parsed as Long);else->parsed}
    }
    val result:JsonElement=when(request.getValue("operation").jsonPrimitive.content) {
        "rnf"->{dictionary.rnf{input.get()};JsonPrimitive("()")}
        "force"->{PandocDeepSeq.force(dictionary){input.get()}();JsonPrimitive("forced")}
        "deepseq"->JsonPrimitive(PandocDeepSeq.deepseq(dictionary,{input.get()}){7}())
        "rwhnf"->{PandocDeepSeq.rwhnf{input.get()};JsonPrimitive("()")}
        else->error("Operation")
    }
    buildJsonObject{put("result",result)}
} catch(e:Exception){buildJsonObject{put("exception",e.message?.lineSequence()?.firstOrNull()?:e.javaClass.name)}}
fun main() {
    val root=File("tools/haskell-types-deepseq-reference")
    val requests=File(root,"requests.jsonl").readLines();val expected=File(root,"responses.jsonl").readLines()
    for(i in requests.indices) {
        val actual=deepSeqExecute(Json.parseToJsonElement(requests[i]).jsonObject)
        check(deepSeqTypedExecute(Json.parseToJsonElement(requests[i]).jsonObject)==Json.parseToJsonElement(expected[i]))
        check(actual==Json.parseToJsonElement(expected[i])){"Case $i ${requests[i]} expected ${expected[i]} actual $actual"}
    }
    println("${requests.size} original NFData finite assertions PASS")
}
