package io.heapy.kotdoc.ast
import kotlinx.serialization.json.*
import java.io.File
import java.math.BigInteger
private val primitiveIdentity=object:DataUnfold<DataIdentity.Tag> {
 override fun <A> inject(value:DataThunk<A>)=DataIdentity.pure(value)
 override fun <D,B> apply(evidence:DataEvidence<D>,function:DataThunk<DataKind<DataIdentity.Tag,DataFunction<D,B>>>):DataKind<DataIdentity.Tag,B> = error("primitive field")
}
fun main(){
 val requests=DataProofFiles.lines("numeric-requests.jsonl").map{Json.parseToJsonElement(it).jsonObject}
 val expected=DataProofFiles.lines("numeric-native.jsonl").map(Json::parseToJsonElement)
 val integer=DataDictionaries.int();val double=DataDictionaries.double();val character=DataDictionaries.char()
 for((index,request)in requests.withIndex()) {
  fun text(name:String)=request.getValue(name).jsonPrimitive.content
  fun constructor(rep:DataConstrRep,type:DataTypeInfo)=DataConstr({rep},{"numeric"},{error("fields")},{error("fixity")},{type})
  val actual=when(text("op")) {
   "int"->{val c=constructor(DataConstrRep.Integral(BigInteger(text("a"))),integer.dataTypeOf(DataThunk{0L}));JsonPrimitive(DataIdentity.run(PandocDataClass.gunfold(integer,primitiveIdentity){c}()).toString())}
   "rational"->{val c=constructor(DataConstrRep.Rational(BigInteger(text("a")),BigInteger(text("b"))),double.dataTypeOf(DataThunk{0.0}));val value=DataIdentity.run(PandocDataClass.gunfold(double,primitiveIdentity){c}());JsonPrimitive(java.lang.Long.toUnsignedString(value.toRawBits()))}
   "double-ratio"->{val value=Double.fromBits(java.lang.Long.parseUnsignedLong(text("a")));val rep=PandocDataClass.toConstr(double){value}().representation as DataConstrRep.Rational;buildJsonArray{add(rep.numerator.toString());add(rep.denominator.toString())}}
   "char"->{val value=request.getValue("cp").jsonPrimitive.int;val c=PandocDataClass.toConstr(character){value}();JsonPrimitive(DataIdentity.run(PandocDataClass.gunfold(character,primitiveIdentity){c}()))}
   else->error("operation")
  }
  check(actual==expected[index]){"numeric $index $request: $actual != ${expected[index]}"}
 }
 println("${requests.size} independent primitive numeric/IEEE observations PASS")
}
