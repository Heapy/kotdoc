package io.heapy.kotdoc.math

import io.heapy.kotdoc.formats.HaskellDouble
import io.heapy.kotdoc.layout.CharacterString
import io.heapy.kotdoc.layout.CharacterStringChars
import io.heapy.kotdoc.layout.TextChars
import io.heapy.kotdoc.logging.assertLogJsonEquals
import io.heapy.kotdoc.xml.*
import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.math.BigInteger
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import kotlin.test.*

class MathMLTest {
    private fun str(v: JsonElement)=if(v is JsonArray) TextChars.fromPoints(v.map {it.jsonPrimitive.int}.toIntArray()) else v.jsonPrimitive.content
    private fun chars(v: JsonElement)=CharacterString(v.jsonArray.map {it.jsonPrimitive.int}.toIntArray())
    private fun cps(s: CharacterString)=JsonArray(s.points().map(::JsonPrimitive))
    private fun ratio(v: JsonElement)=v.jsonArray.let {MathRational(BigInteger(str(it[0])),BigInteger(str(it[1])))}
    private fun expression(v: JsonElement): MathExp {
        val xs=v.jsonArray
        fun e(i: Int)=expression(xs[i])
        fun s(i: Int)=str(xs[i])
        fun list(i: Int)=xs[i].jsonArray.map(::expression)
        return when(s(0)) {
            "ENumber" -> MathExp.ENumber(s(1))
            "EGrouped" -> MathExp.EGrouped(list(1))
            "EDelimited" -> MathExp.EDelimited(s(1),s(2),xs[3].jsonArray.map { it.jsonArray.let { d->if(str(d[0])=="Left") DelimitedPart.Middle(str(d[1])) else DelimitedPart.Expression(expression(d[1])) } })
            "EIdentifier" -> MathExp.EIdentifier(s(1))
            "EMathOperator" -> MathExp.EMathOperator(s(1))
            "ESymbol" -> MathExp.ESymbol(MathSymbolType.valueOf(s(1)),s(2))
            "ESpace" -> MathExp.ESpace(ratio(xs[1]))
            "ESub" -> MathExp.ESub(e(1),e(2))
            "ESuper" -> MathExp.ESuper(e(1),e(2))
            "ESubsup" -> MathExp.ESubsup(e(1),e(2),e(3))
            "EOver" -> MathExp.EOver(xs[1].jsonPrimitive.boolean,e(2),e(3))
            "EUnder" -> MathExp.EUnder(xs[1].jsonPrimitive.boolean,e(2),e(3))
            "EUnderover" -> MathExp.EUnderover(xs[1].jsonPrimitive.boolean,e(2),e(3),e(4))
            "EPhantom" -> MathExp.EPhantom(e(1))
            "EBoxed" -> MathExp.EBoxed(e(1))
            "ECancel" -> MathExp.ECancel(StrokeType.valueOf(s(1)),e(2))
            "EFraction" -> MathExp.EFraction(FractionType.valueOf(s(1)),e(2),e(3))
            "ERoot" -> MathExp.ERoot(e(1),e(2))
            "ESqrt" -> MathExp.ESqrt(e(1))
            "EScaled" -> MathExp.EScaled(ratio(xs[1]),e(2))
            "EArray" -> MathExp.EArray(xs[1].jsonArray.map {MathAlignment.valueOf(str(it))},xs[2].jsonArray.map {row->row.jsonArray.map {cell->cell.jsonArray.map(::expression)}})
            "EText" -> MathExp.EText(MathTextType.valueOf(s(1)),s(2))
            "EStyled" -> MathExp.EStyled(MathTextType.valueOf(s(1)),list(2))
            else->error("expression")
        }
    }

