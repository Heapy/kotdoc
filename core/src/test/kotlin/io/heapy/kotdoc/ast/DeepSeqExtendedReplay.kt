package io.heapy.kotdoc.ast
import kotlinx.serialization.json.*
import java.io.File

private fun space()=DeepSeqData.node("Inline","Space",emptyList())
private fun emph(list:()->List<Any?>)=DeepSeqData.node("Inline","Emph",listOf(DeepSeqValue(DeepSeqData.list(DeepSeqData.named("Inline")),list)))
private fun failInline(message:String)=DeepSeqValue(DeepSeqData.named("Inline")){error(message)}
private fun value(block:()->Any?)=DeepSeqValue(DeepSeqData.named("Inline"),block)
private fun normal(input:DeepSeqValue<*>):String { @Suppress("UNCHECKED_CAST") (input.dictionary as NFData<Any?>).rnf{input.value()};return "()" }
@Suppress("UNCHECKED_CAST")
private fun tuple(vararg fields:DeepSeqValue<*>)=DeepSeqValue(DeepSeqData.tuple(fields.map{it.dictionary as NFData<Any?>})){DeepSeqTuple(fields.toList())}
private fun key(value:()->String)=DeepSeqValue(DeepSeqData.text,value)
private fun map(keyData:NFData<*> =DeepSeqData.text,itemData:NFData<*> =DeepSeqData.named("Inline"),root:()->DeepSeqMap)=DeepSeqValue(DeepSeqData.map(keyData,itemData),root)
private fun bin(key:DeepSeqValue<*>,item:DeepSeqValue<*>,left:()->DeepSeqMap={DeepSeqMap.tip()},right:()->DeepSeqMap={DeepSeqMap.tip()})=DeepSeqMap.bin({1L},key,item,left,right)
private class CustomPairBox(first:()->Any?,second:()->Any?) {
    private val a=GenericMemo(first);private val b=GenericMemo(second)
    val first get()=a.get();val second get()=b.get()
}
private val pairDictionary=NFData<CustomPairBox>{input->val box=input();DeepSeqData.named("Inline").rnf{box.first}}
private fun pairBox(first:()->Any?={space().value()},second:()->Any?={error("second")})=CustomPairBox(first,second)
internal fun deepSeqExtendedExecute(name:String):JsonObject = try {
    val result:String=if(name.startsWith("return/")) {
        val parts=name.split('/');val operation=parts[1];val shape=parts[2]
        val output:()->Any?={when(shape){
            "list-root"->GenericList.defer<Any?>{error("result root")}
            "list-head"->GenericList.cons<Any?>({error("head")},{GenericList.empty()})
            "map-root"->DeepSeqMap.defer{error("result root")}
            "map-value"->bin(key{"k"},failInline("value"))
            "map-size"->DeepSeqMap.bin({error("size")},key{"k"},space(),{DeepSeqMap.tip()},{DeepSeqMap.tip()})
            "map-left"->bin(key{"k"},space(),{error("left")})
            "node-root"->DeepSeqNode("Inline","Emph",listOf(DeepSeqValue(DeepSeqData.list(DeepSeqData.named("Inline"))){error("field")}))
            "node-head"->DeepSeqNode("Inline","Emph",listOf(DeepSeqValue(DeepSeqData.list(DeepSeqData.named("Inline"))){GenericList.cons({error("head")},{GenericList.empty()})}))
            "newtype"->DeepSeqNode("RowSpan","RowSpan",listOf(DeepSeqValue(DeepSeqData.long){error("int")}))
            "tuple"->DeepSeqTuple(listOf(failInline("first"),space()))
            "input-first","unused"->GenericList.defer<Any?>{error("result root")}
            else->error("return shape")
        }}
        val dictionary=if(shape=="input-first")DeepSeqData.named("Inline")else NFData<Any?>{}
        val result=if(operation=="deepseq")PandocDeepSeq.deepseq(dictionary,{error(if(shape=="input-first")"input"else"ignored")},output)
            else PandocDeepSeq.strictApply(dictionary,{{_:()->Any?->output()}},{error(if(shape=="input-first")"input"else"ignored")})
        if(shape!="unused")result()
        "7"
    } else when(name) {
        "rnf/ignored"->{PandocDeepSeq.rnfLazy(DeepSeqData.named("Inline")){error("root")};"7"}
        "rwhnf/ignored"->{PandocDeepSeq.rwhnfLazy(failInline("root"));"7"}
        "custom/force-ignored"->{PandocDeepSeq.force(NFData<Any?>{}){error("input")};"7"}
        "custom/pair-root"->{pairDictionary.rnf{error("pair")};"()"}
        "custom/pair-second-ignored"->{pairDictionary.rnf{pairBox()};"()"}
        "custom/pair-first"->{pairDictionary.rnf{pairBox({error("first")},{space().value()})};"()"}
        "custom/pair-deep"->{pairDictionary.rnf{pairBox({emph{GenericList.cons({error("head")},{GenericList.empty()})}.value()})};"()"}
        "custom/pair-force"->{PandocDeepSeq.force(pairDictionary){pairBox()}();"7"}
        "custom/pair-apply-ignored"->PandocDeepSeq.strictApply(pairDictionary,{{_:()->CustomPairBox->7}},{pairBox()})().toString()
        "custom/pair-apply-second"->PandocDeepSeq.strictApply(pairDictionary,{{get:()->CustomPairBox->get().second;7}},{pairBox()})().toString()
        "force/ignored"->{PandocDeepSeq.force(DeepSeqData.named("Inline")){error("root")};"7"}
        "force/root"->{PandocDeepSeq.force(DeepSeqData.named("Inline")){error("root")}();"7"}
        "force/deep"->{val x=emph{GenericList.cons({error("head")},{GenericList.empty()})};PandocDeepSeq.force(x.dictionary){x.value()}();"7"}
        "force/identity"->{val x=emph{GenericList.cons({space().value()},{GenericList.empty()})};val out=PandocDeepSeq.force(x.dictionary){x.value()}() as DeepSeqNode;val list=out.fields.single().value() as List<*>;val child=list.single() as DeepSeqNode;out.constructor+" ["+child.constructor+"]"}
        "deepseq/ignored"->{PandocDeepSeq.deepseq(DeepSeqData.named("Inline"),{error("first")}){error("second")};"7"}
        "deepseq/first"->{PandocDeepSeq.deepseq(DeepSeqData.named("Inline"),{error("first")}){error("second")}();error("unreachable")}
        "deepseq/second"->{val x=space();PandocDeepSeq.deepseq(x.dictionary,{x.value()}){error("second")}();error("unreachable")}
        "strictApply/const"->{val x=emph{GenericList.cons({error("head")},{GenericList.empty()})};PandocDeepSeq.strictApply(x.dictionary,{{_:()->Any?->7}},{x.value()})().toString()}
        "strictApply/function"->{val x=space();PandocDeepSeq.strictApply<Any?,Int>(x.dictionary,{error("function")},{x.value()})().toString()}
        "strictApply/both"->PandocDeepSeq.strictApply<Any?,Int>(DeepSeqData.named("Inline"),{error("function")},{error("input")})().toString()
        "rwhnf/lazy-child"->{DeepSeqData.whnf(emph{GenericList.cons({error("head")},{GenericList.empty()})});"()"}
        "rwhnf/newtype"->{DeepSeqData.whnf(DeepSeqData.node("RowSpan","RowSpan",listOf(DeepSeqValue(DeepSeqData.long){error("int")})));"()"}
        "custom/deepseq-ignored"->PandocDeepSeq.deepseq(NFData<Any?>{},{error("input")}){7}().toString()
        "custom/strictApply-ignored"->PandocDeepSeq.strictApply(NFData<Any?>{},{{_:()->Any?->7}},{error("input")})().toString()
        "custom/strictApply-demanded"->PandocDeepSeq.strictApply(NFData<Any?>{},{{x:()->Any?->x();7}},{error("input")})().toString()
        "custom/strict-root/force/root","custom/strict-root/force/head","custom/strict-root/apply/root","custom/strict-root/apply/head"->{
            val x=emph{if(name.endsWith("/root"))error("field")else GenericList.cons({error("head")},{GenericList.empty()})}
            if(name.contains("/force/")){PandocDeepSeq.force(NFData<Any?>{}){x.value()}();"7"}
            else PandocDeepSeq.strictApply(NFData<Any?>{},{{get:()->Any?->get();7}},{x.value()})().toString()
        }
        "custom/force"->{PandocDeepSeq.force(NFData<Any?>{}){error("input")}();"7"}
        "custom/map-value-ignored"->normal(map(itemData=NFData<Any?>{}){bin(key{"k"},DeepSeqValue(NFData<Any?>{}){error("value")})})
        "custom/map-key-root"->normal(map(keyData=NFData<Any?>{}){bin(DeepSeqValue(NFData<Any?>{}){error("key")},space())})
        "custom/map-key-pair"->normal(map(keyData=pairDictionary){bin(DeepSeqValue(pairDictionary){pairBox()},space())})
        "custom/map-key-pair-first"->normal(map(keyData=pairDictionary){bin(DeepSeqValue(pairDictionary){pairBox({error("first")},{space().value()})},space())})
        "custom/map-key-before-value"->normal(map(keyData=pairDictionary){bin(DeepSeqValue(pairDictionary){pairBox({error("first")},{space().value()})},failInline("value"))})
        "custom/ignored"->normal(DeepSeqValue(NFData<Any?>{}){error("ignored")})
        "custom/list","custom/list-tail"->{val ignored=NFData<Any?>{};normal(DeepSeqValue(DeepSeqData.list(ignored)){GenericList.cons({error("ignored")},{if(name=="custom/list-tail")error("tail")else GenericList.empty()})})}
        "custom/tuple"->normal(tuple(DeepSeqValue(NFData<Any?>{}){error("ignored")},space()))
        "list/head","list/tail","list/order"->normal(DeepSeqValue(DeepSeqData.list(DeepSeqData.named("Inline"))){GenericList.cons({if(name=="list/tail")space().value()else error("head")},{if(name=="list/head")GenericList.empty()else error("tail")})})
        "tuple/order"->normal(tuple(failInline("first"),failInline("second")))
        "triple/order"->normal(tuple(space(),failInline("second"),failInline("third")))
        "maybe/none"->normal(DeepSeqValue(DeepSeqData.maybe(DeepSeqData.named("Inline"))){GenericMaybe.None})
        "maybe/just"->normal(DeepSeqValue(DeepSeqData.maybe(DeepSeqData.named("Inline"))){GenericMaybe.Some{error("just")}})
        "map/value"->normal(map{bin(key{"k"},failInline("value"))})
        "map/key"->normal(map{bin(key{error("key")},space())})
        "map/left"->normal(map{bin(key{"k"},space(),{error("left")})})
        "map/right"->normal(map{bin(key{"k"},space(),right={error("right")})})
        "map/root-before-value"->normal(map{bin(key{"k"},failInline("value"),{error("left")})})
        "map/value-before-left-value"->normal(map{bin(key{"m"},failInline("root-value"),{bin(key{"a"},failInline("left-value"))})})
        "map/key-before-value"->normal(map{bin(key{error("key")},failInline("value"))})
        "map/size-min","map/size-max"->normal(map{DeepSeqMap.bin({if(name=="map/size-min")Long.MIN_VALUE else Long.MAX_VALUE},key{"k"},space(),{DeepSeqMap.tip()},{DeepSeqMap.tip()})})
        "double/nan","double/positive-infinity","double/negative-infinity"->normal(DeepSeqData.node("ColWidth","ColWidth",listOf(DeepSeqValue(DeepSeqData.double){when(name){"double/nan"->Double.NaN;"double/positive-infinity"->Double.POSITIVE_INFINITY;else->Double.NEGATIVE_INFINITY}})))
        else->if(name.startsWith("scalar/")) {
            val parts=name.split('/');val dictionary=DeepSeqData.named(parts[1])
            normal(DeepSeqValue(dictionary){if(parts[2]=="root")error("scalar")else when(parts[1]){"Int"->0L;"Double"->Double.NaN;"Bool"->false;"Char"->'x'.code;"Text"->"unicode漢😀";else->error("scalar type")}})
        } else {
            val parts=name.split('/');val size=parts.last().toInt()
            when(parts[1]) {
                "inline"->{var x=space();repeat(size){val old=x;x=emph{GenericList.cons({old.value()},{GenericList.empty()})}};normal(x)}
                "list","list-last"->{var xs:GenericList<Any?> = if(parts[1]=="list-last")GenericList.cons({error("last")},{GenericList.empty()})else GenericList.empty();repeat(size){val tail=xs;xs=GenericList.cons({space().value()},{tail})};normal(DeepSeqValue(DeepSeqData.list(DeepSeqData.named("Inline"))){xs})}
                "map-skew","map-skew-tail"->{var tree=if(parts[1]=="map-skew-tail")DeepSeqMap.defer{error("tail")}else DeepSeqMap.tip();for(i in size downTo 1){val old=tree;tree=bin(DeepSeqValue(DeepSeqData.long){i.toLong()},space(),{old})};normal(map(keyData=DeepSeqData.long){tree})}
                "map"->{fun tree(from:Int,to:Int):DeepSeqMap {if(from==to)return DeepSeqMap.tip();val mid=(from+to) ushr 1;return bin(DeepSeqValue(DeepSeqData.long){mid.toLong()},space(),{tree(from,mid)},{tree(mid+1,to)})};normal(map(keyData=DeepSeqData.long){tree(0,size)})}
                else->error("probe")
            }
        }
    }
    buildJsonObject{put("result",result)}
} catch(e:Exception){buildJsonObject{put("exception",e.message?.lineSequence()?.firstOrNull()?:e.javaClass.name)}}
fun main(){
 val lines=File("tools/haskell-types-deepseq-reference/extended-native.jsonl").readLines().chunked(2)
 for((wire,want)in lines){val name=Json.parseToJsonElement(wire).jsonPrimitive.content;val actual=deepSeqExtendedExecute(name);check(actual==Json.parseToJsonElement(want)){"$name expected$want actual$actual"}}
 println("${lines.size} original NFData extended/deep assertions PASS")
}
