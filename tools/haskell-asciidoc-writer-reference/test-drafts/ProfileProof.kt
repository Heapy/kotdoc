package io.heapy.kotdoc.writers
import io.heapy.kotdoc.ast.*
import io.heapy.kotdoc.readers.DokuWikiIdentifiers
import io.heapy.kotdoc.options.Extensions
import io.heapy.kotdoc.latex.LaTeXReaderText
import io.heapy.kotdoc.parsing.*
import kotlinx.serialization.json.*
import java.io.File
import java.util.zip.GZIPInputStream
fun main(args:Array<String>){val dir=File(args[0]);val expected=GZIPInputStream(File(dir,"classifiers.bin.gz").inputStream()).use{it.readBytes()};val lower=Json.parseToJsonElement(File(dir,"lower-mappings.json").readText()).jsonArray.associate{it.jsonArray[0].jsonPrimitive.int to it.jsonArray[1].jsonPrimitive.content};val upper=Json.parseToJsonElement(File(dir,"upper-mappings.json").readText()).jsonArray.associate{it.jsonArray[0].jsonPrimitive.int to it.jsonArray[1].jsonPrimitive.content};val fold=Json.parseToJsonElement(File(dir,"casefold-mappings.json").readText()).jsonArray.associate{it.jsonArray[0].jsonPrimitive.int to it.jsonArray[1].jsonPrimitive.content}
for(cp in 0..0x10ffff){val text=String(Character.toChars(cp));val normalized=if(cp in 0xd800..0xdfff)"\ufffd"else text;check(LaTeXReaderText.toLower(normalized)==(lower[cp]?:normalized)){"lower $cp"};check(LaTeXReaderText.toUpper(normalized)==(upper[cp]?:normalized)){"upper $cp"};check(OrgCaseFold.fold(normalized)==(fold[cp]?:normalized)){"fold $cp"};val flags=(if(ParserCharacters.isSpace(cp))1 else 0)+(if(RegexCharacters.isPunctuation(cp))2 else 0)+(if(ParserCharacters.isDigit(cp))4 else 0)+(if(ParserCharacters.isAlphaNum(cp))8 else 0);check(flags==expected[cp].toInt()){"flags $cp"}}
var count=0;GZIPInputStream(File(dir,"identifiers.jsonl.gz").inputStream()).bufferedReader().useLines{lines->lines.forEach{line->val r=Json.parseToJsonElement(line).jsonObject;val q=r.getValue("request").jsonObject;val xs=q.getValue("inlines").jsonArray.map(PandocJson::decodeInline);val ext=Extensions.fromJson(q.getValue("extensions"));val used=q.getValue("used").jsonArray.map{it.jsonPrimitive.content}.toSet();check(DokuWikiIdentifiers.unique(xs,used,ext)==r.getValue("expected").jsonObject.getValue("identifier").jsonPrimitive.content){q.getValue("name").toString()};count++}}
println("PASS all1114112 classifier/lower/upper/casefold positions + $count identifiers")}
