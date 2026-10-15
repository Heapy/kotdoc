{-# LANGUAGE OverloadedStrings, ScopedTypeVariables #-}
-- Observation/serialization only; actual complete original XML/texmath libraries.
module Main where
import qualified MathWire as W
import Text.TeXMath.Readers.MathML(readMathML)
import Text.TeXMath.Readers.MathML.EntityMap(getUnicode)
import Text.XML.Light.Input
import Text.XML.Light.Lexer
import qualified Text.XML.Light.Lexer as X
import Text.XML.Light.Types
import Text.Show.Pretty(ppShow)
import Data.Aeson
import Data.Char(chr,ord)
import qualified Data.Text as T
import qualified Data.ByteString as B
import qualified Data.ByteString.Char8 as C
import qualified Data.ByteString.Lazy as BL
import qualified Data.ByteString.Lazy.Char8 as L
import qualified Control.Exception as E
import Control.Monad(unless)
import Data.Binary.Put
import Crypto.Hash(SHA256,Digest,hashlazy)
import System.IO
points=toJSON . map ord
qv q=toJSON[points(qName q),maybe Null points(qURI q),maybe Null points(qPrefix q)]
av a=toJSON[qv(attrKey a),points(attrVal a)]
lv=maybe Null (toJSON . show)
cv (Elem e)=toJSON[String "element",qv(elName e),toJSON(map av(elAttribs e)),toJSON(map cv(elContent e)),lv(elLine e)]
cv (Text c)=toJSON[String "text",toJSON(show(cdVerbatim c)),points(cdData c),lv(cdLine c)]
cv (CRef s)=toJSON[String "ref",points s]
tv (TokStart p n as b)=toJSON[String "start",toJSON(show p),qv n,toJSON(map av as),toJSON b]
tv (TokEnd p n)=toJSON[String "end",toJSON(show p),qv n]
tv (TokText c)=toJSON[String "text",toJSON(show(cdVerbatim c)),points(cdData c),lv(cdLine c)]
tv (TokCRef s)=toJSON[String "ref",points s]
raw v=map(chr . W.as)(W.arr v)
readResult s=case readMathML s of
 Left e->toJSON[String "Left",toJSON e]
 Right es->toJSON[String "Right",toJSON(map W.ev es)]
lsv xs=toJSON[[toJSON(show l),toJSON(ord c)]|(l,c)<-xs]
response v=case W.arr v of
 [String "read",s]->pure(readResult(W.str s))
 [String "read-show",s]->pure(toJSON(either ("L"<>) (T.pack . ("R"++) . show)(readMathML(W.str s))))
 [String "golden",s]->pure(toJSON(either (\e->e) (T.pack . ppShow) (readMathML(W.str s))))
 [String "native",s]->pure(toJSON(map W.ev (read(T.unpack(W.str s)))))
 [String "entities",keys]->pure(toJSON[ [toJSON k,maybe Null toJSON(getUnicode k)] | x<-W.arr keys,let k=W.str x])
 [String "xml",s]->let x=raw s in pure(object["tokens" .= map tv(tokens x),"contents" .= map cv(parseXML x),"root" .= fmap (cv . Elem)(parseXMLDoc x)])
 [String "xml-text",s]->let x=W.str s in pure(object["tokens" .= map tv(tokens x),"contents" .= map cv(parseXML x),"root" .= fmap (cv . Elem)(parseXMLDoc x)])
 [String "xml-bytes",s]->let x=B.pack(map W.as(W.arr s)) in pure(object["tokens" .= map tv(tokens x),"contents" .= map cv(parseXML x),"root" .= fmap (cv . Elem)(parseXMLDoc x)])
 [String "xml-scanner",s]->let x=customScanner X.uncons(raw s) in pure(object["tokens" .= map tv(tokens x),"contents" .= map cv(parseXML x),"root" .= fmap (cv . Elem)(parseXMLDoc x)])
 [String "scanner-first"]->let next False=Just('A',True);next True=error "forced scanner tail" in pure(toJSON(fmap (ord . fst)(X.uncons(customScanner next False))))
 [String "lex-utils",s]->let x=raw s in pure(object["decoded" .= [case t of TxtBit a->toJSON[String "text",points a];CRefBit a->toJSON[String "ref",points a]|t<-decode_text x],"attribute" .= points(decode_attr x),"reference" .= fmap ord(cref_to_char x),"numeric" .= fmap ord(num_esc x)])
 [String "lexer",first,s]->do
  let ls=linenumber(read(W.as first))(raw s)
      pair f g=let(a,b)=f ls in toJSON[g a,lsv b]
      (as,empty,ts)=attribs ls
  pure(object["lines" .= lsv ls,"tokens" .= map tv(tokens' ls),"name" .= pair qualName qv,"attr" .= pair attrib av,
    "value" .= pair attr_val points,"string" .= pair string points,"space" .= lsv(dropSpace ls),
    "tag" .= map tv(tag ls),"attrs" .= toJSON[toJSON(map av as),toJSON empty,toJSON(map tv ts)],
    "special" .= map tv(special(read(W.as first),'!')ls),"break" .= pair (breakn(=='&')) points,"breakAfter" .= pair (break'(=='&')) points])
 [String "read-range",first,count]->do
  let outputs=[either ("L"<>) (T.pack . ("R"++) . show)(readMathML("<math><mi>"<>T.singleton(chr n)<>"</mi></math>"))|n<-[W.as first..W.as first+W.as count-1]]
      bytes=runPut(mapM_(\t->putWord32be(fromIntegral(T.length t)) >> mapM_(putWord32be.fromIntegral.ord)(T.unpack t))outputs)
  pure(object["sha" .= show(hashlazy bytes::Digest SHA256),"success" .= length(filter(T.isPrefixOf "R")outputs),"failure" .= length(filter(T.isPrefixOf "L")outputs)])
 _->error "request"
main=loop where
 loop=do
  end<-isEOF
  unless end $ do
   line<-C.getLine
   result<-E.try $ do
    value<-response(either error id(eitherDecode(BL.fromStrict line)))
    E.evaluate(BL.length(encode value));pure value
   L.putStrLn(encode(either (\(e::E.SomeException)->object["host" .= show e]) id result));hFlush stdout;loop
