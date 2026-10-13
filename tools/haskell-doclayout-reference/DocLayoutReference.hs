{-# LANGUAGE OverloadedStrings, ScopedTypeVariables #-}
module Main where
import qualified Text.DocLayout as D
import qualified Text.DocLayout.ANSIFont as F
import qualified Text.DocLayout.Attributed as A
import qualified Text.DocLayout.HasChars as H
import Data.Aeson
import qualified Data.ByteString.Lazy.Char8 as B
import qualified Data.ByteString as S
import qualified Data.Text as T
import qualified Data.Text.Lazy as TL
import qualified Data.Text.Lazy.Builder as TB
import Data.Foldable(toList)
import qualified Data.Vector as V
import Data.Char(chr,ord)
import Test.QuickCheck(arbitrary,Gen)
import Test.QuickCheck.Gen(unGen)
import Test.QuickCheck.Random(mkQCGen)
import System.Environment(getArgs)
import Control.Exception(SomeException,try,evaluate,displayException)
import Control.Monad.State.Strict(State,runState,state)

as :: FromJSON a => Value -> a
as v=case fromJSON v of Success x->x;Error e->error e
arr (Array a)=V.toList a
arr _=error "expected array"
style v=case arr v of
 [String "weight",x]->F.RWeight(if as x then F.Bold else F.Normal)
 [String "shape",x]->F.RShape(if as x then F.Italic else F.Roman)
 [String "underline",x]->F.RUnderline([F.ULNone,F.ULSingle,F.ULDouble,F.ULCurly]!!as x)
 [String "strikeout",x]->F.RStrikeout(if as x then F.Struck else F.Unstruck)
 [String "fg",x]->F.RForeground(maybe F.FGDefault (F.FG . toEnum)(as x))
 [String "bg",x]->F.RBackground(maybe F.BGDefault (F.BG . toEnum)(as x))
 _->error "style"
font v=case arr v of
 [w,s,u,k,fg,bg,l]->F.Font (if as w then F.Bold else F.Normal) (if as s then F.Italic else F.Roman)
  ([F.ULNone,F.ULSingle,F.ULDouble,F.ULCurly]!!as u) (if as k then F.Struck else F.Unstruck)
  (maybe F.FGDefault (F.FG . toEnum)(as fg)) (maybe F.BGDefault (F.BG . toEnum)(as bg)) (as l)
 _->error "font"
attr :: Value -> A.Attr T.Text
attr v=case arr v of [l,f,x]->A.Attr(as l)(font f)(as x);_->error "attr"
attributed :: Value -> A.Attributed T.Text
attributed = A.fromList . map attr . arr
encodeFont f=toJSON [toJSON(F.ftWeight f==F.Bold),toJSON(F.ftShape f==F.Italic),toJSON(underline(F.ftUnderline f)),
 toJSON(F.ftStrikeout f==F.Struck),toJSON(foreground(F.ftForeground f)),toJSON(background(F.ftBackground f)),toJSON(F.ftLink f)]
 where underline u=case u of F.ULNone->0::Int;F.ULSingle->1;F.ULDouble->2;F.ULCurly->3
       foreground F.FGDefault=Nothing;foreground(F.FG c)=Just(fromEnum c)
       background F.BGDefault=Nothing;background(F.BG c)=Just(fromEnum c)
encodeAttr(A.Attr l f x)=toJSON[toJSON l,encodeFont f,toJSON x]
encodeAttributed(A.Attributed s)=toJSON(map encodeAttr(toList s))
doc :: Value -> D.Doc T.Text
doc (String s)=D.literal s
doc v=case arr v of
 [String "empty"]->D.Empty
 [String "space"]->D.space
 [String "cr"]->D.cr
 [String "newline"]->D.NewLine
 [String "blanklines",n]->D.blanklines(as n)
 [String "raw-text",n,s]->D.Text(as n)(as s)
 [String "block",n,ls]->D.Block(as n)(map attributed(arr ls))
 [String "vfill",n,s]->D.VFill(as n)(as s)
 [String "cooked",n,s]->D.CookedText(as n)(attributed s)
 [String "prefixed",s,d]->D.Prefixed(as s)(doc d)
 [String "before",d]->D.BeforeNonBlank(doc d)
 [String "flush",d]->D.Flush(doc d)
 [String "after",s]->D.AfterBreak(as s)
 [String "concat",a,b]->D.Concat(doc a)(doc b)
 [String "append",a,b]->doc a <> doc b
 [String "spaced",a,b]->doc a D.<+> doc b
 [String "above",a,b]->doc a D.$$ doc b
 [String "separated",a,b]->doc a D.$+$ doc b
 [String "styled",s,d]->D.Styled(style s)(doc d)
 [String "linked",s,d]->D.Linked(as s)(doc d)
 [String "nowrap",d]->D.nowrap(doc d)
 [String "chomp",d]->D.chomp(doc d)
 [String "nestle",d]->D.nestle(doc d)
 [String "nest",n,d]->D.nest(as n)(doc d)
 [String "hang",n,a,b]->D.hang(as n)(doc a)(doc b)
 [String "lblock",n,d]->D.lblock(as n)(doc d)
 [String "rblock",n,d]->D.rblock(as n)(doc d)
 [String "cblock",n,d]->D.cblock(as n)(doc d)
 [String "fill",s]->D.vfill(as s)
 [String "hcat",ds]->D.hcat(map doc(arr ds))
 [String "hsep",ds]->D.hsep(map doc(arr ds))
 [String "vcat",ds]->D.vcat(map doc(arr ds))
 [String "vsep",ds]->D.vsep(map doc(arr ds))
 [String "braces",d]->D.braces(doc d)
 [String "brackets",d]->D.brackets(doc d)
 [String "parens",d]->D.parens(doc d)
 [String "quotes",d]->D.quotes(doc d)
 [String "doubleQuotes",d]->D.doubleQuotes(doc d)
 _->error ("doc "++show v)
widths xs=let s=map chr xs in [D.realLength s,D.realLengthWideContext s,D.realLengthNarrowContextNoShortcut s,D.realLengthWideContextNoShortcut s]
process ("width",v)=toJSON(widths(as v))
process ("chars",v)=toJSON [[D.charWidth(chr c)]++widths[c]++map fromEnum[D.isSkinToneModifier(chr c),D.isEmojiVariation(chr c),D.isZWJ(chr c)] | c<-as v]
process ("samples",v)=toJSON [map ord (unGen (arbitrary::Gen String) (mkQCGen(seed+i)) (i `mod` 101)) | i<-[0..count-1]]
 where [seed,count]=as v
process ("font",v)=case arr v of
 [f,ss]->let out=foldl(F.~>)(font f)(map style(arr ss)) in object["font" .= encodeFont out,"render" .= (F.renderFont out::T.Text)]
 _->error "font request"
process ("attr-append",v)=case arr v of [a,b]->encodeAttr(attr a <> attr b);_->error "attr append"
process ("attributed",v)=let a=attributed v in object[
 "right" .= H.foldrChar(\c xs->ord c:xs)[] a,"left" .= H.foldlChar(\xs c->ord c:xs)[] a,
 "null" .= H.isNull a,"split" .= map encodeAttributed(H.splitLines a),
 "build" .= TL.toStrict(TB.toLazyText(H.build a))]
process ("rich-doc",v)=case arr v of
 [a,operation,ws]->let d0=D.literal(attributed a);d=transform(as operation)d0 in object[
  "offset" .= D.offset d,"minOffset" .= D.minOffset d,"height" .= D.height d,
  "renders" .= [[encodeAttributed(D.render w d),toJSON(TL.toStrict(D.renderANSI w d))] | w<-as ws]]
 _->error "rich doc"
process ("part-doc",v)=case arr v of
 [a,operation,ws]->let d0=D.literal(attr a);d=transform(as operation)d0 in object[
  "offset" .= D.offset d,"minOffset" .= D.minOffset d,"height" .= D.height d,
  "renders" .= [[encodeAttr(D.render w d),toJSON(TL.toStrict(D.renderANSI w d))] | w<-as ws]]
 _->error "part doc"
process ("string-doc",v)=case arr v of
 [cs,operation,ws]->let points=as cs;d0=D.literal(map chr points)::D.Doc String;d=transform(as operation)d0 in object[
  "offset" .= D.offset d,"minOffset" .= D.minOffset d,"height" .= D.height d,
  "renders" .= [[toJSON(map ord(D.render w d)),toJSON(TL.toStrict(D.renderANSI w d))] | w<-as ws]]
 _->error "string doc"
process ("doc",v)=case arr v of
 [d,ws,k]->let x=doc d in object["offset" .= D.offset x,"minOffset" .= D.minOffset x,"height" .= D.height x,
   "column" .= D.updateColumn x (as k),"empty" .= D.isEmpty x,
   "renders" .= [[D.render w x,TL.toStrict(D.renderANSI w x)] | w<-as ws]]
 _->error "doc request"
process ("measure",v)=case arr v of
 [d,k]->let x=doc d in object["offset" .= D.offset x,"minOffset" .= D.minOffset x,"column" .= D.updateColumn x(as k)]
 _->error "measure request"
process ("walk",v)=let d=doc v;m=fmap T.reverse d;(t,(_,effects))=runState(traverse step d)(0::Int,[]) in object[
 "values" .= toList d,"mappedValues" .= toList m,"traversedValues" .= toList t,"effects" .= reverse effects,
 "mappedRenders" .= [[D.render w m,TL.toStrict(D.renderANSI w m)] | w<-[Nothing,Just 4,Just 10]],
 "traversedRenders" .= [[D.render w t,TL.toStrict(D.renderANSI w t)] | w<-[Nothing,Just 4,Just 10]]]
 where step :: T.Text -> State (Int,[T.Text]) T.Text
       step x=state $ \(n,xs)->(T.pack(show n)<>":"<>x,(n+1,x:xs))
process _=error "operation"
transform :: D.HasChars a => String -> D.Doc a -> D.Doc a
transform op d=case op of
 "identity"->d;"lblock"->D.lblock 3 d;"rblock"->D.rblock 4 d;"cblock"->D.cblock 5 d
 "prefix"->D.prefixed "> " d;"bold"->D.bold d;"link"->D.link "https://x" d
 "nowrap"->D.nowrap d;"chomp"->D.chomp d;"nestle"->D.nestle d
 _->error "transform"
main=do
 args<-getArgs
 if args==["--reference-info"] then B.putStrLn(encode["doclayout-0.6","emojis-0.1.5"])
 else B.getContents >>= mapM_ respond . B.lines
 where respond s=do
        let req=case eitherDecode s of Right a->a;Left e->error e
        result<-try(evaluate(B.toStrict(encode(process req))))::IO(Either SomeException S.ByteString)
        case result of Left e->B.putStrLn(encode(object["exception" .= takeWhile(/='\n')(displayException e)]));Right b->B.putStrLn(B.fromStrict b)
