// Text.Pandoc.Parsing.GridTable (used gridTableWith closure), Pandoc 3.12.1.
// Copyright (C) 2006-2024 John MacFarlane; GPL-2.0-or-later.
// gridtables 0.1.1.0 raw tracer: Copyright © 2022-2023 Posit Software, PBC; MIT.
package io.heapy.kotdoc.readers.org
import io.heapy.kotdoc.ast.*
import io.heapy.kotdoc.ast.Builder as B
import io.heapy.kotdoc.parsing.*
import io.heapy.kotdoc.layout.CharacterStringChars
import io.heapy.kotdoc.readers.RstGridTable
/** Cell parser effects run now, cell Future payloads run only against final state. */
internal fun OrgGrammar.gridTable():OP<OF<Blocks>> = lift(RstGridTable.parser<OrgState>()).before(blankline().many1().optional()).flatMap{table->
 fun parseCell(raw:String):OP<OF<Blocks>> = state().flatMap{old->
  lift(Parsec.modify<OrgState>{it.copy(input=ParseInput(Sources.fromText(raw)),position=old.position.copyRaw(name=CharacterStringChars.concat(listOf(old.position.nameRaw,CharacterStringChars.fromString("_chunk"))),line=1,column=1),user=it.user.copy(lastStr=null))}).then(delay{blocks()}).flatMap{result->
   lift(Parsec.modify<OrgState>{it.copy(input=old.input,position=old.position,user=it.user.copy(lastStr=old.user.lastStr))}).map{result}}
 }
 val rowParsers=table.rows.map{row->sequenceParsers(row.map{cell->val stripped=cell.lines.map{it.trimEnd{c->ParserCharacters.isSpace(c.code)}};val unindented=if(stripped.all{it.isEmpty()||it.startsWith(' ')})stripped.map{it.drop(1)}else stripped
 val text=unindented.joinToString("\n",postfix=if(unindented.isEmpty())""else"\n")+"\n"
 parseCell(text).map{it.mapValue{b->Cell(Attr(),Alignment.AlignDefault,cell.rowSpan.toLong(),cell.colSpan.toLong(),b.values)}}
 }).map(::sequence)}
 sequenceParsers(rowParsers).flatMap{parsed->state().map{s->
 val widths=table.specs.map{it.second.toLong()+1};val norm=maxOf(widths.sum()+widths.size-2,s.user.options.columns).toDouble();val specs=table.specs.zip(widths){spec,w->ColSpec(spec.first,ColWidth.Width(w/norm))}
 sequence(parsed).mapValue{rows->val h=table.head?:0;val head=rows.take(h).map{Row(Attr(),it)};val rest=rows.drop(h).map{Row(Attr(),it)};val len=table.foot?.let{it-h-1}?:rest.size;val body=rest.take(len.coerceAtLeast(0));val foot=rest.drop(len.coerceAtLeast(0))
 B.tableWith(Attr(),Caption(null,emptyList()),specs,TableHead(Attr(),head),listOf(TableBody(Attr(),0,emptyList(),body)),TableFoot(Attr(),foot))}
 }}
}
