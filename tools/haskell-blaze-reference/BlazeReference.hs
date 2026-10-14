{-# LANGUAGE OverloadedStrings, ScopedTypeVariables, NoMonomorphismRestriction #-}
module Main where
import Prelude hiding(null)
import qualified Prelude as P
import qualified Text.Blaze as B
import Text.Blaze.Internal
import qualified Text.Blaze.Renderer.String as RS
import qualified Text.Blaze.Renderer.Text as RT
import qualified Text.Blaze.Renderer.Utf8 as RU
import qualified Text.Blaze.Renderer.Pretty as RP
import qualified Text.Blaze.Html5 as H
import qualified Text.Blaze.Html5.Attributes as A
import qualified PandocBlaze as PB
import qualified Text.DocLayout as D
import Data.Aeson hiding(String)
import qualified Data.Aeson as J
import qualified Data.ByteString as S
import qualified Data.ByteString.Lazy.Char8 as L
import qualified Data.Text as T
import qualified Data.Text.Lazy as TL
import qualified Data.Text.Encoding as TE
import Data.Text.Encoding.Error(lenientDecode)
import qualified Data.Vector as V
import Data.Char(chr,ord)
import Control.Monad(void)
import Control.Exception(SomeException,try,evaluate,displayException)
import System.IO(hSetBuffering,stdout,BufferMode(LineBuffering))
import Numeric(readHex)
import Data.String(fromString)

as v=case fromJSON v of Success a->a;Error e->error e
arr (Array a)=V.toList a
arr _=error "array"
j=toJSON
hex=T.pack.concatMap(\b->["0123456789abcdef"!!fromIntegral(b `div` 16),"0123456789abcdef"!!fromIntegral(b `mod` 16)]).S.unpack
bytes v=S.pack(go(T.unpack(as v))) where
 go []=[]
 go(a:b:xs)=case readHex[a,b] of [(n,"")]->n:go xs;_->error "hex"
 go _=error "hex"
chars v=map(chr.as)(arr v)
static (J.String s)=fromString(T.unpack s)
static v=case arr v of
 [ss,bs,t,mode]->StaticString (fun(as mode)(chars ss)) (bytes bs)(as t)
 _->error "static"
 where fun mode s=case mode::String of "prefix"->(s++);"constant"->const s;"reverse"-> \tail->s++reverse tail;"bomb"-> \_->error "static function";_->error mode
choice v=case arr v of
 [J.String "static",s]->Static(static s)
 [J.String "string",s]->String(chars s)
 [J.String "text",s]->Text(as s)
 [J.String "bytes",s]->ByteString(bytes s)
 [J.String "pre",s]->PreEscaped(choice s)
 [J.String "external",s]->External(choice s)
 [J.String "append",x,y]->AppendChoiceString(choice x)(choice y)
 [J.String "empty"]->EmptyChoiceString
 _->error "choice"
ret (J.String "bomb")=error "return value"
ret v=as v::Int
node :: Value -> MarkupM Int
node v=case arr v of
 [J.String "parent",tag,o,c,n]->Parent(static tag)(static o)(static c)(node n)
 [J.String "custom-parent",tag,n]->CustomParent(choice tag)(node n)
 [J.String "leaf",tag,o,c,n]->Leaf(static tag)(static o)(static c)(ret n)
 [J.String "custom-leaf",tag,close,n]->CustomLeaf(choice tag)(as close)(ret n)
 [J.String "content",s,n]->Content(choice s)(ret n)
 [J.String "comment",s,n]->Comment(choice s)(ret n)
 [J.String "append",a,b]->Append(node a)(node b)
 [J.String "attribute",raw,key,s,n]->AddAttribute(static raw)(static key)(choice s)(node n)
 [J.String "custom-attribute",key,s,n]->AddCustomAttribute(choice key)(choice s)(node n)
 [J.String "empty",n]->Empty(ret n)
 [J.String "map",f,n]->fmap(function f)(node n)
 [J.String "apply",f,n,m]->fmap (\x -> \y->function f (x+y)) (node n) <*> node m
 [J.String "apply-ignore",n,m]->fmap(\x -> const x)(node n) <*> node m
 [J.String "before",a,b]->node a <* node b
 [J.String "then",a,b]->node a *> node b
 [J.String "concat",xs,n]->foldr Append (Empty(ret n)) (map node(arr xs))
 [J.String "external",n]->external(node n)
 [J.String "contents",n]->contents(node n)
 [J.String "bind",n,delta]->node n >>= \x -> Content(String(show(x+as delta)))(x+as delta)
 [J.String "bind-ignore",n,m]->node n >>= \_ -> node m
 [J.String "chunks",kind,xs]->fmap (const 0) $ case as kind::String of
   "text"->B.lazyText(TL.fromChunks(map chunkText(arr xs)))
   "pre-text"->B.preEscapedLazyText(TL.fromChunks(map chunkText(arr xs)))
   "bytes"->B.unsafeLazyByteString(L.fromChunks(map chunkBytes(arr xs)))
   "text-comment"->B.lazyTextComment(TL.fromChunks(map chunkText(arr xs)))
   "bytes-comment"->B.unsafeLazyByteStringComment(L.fromChunks(map chunkBytes(arr xs)))
   "text-value"->Content(extractValue(B.lazyTextValue(TL.fromChunks(map chunkText(arr xs)))))()
   "pre-text-value"->Content(extractValue(B.preEscapedLazyTextValue(TL.fromChunks(map chunkText(arr xs)))))()
   "bytes-value"->Content(extractValue(B.unsafeLazyByteStringValue(L.fromChunks(map chunkBytes(arr xs)))))()
   _->error "chunks kind"
 _->error "node"
 where function v=case arr v of [J.String "add",n]->(+as n);[J.String "const",n]->const(ret n);[J.String "bomb"]-> \_->error "map value";_->error "function"
extractValue v=case Empty () B.! customAttribute "x" v of AddCustomAttribute _ s _->s;_->error "attribute shape"
chunkText (J.String "!bottom!")=error "chunk value"
chunkText v=as v
chunkBytes (J.String "!bottom!")=error "chunk value"
chunkBytes v=bytes v
encDoc d=case d of
 D.Text w x->j[j("text"::T.Text),j w,j x]
 D.Flush x->j[j("flush"::T.Text),encDoc x]
 D.BreakingSpace->j[ j("space"::T.Text)]
 D.CarriageReturn->j[j("cr"::T.Text)]
 D.NewLine->j[j("newline"::T.Text)]
 D.Concat a b->j[j("concat"::T.Text),encDoc a,encDoc b]
 D.Empty->j[j("empty"::T.Text)]
 _->error "unexpected layout constructor"
choiceShape c=case c of
 Static s->j[j("static"::T.Text),j(getText s),j(hex(getUtf8ByteString s))]
 String s->j[j("string"::T.Text),j(map ord s)]
 Text s->j[j("text"::T.Text),j s]
 ByteString s->j[j("bytes"::T.Text),j(hex s)]
 PreEscaped s->j[j("pre"::T.Text),choiceShape s]
 External s->j[j("external"::T.Text),choiceShape s]
 AppendChoiceString a b->j[j("append"::T.Text),choiceShape a,choiceShape b]
 EmptyChoiceString->j[j("empty"::T.Text)]
ss s=j[j(getText s),j(hex(getUtf8ByteString s))]
nodeShape :: MarkupM a -> Value
nodeShape n=case n of
 Parent t o c h->j[j("parent"::T.Text),ss t,ss o,ss c,nodeShape h]
 CustomParent t h->j[j("custom-parent"::T.Text),choiceShape t,nodeShape h]
 Leaf t o c _->j[j("leaf"::T.Text),ss t,ss o,ss c]
 CustomLeaf t close _->j[j("custom-leaf"::T.Text),choiceShape t,j close]
 Content c _->j[j("content"::T.Text),choiceShape c]
 Comment c _->j[j("comment"::T.Text),choiceShape c]
 Append a b->j[j("append"::T.Text),nodeShape a,nodeShape b]
 AddAttribute r k v h->j[j("attribute"::T.Text),ss r,ss k,choiceShape v,nodeShape h]
 AddCustomAttribute k v h->j[j("custom-attribute"::T.Text),choiceShape k,choiceShape v,nodeShape h]
 Empty _->j[j("empty"::T.Text)]
observe :: ToJSON a => a -> IO Value
observe x=do
 r<-try(evaluate(L.toStrict(encode x)))::IO(Either SomeException S.ByteString)
 pure$case r of Left e->object["error" .= takeWhile(/='\n')(displayException e)];Right b->either error id(eitherDecode(L.fromStrict b))
render n=do
 let u=void n
 s<-observe(map ord(RS.renderMarkup u));t<-observe(TL.toStrict(RT.renderMarkup u));ul<-observe(hex(L.toStrict(RU.renderMarkup u)))
 p<-observe(map ord(RP.renderMarkup u));l<-observe(TL.toStrict(RT.renderMarkupWith(TE.decodeUtf8With lenientDecode)u))
 val<-observe (value n);empty<-observe(null n);shape<-observe(nodeShape n)
 doc<-observe(encDoc(PB.layoutMarkup u))
 layout<-observe[[D.render width(PB.layoutMarkup u)]|width<-[Nothing,Just 0,Just 1,Just 12,Just 40]]
 pure(object["string" .= s,"text" .= t,"utf8" .= ul,"pretty" .= p,"lenient" .= l,"value" .= val,"null" .= empty,"shape" .= shape,"doc" .= doc,"layout" .= layout])
 where value :: MarkupM a -> a
       value m=case m of Parent _ _ _ n->value n;CustomParent _ n->value n;Leaf _ _ _ x->x;CustomLeaf _ _ x->x;Content _ x->x;Comment _ x->x;Append _ n->value n;AddAttribute _ _ _ n->value n;AddCustomAttribute _ _ n->value n;Empty x->x
process(op,v)=case op::String of
 "render"->render(node v)
 "choice"->case arr v of
  [c,tail]->do
   s<-observe(map ord(RS.fromChoiceString(choice c)(chars tail)))
   r<-render(Content(choice c)(1::Int))
   pure(object["suffix" .= s,"render" .= r])
  _->error "choice pair"
 _->error op
main=do
 hSetBuffering stdout LineBuffering
 L.getContents>>=mapM_(\line->case eitherDecode line of Right req->process req>>=L.putStrLn.encode;Left e->error e).L.lines
