// Text.Pandoc.Readers.Org.Blocks, Copyright (C) 2014-2024 Albert Krewinkel.
// GPL-2.0-or-later; original source and notices retained in upstream/pandoc.
package io.heapy.kotdoc.readers.org
import io.heapy.kotdoc.ast.*
internal fun OrgGrammar.blocks():OP<OF<Blocks>> = till(delay{block()},headerStart().lookAhead().map{Unit} or eof()).map(::blocks)
internal fun OrgGrammar.block():OP<OF<Blocks>> = choice(listOf(blanklines().map{future(Blocks())},table(),dynamicBlock(),orgBlock(),figure(),example(),genericDrawer(),include(),specialLine(),horizontalRule(),orgList(),latexFragment(),noteBlock(),rawOrgLine(),paraOrPlain())).label("block")
internal fun OrgGrammar.blockList():OP<List<Block>> = documentTree().flatMap{tree->state().flatMap{s->unprunedHeadlineToBlocks(tree.runF(s.user),s.user)}}
internal fun OrgGrammar.document():OP<Pandoc> = blockList().flatMap{blocks->metaExport().flatMap{meta->state().map{s->val result=meta.runF(s.user);for(message in s.user.messages)runtime.report(message);Pandoc(result,blocks)}}}
