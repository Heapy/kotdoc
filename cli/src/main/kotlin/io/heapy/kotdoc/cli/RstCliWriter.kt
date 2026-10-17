// Text.Pandoc.Class.IO.logOutput, copyright (C) 2006-2024 John MacFarlane.
// GPL-2.0-or-later.
package io.heapy.kotdoc.cli
import io.heapy.kotdoc.ast.Pandoc
import io.heapy.kotdoc.options.WriterOptions
import io.heapy.kotdoc.writers.RstWriter
import io.heapy.kotdoc.logging.Logging
internal object RstCliWriter {
 fun write(options:WriterOptions,document:Pandoc):String=RstWriter.write(options,document){message->
  if (Logging.messageVerbosity(message) != io.heapy.kotdoc.logging.Verbosity.INFO) System.err.println("[${Logging.messageVerbosity(message)}] "+Logging.showLogMessage(message).replace("\n","\n  "))
 }
}
