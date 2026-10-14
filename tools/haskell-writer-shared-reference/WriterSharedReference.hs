{-# LANGUAGE OverloadedStrings, ScopedTypeVariables #-}
module Main where
import Data.Aeson
import qualified Data.ByteString.Lazy.Char8 as L
import qualified Data.ByteString as B
import qualified Data.Text as T
import qualified Data.Set as Set
import Data.Char (chr,ord)
import Data.Maybe (isJust)
import Text.Pandoc.Definition
import Text.Pandoc.Walk
import qualified Text.DocLayout as D
import qualified PandocOptionsModels as O
import qualified TemplateAdapter as A
import qualified SourceWriterShared as W
import Control.Exception (SomeException,try,evaluate,displayException)
import System.IO (hSetBuffering,stdout,BufferMode(LineBuffering))
as :: FromJSON a => Value -> a
as=A.as
arr=A.arr
j :: ToJSON a => a -> Value
j=toJSON
opts old=O.WriterOptions{O.writerExtensions=O.Extensions(Set.fromList[O.Ext_old_dashes | old])}
observeDoc d=object["doc" .= A.encDoc d,"render" .= D.render Nothing d,"wrapped" .= D.render(Just 17)d,"offset" .= D.offset d,"minOffset" .= D.minOffset d]
task bs=object["maybe" .= (W.toTaskListItem bs::Maybe(Bool,[Block])),"list" .= (W.toTaskListItem bs::[(Bool,[Block])])]
inline v=let i=as v::Inline in object["display" .= W.isDisplayMath i,"sub" .= W.toSubscriptInline i,"super" .= W.toSuperscriptInline i,"remove" .= W.removeLinks[i]]
block v=let b=as v::Block in object["math" .= W.fixDisplayMath b,"ends" .= W.endsWithPlain[b],"task" .= task[b]]
xmlAccept c=let s=T.singleton c;p=Pandoc mempty[Plain[Code(s,[],[])"x"]]
            in case W.ensureValidXmlIdentifiers p of Pandoc _ [Plain[Code(s',_,_)_]]->s'==s;_->error "xml probe"
initialAccept c=let d=D.Text 2(T.singleton c<>".")<>D.BreakingSpace
                in A.encDoc(W.splitSentences d)==A.encDoc d
peel (Span a [i])=let (attrs,leaf)=peel i in (a:attrs,leaf)
peel i=([],i)
deepInline :: Int -> Bool -> Inline
deepInline depth bad=foldr (\n i->Span(T.pack(show n),["depth"],[("x","y")])[i]) (if bad then Str "x" else Str "12+") [1..depth]
process :: (T.Text,Value) -> Value
process (op,v)=case op of
 "char-range" -> case arr v of [start,end]->j[j[j(fmap ord(W.toSubscript c)),j(fmap ord(W.toSuperscript c)),j(xmlAccept c),j(initialAccept c)] | n<-[as start..as end-1],let c=chr n];_->error "range"
 "inline" -> inline v
 "block" -> block v
 "inlines" -> let xs=as v::[Inline] in object["strip" .= W.stripLeadingTrailingSpace xs,"remove" .= W.removeLinks xs]
 "blocks" -> let xs=as v::[Block] in object["ends" .= W.endsWithPlain xs,"task" .= task xs]
 "document" -> let p=as v::Pandoc in object["xml" .= W.ensureValidXmlIdentifiers p,"math" .= walk W.fixDisplayMath p]
 "unsmartify" -> case arr v of [old,points]->j(map ord(T.unpack(W.unsmartify(opts(as old))(T.pack(map chr(as points))))));_->error "unsmartify"
 "layout" -> case arr v of [open,close,d]->let x=A.doc d in object["delimited" .= observeDoc(W.delimited(A.doc open)(A.doc close)x),"sentences" .= observeDoc(W.splitSentences x)];_->error "layout"
 "layout-structure" -> case arr v of [open,close,d]->let x=A.doc d in j[A.encDoc(W.delimited(A.doc open)(A.doc close)x),A.encDoc(W.splitSentences x)];_->error "layout structure"
 "deep-inline" -> case arr v of [n,bad]->let i=deepInline(as n)(as bad) in object["sub" .= fmap peel(W.toSubscriptInline i),"super" .= fmap peel(W.toSuperscriptInline i)];_->error "deep inline"
 "deep-ends" -> case arr v of [n,success]->j(W.endsWithPlain(foldr (\_ bs->[BulletList[bs]]) [if as success then Plain[] else Para[]] [1..as n::Int]));_->error "deep ends"
 "deep-layout" -> case arr v of
   [n,left]->
     let xs=take(as n)(cycle[D.BreakingSpace,D.CarriageReturn,D.BlankLines 2])
         d=if as left then foldl(<>)mempty xs else foldr(<>)mempty xs
     in object["delimited" .= D.render Nothing(W.delimited "[" "]" d),"sentences" .= D.render Nothing(W.splitSentences d)]
   _->error "deep layout"
 "partial" -> case as v::T.Text of
   "script-stop" -> j(W.toSubscriptInline(Span nullAttr[Str "unsupported",error "later inline"]))
   "plain-last" -> j(W.endsWithPlain[error "earlier block",Plain[error "plain contents"]])
   "task-zero" -> task [HorizontalRule,error "later block"]
   "task-success" -> j(fmap fst(W.toTaskListItem [Plain[Str "☐",Space,error "contents"],error "later block"]::Maybe(Bool,[Block])))
   "unsmartify-no-extension" -> j(W.unsmartify(error "options")"no smart dashes")
   "display-direct" -> j(W.isDisplayMath(Math DisplayMath ""))
   "display-span" -> j(W.isDisplayMath(Span nullAttr[Math DisplayMath ""]))
   "math-all" -> j(W.fixDisplayMath(Para[Math DisplayMath "x",Math DisplayMath "y"]))
   _->error "partial"
 _->error "operation"
observe x=do
 r<-try(evaluate(L.toStrict(encode x)))::IO(Either SomeException B.ByteString)
 pure$case r of Left e->object["error" .= takeWhile(/='\n')(displayException e)];Right b->either error id(eitherDecode(L.fromStrict b))
main=do
 hSetBuffering stdout LineBuffering
 L.getContents >>= mapM_(\line->case eitherDecode line of Right req->observe(process req)>>=L.putStrLn.encode;Left e->error e).L.lines