    private fun qname(v: JsonElement): LegacyXmlName=v.jsonArray.let {LegacyXmlName(chars(it[0]),if(it[1]==JsonNull) null else chars(it[1]),if(it[2]==JsonNull) null else chars(it[2]))}
    private fun attribute(v: JsonElement)=v.jsonArray.let {LegacyXmlAttribute(qname(it[0]),chars(it[1]))}
    private fun line(v: JsonElement)=if(v==JsonNull) null else BigInteger(str(v))
    private fun xml(v: JsonElement): LegacyXmlContent {
        val x=v.jsonArray
        return when(str(x[0])) {
            "element"->LegacyXmlElement(qname(x[1]),x[2].jsonArray.map(::attribute),x[3].jsonArray.map(::xml),line(x[4]))
            "text"->LegacyXmlText(chars(x[2]),LegacyXmlTextKind.valueOf(str(x[1])),line(x[3]))
            "ref"->LegacyXmlCRef(chars(x[1]))
            else->error("xml")
        }
    }
    private fun qv(n: LegacyXmlName)=buildJsonArray {add(cps(n.local));add(n.uri?.let(::cps) ?: JsonNull);add(n.prefix?.let(::cps) ?: JsonNull)}
    private fun av(a: LegacyXmlAttribute)=buildJsonArray {add(qv(a.name));add(cps(a.value))}
    private fun tree(c: LegacyXmlContent): JsonElement=when(c) {
        is LegacyXmlElement->buildJsonArray {add("element");add(qv(c.name));add(JsonArray(c.attributes.map(::av)));add(JsonArray(c.content.map(::tree)));add(c.line?.toString()?.let(::JsonPrimitive) ?: JsonNull)}
        is LegacyXmlText->buildJsonArray {add("text");add(c.kind.name);add(cps(c.text));add(c.line?.toString()?.let(::JsonPrimitive) ?: JsonNull)}
        is LegacyXmlCRef->buildJsonArray {add("ref");add(cps(c.reference))}
    }
    private fun operator(o: MathOperatorRecord?): JsonElement=if(o==null) JsonNull else buildJsonObject {
        put("oper",o.oper);put("description",o.description);put("form",o.form.name);put("priority",o.priority.toString());put("left",o.leftSpace.toString());put("right",o.rightSpace.toString());put("properties",JsonArray(o.properties.map(::JsonPrimitive)));put("show",MathShow.show(o))
    }
    private fun witness(s: CharacterString): JsonElement {
        val points=s.points();val hash=MessageDigest.getInstance("SHA-256")
        for(cp in points) {hash.update((cp ushr 24).toByte());hash.update((cp ushr 16).toByte());hash.update((cp ushr 8).toByte());hash.update(cp.toByte())}
        return buildJsonObject {put("length",points.size);put("sha",hash.digest().toHexString())}
    }
    private class PredicateFailure: Exception()
    private fun run(v: JsonArray): JsonElement=when(str(v[0])) {
        "mml"->{
            val e=MathMLWriter.writeMathML(DisplayType.valueOf(str(v[1])),v[2].jsonArray.map(::expression))
            buildJsonObject {
                put("tree",tree(e));put("compact",witness(LegacyXmlOutput.showElement(e)));put("pretty",witness(LegacyXmlOutput.prettyElement(e)))
                put("top",witness(LegacyXmlOutput.prettyTopElement(e)));put("topRaw",witness(LegacyXmlOutput.showTopElement(e)));put("long",witness(LegacyXmlOutput.printContent(e,LegacyXmlPrintConfig(shortEmptyTag={false}))))
            }
        }
        "dictionary-table"->JsonArray(MathMLOperators.operators.map(::operator))
        "dictionary"->JsonArray(v[1].jsonArray.map {it.jsonArray.let {q->operator(MathMLOperators.getMathMLOperator(str(q[0]),FormType.valueOf(str(q[1]))))}})
        "dictionary-range"->{val form=FormType.valueOf(str(v[1]));val first=v[2].jsonPrimitive.int;val count=v[3].jsonPrimitive.int
            JsonArray((first until first+count).mapNotNull {cp->MathMLOperators.getMathMLOperator(TextChars.fromPoints(intArrayOf(cp)),form)?.let {o->buildJsonArray {add(cp);add(operator(o))}}})
        }
        "numbers"->JsonArray(v[1].jsonArray.map {x->val r=ratio(x);val d=MathNumbers.toDouble(r)
            buildJsonArray {add(d.toRawBits().toULong().toString(16).padStart(16,'0'));add(HaskellDouble.showFixed(d,3));add(MathNumbers.roundToInt64(r*MathRational(100)).toString())}
        })
        "xml"->{val c=xml(v[1]);buildJsonObject {
            put("tree",tree(c));put("compact",cps(LegacyXmlOutput.showContent(c)));put("pretty",cps(LegacyXmlOutput.prettyContent(c)));put("long",cps(LegacyXmlOutput.printContent(c,LegacyXmlPrintConfig(shortEmptyTag={false}))))
            if(c is LegacyXmlElement) {put("topRaw",cps(LegacyXmlOutput.showTopElement(c)));put("topPretty",cps(LegacyXmlOutput.prettyTopElement(c)))}
        }}
        "config"->{val c=xml(v[1]);val mode=str(v[3]);val pred: (LegacyXmlName)->Boolean={n->when(mode) {
            "even"->n.local.points().size%2==0
            "named"->n.local==CharacterStringChars.fromString("empty")
            "throw"->if(n.local==CharacterStringChars.fromString("throw")) throw PredicateFailure() else true
            else->false
        }}
            try {cps(LegacyXmlOutput.printContent(c,LegacyXmlPrintConfig(pred,v[2].jsonPrimitive.boolean)))} catch(e: PredicateFailure) {JsonPrimitive("host")}
        }
        "names"->{val ns=v[1].jsonArray.map(::qname);buildJsonObject {
            put("render",JsonArray(ns.map {cps(LegacyXmlOutput.showQName(it))}));put("ordering",JsonArray(ns.flatMap {a->ns.map {b->JsonPrimitive(order(a.compareTo(b)))}}));put("eq",JsonArray(ns.flatMap {a->ns.map {b->JsonPrimitive(a==b)}}))
        }}
        "attributes"->{val xs=v[1].jsonArray.map(::attribute);buildJsonObject {
            put("render",JsonArray(xs.map {cps(LegacyXmlOutput.showAttr(it))}));put("ordering",JsonArray(xs.flatMap {a->xs.map {b->JsonPrimitive(order(a.compareTo(b)))}}));put("eq",JsonArray(xs.flatMap {a->xs.map {b->JsonPrimitive(a==b)}}))
        }}
        "escape-range"->{val first=v[1].jsonPrimitive.int;val count=v[2].jsonPrimitive.int;witness(LegacyXmlOutput.escape(CharacterString(IntArray(count) {first+it})))}
        else->error("request")
    }
    private fun order(c: Int)=if(c<0) "LT" else if(c>0) "GT" else "EQ"
    @TestFactory
    fun originalMathMLObservations(): List<DynamicTest> = GZIPInputStream(checkNotNull(javaClass.getResourceAsStream("/mathml/data.jsonl.gz"))).bufferedReader().use {r->
        r.readLine();r.lineSequence().mapIndexed {i,line->var transport: String?=line
            DynamicTest.dynamicTest("MathML ${i+1}") {
                val c=Json.parseToJsonElement(checkNotNull(transport)).jsonObject;transport=null
                assertLogJsonEquals(c.getValue("expected"),run(c.getValue("request").jsonArray))
            }
        }.toList()
    }
    @Test
    fun deepFiniteFormulaAndOwnedXMLLists() {
        var e: MathExp=MathExp.EIdentifier("x");repeat(10000) {e=MathExp.ESqrt(e)}
        val root=MathMLWriter.writeMathML(DisplayType.DisplayBlock,listOf(e))
        val rendered=LegacyXmlOutput.showElement(root).points()
        val expected="<math display=\"block\" xmlns=\"http://www.w3.org/1998/Math/MathML\">"+"<msqrt>".repeat(10000)+"<mi>x</mi>"+"</msqrt>".repeat(10000)+"</math>"
        assertContentEquals(expected.codePoints().toArray(),rendered)
        val mutable=mutableListOf<LegacyXmlContent>(LegacyXmlText("value"));val node=LegacyXmlElement.node("n",mutable);mutable.clear();assertEquals(1,node.content.size)
        val duplicate=MathExp.EScaled(MathRational(2),MathExp.EScaled(MathRational(3),MathExp.ESymbol(MathSymbolType.Open,"(")))
        val result=MathMLWriter.writeMathML(DisplayType.DisplayBlock,listOf(duplicate))
        val child=result.content.single() as LegacyXmlElement
        assertEquals(listOf("200%","200%","300%","300%","true","prefix"),child.attributes.map {CharacterStringChars.build(it.value)})
    }
}
