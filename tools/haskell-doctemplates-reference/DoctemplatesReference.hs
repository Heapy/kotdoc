{-# LANGUAGE OverloadedStrings, ScopedTypeVariables #-}
module Main where
import Text.DocTemplates.Internal
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
 [String "raw",n,s]->D.Text(as n)(as s)
 [String "literal",s]->D.literal(as s)
 [String "concat",x,y]->D.Concat(doc x)(doc y)
 [String "hsep",xs]->D.hsep(map doc(arr xs))
 [String "vcat",xs]->D.vcat(map doc(arr xs))
 [String "prefix",p,d]->D.prefixed(as p)(doc d)
 [String "bold",d]->D.bold(doc d)
 [String "link",l,d]->D.link(as l)(doc d)
 [String "before",d]->D.beforeNonBlank(doc d)
 [String "after",s]->D.afterBreak(as s)
 [String "block",w,xs]->D.Block(as w)(map (A.fromList . map part . arr)(arr xs))
 [String "cooked",w,xs]->D.CookedText(as w)(A.fromList(map part(arr xs)))
 _->error "doc"
 where part x=case arr x of [l,s]->A.Attr(as l) F.baseFont(as s);_->error "part"

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
        r<-try(evaluate(force(operation op input)))::IO(Either SomeException B.ByteString)
        B.putStrLn $ case r of Left e->encode(object["exception" .= displayException e]);Right b->b
       force x=let b=encode x in B.length b `seq` b
