// Text.Pandoc.Readers.Org.Blocks / Parsing.General.insertIncludedFile.
// Copyright (C) 2014-2024 Albert Krewinkel; (C) 2006-2024 John MacFarlane.
// GPL-2.0-or-later; original source and notices retained in upstream/pandoc.
package io.heapy.kotdoc.readers.org
import io.heapy.kotdoc.ast.*
import io.heapy.kotdoc.ast.Builder as B
import io.heapy.kotdoc.parsing.*
import io.heapy.kotdoc.resources.*
import io.heapy.kotdoc.layout.CharacterStringChars
import io.heapy.kotdoc.logging.LogMessage
import io.heapy.kotdoc.errors.PandocError
internal fun orgTextLines(raw:String)=if(raw.isEmpty())emptyList()else raw.split('\n').let{if(it.last()=="")it.dropLast(1)else it}
internal fun OrgGrammar.include():OP<OF<Blocks>> = metaLineStart().before(anyCase("include:")).before(skipSpaces()).then(char('"')).then(till(noneOf("\n\r\t"),char('"'))).map(::chars).flatMap{file->
 skipSpaces().then(take1(ParserCharacters::isAlphaNum)).attempt().many().flatMap{args->keyValues().flatMap{params->state().flatMap{old->
  if(file in old.user.includeFiles)throw PandocError.PandocParseError("Include file loop at "+old.position.toString())
  val bounds=params.firstOrNull{it.first=="lines"}?.second?.let{if(it.length>=2)it.drop(1).dropLast(1)else""}
  val start=bounds?.substringBefore('-')?.let(::safeReadInteger)?.toLong();val end=bounds?.substringAfterLast('-')?.let(::safeReadInteger)?.toLong()?.minus(1)
  val paths=dataRuntime.filePaths;val path=paths.combine(paths.directory(old.position.name),file)
  update{it.copy(includeFiles=listOf(file)+it.includeFiles)}.then(lift(Parsec.lift<OrgState,String>{
   val raw=try{PandocText.toTextM(CharacterStringChars.fromString(path),dataRuntime.readFileStrict(CharacterStringChars.fromString(path)))}catch(_:PandocError){null}
   if(raw==null){runtime.report(LogMessage.CouldNotLoadIncludeFile(file,old.position));""}else if(start==null&&end==null)raw else{
    val lines=orgTextLines(raw);val first=start?.let{if(it>=0)it else lines.size+it}?:1;val last=end?.let{if(it>=0)it else lines.size+it}?:lines.size.toLong()
    val picked=lines.drop((first-1).coerceIn(0,Int.MAX_VALUE.toLong()).toInt()).take((last-(first-1)).coerceIn(0,Int.MAX_VALUE.toLong()).toInt());picked.joinToString("\n",postfix=if(picked.isEmpty())""else"\n")
   }
  })).flatMap{contents->
   val raw=take{true}
   val parser:OP<OF<Blocks>> = when{
    args.firstOrNull()=="example"->raw.map{future(B.codeBlock(it))}
    args==listOf("export")->pure(future(Blocks()))
    args.size==2&&args[0]=="export"->raw.map{future(B.rawBlock(args[1],it))}
    args.firstOrNull()=="src"->raw.map{future(B.codeBlockWith(if(args.size==2)Attr(classes=listOf(args[1]))else Attr(),it))}
    else->delay{blockList()}.map{bs->val level=params.firstOrNull{it.first=="minlevel"}?.second?.let(::safeReadInteger)?.toLong();if(level==null)future(Blocks(bs))else {
     var minimum:Long?=null;val query=object:PandocVisitor(){override fun visitBlock(value:Block){if(value is Block.Header)minimum=minimum?.let{minOf(it,value.level)}?:value.level}};query.walkBlocks(bs);val shift=(minimum?:0)-level
     val walk=object:PandocVisitor(){override fun transformBlock(value:Block):Block=if(value !is Block.Header)value else {val n=value.level-shift;if(n>0)value.copy(level=n)else Block.Para(value.content)}}
     future(Blocks(walk.walkBlocks(bs)))
    }}
   }
   lift(Parsec.modify<OrgState>{it.copy(input=ParseInput(Sources.fromText(contents)),position=SourcePos(file,start?:1,1))}).then(parser).before(lift(Parsec.modify<OrgState>{it.copy(input=old.input,position=old.position,user=it.user.copy(includeFiles=it.user.includeFiles.drop(1)))}))
  }
 }}}
}.attempt()
