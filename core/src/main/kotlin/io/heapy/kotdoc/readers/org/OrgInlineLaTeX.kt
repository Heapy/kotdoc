// Text.Pandoc.Readers.Org.Inlines, Copyright (C) 2014-2025 Albert Krewinkel.
// GPL-2.0-or-later; original source and notices retained in upstream/pandoc.
package io.heapy.kotdoc.readers.org
import io.heapy.kotdoc.ast.*
import io.heapy.kotdoc.ast.Builder as B
import io.heapy.kotdoc.parsing.*
import io.heapy.kotdoc.options.*
import io.heapy.kotdoc.math.*
internal fun OrgGrammar.inlineLaTeXCommand():OP<String> = state().flatMap{s->lift(Parsec.lift<OrgState,ParserReply<ParseState<OrgState>,String>>{
 latex.rawLaTeXInline(OrgAccess,OrgAccess).run(s.copy(position=SourcePos("source")))
}).flatMap{reply->if(!reply.success)zero()else {val command=checkNotNull(reply.value).trimEnd{ParserCharacters.isSpace(it.code)};val count=command.codePointCount(0,command.length);(0 until count).fold(pure(Unit)){p,_->p.then(any()).map{Unit}}.map{command}}}}.attempt()
internal fun OrgGrammar.orgInlineLaTeX():OP<OF<Inlines>> = inlineLaTeXCommand().flatMap{command->state().flatMap{s->
 val option=s.user.export.withLatex
 val inline=lift(Parsec.lift<OrgState,Inlines?>{
  when(option){TeXExport.Ignore->Inlines();TeXExport.Verbatim->B.text(command);TeXExport.Export->{
   val options=ReaderOptions(extensions=Extensions.from(listOf(Extension.read("raw_tex"))))
   val initial=OrgState.fromOptions(options)
   val raw=withRaw(lift(latex.inlineCommand(OrgAccess,OrgAccess))).map{(_,text)->B.rawInline("latex",text)}.run(ParseState(ParseInput(Sources.fromText(command)),SourcePos(""),initial),OrgLocal())
   if(raw.success)raw.value else null
  }}
 })
 inline.flatMap{raw->
  if(command.startsWith("\\begin{"))raw?.let{pure(future(it))}?:zero()
  else {val entity=MathMLEntities.getUnicode(command.drop(1).trimEnd('{','}'))?.let{B.str(if(s.user.export.withEntities)it else command)}
   val math=when(option){TeXExport.Ignore->Inlines();TeXExport.Verbatim->B.str(command);TeXExport.Export->when(val parsed=MathTeXReader.readTeX(command)){is MathTeXResult.Success->MathPandocWriter.writePandoc(DisplayType.DisplayInline,parsed.expressions)?.let(::Inlines);is MathTeXResult.Failure->null}}
   (entity?:math?:raw)?.let{pure(future(it))}?:zero()
  }
 }
}}.attempt()
