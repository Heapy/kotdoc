{-# LANGUAGE OverloadedStrings, ScopedTypeVariables, NoMonomorphismRestriction #-}
module Main where
import Skylighting.Types
import Skylighting.Styles
import qualified Skylighting.Format.ANSI as A
import qualified Skylighting.Format.HTML as H
import qualified Skylighting.Format.LaTeX as X
import qualified Skylighting.Format.ConTeXt as C
import qualified Skylighting.Format.Typst as Y
import qualified Text.Blaze.Html.Renderer.Text as R
import Data.Aeson
import qualified Data.ByteString as B
import qualified Data.ByteString.Lazy.Char8 as L
import qualified Data.Text as T
import qualified Data.Text.Lazy as TL
import qualified Data.Map.Strict as M
import qualified Data.Vector as V
import qualified Data.Binary as Bin
import System.IO(hSetBuffering,stdout,BufferMode(LineBuffering))
import Control.Exception(SomeException,try,evaluate,displayException)
import Numeric(readHex)
as v=case fromJSON v of Success a->a;Error e->error e
arr (Array v)=V.toList v
arr _=error "array"
j=toJSON
hex=T.pack.concatMap (\w->["0123456789abcdef"!!fromIntegral(w `div` 16),"0123456789abcdef"!!fromIntegral(w `mod` 16)]).B.unpack
fromHex s=B.pack(go(T.unpack s)) where
 go []=[]
 go (a:b:rest)=case readHex[a,b] of [(n,"")]->n:go rest;_->error "hex"
 go _=error "hex"
opts v=case arr v of [n,s,a,t,c,k,p,l]->FormatOptions(as n)(as s)(as a)(as t)(as c)(as k)(as p)(read(as l));_->error "opts"
jo(FormatOptions n s a t c k p l)=j[j n,j s,j a,j t,j c,j k,j p,j(show l)]
color Null=Nothing
color v=case arr v of [r,g,b]->Just(RGB(as r)(as g)(as b));_->error "color"
jc Nothing=Null
jc(Just(RGB r g b))=j[j r,j g,j b]
ts v=case arr v of [c,b,w,i,u]->TokenStyle(color c)(color b)(as w)(as i)(as u);_->error "token-style"
jts(TokenStyle c b w i u)=j[jc c,jc b,j w,j i,j u]
style v=case arr v of [t,d,b,l,lb]->Style(M.fromList[(read(as k),ts x)|row<-arr t,let [k,x]=arr row])(color d)(color b)(color l)(color lb);_->error "style"
js(Style t d b l lb)=j[j[j[j(show k),jts v]|(k,v)<-M.toList t],jc d,jc b,jc l,jc lb]
lines_ v=[[(read(as t),as x)|row<-arr ln,let [t,x]=arr row]|ln<-arr v]
styles=[("pygments",pygments),("tango",tango),("espresso",espresso),("zenburn",zenburn),("kate",kate),("monochrome",monochrome),("breezedark",breezeDark),("haddock",haddock)]
format o s ls=object[
 "html-inline" .= TL.toStrict(R.renderHtml(H.formatHtmlInline o ls)),
 "html-block" .= TL.toStrict(R.renderHtml(H.formatHtmlBlock o ls)),
 "html4-block" .= TL.toStrict(R.renderHtml(H.formatHtml4Block o ls)),
 "latex-inline" .= X.formatLaTeXInline o ls,"latex-block" .= X.formatLaTeXBlock o ls,
 "context-inline" .= C.formatConTeXtInline o ls,"context-block" .= C.formatConTeXtBlock o ls,
 "typst-inline" .= Y.formatTypstInline o ls,"typst-block" .= Y.formatTypstBlock o ls,
 "ansi" .= A.formatANSI o s ls]
styleFormat s=object["css" .= H.styleToCss s,"latex" .= X.styleToLaTeX s,"context" .= C.styleToConTeXt s,"typst" .= Y.styleToTypst s]
enc :: Bin.Binary a=>a->Value
enc=j.hex.L.toStrict.Bin.encode
dec :: (Bin.Binary a,Show a)=>(a->Value)->Value->Value
dec observe v=case Bin.decodeOrFail(L.fromStrict(fromHex(as v))) of
 Left(rest,n,e)->object["error" .= e,"consumed" .= n,"remaining" .= hex(L.toStrict rest)]
 Right(rest,n,a)->object["value" .= observe a,"show" .= show a,"encoded" .= enc a,"consumed" .= n,"remaining" .= hex(L.toStrict rest)]
process(op,v)=case op::String of
 "format"->case arr v of [o,s,ls]->format(opts o)(style s)(lines_ ls);_->error "format"
 "ansi"->case arr v of [o,s,ls]->j(A.formatANSI(opts o)(style s)(lines_ ls));_->error "ansi"
 "style"->styleFormat(style v)
 "builtin"->case lookup(as v)styles of Just s->j[js s,styleFormat s];_->error "builtin"
 "opts"->object["value" .= jo(opts v),"show" .= show(opts v),"encoded" .= enc(opts v)]
 "opts-order"->case arr v of [a,b]->object["order" .= show(compare(opts a)(opts b)),"equal" .= (opts a==opts b)];_->error "order"
 "encode"->case arr v of
  [String "options",x]->enc(opts x)
  [String "level",x]->enc(read(as x)::ANSIColorLevel)
  [String "color",x]->enc(maybe(error "null color") id(color x))
  [String "token-style",x]->enc(ts x)
  [String "style",x]->enc(style x)
  _->error "encode"
 "decode"->case arr v of
  [String "options",x]->dec jo x
  [String "level",x]->dec (j.show::ANSIColorLevel->Value)x
  [String "color",x]->dec (jc.Just)x
  [String "token-style",x]->dec jts x
  [String "style",x]->dec js x
  _->error "decode"
 _->error "op"
main=do
 hSetBuffering stdout LineBuffering
 contents<-L.getContents
 mapM_ respond(L.lines contents)
 where respond line=do
        let request=either error id(eitherDecode line)
        result<-try(evaluate(L.toStrict(encode(process request))))::IO(Either SomeException B.ByteString)
        case result of
         Left e->L.putStrLn(encode(object["exception" .= takeWhile(/='\n')(displayException e)]))
         Right b->L.putStrLn(L.fromStrict b)
