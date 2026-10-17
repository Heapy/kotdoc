{-# LANGUAGE DataKinds, OverloadedStrings, TypeFamilies, ScopedTypeVariables, TypeOperators #-}
-- Original GHC.Generic permits caller-defined dictionaries, including lazy fields.
import GHC.Float (castDoubleToWord64, castWord64ToDouble)
import GHC.Generics
import qualified Text.Pandoc.Definition as P
import qualified Text.Pandoc.Builder as B
import qualified Data.Sequence.Internal as S
import Data.Aeson
import qualified Data.ByteString.Lazy.Char8 as L
import Control.Exception
import System.IO
import Data.IORef
import System.IO.Unsafe (unsafePerformIO)
data Ignored=Ignored
instance Generic Ignored where
 type Rep Ignored=U1
 from _=U1
 to _=Ignored
data Strict=Strict ![Int]
instance Generic Strict where
 type Rep Strict=K1 R [Int]
 from (Strict x)=K1 x
 to (K1 x)=Strict x
data LazyPair=LazyPair [Int] [Int]
instance Generic LazyPair where
 type Rep LazyPair=K1 R [Int] :*: K1 R [Int]
 from (LazyPair a b)=K1 a :*: K1 b
 to (K1 a :*: K1 b)=LazyPair a b
data BodyM=BodyM
instance Generic BodyM where
 type Rep BodyM=M1 D ('MetaData "BodyM" "Oracle" "oracle" 'False) U1
 from BodyM=M1 U1
 to (M1 U1)=BodyM
data BodyK=BodyK [Int]
instance Generic BodyK where
 type Rep BodyK=K1 R [Int]
 from (BodyK xs)=K1 xs
 to (K1 xs)=BodyK xs
observe name value=do
 result<-try(evaluate value)
 L.putStrLn(encode(name::String))
 L.putStrLn(encode(case result of Right v->object["result" .= show(v::Int)];Left(e::SomeException)->object["exception" .= takeWhile(/='\n')(displayException e)]))
observeBits name bits=do
 let x=castWord64ToDouble bits
 let extracted=case from (P.ColWidth x) of M1(L1(M1(M1(K1 y))))->y;_->error "constructor"
 let roundtrip=case (to(from(P.ColWidth x))::P.ColWidth) of P.ColWidth y->y;_->error "constructor"
 L.putStrLn(encode(name::String))
 L.putStrLn(encode(object["result" .= (show(castDoubleToWord64 extracted)++":"++show(castDoubleToWord64 roundtrip))]))
counted ref value=unsafePerformIO(modifyIORef' ref (+1)>>pure value)
{-# NOINLINE counted #-}
ignoreError action=(action>>pure()) `catch` (\(_::SomeException)->pure())
sharedResult name value=do
 L.putStrLn(encode(name::String))
 L.putStrLn(encode(object["result" .= (value::String)]))
shareFromM=do
 calls<-newIORef(0::Int)
 let r=counted calls (from (error "input"::BodyM))::Rep BodyM ()
 evaluate(case r of M1 _->7::Int)
 before<-readIORef calls
 ignoreError(evaluate r);ignoreError(evaluate r)
 after<-readIORef calls
 sharedResult "sharing/fromM" (show before++":"++show after)
shareFromK=do
 calls<-newIORef(0::Int)
 let r=counted calls (from (error "input"::BodyK))::Rep BodyK ()
 evaluate(case r of K1 _->7::Int)
 before<-readIORef calls
 ignoreError(evaluate r);ignoreError(evaluate r)
 after<-readIORef calls
 sharedResult "sharing/fromK" (show before++":"++show after)
shareToM=do
 calls<-newIORef(0::Int)
 let r=counted calls (to (error "representation")::BodyM)
 evaluate(const(7::Int)r)
 before<-readIORef calls
 ignoreError(evaluate r);ignoreError(evaluate r)
 after<-readIORef calls
 sharedResult "sharing/toM" (show before++":"++show after)
shareToK=do
 calls<-newIORef(0::Int);roots<-newIORef(0::Int)
 let rep=counted roots (error "representation")::Rep BodyK ()
 let r=counted calls (to rep::BodyK)
 evaluate(const(7::Int)r)
 before<-readIORef calls
 evaluate r;evaluate r
 after<-readIORef calls;unforced<-readIORef roots
 ignoreError(evaluate(case r of BodyK xs->xs));ignoreError(evaluate(case r of BodyK xs->xs))
 forced<-readIORef roots
 sharedResult "sharing/toK" (show before++":"++show after++":"++show unforced++":"++show forced)
main=do
 hSetBuffering stdout LineBuffering
 observe "ignored/from-root" (from (error "input"::Ignored) `seq` 7)
 observe "ignored/to-root" ((to (error "representation")::Ignored) `seq` 7)
 observe "strict/from-root" (from (error "input"::Strict) `seq` 7)
 observe "strict/from-field" (from (Strict (error "field")) `seq` 7)
 observe "strict/from-head" (from (Strict [error "head"]) `seq` 7)
 observe "strict/to-root" ((to (error "representation")::Strict) `seq` 7)
 observe "strict/to-field" ((to (K1 (error "field"))::Strict) `seq` 7)
 observe "strict/to-head" ((to (K1 [error "head"])::Strict) `seq` 7)
 observe "strict/to-unused" (const 7 (to (K1 (error "field"))::Strict))
 observe "pair/from-root" (from (error "input"::LazyPair) `seq` 7)
 observe "pair/from-fields" (from (LazyPair (error "first") (error "second")) `seq` 7)
 observe "pair/to-root" ((to (error "representation")::LazyPair) `seq` 7)
 observe "pair/to-fields" ((to (K1 (error "first") :*: K1 (error "second"))::LazyPair) `seq` 7)
 observe "pair/to-first" (case (to (K1 (error "first") :*: K1 (error "second"))::LazyPair) of LazyPair a _->a `seq` 7)
 observe "pair/to-second" (case (to (K1 (error "first") :*: K1 (error "second"))::LazyPair) of LazyPair _ b->b `seq` 7)
 observe "pair/to-first-head" (case (to (K1 [error "head"] :*: K1 (error "second"))::LazyPair) of LazyPair a _->a `seq` 7)
 observe "deep/from-inline" (from (iterate (P.Emph . (:[])) P.Space !! 50000) `seq` 7)
 observe "deep/to-from-inline" ((to (from (iterate (P.Emph . (:[])) P.Space !! 50000))::P.Inline) `seq` 7)
 observe "deep/from-many" (from (B.fromList (replicate 50000 (error "element"::Int))) `seq` 7)
 observe "deep/to-from-many" ((to (from (B.fromList (replicate 50000 (error "element"::Int))))::B.Many Int) `seq` 7)
 observe "many/tree/from" ((from (B.Many (S.Seq (error "tree"))::B.Many Int)) `seq` 7)
 observe "many/single-cell/from" ((from (B.Many (S.Seq (S.Single (error "cell")))::B.Many Int)) `seq` 7)
 observe "many/deep-prefix/from" ((from (B.Many (S.Seq (S.Deep 2 (error "prefix") S.EmptyT (S.One (error "cell"))))::B.Many Int)) `seq` 7)
 observe "many/deep-middle/from" ((from (B.Many (S.Seq (S.Deep 2 (S.One (error "cell")) (error "middle") (S.One (error "cell"))))::B.Many Int)) `seq` 7)
 observe "many/deep-suffix/from" ((from (B.Many (S.Seq (S.Deep 2 (S.One (error "cell")) S.EmptyT (error "suffix")))::B.Many Int)) `seq` 7)
 observe "many/deep-both/from" ((from (B.Many (S.Seq (S.Deep 2 (error "prefix") S.EmptyT (error "suffix")))::B.Many Int)) `seq` 7)
 observe "many/tree/toFrom" ((to (from (B.Many (S.Seq (error "tree"))::B.Many Int))::B.Many Int) `seq` 7)
 observe "many/single-cell/toFrom" ((to (from (B.Many (S.Seq (S.Single (error "cell")))::B.Many Int))::B.Many Int) `seq` 7)
 observe "many/deep-prefix/toFrom" ((to (from (B.Many (S.Seq (S.Deep 2 (error "prefix") S.EmptyT (S.One (error "cell"))))::B.Many Int))::B.Many Int) `seq` 7)
 observe "many/deep-middle/toFrom" ((to (from (B.Many (S.Seq (S.Deep 2 (S.One (error "cell")) (error "middle") (S.One (error "cell"))))::B.Many Int))::B.Many Int) `seq` 7)
 observe "many/deep-suffix/toFrom" ((to (from (B.Many (S.Seq (S.Deep 2 (S.One (error "cell")) S.EmptyT (error "suffix")))::B.Many Int))::B.Many Int) `seq` 7)
 observe "many/deep-both/toFrom" ((to (from (B.Many (S.Seq (S.Deep 2 (error "prefix") S.EmptyT (error "suffix")))::B.Many Int))::B.Many Int) `seq` 7)
 observeBits "float/0000000000000000" 0x0000000000000000
 observeBits "float/8000000000000000" 0x8000000000000000
 observeBits "float/0000000000000001" 0x0000000000000001
 observeBits "float/7fefffffffffffff" 0x7fefffffffffffff
 observeBits "float/7ff0000000000000" 0x7ff0000000000000
 observeBits "float/fff0000000000000" 0xfff0000000000000
 observeBits "float/7ff8000000000001" 0x7ff8000000000001
 observeBits "float/fff812345678abcd" 0xfff812345678abcd
 observe "bodyM/from-unused" (const 7 (from (error "input"::BodyM)))
 observe "bodyM/from-projection" (case from (error "input"::BodyM) of M1 _->7)
 observe "bodyM/from-root" (from (error "input"::BodyM) `seq` 7)
 observe "bodyM/to-unused" (const 7 (to (error "representation")::BodyM))
 observe "bodyM/to-root" ((to (error "representation")::BodyM) `seq` 7)
 observe "bodyK/from-unused" (const 7 (from (error "input"::BodyK)))
 observe "bodyK/from-projection" (case from (error "input"::BodyK) of K1 _->7)
 observe "bodyK/from-root" (from (error "input"::BodyK) `seq` 7)
 observe "bodyK/to-unused" (const 7 (to (error "representation")::BodyK))
 observe "bodyK/to-root" ((to (error "representation")::BodyK) `seq` 7)
 observe "bodyK/to-field" (case (to (error "representation")::BodyK) of BodyK xs->xs `seq` 7)
 observe "bodyK/to-head" (case (to (K1 [error "head"])::BodyK) of BodyK xs->xs `seq` 7)
 shareFromM
 shareFromK
 shareToM
 shareToK
