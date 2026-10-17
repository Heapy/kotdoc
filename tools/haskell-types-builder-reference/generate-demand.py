#!/usr/bin/env python3
"""Generate controlled original expressions, never expected responses."""
import json
from pathlib import Path
H=Path(__file__).resolve().parent
args={}
def setops(names,values):
 for op in names.split():args[op]=values
setops('text str code math displayMath codeBlock',[('text','txt')])
setops('emph underline strong strikeout superscript subscript smallcaps singleQuoted doubleQuoted trimInlines para plain',[('inlines','ils')])
setops('cite',[('citations','[]'),('inlines','ils')])
setops('codeWith codeBlockWith',[('attr','nullAttr'),('text','txt')])
setops('rawInline rawBlock',[('format','txt'),('text','txt')])
setops('linkWith imageWith',[('attr','nullAttr'),('url','txt'),('title','txt'),('inlines','ils')])
setops('link image',[('url','txt'),('title','txt'),('inlines','ils')])
setops('note blockQuote',[('blocks','bs')])
setops('spanWith',[('attr','nullAttr'),('inlines','ils')])
setops('space softbreak linebreak horizontalRule',[])
setops('lineBlock',[('lines','[ils]')])
setops('orderedListWith',[('attributes','(1,DefaultStyle,DefaultDelim)'),('items','[bs]')])
setops('orderedList bulletList',[('items','[bs]')])
setops('definitionList',[('definitions','[(ils,[bs])]')])
setops('headerWith',[('attr','nullAttr'),('level','1'),('inlines','ils')])
setops('header',[('level','1'),('inlines','ils')])
setops('figureWith',[('attr','nullAttr'),('caption','capt'),('blocks','bs')])
setops('figure',[('caption','capt'),('blocks','bs')])
setops('simpleFigureWith',[('attr','nullAttr'),('inlines','ils'),('url','txt'),('title','txt')])
setops('simpleFigure',[('inlines','ils'),('url','txt'),('title','txt')])
setops('divWith',[('attr','nullAttr'),('blocks','bs')])
probes=[]
def add(name,expr):probes.append((name,expr))
for op,fields in args.items():
 expr='P.'+op+' '+ ' '.join('('+v+')'for _,v in fields)
 add(op+'/length',f'show(length(P.toList({expr})))')
 add(op+'/element-root',f'root(head(P.toList({expr})))')
 for index,(name,_)in enumerate(fields):
  vs=[v if i!=index else f'bad "{name}"'for i,(_,v)in enumerate(fields)]
  expr='P.'+op+' '+ ' '.join('('+v+')'for v in vs)
  add(op+'/'+name+'/length',f'show(length(P.toList({expr})))')
  add(op+'/'+name+'/element-root',f'root(head(P.toList({expr})))')
