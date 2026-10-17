// Independent pinned helper observations; GPL-2.0-or-later.
package io.heapy.kotdoc.readers.org
import io.heapy.kotdoc.ast.*
import io.heapy.kotdoc.parsing.*
import io.heapy.kotdoc.options.*
import io.heapy.kotdoc.state.*
import io.heapy.kotdoc.logging.Logging
import kotlinx.serialization.json.*
import java.util.zip.GZIPInputStream
import java.io.FileInputStream
private fun pos(p:SourcePos?)=p?.let{buildJsonObject{put("name",p.name);put("line",p.line);put("column",p.column)}}?:JsonNull
private fun export(e:OrgExport)=buildJsonObject{
 put("archived",when(e.archivedTrees){ArchivedTrees.Export->"export";ArchivedTrees.NoExport->"none";ArchivedTrees.HeadlineOnly->"headline"});put("drawers",buildJsonObject{put(if(e.drawers.first)"exclude"else"include",JsonArray(e.drawers.second.map(::JsonPrimitive)))})
 put("emphasizedText",e.emphasizedText);put("headlineLevels",e.headlineLevels);put("preserveBreaks",e.preserveBreaks);put("smartQuotes",e.smartQuotes);put("specialStrings",e.specialStrings);put("subSuperscripts",when(e.subSuperscripts){SubSup.All->"all";SubSup.Braced->"braced";SubSup.None->"none"})
 put("withAuthor",e.withAuthor);put("withCreator",e.withCreator);put("withEmail",e.withEmail);put("withEntities",e.withEntities);put("withFootnotes",e.withFootnotes);put("withLatex",when(e.withLatex){TeXExport.Export->"export";TeXExport.Ignore->"ignore";TeXExport.Verbatim->"verbatim"});put("withPlanning",e.withPlanning);put("withTags",e.withTags);put("withTables",e.withTables);put("withTodoKeywords",e.withTodoKeywords)
}
class OrgReaderHelperTest {
 @org.junit.jupiter.api.TestFactory fun cases()=orgRows("helpers.jsonl.gz").mapIndexed{ i,row->org.junit.jupiter.api.DynamicTest.dynamicTest("helper/$i"){verify(row)}}
 private fun verify(row:JsonObject){val q=row.getValue("request").jsonObject;val mode=q.getValue("mode").jsonPrimitive.content;val input=q.getValue("input").jsonPrimitive.content;val runtime=PandocStateContext();val g=OrgGrammar(runtime)
 val observed=with(g){when(mode){
 "isImageFilename"->JsonPrimitive(OrgShared.isImageFilename(input));"cleanLinkText"->OrgShared.cleanLinkText(input)?.let(::JsonPrimitive)?:JsonNull;"translateLang"->JsonPrimitive(OrgShared.translateLang(input));"originalLang"->JsonArray(OrgShared.originalLang(input).map{JsonArray(listOf(JsonPrimitive(it.first),JsonPrimitive(it.second)))})
 else->{val p:OP<JsonElement> = when(mode){
 "export"->exportSettings().map{JsonNull};"hline"->hline().map{JsonNull};"headerStart"->headerStart().map(::JsonPrimitive);"tableStart"->tableStart().map{JsonPrimitive(chars(listOf(it)))};"latexEnvStart"->latexEnvStart().map(::JsonPrimitive);"bulletListStart"->bulletListStart().map(::JsonPrimitive)
 "orderedListStart"->orderedListStart().map{(i,a)->JsonArray(listOf(JsonPrimitive(i),JsonArray(listOf(JsonPrimitive(a.start),buildJsonObject{put("t",a.style.name)},buildJsonObject{put("t",a.delimiter.name)}))))}
 "drawerStart"->drawerStart().map(::JsonPrimitive);"metaLineStart"->metaLineStart().map{JsonNull};"commentLineStart"->commentLineStart().map{JsonNull};"exampleLineStart"->exampleLineStart().map{JsonNull};"noteMarker"->noteMarker().map(::JsonPrimitive);"endOfBlock"->endOfBlock().map{JsonNull};"anyLine"->anyLine().map(::JsonPrimitive);"newline"->newline().map{JsonPrimitive(chars(listOf(it)))};"blanklines"->blanklines().map(::JsonPrimitive);"argKey"->argKey().map(::JsonPrimitive);"argWord"->argWord().map(::JsonPrimitive);"tagWord"->tagWord().map(::JsonPrimitive);"anchor"->anchor().map(::JsonPrimitive);else->error(mode)}
 val exts=q["extensions"]?.jsonArray?.map{Extension.read(it.jsonPrimitive.content)}?.let(Extensions::from)?:ExtensionRegistry.defaults("org")
 val r=p.run(ParseState(ParseInput(Sources.fromText(input)),SourcePos("source"),OrgState.fromOptions(ReaderOptions(extensions=exts))),OrgLocal());val logs=Json.parseToJsonElement(Logging.encodeLogMessagesText(runtime.getLog()))
 if(!r.success)buildJsonObject{put("parseError",r.error.showText());put("logs",logs)}else buildJsonObject{
 put("value",checkNotNull(r.value));put("export",export(r.state.user.export));put("position",pos(r.state.position));put("lastPre",pos(r.state.user.lastPre));put("lastForbidden",pos(r.state.user.lastForbidden));put("logs",logs)
 var stream=r.state.input;val rest=StringBuilder();while(stream.point!=null){rest.appendCodePoint(checkNotNull(stream.point));stream=stream.tail()};put("remainder",rest.toString())
 }
 }
 }}
org.junit.jupiter.api.Assertions.assertTrue(orgJsonEqual(observed,row.getValue("expected")),"request: $q\nactual: $observed\nexpected: ${row.getValue("expected")}")
 }
}
