package io.heapy.kotdoc.crypto
import io.heapy.kotdoc.ast.*
import kotlinx.serialization.json.*
import java.math.BigInteger
import java.io.File
object RsaTypesDataReplay {
 private class Tag
 private class Box<A>(val get:DataThunk<Any?>):DataKind<Tag,A>{override fun forceGenericRepRoot(){get()}}
 private fun box(v:()->Any?)=Box<Any?>(DataThunk(v))
 private fun obj(vararg p:Pair<String,JsonElement>)=JsonObject(mapOf(*p))
 private fun str(v:Any?)=JsonPrimitive(v.toString())
 @Suppress("UNCHECKED_CAST") private fun dictionary(type:GenericType):DataDictionary<Any?> = when(type.name){"Int"->DataDictionaries.int();"Integer"->CryptoRsaTypesData.integer;"PublicKey"->CryptoRsaTypesData.publicKey;"PrivateKey"->CryptoRsaTypesData.privateKey;"KeyPair"->CryptoRsaTypesData.keyPair;else->error("dictionary$type")}as DataDictionary<Any?>
 private fun <A> shape(d:DataDictionary<A>,v:DataThunk<A>):JsonElement {
  val c=d.toConstr(v)
  return when(val r=c.representation){is DataConstrRep.Integral->obj("integer" to str(r.value));else->obj("constructor" to str(c.name),"fields" to JsonArray(d.gmapQ(object:DataQuery<JsonElement>{override fun <B>apply(evidence:DataEvidence<B>,value:DataThunk<B>)=shape(dictionary(evidence.representation.type),DataThunk{value()})},v).toList()))}
 }
 @Suppress("UNCHECKED_CAST") private fun <A> observe(d:DataDictionary<A>,v:()->A,q:JsonObject):JsonElement {
  val bad=q["bad"]?.jsonArray?.map{it.jsonPrimitive.content}?:emptyList()
  fun <B>b(name:String,f:()->B):B=if(name in bad)throw IllegalStateException(name)else f()
  val value=DataThunk(v);val op=q["op"]!!.jsonPrimitive.content
  val query=object:DataQuery<String>{override fun <B>apply(evidence:DataEvidence<B>,value:DataThunk<B>)=b("query"){evidence.representation.type.name}}
  val identity=object:DataTransform{override fun <B>apply(evidence:DataEvidence<B>,value:DataThunk<B>):B=b("transform"){value()}}
  val con=DataThunk{b("constr"){if(q["constructor"]?.jsonPrimitive?.content=="integer")CryptoRsaTypesData.integer.toConstr(DataThunk{b("constructorValue"){q["integer"]!!.jsonPrimitive.content.toBigInteger()}})else {lateinit var t:DataTypeInfo;val c=DataConstr({DataConstrRep.Algebraic(2)},{"True"},{GenericList.empty()},{DataFixity.Prefix},{t});t=DataTypeInfo({"Bool"},{DataTypeRep.Algebraic(DataThunk{GenericList.view(listOf(c))})});c}}}
  fun list(xs:List<String>)=JsonArray(xs.map(::str))
  return when(op){
   "shape"->shape(d,value)
   "gfoldl","gfoldlIgnore"->{val f=object:DataFold<Tag>{override fun <B>inject(value:DataThunk<B>):DataKind<Tag,B> = box{if(op=="gfoldlIgnore")b("z"){"z"}else emptyList<String>()}as DataKind<Tag,B>
    override fun <B,C>apply(evidence:DataEvidence<B>,function:DataThunk<DataKind<Tag,DataFunction<B,C>>>,value:DataThunk<B>):DataKind<Tag,C> = box{if(op=="gfoldlIgnore")"k"else ((function()as Box<*>).get()as List<String>)+query.apply(evidence,value)}as DataKind<Tag,C>}
    val result=(d.gfoldl(f,value)as Box<*>).get();if(result is String)str(result)else list(result as List<String>)}
   "gunfold","gunfoldIgnore"->{val f=object:DataUnfold<Tag>{override fun <B>inject(value:DataThunk<B>):DataKind<Tag,B> = box{if(op=="gunfoldIgnore")b("z"){"z"}else 0}as DataKind<Tag,B>
    override fun <B,C>apply(evidence:DataEvidence<B>,function:DataThunk<DataKind<Tag,DataFunction<B,C>>>):DataKind<Tag,C> = box{if(op=="gunfoldIgnore")"k"else ((function()as Box<*>).get()as Int)+1}as DataKind<Tag,C>}
    val r=(d.gunfold(f,con)as Box<*>).get();if(r is String)str(r)else JsonPrimitive(r as Int)}
   "gmapT"->shape(d,DataThunk{d.gmapT(identity,value)})
   "gmapTRoot"->{d.gmapT(object:DataTransform{override fun <B>apply(evidence:DataEvidence<B>,value:DataThunk<B>):B=error("transform")},value);JsonPrimitive(42)}
   "gmapQl"->list(d.gmapQl({x,y->x()+y()},DataThunk{emptyList<String>()},object:DataQuery<List<String>>{override fun <B>apply(evidence:DataEvidence<B>,value:DataThunk<B>)=listOf(query.apply(evidence,value))},value))
   "gmapQr"->list(d.gmapQr({x,y->x()+y()},DataThunk{emptyList<String>()},object:DataQuery<List<String>>{override fun <B>apply(evidence:DataEvidence<B>,value:DataThunk<B>)=listOf(query.apply(evidence,value))},value))
   "gmapQ"->list(d.gmapQ(query,value).toList())
   "gmapQi"->str(d.gmapQi(DataThunk{q["index"]?.jsonPrimitive?.content?.toLong()?:0L},query,value))
   "gmapM"->{val f=object:DataMonadicTransform<DataIdentity.Tag>{override fun <B>apply(evidence:DataEvidence<B>,value:DataThunk<B>)=DataIdentity.pure(DataThunk{b("transform"){value()}})};shape(d,DataThunk{DataIdentity.run(d.gmapM(DataIdentity,f,value))})}
   "gmapMp","gmapMo","gmapMpFail","gmapMoFail"->{val f=object:DataMonadicTransform<DataProofMaybe.Tag>{override fun <B>apply(evidence:DataEvidence<B>,value:DataThunk<B>):DataKind<DataProofMaybe.Tag,B> = if(op.endsWith("Fail"))DataProofMaybe.zero()else DataProofMaybe.pure(DataThunk{b("transform"){value()}})}
    val r=DataProofMaybe.run(if(op.startsWith("gmapMp"))d.gmapMp(DataProofMaybe,f,value)else d.gmapMo(DataProofMaybe,f,value));if(op.endsWith("Fail"))JsonPrimitive(r is GenericMaybe.None)else when(r){GenericMaybe.None->JsonNull;is GenericMaybe.Some->shape(d,DataThunk{r.value})}}
   "dataCast1"->JsonPrimitive(d.dataCast1(DataThunk{error("type")},object:DataCast1<Tag>{override fun <B>value(argument:DataEvidence<B>):DataKind<Tag,Any?> = error("cast")})is GenericMaybe.None)
   "dataCast2"->JsonPrimitive(d.dataCast2(DataThunk{error("type")},object:DataCast2<Tag>{override fun <B,C>value(left:DataEvidence<B>,right:DataEvidence<C>):DataKind<Tag,Any?> = error("cast")})is GenericMaybe.None)
   else->error(op)
  }
 }
 fun observe(q:JsonObject):JsonElement=try {
  val bad=q["bad"]?.jsonArray?.map{it.jsonPrimitive.content}?:emptyList();fun <A>b(k:String,v:()->A):A=if(k in bad)error(k)else v()
  val pub={b("root"){CryptoRsaTypes.PublicKey.deferred({b("size"){1L}},{b("n"){2.toBigInteger()}},{b("e"){3.toBigInteger()}})}}
  val priv={b("root"){CryptoRsaTypes.PrivateKey.deferred({b("pub"){CryptoRsaTypes.PublicKey(1,2.toBigInteger(),3.toBigInteger())}},{b("d"){4.toBigInteger()}},{b("p"){5.toBigInteger()}},{b("q"){6.toBigInteger()}},{b("dP"){7.toBigInteger()}},{b("dQ"){8.toBigInteger()}},{b("qinv"){9.toBigInteger()}})}}
  when(q["type"]!!.jsonPrimitive.content){"PublicKey"->observe(CryptoRsaTypesData.publicKey,pub,q);"PrivateKey"->observe(CryptoRsaTypesData.privateKey,priv,q);"KeyPair"->observe(CryptoRsaTypesData.keyPair,{CryptoRsaTypes.KeyPair.deferred(priv)},q);else->observe(CryptoRsaTypesData.integer,{b("root"){(q["integer"]?.jsonPrimitive?.content?:"23").toBigInteger()}},q)}
 }catch(e:Exception){obj("exception" to str(e.message))}
 @JvmStatic fun main(args:Array<String>){val rows=Json.parseToJsonElement(File(args[0]).readText()).jsonArray;for((i,r)in rows.withIndex()){val row=r.jsonObject;val actual=observe(row.getValue("request").jsonObject);check(actual==row["expected"]){"row$i ${row["request"]}\nexpected=${row["expected"]}\nactual=$actual"}};println("PASS ${rows.size} original RSA.Types Data method observations")}
}
