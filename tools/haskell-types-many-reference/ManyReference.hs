{-# LANGUAGE OverloadedStrings, ScopedTypeVariables #-}
-- Calls only pinned original Builder and its actual derived dictionaries/defaults.
import qualified Text.Pandoc.Builder as P
import Text.Pandoc.Definition
import qualified Data.Foldable as F
import qualified Data.Traversable as V
import qualified Data.Sequence as S
import qualified Data.Text as T
import Data.List.NonEmpty(NonEmpty(..))
import Data.Semigroup
import Data.Monoid
import Data.String
import Control.Monad.State.Strict
import Control.Applicative
import Control.Exception
import Data.Aeson
import qualified Data.Aeson.KeyMap as K
import qualified Data.ByteString.Lazy.Char8 as B
import System.IO
field n(Object o)=maybe(error "field")id(K.lookup n o)
as v=case fromJSON v of Success x->x;Error e->error e
newtype Trace=Trace String
instance Semigroup Trace where Trace a<>Trace b=Trace("("++a++">"++b++")")
instance Monoid Trace where mempty=Trace "e"
data Shape a=Shape a String
instance Functor Shape where fmap f(Shape x s)=Shape(f x)("f("++s++")")
instance Applicative Shape where
 pure x=Shape x "p"
 Shape f s <*> Shape x t=Shape(f x)("a("++s++","++t++")")
 liftA2 f(Shape x s)(Shape y t)=Shape(f x y)("2("++s++","++t++")")
instance Monad Shape where Shape x s >>= f=let Shape y t=f x in Shape y("b("++s++","++t++")")
shapeTrace(Shape _ s)=s
floatValues r=P.fromList(map number(as(field "numbers" r)::[String]))::P.Many Double
 where number "NaN"=0/0;number "Infinity"=1/0;number "-Infinity"= -1/0;number x=read x
observe r=let {xs=as(field "values" r)::[Int]; many=P.fromList xs; term x z="("++show x++">"++z++")"; left z x="("++z++">"++show x++")"; action x=modify(++[x])>>pure(x+1)::State [Int] Int} in
 case as(field "operation" r)::String of
 "replaceTrace"->let Trace result=F.foldMap(Trace . show)((as(field "argument" r)::Int)<$many)in result
 "mapMShape"->shapeTrace(V.mapM(\x->Shape x(show x))many)
 "sequenceAShape"->shapeTrace(sequenceA(fmap(\x->Shape x(show x))many))
 "sequenceShape"->shapeTrace(V.sequence(fmap(\x->Shape x(show x))many))
 "traverseShape"->shapeTrace(traverse(\x->Shape x(show x))many)
 "fromChars"->show(fromString(map toEnum(as(field "characters" r)::[Int]))::P.Inlines)
 "floatSum"->show(F.sum(floatValues r))
 "floatProduct"->show(F.product(floatValues r))
 "floatMaximum"->show(F.maximum(floatValues r))
 "floatMinimum"->show(F.minimum(floatValues r))
 "algebra"->let
   kind=as(field "kind" r)::String
   method=as(field "method" r)::String
   multiplier=as(field "argument" r)::Int
   left=as(field "left" r)::String
   right=as(field "right" r)::String
   apply :: Monoid a => (Int -> a -> a) -> a -> a -> a
   apply repeat x y=case method of
    "append"->x<>y
    "mappend"->mappend x y
    "mempty"->mempty
    "mconcat"->mconcat[x,y,x]
    "sconcat"->sconcat(x:|[y,x])
    "stimes"->repeat multiplier x
    _->error "method"
   in if method=="fromString"then show(fromString left::P.Inlines)
      else if kind=="inline"then show(apply stimes (P.fromList(read left)::P.Inlines)(P.fromList(read right)))
      else show(apply stimes(P.fromList(read left)::P.Blocks)(P.fromList(read right)))
 "seqAppendTrace"->let Trace result=F.foldMap(Trace . show)((S.fromList xs) S.>< (S.fromList(reverse xs++[7]))) in result
 "seqCycleTrace"->let Trace result=F.foldMap(Trace . show)(stimes (as(field "argument" r)::Int)(S.fromList xs)) in result
 "seqCycle"->show(stimes (as(field "argument" r)::Int)(S.fromList xs))
 "constructor"->show(P.Many(S.fromList xs))
 "unMany"->show(P.unMany many)
 "singleton"->show(P.singleton (as(field "argument" r)::Int))
 "fromList"->show(P.fromList xs)
 "toList"->show(P.toList many)
 "isNull"->show(P.isNull many)
 "fmap"->show(fmap(+1)many)
 "replace"->show((as(field "argument" r)::Int)<$many)
 "fold"->show(getSum(F.fold(fmap Sum many)))
 "foldMap"->show(getSum(F.foldMap Sum many))
 "foldMapPrime"->show(getSum(F.foldMap' Sum many))
 "foldMapTrace"->let Trace result=F.foldMap(Trace . show)many in result
 "foldMapPrimeTrace"->let Trace result=F.foldMap'(Trace . show)many in result
 "foldr"->F.foldr term "z" many
 "foldrPrime"->F.foldr' term "z" many
 "foldl"->F.foldl left "z" many
 "foldlPrime"->F.foldl' left "z" many
 "foldr1"->show(F.foldr1(-)many)
 "foldl1"->show(F.foldl1(-)many)
 "null"->show(F.null many)
 "length"->show(F.length many)
 "elem"->show(F.elem(as(field "argument" r)::Int)many)
 "maximum"->show(F.maximum many)
 "minimum"->show(F.minimum many)
 "sum"->show(F.sum many)
 "product"->show(F.product many)
 "traverse"->show(runState(traverse action many)[])
 "sequenceA"->show(runState(sequenceA(fmap action many))[])
 "mapM"->show(runState(V.mapM action many)[])
 "sequence"->show(runState(V.sequence(fmap action many))[])
 "traverseList"->show(traverse(\x->[x,negate x])many)
 "traverseMaybe"->show(traverse(\x->if x<0 then Nothing else Just(x+1))many)
 _->error "operation"
respond line=do
 let request=either error id(eitherDecode line)
 result<-try(evaluate(let bytes=encode(observe request)in B.length bytes `seq` bytes))::IO(Either SomeException B.ByteString)
 B.putStrLn(either(encode.object.pure.("exception" .=).takeWhile(/='\n').displayException)id result)
main=do hSetBuffering stdout LineBuffering;input<-B.getContents;mapM_ respond(B.lines input)
