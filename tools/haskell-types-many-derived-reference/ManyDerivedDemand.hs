{-# LANGUAGE ScopedTypeVariables, OverloadedStrings #-}
-- Executes actual original dictionaries on partial/custom values, no replacement schemes.
import qualified Text.Pandoc.Builder as B
import qualified Data.Sequence.Internal as S
import Data.Aeson
import qualified Data.ByteString.Lazy.Char8 as L
import Control.Exception
import System.IO
newtype Ignore = Ignore Int deriving Show
instance Eq Ignore where _ == _ = True; _ /= _ = error "ne-override"
instance Ord Ignore where compare _ _ = EQ; _ <= _=error "le-override"
newtype IgnoreList = IgnoreList Int
instance Show IgnoreList where
 showsPrec _ _ = showString "E"
 showList _ = showString "CUSTOM"
observe name value=do
 r<-try(evaluate(forceString value))
 L.putStrLn(encode(name::String))
 L.putStrLn(encode(case r of Right v->object["result" .= v];Left(e::SomeException)->object["exception" .= takeWhile(/='\n')(displayException e)]))
forceString s=foldr seq () s `seq` s
badRoot :: B.Many Int
badRoot=error "root"
badHead :: B.Many Int
badHead=B.Many(S.Seq(S.Single(S.Elem(error "head"))))
badTail :: B.Many Int
badTail=B.Many(S.Seq(S.Deep 3 (S.One(S.Elem 1)) (error "middle") (S.One(S.Elem 2))))
empty=B.fromList[]::B.Many Int
one=B.fromList[1]::B.Many Int
main=do
 hSetBuffering stdout LineBuffering
 sequence_[observe("show-root/"++show n)(take n(showsPrec 0 badRoot "TAIL"))|n<-[0,1,5,14,22,23,24,25,30]]
 sequence_[observe("show-head/"++show n)(take n(showsPrec 0 badHead "TAIL"))|n<-[0,1,22,23,24,25,30]]
 sequence_[observe("show-tail/"++show n)(take n(showsPrec 0 badTail "TAIL"))|n<-[0,1,22,23,24,25,26,27,30]]
 sequence_[observe("show-listroot/"++show n)(take n(showList(error "listroot"::[B.Many Int])"TAIL"))|n<-[0,1,2,10]]
 sequence_[observe("show-listtail/"++show n)(take n(showList([one]++error "listtail")"TAIL"))|n<-[0,1,10,28,29,30,31,32]]
 observe "show-ignore-root" (show(error "root"::B.Many IgnoreList))
 observe "show-ignore-head" (show(B.fromList[error "head"]::B.Many IgnoreList))
 observe "eq-ignore-head" (show((B.fromList[error "left"]::B.Many Ignore)==B.fromList[error "right"]))
 observe "ne-ignore-head" (show((B.fromList[error "left"]::B.Many Ignore)/=B.fromList[error "right"]))
 observe "compare-ignore-head" (show(compare(B.fromList[error "left"]::B.Many Ignore)(B.fromList[error "right"])))
 observe "eq-size-beforehead" (show(badHead==empty))
 observe "eq-leftroot" (show((error "left"::B.Many Int)==(error "right")))
 observe "eq-lefttree-rightroot" (show((B.Many(S.Seq(error "lefttree"))::B.Many Int)==(error "right")))
 observe "compare-empty-badhead" (show(compare empty badHead))
 observe "compare-badhead-empty" (show(compare badHead empty))
 observe "compare-lefttree-rightroot" (show(compare(B.Many(S.Seq(error "lefttree"))::B.Many Int)(error "right")))
 observe "eq-cached-length" (show((B.Many(S.Seq(S.Deep 7 (S.One(S.Elem 1)) S.EmptyT (S.One(S.Elem 2))))::B.Many Int)==B.fromList[1,2]))
 observe "compare-ignores-cache" (show(compare(B.Many(S.Seq(S.Deep 7 (S.One(S.Elem 1)) S.EmptyT (S.One(S.Elem 2))))::B.Many Int)(B.fromList[1,2])))
 observe "eq-strict-prefix" (show((B.Many(S.Seq(S.Deep 7 (error "prefix") S.EmptyT (S.One(S.Elem 2))))::B.Many Int)==empty))
 observe "eq-strict-suffix" (show((B.Many(S.Seq(S.Deep 7 (S.One(S.Elem 1)) S.EmptyT (error "suffix")))::B.Many Int)==empty))
 observe "eq-prefix-before-suffix" (show((B.Many(S.Seq(S.Deep 7 (error "prefix") S.EmptyT (error "suffix")))::B.Many Int)==empty))
 sequence_[observe("nan/"++op)(value)| (op,value)<-let a=B.fromList[0/0::Double];b=B.fromList[0/0::Double] in [("eq",show(a==b)),("ne",show(a/=b)),("compare",show(compare a b)),("lt",show(a<b)),("le",show(a<=b)),("gt",show(a>b)),("ge",show(a>=b))]]
