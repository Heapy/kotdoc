package io.heapy.kotdoc.crypto
import io.heapy.kotdoc.ast.*
import kotlinx.serialization.json.*
object RsaTypesLexProof {
    fun observe(q:JsonObject):JsonElement=try {
        fun text(k:String,d:String="")=q[k]?.jsonPrimitive?.content?:d
        val projection=text("projection","alternative")
        if(projection=="ignore")JsonPrimitive(42)else {
            val end=text("end","eof");val points=text("input").codePoints().toArray()
            fun chars(i:Int):GenericList<Int> = GenericList.defer {
                if(i<points.size)GenericList.cons({points[i]}){chars(i+1)}else when(end){
                    "tail"->error("inputTail")
                    "head"->GenericList.cons({error("inputHead")}){GenericList.empty()}
                    else->GenericList.empty()
                }
            }
            val input=ManyReadInput{if(end=="root")error("inputRoot")else chars(0)}
            val method=text("method","read");val prec=q["precedence"]?.jsonPrimitive?.long?:0
            fun <B> select(rows:GenericList<ManyReadAlternative<B>>,render:(B)->JsonElement):JsonElement {
                val step=rows.step()
                if(projection=="alternative")return JsonPrimitive(step is GenericList.Step.Cons)
                if(step !is GenericList.Step.Cons)return JsonPrimitive("EMPTY")
                val row=step.head()
                if(projection=="value")return render(row.value())
                var rest=row.remainder().sourceCharacters();val output=StringBuilder()
                repeat(q["prefixLength"]?.jsonPrimitive?.int?:3){when(val node=rest.step()){
                    GenericList.Step.Nil->return JsonPrimitive(output.toString())
                    is GenericList.Step.Cons->{output.appendCodePoint(node.head());rest=GenericList.defer{node.tail()}}
                }}
                return JsonPrimitive(output.toString())
            }
            fun <A> read(d:ManyElementRead<A>,render:(A)->String):JsonElement = if(method=="read"||method=="readPrec")
                select(if(method=="read")d.readsPrec({prec},input)else d.readPrec().results(prec,input)){JsonPrimitive(render(it))}
            else select(if(method=="readList")d.readList(input)else d.readListPrec().results(prec,input)){JsonArray(it.map{v->JsonPrimitive(render(v))})}
            when(text("type","PublicKey")){
                "PublicKey"->read(CryptoRsaTypesRead.publicKey){it.toString()}
                "PrivateKey"->read(CryptoRsaTypesRead.privateKey){it.debugShow()}
                else->read(CryptoRsaTypesRead.keyPair){it.debugShow()}
            }
        }
    }catch(e:Throwable){buildJsonObject{put("exception",e.message?:e.javaClass.simpleName)}}
}
