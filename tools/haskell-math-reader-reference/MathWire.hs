{-# LANGUAGE OverloadedStrings, ScopedTypeVariables, TupleSections #-}
module MathWire where
import Text.TeXMath.Types
import Text.TeXMath.Shared
import Text.TeXMath.TeX
import Text.TeXMath.Unicode.ToUnicode
import Text.TeXMath.Writers.Pandoc
import Text.TeXMath.Readers.TeX(readTeX)
import Text.TeXMath.Readers.MathML(readMathML)
import Text.TeXMath.Readers.OMML(readOMML)
import Data.Aeson
import qualified Data.Vector as V
import qualified Data.Text as T
import qualified Data.ByteString as B
import qualified Data.ByteString.Lazy as BL
import qualified Data.ByteString.Lazy.Char8 as C
import Data.Char(chr,ord)
import Data.Ratio
import Data.List(sort)
import Data.Maybe(fromMaybe)
import Control.Monad(unless)
import qualified Control.Exception as E
import System.IO
import Text.Read(readMaybe)
import Text.Show.Pretty(ppShow)
as :: FromJSON a=>Value->a
as v=case fromJSON v of Success a->a;Error e->error e
arr (Array v)=V.toList v
arr _=error "array"
rat v=case arr v of [a,b]->read(as a)%read(as b);_->error "ratio"
rval x=toJSON[show(numerator x),show(denominator x)]
val x=String(T.pack(show x))
str :: Value->T.Text
str (Array v)=T.pack(map(chr.as)(V.toList v))
str v=as v
expr v=case arr v of
 [String "ENumber",a]->ENumber (str $ a)
 [String "EGrouped",a]->EGrouped (map expr . arr $ a)
 [String "EDelimited",a,b,c]->EDelimited (str $ a) (str $ b) (map delim . arr $ c)
 [String "EIdentifier",a]->EIdentifier (str $ a)
 [String "EMathOperator",a]->EMathOperator (str $ a)
 [String "ESymbol",a,b]->ESymbol (read . as $ a) (str $ b)
 [String "ESpace",a]->ESpace (rat $ a)
 [String "ESub",a,b]->ESub (expr $ a) (expr $ b)
 [String "ESuper",a,b]->ESuper (expr $ a) (expr $ b)
 [String "ESubsup",a,b,c]->ESubsup (expr $ a) (expr $ b) (expr $ c)
 [String "EOver",a,b,c]->EOver (as $ a) (expr $ b) (expr $ c)
 [String "EUnder",a,b,c]->EUnder (as $ a) (expr $ b) (expr $ c)
 [String "EUnderover",a,b,c,d]->EUnderover (as $ a) (expr $ b) (expr $ c) (expr $ d)
 [String "EPhantom",a]->EPhantom (expr $ a)
 [String "EBoxed",a]->EBoxed (expr $ a)
 [String "ECancel",a,b]->ECancel (read . as $ a) (expr $ b)
 [String "EFraction",a,b,c]->EFraction (read . as $ a) (expr $ b) (expr $ c)
 [String "ERoot",a,b]->ERoot (expr $ a) (expr $ b)
 [String "ESqrt",a]->ESqrt (expr $ a)
 [String "EScaled",a,b]->EScaled (rat $ a) (expr $ b)
 [String "EArray",a,b]->EArray (map (read . as) . arr $ a) (map (map (map expr . arr) . arr) . arr $ b)
 [String "EText",a,b]->EText (read . as $ a) (str $ b)
 [String "EStyled",a,b]->EStyled (read . as $ a) (map expr . arr $ b)
 _->error "expression"
delim v=case arr v of [String "Left",x]->Left(str x);[String "Right",x]->Right(expr x);_->error "delimiter"
ev e=case e of
 ENumber a->toJSON[String "ENumber",(toJSON $ a)]
 EGrouped a->toJSON[String "EGrouped",(toJSON . map ev $ a)]
 EDelimited a b c->toJSON[String "EDelimited",(toJSON $ a),(toJSON $ b),(toJSON . map dv $ c)]
 EIdentifier a->toJSON[String "EIdentifier",(toJSON $ a)]
 EMathOperator a->toJSON[String "EMathOperator",(toJSON $ a)]
 ESymbol a b->toJSON[String "ESymbol",(val $ a),(toJSON $ b)]
 ESpace a->toJSON[String "ESpace",(rval $ a)]
 ESub a b->toJSON[String "ESub",(ev $ a),(ev $ b)]
 ESuper a b->toJSON[String "ESuper",(ev $ a),(ev $ b)]
 ESubsup a b c->toJSON[String "ESubsup",(ev $ a),(ev $ b),(ev $ c)]
 EOver a b c->toJSON[String "EOver",(toJSON $ a),(ev $ b),(ev $ c)]
 EUnder a b c->toJSON[String "EUnder",(toJSON $ a),(ev $ b),(ev $ c)]
 EUnderover a b c d->toJSON[String "EUnderover",(toJSON $ a),(ev $ b),(ev $ c),(ev $ d)]
 EPhantom a->toJSON[String "EPhantom",(ev $ a)]
 EBoxed a->toJSON[String "EBoxed",(ev $ a)]
 ECancel a b->toJSON[String "ECancel",(val $ a),(ev $ b)]
 EFraction a b c->toJSON[String "EFraction",(val $ a),(ev $ b),(ev $ c)]
 ERoot a b->toJSON[String "ERoot",(ev $ a),(ev $ b)]
 ESqrt a->toJSON[String "ESqrt",(ev $ a)]
 EScaled a b->toJSON[String "EScaled",(rval $ a),(ev $ b)]
 EArray a b->toJSON[String "EArray",(toJSON . map show $ a),(toJSON . map (map (map ev)) $ b)]
 EText a b->toJSON[String "EText",(val $ a),(toJSON $ b)]
 EStyled a b->toJSON[String "EStyled",(val $ a),(toJSON . map ev $ b)]
dv (Left x)=toJSON[String "Left",toJSON x]
dv (Right x)=toJSON[String "Right",ev x]
tex v=case arr v of
 [String "ControlSeq",s]->ControlSeq(str s)
 [String "Literal",s]->Literal(str s)
 [String "Token",n]->Token(chr(as n))
 [String "Grouped",xs]->Grouped(map tex(arr xs))
 [String "Space"]->Space
 _->error "TeX"
tv (ControlSeq s)=toJSON[String "ControlSeq",toJSON s]
tv (Literal s)=toJSON[String "Literal",toJSON s]
tv (Token c)=toJSON[String "Token",toJSON(ord c)]
tv (Grouped xs)=toJSON[String "Grouped",toJSON(map tv xs)]
tv Space=toJSON[String "Space"]
rewrite e=case e of ENumber t->ENumber(t<>"!");EGrouped xs->EStyled TextBold xs;ESpace _->EIdentifier "s";_->e
rewriteList []=[ENumber "z"]
rewriteList (ENumber a:ENumber b:xs)=ENumber(a<>b):xs
rewriteList xs=xs
response req=case arr req of
 [String "parse",fmt,s]->do
  let result=case as fmt::String of
       "tex"->readTeX(str s);"mml"->readMathML(str s);"mathml"->readMathML(str s);"omml"->readOMML(str s)
       "native"->maybe(Left "native-read-error") Right(readMaybe(as s))
       _->Left "unsupported"
  pure(either (\e->toJSON[String "Left",toJSON e]) (\es->toJSON[String "Right",toJSON(map ev es)]) result)
 [String "expressions",xs]->do
  let es=map expr(arr xs)
  pure(object["show" .= show es,"show11" .= map (\e->showsPrec 11 e "") es,
    "fixed" .= map(ev.fixTree) es,"walk" .= map(ev.everywhereExp rewrite) es,
    "lists" .= map ev(everywhereExpList rewriteList es),
    "downInline" .= map(ev.handleDownup DisplayInline) es,"downBlock" .= map(ev.handleDownup DisplayBlock) es,
    "pandoc" .= fmap show(writePandoc DisplayInline es),"pandocBlock" .= fmap show(writePandoc DisplayBlock es),
    "primes" .= map toPrimes es,"operators" .= map(fmap tv.getOperator) es,
    "sorted" .= map ev(sort es),"ordering" .= [show(compare a b)|a<-take 12 es,b<-take 12 es]])
 [String "pandocGolden",xs]->pure(toJSON(fmap ppShow(writePandoc DisplayBlock(map expr(arr xs)))))
 [String "shared",rs,texts,cs,alignments]->do
  let ratios=map rat(arr rs);ts=map str(arr texts);characters=map(chr.as)(arr cs);als=map(map(read.as).arr)(arr alignments)
  pure(object["widths" .= map(fmap rval.getSpaceWidth) characters,
   "spaces" .= map getSpaceChars ratios,"scalerCommands" .= map getScalerCommand ratios,
   "scalerValues" .= map(fmap rval.getScalerValue) ts,"lengths" .= map(fmap rval.readLength) ts,
   "textTypes" .= map(show.getTextType) ts,"greek" .= map isUppercaseGreek ts,
   "diacriticsUnder" .= map(getDiacriticalCommand Under) ts,"diacriticsOver" .= map(getDiacriticalCommand Over) ts,
   "rl" .= map isRLSequence als])
 [String "styles",ts,envs]->do
  let types=map(read.as)(arr ts);environments=map(map str.arr)(arr envs)
  pure(object["mml" .= map getMMLType types,"commands" .= [[getLaTeXTextCommand env tt|tt<-types]|env<-environments]])
 [String "unicode",tt,cs]->do
  let t=read(as tt);characters=map(chr.as)(arr cs);s=T.pack characters
  pure(object["forward" .= map(fmap ord.toUnicodeChar.(t,)) characters,
   "reverse" .= map(fmap(\(a,b)->(show a,ord b)).fromUnicodeChar) characters,
   "to" .= toUnicode t s,"from" .= fromUnicode t s])
 [String "unicode-range",tt,first,count]->do
  let t=read(as tt);codes=[as first .. as first+as count-1]
  pure(object["forward" .= [(c,ord b)|c<-codes,Just b<-[toUnicodeChar(t,chr c)]],
    "reverse" .= [(c,show a,ord b)|c<-codes,Just(a,b)<-[fromUnicodeChar(chr c)]]])
 [String "escape-range",first,count]->do
  let codes=[as first .. as first+as count-1]
  pure(toJSON[(c,tv t,renderTeX t "rest")|c<-codes,let t=escapeLaTeX(chr c),t/=Token(chr c)])
 [String "tex",nodes,suffix]->pure(toJSON[renderTeX(tex t)(str suffix)|t<-arr nodes])
 [String "escape",cs]->pure(toJSON[object["node" .= tv(escapeLaTeX(chr(as c))),"out" .= renderTeX(escapeLaTeX(chr(as c))) "rest"]|c<-arr cs])
 [String "control",xs]->pure(toJSON(map(isControlSeq.str)(arr xs)))
 [String "tables"]->pure(object["diacriticals" .= diacriticals,"scalers" .= [(c,rval r)|(c,r)<-scalers],"environment" .= defaultEnv])
 [String "tex-meta",xs]->do
  let ts=map tex(arr xs)
  pure(object["show" .= show ts,"show11" .= map(\t->showsPrec 11 t "") ts,"eq" .= [a==b|a<-ts,b<-ts]])
 [String "records",xs]->pure(toJSON(map record (arr xs)))
  where
   record v=case arr v of
    [String "operator",a,b,c,d,e,f,g]->let r=Operator(str a)(str b)(case as c::String of "FPrefix"->FPrefix;"FPostfix"->FPostfix;_->FInfix)(read(as d))(read(as e))(read(as f))(map str(arr g)) in toJSON[show r,showsPrec 11 r ""]
    [String "unicode",a,b,c,d]->let r=Record(chr(as a))[(str x,str y)|z<-arr b,let[x,y]=arr z](read(as c))(str d) in toJSON[show r,showsPrec 11 r ""]
    _->error "record"
 _->error "request"
main=loop where
 loop=do
  end<-isEOF
  unless end $ do
   line<-B.getLine
   r<-E.try $ do
    v<-response(either error id(eitherDecode(BL.fromStrict line)))
    E.evaluate(BL.length(encode v));pure v
   C.putStrLn(encode(either(\(_::E.SomeException)->String "host") id r));hFlush stdout;loop
