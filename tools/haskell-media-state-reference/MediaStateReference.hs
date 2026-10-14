{-# LANGUAGE OverloadedStrings, ScopedTypeVariables #-}
module Main where
import Data.Aeson
import qualified Data.Text as T
import qualified Data.Vector as V
import qualified Data.ByteString.Char8 as B
import qualified Data.ByteString.Lazy as BL
import qualified Data.ByteString.Lazy.Char8 as C
import qualified Data.Map.Strict as M
import qualified Data.Map.Internal as I
import Data.Char(chr,ord)
import Data.Int(Int64)
import Data.Semigroup(stimes)
import Numeric(readHex)
import Text.Pandoc.MediaBag
import qualified Text.Pandoc.URI as PU
import qualified Network.URI as NU
import Control.Exception(try,SomeException,evaluate,displayException)
import System.IO(stdout,hFlush,isEOF)
as :: FromJSON a => Value -> a
as v=case fromJSON v of Success a->a;Error e->error e
arr (Array v)=V.toList v
arr _=error "array"
chars v=map(chr.as)(arr v)
txt=T.pack.chars
maybeText Null=Nothing
maybeText v=Just(txt v)
hex = T.pack . concatMap (\w -> ["0123456789abcdef"!!fromIntegral(w `div` 16),"0123456789abcdef"!!fromIntegral(w `mod` 16)]) . BL.unpack
unhex s=BL.pack(go(T.unpack s)) where
 go []=[]
 go(a:b:rest)=case readHex[a,b] of [(n,"")]->n:go rest;_->error "hex"
 go _=error "hex"
entry v=case arr v of [p,m,b]->(chars p,maybeText m,unhex(as b));_->error "entry"
bag v=foldl (\b e->let(p,m,bs)=entry e in insertMedia p m bs b) mempty(arr v)
item i=object["mime" .= mediaMimeType i,"path" .= map ord(mediaPath i),"bytes" .= hex(mediaContents i),"shown" .= T.pack(show i)]
bagFields b qs=object["directory" .= [[toJSON(map ord p),toJSON m,toJSON n]|(p,m,n)<-mediaDirectory b],
 "items" .= [[toJSON(map ord p),toJSON m,toJSON(hex bs)]|(p,m,bs)<-mediaItems b],"shown" .= T.pack(show b),
 "lookups" .= [maybe Null item(lookupMedia(chars q)b)|q<-arr qs]]
mapShape I.Tip=Null
mapShape(I.Bin n k v l r)=toJSON[toJSON n,toJSON k,toJSON(v::Int),mapShape l,mapShape r]
mapOps ops=scanl step M.empty(arr ops) where
 step m v=case arr v of
  [String "insert",k,n]->M.insert(txt k)(as n)m
  [String "delete",k]->M.delete(txt k)m
  [String "union",xs]->M.union m(foldl (\z p->let[k,n]=arr p in M.insert(txt k)(as n)z)M.empty(arr xs))
  _->error "map op"
response req=case arr req of
 [String "bag",xs,qs]->pure(bagFields(bag xs)qs)
 [String "delete",xs,p,qs]->let b=bag xs in pure(object["before" .= bagFields b qs,"after" .= bagFields(deleteMedia(chars p)b)qs])
 [String "union",xs,ys,qs]->let a=bag xs;b=bag ys in pure(object["left" .= bagFields a qs,"right" .= bagFields b qs,"combined" .= bagFields(a<>b)qs])
 [String "map",ops]->pure(toJSON(map mapShape(mapOps ops)))
 [String "uri",p]->pure(object["known" .= PU.isURI(txt p),"parsed" .= fmap (\u->toJSON[toJSON(NU.uriScheme u),toJSON(map ord(NU.uriPath u))])(NU.parseURI(chars p))])
 [String "times",xs,n,qs]->pure(bagFields(stimes(read(T.unpack(as n))::Integer)(bag xs))qs)
 [String "item",m,p,b,prec]->let i=MediaItem(txt m)(chars p)(unhex(as b)) in pure(object["item" .= item i,"shown" .= T.pack(showsPrec(as prec)i "")])
 [String "compare",a,b]->do
   let make v=case arr v of [m,p,s]->MediaItem(txt m)(chars p)(unhex(as s));_->error "item"
       x=make a
       y=make b
   pure(object["eq" .= (x==y),"compare" .= show(compare x y),"lt" .= (x<y),"le" .= (x<=y),"gt" .= (x>y),"ge" .= (x>=y),"min" .= item(min x y),"max" .= item(max x y)])
 [String "demand",p,m,String op]->do
   let b=insertMedia(chars p)(maybeText m)(error "media payload") mempty
       result=case op of
        "mime"->toJSON(mediaMimeType <$> lookupMedia(chars p)b)
        "path"->toJSON(fmap(map ord.mediaPath)(lookupMedia(chars p)b))
        "delete"->toJSON(show(deleteMedia(chars p)b))
        "directory"->toJSON(show(mediaDirectory b))
        "show"->toJSON(show b)
        _->error "demand op"
   pure result
 _->error "operation"
main=loop where
 loop=do
  end<-isEOF
  if end then pure() else do
   line<-B.getLine
   let req=case eitherDecode(BL.fromStrict line) of Right v->v;Left e->error e
   r<-try(response req >>= \v->evaluate(BL.length(encode v))>>pure v)::IO(Either SomeException Value)
   C.putStrLn(encode(either(\e->object["exception" .= takeWhile(/='\n')(displayException e)])id r))
   hFlush stdout;loop
