package io.heapy.kotdoc.crypto
import io.heapy.kotdoc.ast.*
import kotlinx.serialization.json.*
import java.math.BigInteger
import java.io.File
object RsaTypesReplay {
    private fun Any?.json():JsonElement=when(this){null->JsonNull;is String->JsonPrimitive(this);is Boolean->JsonPrimitive(this);is Number->JsonPrimitive(this);is List<*>->JsonArray(map{it.json()});else->error("json $this")}
    private fun obj(vararg p:Pair<String,JsonElement>)=JsonObject(mapOf(*p))
    private fun desc(v:GenericRepValue):JsonElement=when(val t=v.type){
        is GenericRepType.Metadata->{val child=(v.layer()as GenericRepLayer.Metadata).child;when(val m=t.information){
            is GenericRepMetadata.Datatype->obj("datatype" to m.name.json(),"module" to m.module.json(),"package" to m.packageName.json(),"newtype" to m.newtype.json(),"child" to desc(child))
            is GenericRepMetadata.Constructor->obj("constructor" to m.name.json(),"record" to m.record.json(),"fixity" to "Prefix".json(),"child" to desc(child))
            is GenericRepMetadata.Selector->obj("selector" to m.name.json(),"unpackedness" to m.unpackedness.json(),"strictness" to m.strictness.json(),"decided" to m.decidedStrictness.json(),"child" to desc(child))}}
        is GenericRepType.Product->{val p=v.layer()as GenericRepLayer.Product;obj("product" to JsonArray(listOf(desc(p.left),desc(p.right))))}
        is GenericRepType.Field->obj("fieldType" to t.type.name.json())
        else->error("RSA Rep")}
    private fun edit(v:GenericRepValue,errors:List<String>,path:String=""):GenericRepValue=GenericRepValue(v.type){when(val t=v.type){
        is GenericRepType.Metadata->GenericRepLayer.Metadata(edit((v.layer()as GenericRepLayer.Metadata).child,errors,path))
        is GenericRepType.Product->{if(path in errors)error("rep:$path");val p=v.layer()as GenericRepLayer.Product;GenericRepLayer.Product(edit(p.left,errors,path+"L"),edit(p.right,errors,path+"R"))}
        is GenericRepType.Field->{if("field:$path" in errors)error("rep:field:$path");v.layer()}
        else->error("RSA Rep")}}
    private fun <A> metadata(d:DataDictionary<A>,v:()->A):JsonElement {
        val value=DataThunk(v);val c=d.toConstr(value)
        val children=d.gmapQ(object:DataQuery<String>{override fun <B>apply(evidence:DataEvidence<B>,value:DataThunk<B>)=evidence.representation.type.name},value).toList()
        return obj("datatype" to d.dataTypeOf(value).name.json(),"constructor" to c.name.json(),"fields" to c.fields.toList().json(),"index" to c.index.json(),"children" to children.json())
    }
    private fun <A> read(d:ManyElementRead<A>,prec:Long,s:String,op:String,render:(A)->String):JsonElement {
        val input=ManyReadInput(s)
        fun suffix(r:ManyReadInput)=r.characters().points().let{String(it,0,it.size)}.json()
        return if(op=="read"||op=="readPrec")JsonArray((if(op=="read")d.readsPrec({prec},input)else d.readPrec().results(prec,input)).map{r->JsonArray(listOf(render(r.value()).json(),suffix(r.remainder())))})
        else JsonArray((if(op=="readList")d.readList(input)else d.readListPrec().results(prec,input)).map{r->JsonArray(listOf(JsonArray(r.value().map{render(it).json()}),suffix(r.remainder())))})
    }
    private fun prefix(chars:GenericList<Int>,count:Int):String {var current=chars;val out=StringBuilder();repeat(maxOf(0,count)){when(val step=current.step()){GenericList.Step.Nil->return out.toString();is GenericList.Step.Cons->{out.appendCodePoint(step.head());current=GenericList.defer{step.tail()}}}};return out.toString()}
    fun observe(q:JsonObject):JsonElement=try {
        fun text(k:String,d:String="")=q[k]?.jsonPrimitive?.content?:d
        val bad=q["bad"]?.jsonArray?.map{it.jsonPrimitive.content}?:emptyList()
        fun <A>b(k:String,v:()->A):A=if(k in bad)throw IllegalStateException(text("errorPrefix")+k)else v()
        fun i(k:String,d:Long)=b(k){text(k,d.toString()).toBigInteger()}
        val pub=cryptoRandomMemo{b("publicRoot"){CryptoRsaTypes.PublicKey.deferred({b("size"){text("size","1").toLong()}},{i("n",-2)},{i("e",3)})}}
        val priv=cryptoRandomMemo{b("privateRoot"){CryptoRsaTypes.PrivateKey.deferred({b("pub"){pub.value}},{i("d",4)},{i("p",5)},{i("q",6)},{i("dP",7)},{i("dQ",8)},{i("qinv",9)})}}
        val pair=cryptoRandomMemo{b("pairRoot"){CryptoRsaTypes.KeyPair.deferred{priv.value}}}
        val errorValue=cryptoRandomMemo{b("errorRoot"){CryptoRsaOaep.Error.entries[text("error","0").toInt()]}}
        val type=text("type","PublicKey");val op=text("op","show");val prec=text("precedence","0").toLong()
        when(op){
            "readProjected"->{
                val points=text("input").codePoints().toArray()
                fun inputChars(i:Int):GenericList<Int> = GenericList.defer{if(i==points.size)b("inputTail"){GenericList.empty()}else GenericList.cons({points[i]}){inputChars(i+1)}}
                val input=ManyReadInput{b("inputRoot"){inputChars(0)}}
                fun <A> projected(d:ManyElementRead<A>,render:(A)->String):JsonElement {
                    val values=cryptoRandomMemo{d.readsPrec({b("precedence"){prec}},input)}
                    fun first():ManyReadAlternative<A> = when(val node=values.value.step()){GenericList.Step.Nil->error("Prelude.head: empty list");is GenericList.Step.Cons->node.head()}
                    return when(text("projection","alternative")){
                        "ignore"->42.json()
                        "alternative"->(values.value.step()is GenericList.Step.Cons).json()
                        "value"->render(first().value()).json()
                        else->{val n=text("prefixLength","0").toInt();if(n<=0)"".json()else prefix(first().remainder().sourceCharacters(),n).json()}
                    }
                }
                when(type){"PublicKey"->projected(CryptoRsaTypesRead.publicKey){it.toString()};"PrivateKey"->projected(CryptoRsaTypesRead.privateKey){it.debugShow()};else->projected(CryptoRsaTypesRead.keyPair){it.debugShow()}}
            }
            "eq","ne"->{
                val r=q["right"]?.jsonObject?:JsonObject(emptyMap())
                fun rt(k:String,d:String="")=r[k]?.jsonPrimitive?.content?:d
                val rb=r["bad"]?.jsonArray?.map{it.jsonPrimitive.content}?:emptyList()
                fun <A> rbad(k:String,v:()->A):A=if(k in rb)throw IllegalStateException(rt("errorPrefix")+k)else v()
                fun ri(k:String,d:Long)=rbad(k){rt(k,d.toString()).toBigInteger()}
                val rp=cryptoRandomMemo{rbad("publicRoot"){CryptoRsaTypes.PublicKey.deferred({rbad("size"){rt("size","1").toLong()}},{ri("n",-2)},{ri("e",3)})}}
                val rv=cryptoRandomMemo{rbad("privateRoot"){CryptoRsaTypes.PrivateKey.deferred({rbad("pub"){rp.value}},{ri("d",4)},{ri("p",5)},{ri("q",6)},{ri("dP",7)},{ri("dQ",8)},{ri("qinv",9)})}}
                val rk={rbad("pairRoot"){CryptoRsaTypes.KeyPair.deferred{rv.value}}}
                val equal=when(type){"PublicKey"->CryptoRsaTypesEq.publicKey.equal({pub.value},{rp.value});"PrivateKey"->CryptoRsaTypesEq.privateKey.equal({priv.value},{rv.value});"Blinder"->CryptoRsaTypesEq.blinder.equal({CryptoRsa.Blinder(i("r",2),i("inverse",3))},{CryptoRsa.Blinder(ri("r",2),ri("inverse",3))});"Error"->CryptoRsaTypesEq.error.equal({errorValue.value},{rbad("errorRoot"){CryptoRsaOaep.Error.entries[rt("error","0").toInt()]}});else->CryptoRsaTypesEq.keyPair.equal({pair.value},rk)}
                (if(op=="eq")equal else !equal).json()
            }
            "showPrefix","showListPrefix","debugPrefix"->{
                val suffix={b("suffix"){ManyShowRuntime.text("TAIL"){GenericList.empty()}}}
                fun <A> chars(d:ManyElementShow<A>,v:()->A):GenericList<Int> = if(op=="showListPrefix")d.showList({GenericList.cons(v){b("listTail"){GenericList.empty()}}},suffix)else d.showsPrec(prec,v,suffix)
                val stream=if(op=="debugPrefix"){if(type=="KeyPair")CryptoRsaTypesShow.debugPair{pair.value}else CryptoRsaTypesShow.debugPrivate({priv.value})}else when(type){"PublicKey"->chars(CryptoRsaTypesShow.publicKey){pub.value};"PrivateKey"->chars(CryptoRsaTypesShow.privateKey){priv.value};"Blinder"->chars(CryptoRsaTypesShow.blinder){CryptoRsa.Blinder(i("r",2),i("inverse",3))};"Error"->chars(CryptoRsaTypesShow.error){errorValue.value};else->chars(CryptoRsaTypesShow.keyPair){pair.value}}
                prefix(stream,text("prefixLength","0").toInt()).json()
            }
            "show"->when(type){"PublicKey"->pub.value.showsPrec(prec);"PrivateKey"->priv.value.showsPrec(prec);"KeyPair"->pair.value.showsPrec(prec);"Blinder"->CryptoRsa.Blinder(i("r",2),i("inverse",3)).toString();else->CryptoRsaOaep.Error.entries[text("error","0").toInt()].toString()}.json()
            "debug"->(if(type=="KeyPair")pair.value.debugShow()else priv.value.debugShow()).json()
            "root"->{when(type){"PublicKey"->pub.value;"PrivateKey"->priv.value;"KeyPair"->pair.value.forceGenericRepRoot();else->CryptoRsa.Blinder(i("r",2),i("inverse",3))};42.json()}
            "nfdata"->{when(type){"PublicKey"->CryptoRsaTypes.publicNFData.rnf{pub.value};"PrivateKey"->CryptoRsaTypes.privateNFData.rnf{priv.value};else->CryptoRsaTypes.pairNFData.rnf{pair.value}};42.json()}
            "selector"->when(text("selector","n")){"size"->CryptoRsaTypes.privateSize(priv.value).toBigInteger();"e"->CryptoRsaTypes.privateE(priv.value);"d"->pair.value.toPrivateKey().d;"pairN"->CryptoRsaTypes.toPublicKey(pair.value).n;"pairSize"->CryptoRsaTypes.toPublicKey(pair.value).size.toBigInteger();"pairE"->CryptoRsaTypes.toPublicKey(pair.value).e;else->CryptoRsaTypes.privateN(priv.value)}.toString().let{Json.parseToJsonElement(it)}
            "typeInfo"->{val t=when(type){"PublicKey"->CryptoRsaTypesData.publicKey.representation.type;"PrivateKey"->CryptoRsaTypesData.privateKey.representation.type;"KeyPair"->CryptoRsaTypesData.keyPair.representation.type;"Integer"->CryptoRsaTypesData.integer.representation.type;else->DataDictionaries.int().representation.type};obj("module" to t.module.json(),"name" to t.name.json(),"package" to t.library.json())}
            "metadata"->when(type){"PublicKey"->metadata(CryptoRsaTypesData.publicKey){pub.value};"PrivateKey"->metadata(CryptoRsaTypesData.privateKey){priv.value};else->metadata(CryptoRsaTypesData.keyPair){pair.value}}
            "datatype"->when(type){"PublicKey"->CryptoRsaTypesData.publicKey.dataTypeOf(DataThunk{pub.value});"PrivateKey"->CryptoRsaTypesData.privateKey.dataTypeOf(DataThunk{priv.value});else->CryptoRsaTypesData.keyPair.dataTypeOf(DataThunk{pair.value})}.name.json()
            "constructor"->when(type){"PublicKey"->CryptoRsaTypesData.publicKey.toConstr(DataThunk{pub.value});"PrivateKey"->CryptoRsaTypesData.privateKey.toConstr(DataThunk{priv.value});else->CryptoRsaTypesData.keyPair.toConstr(DataThunk{pair.value})}.name.json()
            "genericToRoot","genericToShow","genericToDebug"->{
                val errors=q["repErrors"]!!.jsonArray.map{it.jsonPrimitive.content}
                if(type=="PublicKey"){val v=CryptoRsaTypesGeneric.publicKey.to(edit(CryptoRsaTypesGeneric.publicKey.from{pub.value},errors))();if(op=="genericToRoot")42.json()else v.toString().json()}
                else {val v=CryptoRsaTypesGeneric.privateKey.to(edit(CryptoRsaTypesGeneric.privateKey.from{priv.value},errors))();when(op){"genericToRoot"->42.json();"genericToDebug"->v.debugShow().json();else->v.toString().json()}}
            }
            "generic"->if(type=="PublicKey")desc(CryptoRsaTypesGeneric.publicKey.from{pub.value})else desc(CryptoRsaTypesGeneric.privateKey.from{priv.value})
            "read","readPrec","readList","readListPrec"->when(type){"PublicKey"->read(CryptoRsaTypesRead.publicKey,prec,text("input"),op){it.toString()};"PrivateKey"->read(CryptoRsaTypesRead.privateKey,prec,text("input"),op){it.debugShow()};else->read(CryptoRsaTypesRead.keyPair,prec,text("input"),op){it.debugShow()}}
            else->error("op $op")
        }
    }catch(e:Exception){obj("exception" to (e.message?:e.javaClass.simpleName).json())}
    @JvmStatic fun main(args:Array<String>){val rows=Json.parseToJsonElement(File(args[0]).readText()).jsonArray;for((i,r)in rows.withIndex()){val row=r.jsonObject;val actual=observe(row.getValue("request").jsonObject);check(actual==row["expected"]){"row$i ${row["request"]}\nexpected=${row["expected"]}\nactual=$actual"}};println("PASS ${rows.size} original RSA.Types method observations")}
}
