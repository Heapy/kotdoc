// Text.Pandoc.Class.IO.logOutput, copyright (C) 2006-2024 John MacFarlane.
// GPL-2.0-or-later.
package io.heapy.kotdoc.cli
import io.heapy.kotdoc.ast.Pandoc
import io.heapy.kotdoc.options.WriterOptions
import io.heapy.kotdoc.writers.AsciiDocWriter
import io.heapy.kotdoc.logging.Logging
internal object AsciiDocCliWriter {
 // Native.hs readNative maps only exhausted Read alternatives to PandocParseError.
 // Keep this application correction scoped to the newly registered output routes.
 fun readNative(text:String):Pandoc = try { io.heapy.kotdoc.formats.NativeReader.read(text) }
 catch(error:IllegalArgumentException) {
  if(error.message!="couldn't read native")throw error
  throw io.heapy.kotdoc.errors.PandocError.PandocParseError("couldn't read native")
 }
 // Data.readDataFile throws this typed Pandoc error at the App boundary.
 fun <T> prepare(action:()->T):T = try { action() }
 catch(error:io.heapy.kotdoc.templates.TemplateDataFileNotFound) {
  throw io.heapy.kotdoc.errors.PandocError.PandocCouldNotFindDataFileError(error.path)
 }
 fun outputSettings(format:String){if(format=="asciidoctor")report(io.heapy.kotdoc.logging.LogMessage.Deprecated("asciidoctor","use asciidoc instead"))}
 private fun report(message:io.heapy.kotdoc.logging.LogMessage){
  if(Logging.messageVerbosity(message)!=io.heapy.kotdoc.logging.Verbosity.INFO)System.err.println("[${Logging.messageVerbosity(message)}] "+Logging.showLogMessage(message).replace("\n","\n  "))
 }
 fun write(format:String,options:WriterOptions,document:Pandoc):String {
  return (if(format=="asciidoc_legacy")AsciiDocWriter::writeLegacy else AsciiDocWriter::write)(options,document,::report)
 }
}
