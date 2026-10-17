package io.heapy.kotdoc.ast
import kotlinx.serialization.json.*
import java.io.File
private val listType=GenericDatas.list(GenericDatas.int).type
private val fieldType=GenericRepType.Field(listType)
private class CustomStrict(value:()->Any?):GenericRepRoot {
    private val input=SourceThunk(value)
    val field get()=input()
    override fun forceGenericRepRoot(){field}
}
private class CustomLazyPair(first:()->Any?,second:()->Any?):GenericRepRoot {
    private val a=SourceThunk(first);private val b=SourceThunk(second)
    val first get()=a();val second get()=b()
    override fun forceGenericRepRoot(){}
}
private object IgnoreDictionary:GenericRepDictionary<Unit> {
    override val representation=GenericRepType.Unit
    override fun from(value:GenericRepThunk<Unit>)=GenericRepValue(representation){GenericRepLayer.Unit}
    override fun to(value:GenericRepValue)=GenericRepThunk{Unit}
}
private object StrictDictionary:GenericRepDictionary<CustomStrict> {
    override val representation=fieldType
    override fun from(value:GenericRepThunk<CustomStrict>)=GenericRepValue(representation){GenericRepLayer.Field(GenericRepField(listType){value().field})}
    override fun to(value:GenericRepValue)=GenericRepThunk{CustomStrict{(value.layer() as GenericRepLayer.Field).value.value()}}
}
private object PairDictionary:GenericRepDictionary<CustomLazyPair> {
    override val representation=GenericRepType.Product(fieldType,fieldType)
    override fun from(value:GenericRepThunk<CustomLazyPair>)=GenericRepValue(representation){val pair=value();GenericRepLayer.Product(
        GenericRepValue(fieldType){GenericRepLayer.Field(GenericRepField(listType){pair.first})},
        GenericRepValue(fieldType){GenericRepLayer.Field(GenericRepField(listType){pair.second})})}
    override fun to(value:GenericRepValue)=GenericRepThunk{val pair=value.layer() as GenericRepLayer.Product
        CustomLazyPair({(pair.left.layer() as GenericRepLayer.Field).value.value()},{(pair.right.layer() as GenericRepLayer.Field).value.value()})}
}
private val bodyMType=GenericRepType.Metadata(GenericRepMetadata.Datatype("BodyM","Oracle","oracle",false),GenericRepType.Unit)
private object BodyMDictionary:GenericRepDictionary<Unit> {
    override val representation=bodyMType
    override fun from(value:GenericRepThunk<Unit>):GenericRepValue {
        value()
        return GenericRepValue(representation){GenericRepLayer.Metadata(GenericRepValue(GenericRepType.Unit){GenericRepLayer.Unit})}
    }
    override fun to(value:GenericRepValue):GenericRepThunk<Unit> {
        ((value.layer() as GenericRepLayer.Metadata).child).layer()
        return GenericRepThunk{Unit}
    }
}
private class BodyK(value:()->Any?):GenericRepRoot {
    private val input=SourceThunk(value)
    val field get()=input()
    override fun forceGenericRepRoot(){}
}
private object BodyKDictionary:GenericRepDictionary<BodyK> {
    override val representation=fieldType
    override fun from(value:GenericRepThunk<BodyK>):GenericRepValue {
        val box=value()
        return GenericRepValue(representation){GenericRepLayer.Field(GenericRepField(listType){box.field})}
    }
    override fun to(value:GenericRepValue):GenericRepThunk<BodyK> {
        val input=value.layer() as GenericRepLayer.Field
        return GenericRepThunk{BodyK{input.value.value()}}
    }
}
private fun headFailure():GenericList<Long> = GenericList.cons({error("head")},{GenericList.empty()})
private fun field(value:()->Any?)=GenericRepValue(fieldType){GenericRepLayer.Field(GenericRepField(listType,value))}
internal fun genericRepCustomExecute(name:String):JsonObject { return try {
    if(name.startsWith("float/")) {
        val bits=name.substringAfter('/').toULong(16).toLong();val x=Double.fromBits(bits)
        val dictionary=GenericRepData.definition("ColWidth")
        val input=GenericRepData.source("ColWidth","ColWidth",listOf(GenericRepField(GenericDatas.double.type){x}))
        val rep=dictionary.from{input}
        var current=rep
        while(true)when(val node=current.layer()){
            is GenericRepLayer.Metadata->current=node.child
            is GenericRepLayer.Sum->current=node.child
            is GenericRepLayer.Field->{
                val first=(node.value.value() as Double).toRawBits()
                val last=(dictionary.to(rep)().fields.single().value() as Double).toRawBits()
                val typed=GenericRepData.ast<ColWidth>("ColWidth")
                val ast=typed.to(typed.from{ColWidth.Width(x)})() as ColWidth.Width
                check(ast.value.toRawBits()==bits)
                return buildJsonObject{put("result",java.lang.Long.toUnsignedString(first)+":"+java.lang.Long.toUnsignedString(last))}
            }
            else->error("float representation")
        }
    }

    if(name.startsWith("sharing/")) {
        var calls=0;var roots=0
        val operation=name.substringAfter('/')
        val observation=when(operation){
            "fromM"->{
                val dictionary=object:GenericRepDictionary<Unit>{
                    override val representation=bodyMType
                    override fun from(value:GenericRepThunk<Unit>):GenericRepValue{calls++;return BodyMDictionary.from(value)}
                    override fun to(value:GenericRepValue)=BodyMDictionary.to(value)
                }
                val rep=PandocGenericRep.from(dictionary){error("input")};rep.layer();val before=calls
                repeat(2){try{rep.whnf()}catch(_:IllegalStateException){}}
                "$before:$calls"
            }
            "fromK"->{
                val dictionary=object:GenericRepDictionary<BodyK>{
                    override val representation=fieldType
                    override fun from(value:GenericRepThunk<BodyK>):GenericRepValue{calls++;return BodyKDictionary.from(value)}
                    override fun to(value:GenericRepValue)=BodyKDictionary.to(value)
                }
                val rep=PandocGenericRep.from(dictionary){error("input")};rep.layer();val before=calls
                repeat(2){try{rep.whnf()}catch(_:IllegalStateException){}}
                "$before:$calls"
            }
            "toM"->{
                val dictionary=object:GenericRepDictionary<Unit>{
                    override val representation=bodyMType
                    override fun from(value:GenericRepThunk<Unit>)=BodyMDictionary.from(value)
                    override fun to(value:GenericRepValue):GenericRepThunk<Unit>{calls++;return BodyMDictionary.to(value)}
                }
                val output=PandocGenericRep.to(dictionary,GenericRepValue(bodyMType){error("representation")});val before=calls
                repeat(2){try{output()}catch(_:IllegalStateException){}}
                "$before:$calls"
            }
            "toK"->{
                val dictionary=object:GenericRepDictionary<BodyK>{
                    override val representation=fieldType
                    override fun from(value:GenericRepThunk<BodyK>)=BodyKDictionary.from(value)
                    override fun to(value:GenericRepValue):GenericRepThunk<BodyK>{calls++;return BodyKDictionary.to(value)}
                }
                val output=PandocGenericRep.to(dictionary,GenericRepValue(fieldType){roots++;error("representation")});val before=calls
                val box=output();output();val unforced=roots
                repeat(2){try{box.field}catch(_:IllegalStateException){}}
                "$before:$calls:$unforced:$roots"
            }
            else->error("sharing")
        }
        return buildJsonObject{put("result",observation)}
    }
    if(name.startsWith("bodyM/")) {
        val operation=name.substringAfter('/')
        when(operation){
            "from-unused"->PandocGenericRep.from(BodyMDictionary){error("input")}
            "from-projection"->PandocGenericRep.from(BodyMDictionary){error("input")}.layer()
            "from-root"->PandocGenericRep.from(BodyMDictionary){error("input")}.whnf()
            "to-unused"->PandocGenericRep.to(BodyMDictionary,GenericRepValue(bodyMType){error("representation")})
            "to-root"->PandocGenericRep.to(BodyMDictionary,GenericRepValue(bodyMType){error("representation")})()
        }
        return buildJsonObject{put("result","7")}
    }
    if(name.startsWith("bodyK/")) {
        val operation=name.substringAfter('/')
        when(operation){
            "from-unused"->PandocGenericRep.from(BodyKDictionary){error("input")}
            "from-projection"->PandocGenericRep.from(BodyKDictionary){error("input")}.layer()
            "from-root"->PandocGenericRep.from(BodyKDictionary){error("input")}.whnf()
            "to-unused"->PandocGenericRep.to(BodyKDictionary,GenericRepValue(fieldType){error("representation")})
            "to-root"->PandocGenericRep.to(BodyKDictionary,GenericRepValue(fieldType){error("representation")})()
            "to-field"->PandocGenericRep.to(BodyKDictionary,GenericRepValue(fieldType){error("representation")})().field
            "to-head"->PandocGenericRep.to(BodyKDictionary,field{headFailure()})().field
        }
        return buildJsonObject{put("result","7")}
    }
    when(name){
        "ignored/from-root"->IgnoreDictionary.from{error("input")}.whnf()
        "ignored/to-root"->IgnoreDictionary.to(GenericRepValue(GenericRepType.Unit){error("representation")})()
        "strict/from-root"->StrictDictionary.from{error("input")}.whnf()
        "strict/from-field"->StrictDictionary.from{CustomStrict{error("field")}}.whnf()
        "strict/from-head"->StrictDictionary.from{CustomStrict{headFailure()}}.whnf()
        "strict/to-root"->StrictDictionary.to(GenericRepValue(fieldType){error("representation")})()
        "strict/to-field"->StrictDictionary.to(field{error("field")})()
        "strict/to-head"->StrictDictionary.to(field{headFailure()})()
        "strict/to-unused"->StrictDictionary.to(field{error("field")})
        "pair/from-root"->PairDictionary.from{error("input")}.whnf()
        "pair/from-fields"->PairDictionary.from{CustomLazyPair({error("first")},{error("second")})}.whnf()
        "pair/to-root"->PairDictionary.to(GenericRepValue(PairDictionary.representation){error("representation")})()
        "pair/to-fields","pair/to-first","pair/to-second","pair/to-first-head"->{
            val rep=GenericRepValue(PairDictionary.representation){GenericRepLayer.Product(field{if(name=="pair/to-first-head")headFailure()else error("first")},field{error("second")})}
            val pair=PairDictionary.to(rep)()
            if(name.startsWith("sharing/")) {
        var calls=0;var roots=0
        val operation=name.substringAfter('/')
        val observation=when(operation){
            "fromM"->{
                val dictionary=object:GenericRepDictionary<Unit>{
                    override val representation=bodyMType
                    override fun from(value:GenericRepThunk<Unit>):GenericRepValue{calls++;return BodyMDictionary.from(value)}
                    override fun to(value:GenericRepValue)=BodyMDictionary.to(value)
                }
                val rep=PandocGenericRep.from(dictionary){error("input")};rep.layer();val before=calls
                repeat(2){try{rep.whnf()}catch(_:IllegalStateException){}}
                "$before:$calls"
            }
            "fromK"->{
                val dictionary=object:GenericRepDictionary<BodyK>{
                    override val representation=fieldType
                    override fun from(value:GenericRepThunk<BodyK>):GenericRepValue{calls++;return BodyKDictionary.from(value)}
                    override fun to(value:GenericRepValue)=BodyKDictionary.to(value)
                }
                val rep=PandocGenericRep.from(dictionary){error("input")};rep.layer();val before=calls
                repeat(2){try{rep.whnf()}catch(_:IllegalStateException){}}
                "$before:$calls"
            }
            "toM"->{
                val dictionary=object:GenericRepDictionary<Unit>{
                    override val representation=bodyMType
                    override fun from(value:GenericRepThunk<Unit>)=BodyMDictionary.from(value)
                    override fun to(value:GenericRepValue):GenericRepThunk<Unit>{calls++;return BodyMDictionary.to(value)}
                }
                val output=PandocGenericRep.to(dictionary,GenericRepValue(bodyMType){error("representation")});val before=calls
                repeat(2){try{output()}catch(_:IllegalStateException){}}
                "$before:$calls"
            }
            "toK"->{
                val dictionary=object:GenericRepDictionary<BodyK>{
                    override val representation=fieldType
                    override fun from(value:GenericRepThunk<BodyK>)=BodyKDictionary.from(value)
                    override fun to(value:GenericRepValue):GenericRepThunk<BodyK>{calls++;return BodyKDictionary.to(value)}
                }
                val output=PandocGenericRep.to(dictionary,GenericRepValue(fieldType){roots++;error("representation")});val before=calls
                val box=output();output();val unforced=roots
                repeat(2){try{box.field}catch(_:IllegalStateException){}}
                "$before:$calls:$unforced:$roots"
            }
            else->error("sharing")
        }
        return buildJsonObject{put("result",observation)}
    }
    if(name.startsWith("bodyM/")) {
        val operation=name.substringAfter('/')
        when(operation){
            "from-unused"->PandocGenericRep.from(BodyMDictionary){error("input")}
            "from-projection"->PandocGenericRep.from(BodyMDictionary){error("input")}.layer()
            "from-root"->PandocGenericRep.from(BodyMDictionary){error("input")}.whnf()
            "to-unused"->PandocGenericRep.to(BodyMDictionary,GenericRepValue(bodyMType){error("representation")})
            "to-root"->PandocGenericRep.to(BodyMDictionary,GenericRepValue(bodyMType){error("representation")})()
        }
        return buildJsonObject{put("result","7")}
    }
    if(name.startsWith("bodyK/")) {
        val operation=name.substringAfter('/')
        when(operation){
            "from-unused"->PandocGenericRep.from(BodyKDictionary){error("input")}
            "from-projection"->PandocGenericRep.from(BodyKDictionary){error("input")}.layer()
            "from-root"->PandocGenericRep.from(BodyKDictionary){error("input")}.whnf()
            "to-unused"->PandocGenericRep.to(BodyKDictionary,GenericRepValue(fieldType){error("representation")})
            "to-root"->PandocGenericRep.to(BodyKDictionary,GenericRepValue(fieldType){error("representation")})()
            "to-field"->PandocGenericRep.to(BodyKDictionary,GenericRepValue(fieldType){error("representation")})().field
            "to-head"->PandocGenericRep.to(BodyKDictionary,field{headFailure()})().field
        }
        return buildJsonObject{put("result","7")}
    }
    when(name){"pair/to-first","pair/to-first-head"->pair.first;"pair/to-second"->pair.second;else->Unit}
        }
        "deep/from-inline","deep/to-from-inline"->{
            var child:Any?=DeepSeqData.node("Inline","Space",emptyList()).value()
            repeat(50000){val previous=child;child=DeepSeqNode("Inline","Emph",listOf(DeepSeqValue(DeepSeqData.list(DeepSeqData.named("Inline"))){GenericList.cons({previous},{GenericList.empty()})}))}
            val outer=child as DeepSeqNode
            val root=GenericRepData.source("Inline",outer.constructor,outer.fields.map{f->GenericRepField(GenericDatas.list(GenericDefinitionData.named("Inline")).type){f.value()}})
            val dict=GenericRepData.definition("Inline");val rep=dict.from{root}
            if(name.startsWith("deep/from"))rep.whnf()else dict.to(rep)()
        }
        "deep/from-many","deep/to-from-many"->{
            val dict=GenericRepData.many(GenericDatas.int.type)
            val values=object:AbstractList<Long>(){override val size=50000;override fun get(index:Int):Long=error("element")}
            val root=GenericRepData.manySource(GenericDatas.int.type,GenericRepData.manySequence(GenericDatas.int.type){Many.fromList(values)})
            val rep=dict.from{root};if(name.startsWith("deep/from"))rep.whnf()else dict.to(rep)()
        }
        else->if(name.startsWith("many/")) {
            val mode=name.split('/')[1];val operation=name.split('/')[2]
            val dictionary=GenericRepData.many(GenericDatas.int.type)
            val many=Many.fromTree<Long>{when(mode){
                "tree"->error("tree")
                "single-cell"->ManyTree.Single({error("cell")},{error("measure")})
                else->ManyTree.Deep(
                    {if(mode in listOf("deep-prefix","deep-both"))error("prefix")else GenericList.cons({error("cell")},{GenericList.empty<ManyCell<Long>>()})},
                    {if(mode=="deep-middle")error("middle")else ManyTree.Empty()},
                    {if(mode in listOf("deep-suffix","deep-both"))error("suffix")else GenericList.cons({error("cell")},{GenericList.empty<ManyCell<Long>>()})},2L)
            }}
            val root=GenericRepData.manySource(GenericDatas.int.type,GenericRepData.manySequence(GenericDatas.int.type){many})
            val rep=dictionary.from{root};if(operation=="from")rep.whnf()else dictionary.to(rep)()
        }else error("custom case $name")
    }
    buildJsonObject{put("result","7")}
} catch(e:Exception){buildJsonObject{put("exception",e.message?.lineSequence()?.firstOrNull()?:e.javaClass.name)}}
}
fun main(){
    val lines=File("tools/haskell-types-generic-rep-reference/custom-native.jsonl").readLines().chunked(2)
    for((wire,want)in lines){val name=Json.parseToJsonElement(wire).jsonPrimitive.content;val actual=genericRepCustomExecute(name);check(actual==Json.parseToJsonElement(want)){"$name expected$want actual$actual"}}
    println("${lines.size} original custom Generic/deep assertions PASS")
}
