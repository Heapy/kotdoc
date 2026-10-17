{-# LANGUAGE ScopedTypeVariables, OverloadedStrings #-}
import qualified Text.Pandoc.Builder as B
import qualified Data.Sequence as S
import qualified Text.ParserCombinators.ReadPrec as P
import qualified Text.ParserCombinators.ReadP as R
import Text.Read(Read(..))
import Data.Aeson
import qualified Data.ByteString.Lazy.Char8 as L
import Control.Exception
import Data.IORef
import System.IO.Unsafe(unsafePerformIO)
import System.IO
newtype Padded=Padded Char deriving Show
instance Read Padded where
 readPrec=error "unused-readPrec"
 readListPrec=do _<-P.get;c<-P.get;pure[Padded c]
data BadList=BadList deriving Show
instance Read BadList where
 readPrec=error "unused-readPrec"
 readListPrec=pure(error "listroot")
data EmptyRead=EmptyRead deriving Show
instance Read EmptyRead where
 readPrec=error "unused-readPrec"
 readListPrec=pure[]
first p n s=case P.readPrec_to_S p n s of ((x,_):_)->x;_->error "noresult"
observe name value=do
 r<-try(evaluate(forceString value))
 L.putStrLn(encode(name::String))
 L.putStrLn(encode(case r of Right v->object["result" .= v];Left(e::SomeException)->object["exception" .= takeWhile(/='\n')(displayException e)]))
forceString s=foldr seq () s `seq` s
counted ref value=unsafePerformIO(modifyIORef' ref (+1)>>pure value)
{-# NOINLINE counted #-}
sharing name build observeValue=do
 ref<-newIORef(0::Int)
 let p=build ref
 a<-readIORef ref
 evaluate(observeValue p)
 b<-readIORef ref
 evaluate(observeValue p)
 c<-readIORef ref
 observe name(show(a,b,c))
sharingFailure name build=do
 ref<-newIORef(0::Int)
 let p=build ref
 a<-readIORef ref
 (_::Either SomeException Int)<-try(evaluate(first p 0 ""))
 b<-readIORef ref
 (_::Either SomeException Int)<-try(evaluate(first p 0 ""))
 c<-readIORef ref
 observe name(show(a,b,c))
main=do
 hSetBuffering stdout LineBuffering
 observe "pure-ignore-input"(show(first(pure(7::Int)) (error "prec") (error "input")))
 observe "pure-pair-ignore-value"(show(case P.readPrec_to_S(pure(error "value"::Int)) (error "prec") (error "input") of (_:_)->7::Int;_->0))
 observe "pure-value"(show(first(pure(error "value"::Int)) (error "prec") (error "input")))
 observe "look-ignore-input"(show(first(P.look>>pure(7::Int)) (error "prec") (error "input")))
 observe "look-force-input"(show(first(P.look>>= \s->pure(length s)) (error "prec") (error "input")))
 observe "get-ignore-headtail"(show(first(P.get>>pure(7::Int)) (error "prec") (error "char":error "tail")))
 observe "get-force-head"(show(first(P.get) (error "prec") (error "char":error "tail")))
 observe "get-force-tail"(show(case P.readPrec_to_S(P.get>>pure(7::Int))0('a':error "tail")of((_,s):_)->null s;_->False))
 observe "get-pure-infinite"(show(first(P.get>>pure(7::Int)) (error "prec") (repeat(error "char"))))
 observe "look-infinite-prefix"(show(first(P.look>>= \s->pure(take 4 s)) (error "prec") (repeat 'a')))
 observe "choice-result-before-bad"(show(first(pure(7::Int) P.+++ error "parser") (error "prec") (error "input")))
 observe "choice-result-tail-bad"(show(length(take 2(P.readPrec_to_S(pure(7::Int) P.+++ error "parser")0 ""))))
 observe "choice-result-before-get"(show(first((P.get>>pure(1::Int)) P.+++ pure 2) (error "prec") (error "input")))
 observe "choice-final-before-result"(show(first(P.readS_to_Prec(\_ _->error "reads") P.+++ pure(2::Int)) (error "prec") (error "input")))
 observe "final-get-order"(show(P.readPrec_to_S(P.readS_to_Prec(\_ _->[(1::Int,"z"),(2,"a")]) P.+++ (P.get>>pure 3))0 "xy"))
 observe "get-final-order"(show(P.readPrec_to_S((P.get>>pure(3::Int)) P.+++ P.readS_to_Prec(\_ _->[(1,"z"),(2,"a")]))0 "xy"))
 observe "step-ignore-prec"(show(first(P.step(pure(7::Int))) (error "prec") (error "input")))
 observe "reset-prec"(show(first(P.reset(P.readS_to_Prec(\p s->[(p,s)]))) (error "prec") ""))
 observe "step-wrap"(show(first(P.step(P.readS_to_Prec(\p s->[(p,s)]))) maxBound ""))
 observe "guard-prec"(show(first(P.prec 10(pure(7::Int))) (error "prec") (error "input")))
 observe "guard-ignore-body"(show(length(P.readPrec_to_S(P.prec 10(error "parser"::P.ReadPrec Int))11 (error "input"))))
 let partial="Many {unMany = fromList "++(error "char":"}")
 observe "many-get-partial-head-length"(show((S.length.B.unMany)(read partial::B.Many Padded)))
 observe "many-get-partial-head-show"(show(read partial::B.Many Padded))
 let input="Many {unMany = fromList }"
 observe "many-bad-list-remainder"(show(case readsPrec 0 input::[(B.Many BadList,String)]of((_,s):_)->length s;_->(-1)))
 observe "many-bad-list-root"(show((S.length.B.unMany)(read input::B.Many BadList)))
 observe "many-empty-infinite-remainder"(show(case readsPrec 0(input++repeat 'a')::[(B.Many EmptyRead,String)]of((v,s):_)->((S.length.B.unMany) v,take 4 s);_->(-1,"")))
 observe "many-empty-partial-remainder"(show(case readsPrec 0(input++error "tail")::[(B.Many EmptyRead,String)]of((v,_):_)->(S.length.B.unMany) v;_->(-1)))
 observe "many-whole-partial-remainder"(show((S.length.B.unMany)(read(input++error "tail")::B.Many EmptyRead)))
 observe "many-whole-infinite-nonspace"(show((S.length.B.unMany)(read(input++repeat 'a')::B.Many EmptyRead)))
 observe "many-prec-input-order"(show(length(readsPrec(error "prec")(error "input")::[(B.Many Int,String)])))
 observe "many-prec-after-spaces"(show(length(readsPrec(error "prec")"Many {unMany = fromList []}"::[(B.Many Int,String)])))
 observe "many-prec-empty"(show(length(readsPrec(error "prec")""::[(B.Many Int,String)])))
 observe "show-ignore-bad-prec"(take 0(showsPrec(error "prec")(error "value"::B.Many Int)(error "suffix")))
 observe "show-demand-bad-prec"(take 1(showsPrec(error "prec")(error "value"::B.Many Int)(error "suffix")))
 sharing "sharing-pure"(\ref->pure(counted ref(7::Int)))(\p->first p 0 "")
 sharing "sharing-parser"(\ref->counted ref(pure(7::Int)))(\p->first p 0 "")

 sharingFailure "sharing-pure-failure"(\ref->pure(counted ref(error "value"::Int)))
 sharingFailure "sharing-parser-failure"(\ref->counted ref(error "parser"::P.ReadPrec Int))

 observe "final-ignore-remainder"(show(first(P.readS_to_Prec(\_ _->[(1::Int,error "remainder")])>>pure(7::Int))0 ""))
 observe "final-demand-remainder"(show(case P.readPrec_to_S(P.readS_to_Prec(\_ _->[(1::Int,error "remainder")])>>pure(7::Int))0 ""of((_,s):_)->null s;_->False))
 observe "final-ignore-value"(show(first(P.readS_to_Prec(\_ s->[(error "value"::Int,s)])>>pure(7::Int))0 ""))
 observe "pure-return-list-root"(show(first(pure(error "resultroot"::[Int]))0 "" `seq`(7::Int)))
 observe "pure-return-list-head"(show(head(first(pure(1:error "tail"::[Int]))0 "")))
 observe "pure-return-list-spine"(show(first(pure(error "head":error "tail"::[Int]))0 "" `seq`(7::Int)))
