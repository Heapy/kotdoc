// Text.Pandoc.Writers.Shared metaToContext/metaValueToVal mapM closure.
// Copyright (C) 2013-2024 John MacFarlane; GPL-2.0-or-later.
package io.heapy.kotdoc.writers
import io.heapy.kotdoc.ast.*
import io.heapy.kotdoc.options.WriterOptions
import io.heapy.kotdoc.layout.Doc
import io.heapy.kotdoc.templates.*

internal fun asciiDocMetadata(options:WriterOptions,blockWriter:(List<Block>)->Doc<String>,inlineWriter:(List<Inline>)->Doc<String>,metadata:Map<String,MetaValue>):Context<String> {
 if(options.template==null)return Context()
 val convert=DeepRecursiveFunction<MetaValue,Val<String>>{value->when(value){
  is MetaValue.MetaMap->{val entries=linkedMapOf<String,Val<String>>();for(key in value.values.keys.sortedWith(UnicodeTextOrder))entries[key]=callRecursive(value.values.getValue(key));Val.MapVal(Context(entries))}
  is MetaValue.MetaList->{val entries=mutableListOf<Val<String>>();for(child in AsciiDocPatternList(value.values))entries.add(callRecursive(child));Val.ListVal(entries)}
  is MetaValue.MetaBool->Val.BoolVal(value.value)
  is MetaValue.MetaString->Val.SimpleVal(inlineWriter(Builder.text(value.value).values))
  is MetaValue.MetaBlocks->Val.SimpleVal(blockWriter(value.content))
  is MetaValue.MetaInlines->Val.SimpleVal(inlineWriter(value.content))
 }}
 val context=(convert(MetaValue.MetaMap(metadata))as Val.MapVal).context
 // Conversion has completed every metadata callback in map/list source order.
 // This existing helper only serializes the resulting defined context and adds
 // options.variables with the source precedence; no raw MetaList reaches it.
 return WriterMetadata.addVariablesToContext(options,TemplateValues.Text,context)
}

// pandoc-types Definition.docTitle/docAuthors/docDate null-only closure.
// Copyright (C) 2006-2023 John MacFarlane; BSD-3-Clause (licenses/pandoc-types-*).
internal fun asciiDocTitleblock(metadata:Map<String,MetaValue>):Boolean {
 fun single(value:MetaValue.MetaBlocks,plain:Boolean?=null):List<Inline>? {
  val cells=AsciiDocPatternList(value.content);if(!cells.hasAt(0))return null
  val first=cells[0];val content=when{first is Block.Plain&&plain!=false->first.content;first is Block.Para&&plain!=true->first.content;else->return null}
  return if(cells.hasAt(1))null else content
 }
 fun textEmpty(value:MetaValue?):Boolean=when(value){
  is MetaValue.MetaString->false
  is MetaValue.MetaInlines->asciiDocNull(value.content)
  is MetaValue.MetaBlocks->single(value)?.let(::asciiDocNull)?:true
  else->true
 }
 fun authorsEmpty(value:MetaValue?):Boolean=when(value){
  is MetaValue.MetaString,is MetaValue.MetaInlines->false
  is MetaValue.MetaList->{var found=false;val authors=AsciiDocPatternList(value.values)
   for(group in 0..3){for(item in authors){
    val match=when(group){0->item is MetaValue.MetaInlines;1->item is MetaValue.MetaBlocks&&single(item,true)!=null;2->item is MetaValue.MetaBlocks&&single(item,false)!=null;else->item is MetaValue.MetaString}
    if(match){found=true;break}
   };if(found)break};!found
  }
  else->true
 }
 return !(textEmpty(metadata["title"])&&authorsEmpty(metadata["author"])&&textEmpty(metadata["date"]))
}
