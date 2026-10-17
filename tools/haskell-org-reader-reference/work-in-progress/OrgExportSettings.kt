// Text.Pandoc.Readers.Org.ExportSettings, ©2016-2024 Albert Krewinkel.
// GPL-2.0-or-later; original retained in upstream/pandoc.
package io.heapy.kotdoc.readers.org
import io.heapy.kotdoc.parsing.*
import io.heapy.kotdoc.logging.LogMessage
import io.heapy.kotdoc.resources.safeReadInteger
internal fun OrgGrammar.exportSettings():OP<Unit>{
 fun <A> setting(key:String,p:OP<A>,set:(OrgExport,A)->OrgExport):OP<Unit> = text(key).then(char(':')).then(p).flatMap{v->update{it.copy(export=set(it.export,v))}}.attempt()
 val boolean=nonspace().many1().map{xs->chars(xs.map(RegexCharacters::toLower)) !in listOf("nil","{}","()")}.attempt()
 fun b(key:String,set:(OrgExport,Boolean)->OrgExport)=setting(key,boolean,set)
 fun ignored(key:String)=text(key).before(char(':')).before(nonspace().many1()).map{Unit}.attempt()
 fun optionString(s:String)=text(s).before((newline() or sp()).lookAhead()).attempt()
 val integer=satisfy(ParserCharacters::isDigit).many1().map(::chars).flatMap{raw->safeReadInteger(raw)?.toLong()?.let(::pure)?:zero()}.attempt()
 val archived=optionString("headline").map{ArchivedTrees.HeadlineOnly} or boolean.map{if(it)ArchivedTrees.Export else ArchivedTrees.NoExport}
 val subs=optionString("{}").map{SubSup.Braced} or boolean.map{if(it)SubSup.All else SubSup.None}
 val tex=optionString("verbatim").map{TeXExport.Verbatim} or boolean.map{if(it)TeXExport.Export else TeXExport.Ignore}
 val word=char('"').then(satisfy(ParserCharacters::isAlphaNum).many()).before(char('"')).map(::chars).attempt()
 fun stringList(prefix:String)=text(prefix).then(word.flatMap{x->space().skipMany().then(word).many().map{listOf(x)+it}}.option(emptyList())).before(char(')')).attempt()
 val drawers=stringList("(not ").map{true to it} or stringList("(").map{false to it} or boolean.map{it to emptyList<String>()}
 val branches=listOf(
  setting("^",subs){s,v->s.copy(subSuperscripts=v)},b("'"){s,v->s.copy(smartQuotes=v)},b("*"){s,v->s.copy(emphasizedText=v)},b("-"){s,v->s.copy(specialStrings=v)},ignored(":"),ignored("<"),
  b("\\n"){s,v->s.copy(preserveBreaks=v)},setting("arch",archived){s,v->s.copy(archivedTrees=v)},b("author"){s,v->s.copy(withAuthor=v)},ignored("c"),b("creator"){s,v->s.copy(withCreator=v)},
  setting("d",drawers){s,v->s.copy(drawers=v)},ignored("date"),b("e"){s,v->s.copy(withEntities=v)},b("email"){s,v->s.copy(withEmail=v)},b("f"){s,v->s.copy(withFootnotes=v)},
  setting("H",integer){s,v->s.copy(headlineLevels=v)},ignored("inline"),ignored("num"),b("p"){s,v->s.copy(withPlanning=v)},ignored("pri"),ignored("prop"),ignored("stat"),
  b("tags"){s,v->s.copy(withTags=v)},ignored("tasks"),setting("tex",tex){s,v->s.copy(withLatex=v)},ignored("timestamp"),ignored("title"),ignored("toc"),b("todo"){s,v->s.copy(withTodoKeywords=v)},b("|"){s,v->s.copy(withTables=v)},
  take1{it !in intArrayOf(32,9,10,13)}.flatMap{opt->lift(Parsec.lift<OrgState,Unit>{runtime.report(LogMessage.UnknownOrgExportOption(opt))})}.attempt()
 )
 // sepEndBy exportSetting skipSpaces (the separator may succeed without input).
 fun loop():OP<Unit> = choice(branches).before(skipSpaces()).then(delay{loop()}).option(Unit)
 return delay{loop()}.label("export setting")
}
