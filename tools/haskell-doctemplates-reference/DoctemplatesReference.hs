{-# LANGUAGE OverloadedStrings, ScopedTypeVariables, FlexibleContexts #-}
module Main where
import Text.DocTemplates.Internal
import Text.DocTemplates.Parser(compileTemplate)
import Data.Text.Conversions(ToText(..),FromText(..))
import qualified Text.DocLayout as D
import qualified Text.DocLayout.Attributed as A
import qualified Text.DocLayout.ANSIFont as F
import Data.Aeson
import qualified Data.ByteString.Lazy.Char8 as B
import qualified Data.Text as T
import qualified Data.Text.Encoding as E
import qualified Data.Text.Lazy as TL
import qualified Data.Map as M
import qualified Data.Vector as V
import Data.Foldable(toList)
import Data.List(sort)
import Control.Monad.State.Strict(runState,state,State)
import Control.Exception(SomeException,try,evaluate,displayException)

as :: FromJSON a => Value -> a
as v=case fromJSON v of Success x->x;Error e->error e
arr (Array a)=V.toList a
arr _=error "expected array"

doc :: Value -> D.Doc T.Text
doc (String s)=D.literal s
doc v=case arr v of
 [String "empty"]->D.Empty
 [String "space"]->D.BreakingSpace
 [String "cr"]->D.CarriageReturn
 [String "newline"]->D.NewLine
 [String "blanklines",n]->D.BlankLines(as n)
 [String "vfill",n,s]->D.VFill(as n)(as s)
 [String "flush",d]->D.Flush(doc d)
 [String "raw",n,s]->D.Text(as n)(as s)
 [String "text",n,s]->D.Text(as n)(as s)
 [String "literal",s]->D.literal(as s)
 [String "concat",x,y]->D.Concat(doc x)(doc y)
 [String "hsep",xs]->D.hsep(map doc(arr xs))
 [String "vcat",xs]->D.vcat(map doc(arr xs))
 [String "prefix",p,d]->D.prefixed(as p)(doc d)
 [String "bold",d]->D.bold(doc d)
 [String "link",l,d]->D.link(as l)(doc d)
 [String "before",d]->D.beforeNonBlank(doc d)
 [String "after",s]->D.afterBreak(as s)
 [String "styled",r,d]->D.Styled(style r)(doc d)
 [String "block",w,xs]->D.Block(as w)(map (A.fromList . map part . arr)(arr xs))
 [String "cooked",w,xs]->D.CookedText(as w)(A.fromList(map part(arr xs)))
 _->error "doc"
 where part x=case arr x of [l,s]->A.Attr(as l) F.baseFont(as s);_->error "part"

style v=case arr v of
 [String "weight",b]->F.RWeight(if as b then F.Bold else F.Normal)
 [String "shape",b]->F.RShape(if as b then F.Italic else F.Roman)
 [String "strikeout",b]->F.RStrikeout(if as b then F.Struck else F.Unstruck)
 [String "underline",n]->F.RUnderline([F.ULNone,F.ULSingle,F.ULDouble,F.ULCurly]!!as n)
 [String "fg",n]->F.RForeground(maybe F.FGDefault (F.FG . toEnum)(as n))
 [String "bg",n]->F.RBackground(maybe F.BGDefault (F.BG . toEnum)(as n))
 _->error "style"
encStyle r=case r of
 F.RWeight w->toJSON[ String "weight",toJSON(w==F.Bold)]
 F.RShape s->toJSON[ String "shape",toJSON(s==F.Italic)]
 F.RStrikeout s->toJSON[ String "strikeout",toJSON(s==F.Struck)]
 F.RUnderline u->toJSON[ String "underline",toJSON(case u of F.ULNone->0::Int;F.ULSingle->1;F.ULDouble->2;F.ULCurly->3)]
 F.RForeground f->toJSON[String "fg",toJSON(case f of F.FGDefault->Nothing;F.FG c->Just(fromEnum c))]
 F.RBackground b->toJSON[String "bg",toJSON(case b of F.BGDefault->Nothing;F.BG c->Just(fromEnum c))]
encFont f=toJSON[toJSON(F.ftWeight f==F.Bold),toJSON(F.ftShape f==F.Italic),
 toJSON(case F.ftUnderline f of F.ULNone->0::Int;F.ULSingle->1;F.ULDouble->2;F.ULCurly->3),
 toJSON(F.ftStrikeout f==F.Struck),toJSON(case F.ftForeground f of F.FGDefault->Nothing;F.FG c->Just(fromEnum c)),
 toJSON(case F.ftBackground f of F.BGDefault->Nothing;F.BG c->Just(fromEnum c)),toJSON(F.ftLink f)]
encAttributed(A.Attributed a)=toJSON[ toJSON[toJSON l,encFont f,toJSON x] | A.Attr l f x<-toList a]
encDoc :: D.Doc T.Text -> Value
encDoc d=case d of
 D.Text w x->toJSON[String "text",toJSON w,toJSON x]
 D.Block w xs->toJSON[String "block",toJSON w,toJSON(map encAttributed xs)]
 D.VFill w x->toJSON[String "vfill",toJSON w,toJSON x]
 D.CookedText w x->toJSON[String "cooked",toJSON w,encAttributed x]
 D.Prefixed p x->toJSON[String "prefix",toJSON p,encDoc x]
 D.BeforeNonBlank x->toJSON[String "before",encDoc x]
 D.Flush x->toJSON[String "flush",encDoc x]
 D.BreakingSpace->toJSON[String "space"]
 D.AfterBreak x->toJSON[String "after",toJSON x]
 D.CarriageReturn->toJSON[String "cr"]
 D.NewLine->toJSON[String "newline"]
 D.BlankLines n->toJSON[String "blanklines",toJSON n]
 D.Concat x y->toJSON[String "concat",encDoc x,encDoc y]
 D.Styled r x->toJSON[String "styled",encStyle r,encDoc x]
 D.Linked l x->toJSON[String "link",toJSON l,encDoc x]
 D.Empty->toJSON[String "empty"]

pipe :: Value -> Pipe
pipe (String s)=case s of
 "pairs"->ToPairs;"uppercase"->ToUppercase;"lowercase"->ToLowercase;"length"->ToLength
 "reverse"->Reverse;"first"->FirstItem;"last"->LastItem;"rest"->Rest;"allbutlast"->AllButLast
 "chomp"->Chomp;"alpha"->ToAlpha;"roman"->ToRoman;"nowrap"->NoWrap;_->error "pipe"
pipe v=case arr v of
 [String "block",a,n,l,r]->Block([LeftAligned,Centered,RightAligned]!!as a)(as n)(Border(as l)(as r))
 _->error "block pipe"
encPipe p=case p of
 ToPairs->String "pairs";ToUppercase->String "uppercase";ToLowercase->String "lowercase";ToLength->String "length"
 Reverse->String "reverse";FirstItem->String "first";LastItem->String "last";Rest->String "rest"
 AllButLast->String "allbutlast";Chomp->String "chomp";ToAlpha->String "alpha";ToRoman->String "roman";NoWrap->String "nowrap"
 Block a n(Border l r)->toJSON[String "block",toJSON(case a of LeftAligned->0::Int;Centered->1;RightAligned->2),toJSON n,toJSON l,toJSON r]
variable v=case arr v of [ps,fs]->Variable(as ps)(map pipe(arr fs));_->error "variable"
encVariable(Variable ps fs)=toJSON[toJSON ps,toJSON(map encPipe fs)]
template :: Value -> Template T.Text
template v=case arr v of
 [String "empty"]->Empty
 [String "literal",d]->Literal(doc d)
 [String "interpolate",v]->Interpolate(variable v)
 [String "conditional",v,y,n]->Conditional(variable v)(template y)(template n)
 [String "iterate",v,b,s]->Iterate(variable v)(template b)(template s)
 [String "nested",t]->Nested(template t)
 [String "partial",fs,t]->Partial(map pipe(arr fs))(template t)
 [String "concat",x,y]->Concat(template x)(template y)
 [String "append",x,y]->template x<>template y
 _->error "template"
encTemplate t=case t of
 Empty->toJSON[String "empty"]
 Literal d->toJSON[String "literal",encDoc d]
 Interpolate v->toJSON[String "interpolate",encVariable v]
 Conditional v y n->toJSON[String "conditional",encVariable v,encTemplate y,encTemplate n]
 Iterate v b s->toJSON[String "iterate",encVariable v,encTemplate b,encTemplate s]
 Nested x->toJSON[String "nested",encTemplate x]
 Partial ps x->toJSON[String "partial",toJSON(map encPipe ps),encTemplate x]
 Concat x y->toJSON[String "concat",encTemplate x,encTemplate y]

runtime :: forall a. TemplateTarget a => Template a -> Context a -> Value
runtime t ctx=object["template" .= encTemplate(fmap toText t),"variables" .= getVariables t,"values" .= map toText(toList t),
 "rendered" .= observe(renderTemplate t ctx),"mappedValues" .= map toText(toList mapped),
 "mapped" .= observe(renderTemplate mapped ctx),"effects" .= effects,
 "traversedValues" .= map toText(toList traversed),"traversed" .= observe(renderTemplate traversed ctx)]
 where mapped :: Template a
       mapped=fmap (fromText . T.reverse . toText) t
       (traversed,effects)=runState(traverse step t)[]
       step :: a -> State [T.Text] a
       step x=state $ \xs -> (fromText(T.pack(show(length xs))<> ":"<>toText x),xs<>[toText x])
       observe :: D.Doc a -> Value
       observe d=object["doc" .= encDoc(fmap toText d),"offset" .= D.offset d,"minOffset" .= D.minOffset d,
          "height" .= D.height d,"columns" .= [D.updateColumn d i|i<-[-9,0,17]],
          "renders" .= [[toText(D.render w d),TL.toStrict(D.renderANSI w d)]|w<-[Nothing,Just 0,Just 1,Just 3,Just 10,Just 80]]]

val :: Value -> Val T.Text
val v=case arr v of
 [String "json",j]->as j
 [String "simple",d]->SimpleVal(doc d)
 [String "list",xs]->ListVal(map val(arr xs))
 [String "map",xs]->MapVal(Context(M.fromList(map pair(arr xs))))
 [String "bool",x]->BoolVal(as x)
 [String "null"]->NullVal
 _->error "val"
 where pair x=case arr x of [k,v]->(as k,val v);_->error "pair"

describe :: Val T.Text -> Value
describe v=object ["json" .= toJSON v,"values" .= toList v,
 "text" .= (fromVal v::Maybe T.Text),"doc" .= fmap (D.render Nothing)(fromVal v::Maybe(D.Doc T.Text)),
 "list" .= (fromVal v::Maybe[T.Text]),"nestedList" .= (fromVal v::Maybe[[T.Text]]),
 "context" .= toJSON(toContext v::Context T.Text),
 "mappedJson" .= toJSON mapped,"mappedValues" .= toList mapped,
 "effects" .= effects,"traversedJson" .= toJSON traversed,
 "traversedValues" .= toList traversed,
 "renders" .= renders v,"mappedRenders" .= renders mapped,"traversedRenders" .= renders traversed]
 where mapped=fmap T.reverse v
       (traversed,effects)=runState(traverse step v)[]
       step :: T.Text -> State [T.Text] T.Text
       step x=state $ \xs -> (T.pack(show(length xs))<> ":"<>x,xs<>[x])

renders :: Val T.Text -> Value
renders (SimpleVal d)=toJSON [[D.render w d,TL.toStrict(D.renderANSI w d)] | w<-[Nothing,Just 3,Just 10]]
renders (ListVal xs)=toJSON(map renders xs)
renders (MapVal(Context m))=toJSON(M.map renders m)
renders _=Null

operation :: T.Text -> Value -> Value
operation "value" input=describe(val input)
operation "runtime" input=case arr input of
 [t,c]->runtime(template t)(toContext(val c))
 _->error "runtime input"
operation "runtime-string" input=case arr input of
 [t,c]->runtime(fmap T.unpack(template t))(fmap T.unpack(toContext(val c)))
 _->error "runtime String input"
operation "variable" input=case arr input of
 [x,y]->let a=variable x;b=variable y in object["append" .= encVariable(a<>b),"order" .= show(compare a b)]
 _->error "variable input"
operation "pipe-order" input=let ps=map pipe(arr input) in object[
 "sorted" .= map encPipe(sort ps),"orders" .= [[show(compare x y)|y<-ps]|x<-ps]]
operation "json" input=object ["value" .= describe(as input),
 "decodeContext" .= result (fromJSON input::Result(Context T.Text)),
 "toContext" .= toJSON(toContext input::Context T.Text)]
 where result (Success c)=object ["success" .= toJSON c]
       result (Error e)=object ["error" .= e]
operation "json-text" input=case eitherDecode(B.fromStrict(E.encodeUtf8(as input))) of
 Right v->operation "json" v
 Left e->error e
operation "union" input=case arr input of
 [x,y]->let c=toContext(val x)<>toContext(val y)::Context T.Text
            ks=M.keys(unContext c)
        in object ["json" .= toJSON c,"keys" .= ks,"values" .= toList c,
           "lookup" .= [object ["key" .= k,"present" .= M.member k (unContext c),"val" .= (lookupContext k c::Maybe(Val T.Text)),
                         "text" .= (lookupContext k c::Maybe T.Text),
                         "list" .= (lookupContext k c::Maybe[T.Text])] | k<-ks<>["missing"]]]
 _->error "union"
operation "conversion" input=case arr input of
 [String "text",x]->describe(toVal(as x::T.Text))
 [String "string",x]->toJSON(toVal(as x::String)::Val String)
 [String "texts",x]->describe(toVal(as x::[T.Text]))
 [String "strings",x]->toJSON(toVal(as x::[String])::Val String)
 [String "bool",x]->describe(toVal(as x::Bool))
 [String "map",x]->describe(toVal(as x::M.Map T.Text T.Text))
 _->error "conversion"
operation op _=error(T.unpack op)

main :: IO ()
main=do
 inp<-B.getContents
 mapM_ run(B.lines inp)
 where run line=do
        let Just v=decode line
            [String op,input]=arr v
        r<-try(do
          result<-if op=="compile" then case arr input of
            [path,text,c]->do
              compiled<-compileTemplate(as path)(as text)::IO(Either String(Template T.Text))
              return $ case compiled of Left e->object["error" .= e];Right t->runtime t(toContext(val c))
            _->error "compile input"
           else return(operation op input)
          evaluate(force result))::IO(Either SomeException B.ByteString)
        B.putStrLn $ case r of Left e->encode(object["exception" .= displayException e]);Right b->b
       force x=let b=encode x in B.length b `seq` b
