// Text.DocLayout.chomp 0.6, BSD-3-Clause.
// Copyright (C) 2010-2024 John MacFarlane; licenses/DocLayout-BSD-3-Clause.txt.
package io.heapy.kotdoc.writers
import io.heapy.kotdoc.layout.*

/** Source trailing whitespace rules modulo Concat associativity. The source's
 * reassociation changes no render/measure/isEmpty result; keeping the untouched
 * prefix shared avoids materializing every rotation at every nesting level.
 * This private helper does not promise source Doc's Show/Eq representation. */
internal fun asciiDocChomp(input:Doc<String>):Doc<String> = AsciiDocChomp().apply(input)

/** Session-scoped identity memo: Doc is immutable, and nested list conversions
 * reuse earlier prefixes. Never repeat normalization of an unchanged subtree. */
internal class AsciiDocChomp {
 private val normalized=java.util.IdentityHashMap<Doc<String>,Doc<String>>()
 private val clean=DeepRecursiveFunction<Doc<String>,Doc<String>>{d->
  normalized[d] ?: if(d is Doc.Concat){
   val left=callRecursive(d.left);val right=callRecursive(d.right)
   val result=if(left===d.left&&right===d.right&&left!==Doc.Empty&&right!==Doc.Empty)d else left+right
   normalized[d]=result;result
  }else d
 }
 fun apply(input:Doc<String>):Doc<String>{
 val go=DeepRecursiveFunction<Doc<String>,Doc<String>>{input->when(val d=clean.callRecursive(input)){
  is Doc.BlankLines,Doc.NewLine,Doc.CarriageReturn,Doc.BreakingSpace->Doc.Empty
  is Doc.Prefixed->{val child=callRecursive(d.doc);if(child===d.doc)d else d.copy(doc=child)}
  is Doc.Concat->{val right=callRecursive(d.right);if(right===Doc.Empty)callRecursive(d.left)else if(right===d.right&&d.left!==Doc.Empty)d else d.left+right}
  else->d
 }}
 return go(input)
}
}
