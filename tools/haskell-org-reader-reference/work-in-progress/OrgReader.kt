// Text.Pandoc.Readers.Org, Copyright (C) 2014-2025 Albert Krewinkel.
// GPL-2.0-or-later; original source and notices retained in upstream/pandoc.
package io.heapy.kotdoc.readers
import io.heapy.kotdoc.ast.Pandoc
import io.heapy.kotdoc.parsing.*
import io.heapy.kotdoc.options.ReaderOptions
import io.heapy.kotdoc.state.*
import io.heapy.kotdoc.resources.PandocDataRuntime
import io.heapy.kotdoc.readers.org.*
import io.heapy.kotdoc.latex.LaTeXReader
/** Org's include effects and nested LaTeX effects are supplied by the caller.
 * zonedDay is showGregorian(localDay(getZonedTime)), as in LaTeXReader. */
object OrgReader {
 fun read(options:ReaderOptions,text:String):Pandoc=read(options,Sources.fromText(text))
 fun read(options:ReaderOptions,sources:Sources):Pandoc {
  val state=PandocStateContext();return read(options,sources,OrgPureData(state)){"1970-01-01"}
 }
 fun read(options:ReaderOptions,text:String,runtime:PandocDataRuntime,zonedDay:()->String):Pandoc=read(options,Sources.fromText(text),runtime,zonedDay)
 fun read(options:ReaderOptions,sources:Sources,runtime:PandocDataRuntime,zonedDay:()->String):Pandoc {
  val input=sources.ensureFinalNewlines(2)
  val reply=OrgGrammar(runtime,runtime,zonedDay).document().run(ParseState(ParseInput(input),SourcePos(input.initialSourceName()),OrgState.fromOptions(options)),OrgLocal())
  if(!reply.success)throw LaTeXReader.fromParsecError(input,reply.error)
  return checkNotNull(reply.value)
 }
}
