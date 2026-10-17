// Text.Pandoc.Readers.Org.ParserState, Copyright (C) 2014-2025 Albert Krewinkel.
// GPL-2.0-or-later; original sources/notices retained in upstream/pandoc.
package io.heapy.kotdoc.readers.org
import io.heapy.kotdoc.ast.*
import io.heapy.kotdoc.options.*
import io.heapy.kotdoc.parsing.*
import io.heapy.kotdoc.logging.LogMessage
import io.heapy.kotdoc.tex.Macro
internal typealias OF<A> = Future<OrgState,A>
internal typealias OP<A> = ReaderParsec<OrgLocal,OrgState,A>
internal data class OrgLocal(val quote:QuoteContext=QuoteContext.NoQuote)
internal enum class ArchivedTrees { Export,NoExport,HeadlineOnly }
internal enum class SubSup { All,Braced,None }
internal enum class TeXExport { Export,Ignore,Verbatim }
internal data class OrgExport(
 val archivedTrees:ArchivedTrees=ArchivedTrees.HeadlineOnly,val drawers:Pair<Boolean,List<String>> = true to listOf("LOGBOOK"),
 val emphasizedText:Boolean=true,val headlineLevels:Long=3,val preserveBreaks:Boolean=false,val smartQuotes:Boolean=false,
 val specialStrings:Boolean=true,val subSuperscripts:SubSup=SubSup.All,val withAuthor:Boolean=true,val withCreator:Boolean=true,
 val withEmail:Boolean=true,val withEntities:Boolean=true,val withFootnotes:Boolean=true,val withLatex:TeXExport=TeXExport.Export,
 val withPlanning:Boolean=false,val withTags:Boolean=true,val withTables:Boolean=true,val withTodoKeywords:Boolean=true,
)
internal data class TodoMarker(val done:Boolean,val name:String)
internal data class OrgState(
 val anchorIds:Set<String> = emptySet(),val emphasisStack:List<Int> = emptyList(),val emphasisPre:String="-\t ('\"{\u200B",
 val emphasisPost:String="-\t\n .,:!?;'\")}[\u200B",val emphasisNewlines:Long?=null,val excludeTags:Set<String> = setOf("noexport"),
 val excludeTagsChanged:Boolean=false,val export:OrgExport=OrgExport(),val identifiers:Set<String> = emptySet(),val includeFiles:List<String> = emptyList(),
 val lastForbidden:SourcePos?=null,val lastPre:SourcePos?=null,val lastStr:SourcePos?=null,
 val linkFormatters:Map<String,(String)->String> = emptyMap(),val macros:Map<String,(List<String>)->String> = emptyMap(),val macroDepth:Long=0,
 val meta:OF<MetadataMap<MetaValue>> = Future.pure(MetadataMap.empty()),val notes:List<Pair<String,OF<Blocks>>> = emptyList(),val options:ReaderOptions=ReaderOptions(),
 val listContext:Boolean=false,val selectTags:Set<String> = setOf("export"),val selectTagsChanged:Boolean=false,
 val todoSequences:List<List<TodoMarker>> = emptyList(),val trimLeadBlockIndent:Boolean=true,val messages:List<LogMessage> = emptyList(),
 val texMacros:Map<String,Macro> = emptyMap(),
){
 fun activeTodos()=if(todoSequences.isEmpty())listOf(TodoMarker(false,"TODO"),TodoMarker(true,"DONE"))else todoSequences.flatten()
 companion object {fun fromOptions(options:ReaderOptions):OrgState{val smart=Extension.read("smart")in options.extensions
  return OrgState(options=options,export=OrgExport(smartQuotes=smart||Extension.read("smart_quotes")in options.extensions,specialStrings=smart||Extension.read("special_strings")in options.extensions))}}
}
internal object OrgAccess:ReaderOptionsAccess<OrgState>,LastStrPositionAccess<OrgState>,MacrosAccess<OrgState>{
 override fun extractMacros(state:OrgState)=state.texMacros
 override fun updateMacros(update:(Map<String,Macro>)->Map<String,Macro>,state:OrgState)=state.copy(texMacros=update(state.texMacros))
 override fun extractReaderOptions(state:OrgState)=state.options
 override fun getLastStrPos(state:OrgState)=state.lastStr
 override fun setLastStrPos(position:SourcePos?,state:OrgState)=state.copy(lastStr=position)
}