add('caption/short-root','root(P.caption(bad "short")bs)')
add('caption/blocks-root','root(P.caption Nothing(bad "blocks"))')
add('caption/short-payload','root(P.caption(Just(bad "payload"))bs)')
add('caption/long-head','root(P.caption Nothing(P.fromList[bad "block"]))')
add('simpleCaption/blocks-root','root(P.simpleCaption(bad "blocks"))')
add('emptyCaption/root','root P.emptyCaption')
add('nullAttr/root','root nullAttr')
add('attr-fields-lazy','root(head(P.toList(P.codeWith(bad "id",bad "classes",bad "properties")txt)))')
add('link-target-fields-lazy','root(head(P.toList(P.link(bad "url")(bad "title")ils)))')
add('link-target-first','case head(P.toList(P.link txt(bad "title")ils))of Link _ _ (url,_)->T.unpack url;_->bad "constructor"')
add('link-target-second','case head(P.toList(P.link(bad "url")txt ils))of Link _ _ (_,title)->T.unpack title;_->bad "constructor"')
add('definition-head-root','root(head(P.toList(P.definitionList[bad "pair"])))')
add('definition-term','case head(P.toList(P.definitionList[(ils,bad "defs")]))of DefinitionList xs->show(fst(head xs));_->bad "constructor"')
add('definition-defs','case head(P.toList(P.definitionList[(bad "term",[bs])]))of DefinitionList xs->show(snd(head xs));_->bad "constructor"')
add('definition-tail','case head(P.toList(P.definitionList((ils,[]):bad "tail")))of DefinitionList xs->show(take 1 xs);_->bad "constructor"')
add('lineBlock-unused-line','root(head(P.toList(P.lineBlock[bad "line"])))')
add('bulletList-unused-item','root(head(P.toList(P.bulletList[bad "item"])))')
add('note-unused-block','root(head(P.toList(P.note(P.fromList[bad "block"]))))')
add('emph-unused-inline','root(head(P.toList(P.emph(P.fromList[bad "inline"]))))')
add('plain-unused-inline','root(head(P.toList(P.plain(P.fromList[bad "inline"]))))')
add('trim-shared-large','show $ F.length $ P.trimInlines $ P.Many $ S.replicate (2^40) (Str "a")')
add('trim-shared-max','show $ F.length $ P.trimInlines $ P.Many $ S.replicate maxBound (Str "a")')
add('trim-deep-spaces','show $ length $ P.toList $ P.trimInlines $ P.fromList (replicate 50000 Space++[Str "a"]++replicate 50000 SoftBreak)')
add('trim-nested-constructor',r'root $ head $ P.toList $ P.trimInlines $ P.fromList [foldr(\_ x->Emph[x])(Str "a")[1..4096]]')
add('trim-partial-middle','show $ F.length $ P.trimInlines $ P.Many $ SI.Seq $ SI.Deep 3 (SI.One(SI.Elem(Str "a"))) (bad "middle") (SI.One(SI.Elem(Str "b")))')
partialNode='let n x y=SI.Node2 2(SI.Elem x)(SI.Elem y); a=n(Str "a")Space; m=SI.Deep 8(SI.One(n Space Space))(SI.Single(SI.Node2 4 a(bad "node")))(SI.One(n(Str "x")Space)); xs=P.Many(SI.Seq(SI.Deep 10(SI.One(SI.Elem Space))m(SI.One(SI.Elem(Str "z"))))) in '
add('trim-partial-node-length',partialNode+'show(F.length(P.trimInlines xs))')
add('trim-partial-node-prefix',partialNode+'show(take 1(P.toList(P.trimInlines xs)))')
partialSingle='let n=SI.Node2 2(SI.Elem(Str "a"))(SI.Elem Space); m=SI.Deep 4(SI.One n)SI.EmptyT(SI.One(bad "single")); xs=P.Many(SI.Seq(SI.Deep 6(SI.One(SI.Elem Space))m(SI.One(SI.Elem(Str "z"))))) in '
add('trim-partial-single-length',partialSingle+'show(F.length(P.trimInlines xs))')
add('trim-partial-single-prefix',partialSingle+'show(take 1(P.toList(P.trimInlines xs)))')
add('trim-partial-node-two',partialNode+'show(take 2(P.toList(P.trimInlines xs)))')
add('trim-partial-node-demand',partialNode+'show(take 3(P.toList(P.trimInlines xs)))')
add('trim-partial-single-two',partialSingle+'show(take 2(P.toList(P.trimInlines xs)))')
add('trim-partial-single-demand',partialSingle+'show(take 3(P.toList(P.trimInlines xs)))')
add('single-root','root(SI.Single(bad "single"::SI.Node(SI.Elem Inline)))')
add('single-size','show(SI.size(SI.Single(bad "single"::SI.Node(SI.Elem Inline))))')
add('single-leaf-size','show(SI.size(SI.Single(bad "elem"::SI.Elem Inline)))')
source='''{-# LANGUAGE OverloadedStrings #-}
-- Controlled observations evaluate the actual pinned Builder/containers.
import qualified Text.Pandoc.Builder as P
import Text.Pandoc.Definition
import qualified Data.Text as T
import qualified Data.Foldable as F
import qualified Data.Sequence as S
import qualified Data.Sequence.Internal as SI
import Data.Aeson
import qualified Data.ByteString.Lazy.Char8 as B
import Control.Exception
import System.IO
bad=error
root x=x `seq` "root"
ils=P.fromList[Str "a"]
bs=P.fromList[Plain []]
txt=T.pack "a"
capt=Caption Nothing[]
probes :: [(String,String)]
probes=[\n'''+',\n'.join(' ('+json.dumps(n)+','+e+')'for n,e in probes)+''']
main=do
 hSetBuffering stdout LineBuffering
 mapM_ respond probes
 where
  respond(name,value)=do
   result<-try(evaluate(let bytes=encode value in B.length bytes `seq` bytes))::IO(Either SomeException B.ByteString)
   B.putStrLn(encode name)
   B.putStrLn(either(encode.object.pure.("exception" .=).takeWhile(/='\\n').displayException)id result)
'''
(H/'BuilderDemand.hs').write_text(source)
(H/'demand-inputs.json').write_text(json.dumps({'constructors':args,'probe_names':[n for n,_ in probes]},indent=2)+'\n')
print(len(probes),'source demand expressions')
