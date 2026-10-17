{-# LANGUAGE ScopedTypeVariables, OverloadedStrings #-}
-- Independent original pandoc-types/containers dictionaries; no Kotlin expectations.
import qualified Text.Pandoc.Builder as B
import qualified Data.Sequence.Internal as S
import Data.Aeson hiding (Zero)
import qualified Data.ByteString.Lazy.Char8 as L
import Control.Exception
import Text.Read (readPrec_to_S,readListPrec,readPrec,lexP,Lexeme(Ident))
import qualified Text.ParserCombinators.ReadPrec as RP
import qualified Text.ParserCombinators.ReadP as R
import System.IO
newtype Odd = Odd Int deriving Show
instance Eq Odd where
 Odd a == Odd b = a `mod` 2 == b `mod` 2
 _ /= _ = error "element-notEqual"
instance Ord Odd where
 compare (Odd a) (Odd b) = compare b a
 _ <= _ = error "element-lessEqual"
 _ < _ = error "element-less"
 _ > _ = error "element-greater"
 _ >= _ = error "element-greaterEqual"
 min _ _ = error "element-min"
 max _ _ = error "element-max"
newtype NeOnly = NeOnly Int deriving Show
instance Eq NeOnly where NeOnly a /= NeOnly b = a `mod` 2 /= b `mod` 2
instance Ord NeOnly where compare (NeOnly a)(NeOnly b)=compare a b
newtype LeOnly = LeOnly Int deriving (Eq,Show)
instance Ord LeOnly where LeOnly a <= LeOnly b = a>=b
newtype EqFirst = EqFirst Int deriving Show
instance Eq EqFirst where _ == _=True
instance Ord EqFirst where _ <= _=error "unused-le"
newtype ShowOnly = ShowOnly Int
instance Show ShowOnly where show(ShowOnly n)="ONLY"++show n
newtype ShowConflict = ShowConflict Int
instance Show ShowConflict where
 showsPrec p (ShowConflict n)=showString("PREC"++show p++":"++show n)
 show _="SHOW"
newtype Zero = Zero Int deriving (Eq,Show)
instance Ord Zero where compare _ _ = EQ
newtype ListOverride = ListOverride Int
instance Show ListOverride where
 showsPrec p (ListOverride n) = showString ("E"++show p++":"++show n)
 showList _ = showString "CUSTOM"
newtype ReadTag = ReadTag Int deriving Show
instance Read ReadTag where
 readPrec = RP.readP_to_Prec (\p -> R.skipSpaces >> R.string "E" >> pure (ReadTag p))
 readListPrec = RP.readP_to_Prec (\p -> R.skipSpaces >> R.string "TAG" >> pure [ReadTag p])
newtype Duplicate = Duplicate Int deriving Show
instance Read Duplicate where
 readsPrec p s = [(Duplicate 1,s),(Duplicate 2,s)]
 readListPrec = RP.readS_to_Prec (\p s -> [( [Duplicate p],dropWhile (==' ') s),( [Duplicate (p+1)],dropWhile (==' ') s)])
observe name value=do
 r<-try(evaluate(forceString value))
 L.putStrLn(encode(name::String))
 L.putStrLn(encode(case r of Right v->object["result" .= v];Left(e::SomeException)->object["exception" .= takeWhile(/='\n')(displayException e)]))
forceString s=foldr seq () s `seq` s
ops a b=[("eq",show(a==b)),("ne",show(a/=b)),("compare",show(compare a b)),("lt",show(a<b)),("le",show(a<=b)),("gt",show(a>b)),("ge",show(a>=b)),("min",show(min a b)),("max",show(max a b))]
main=do
 hSetBuffering stdout LineBuffering
 let values=[[],[0],[1],[0,1],[1,0],[0,1,2],[2,1,0]]::[[Int]]
 sequence_[observe("int/"++show i++"/"++show j++"/"++op)result|(i,x)<-zip[0::Int ..]values,(j,y)<-zip[0::Int ..]values,(op,result)<-ops(B.fromList x)(B.fromList y)]
 sequence_[observe("odd/"++show i++"/"++show j++"/"++op)result|(i,x)<-zip[0::Int ..]values,(j,y)<-zip[0::Int ..]values,(op,result)<-ops(B.fromList(map Odd x))(B.fromList(map Odd y))]
 sequence_[observe("show/"++show i++"/"++show p)(showsPrec p(B.fromList x)"TAIL")|(i,x)<-zip[0::Int ..]values,p<-[-1,0,10,11,maxBound]]
 sequence_[observe("show-override/"++show p)(showsPrec p(B.fromList[ListOverride 1,ListOverride 2])"TAIL")|p<-[0,10,11]]
 let inputs=["Many {unMany = fromList []}","Many {unMany = fromList [1,2]} rest","(Many {unMany = fromList [1]})tail","Many (fromList [1])","Many { unMany = (fromList [1]) }","Many {unMany = fromList [1,]}","Many {unMany = fromList [(-1)]}"]
 sequence_[observe("read/"++show i++"/"++show p)(show(readsPrec p s::[(B.Many Int,String)]))|(i,s)<-zip[0::Int ..]inputs,p<-[-1,0,10,11,maxBound]]

 sequence_[observe("read-list-override/"++show p)(show(readsPrec p "Many {unMany = fromList TAG}tail"::[(B.Many ReadTag,String)]))|p<-[0,10,11,12]]
 sequence_[observe("read-char/"++show i)(show(readsPrec 0 s::[(B.Many Char,String)]))|(i,s)<-zip[0::Int ..]["Many {unMany = fromList \"ab\"}T","Many {unMany = fromList ['a']}T","Many {unMany = fromList (['a'])}T","Many {unMany = fromList ((['a']))}T"]]
 observe "read-duplicate" (show(readsPrec 0 "Many {unMany = fromList }tail"::[(B.Many Duplicate,String)]))
 observe "read-duplicate-whole" (show(read "Many {unMany = fromList }"::B.Many Duplicate))

 sequence_[observe("large/"++show n++"/"++show j++"/"++op)result|n<-[0,1,2,3,4,5,6,7,8,9,15,16,17,31,32,33,255,256,257,2048],let xs=[0..n-1]::[Int],(j,ys)<-zip[0::Int ..][xs,drop 1 xs,[],if null xs then[1]else init xs++[(-1)],reverse xs],(op,result)<-ops(B.fromList xs)(B.fromList ys)]
 sequence_[observe("zero/"++op)result|(op,result)<-ops(B.fromList[Zero 1])(B.fromList[Zero 2])]

 sequence_[observe("ne-only/"++show i++"/"++show j++"/"++op)result|(i,x)<-zip[0::Int ..][[],[1],[2],[1,2]],(j,y)<-zip[0::Int ..][[],[1],[2],[1,2]],(op,result)<-ops(B.fromList(map NeOnly x))(B.fromList(map NeOnly y))]
 sequence_[observe("le-only/"++show i++"/"++show j++"/"++op)result|(i,x)<-zip[0::Int ..][[],[1],[2],[1,2]],(j,y)<-zip[0::Int ..][[],[1],[2],[1,2]],(op,result)<-ops(B.fromList(map LeOnly x))(B.fromList(map LeOnly y))]
 sequence_[observe("eq-first/"++show i++"/"++show j++"/"++op)result|(i,x)<-zip[0::Int ..][[],[1],[2],[1,2]],(j,y)<-zip[0::Int ..][[],[1],[2],[1,2]],(op,result)<-ops(B.fromList(map EqFirst x))(B.fromList(map EqFirst y))]
 sequence_[observe("show-only/"++show p)(showsPrec p(B.fromList[ShowOnly 1,ShowOnly 2])"TAIL")|p<-[0,10,11]]
 sequence_[observe("show-conflict/"++show p)(showsPrec p(B.fromList[ShowConflict 1,ShowConflict 2])"TAIL")|p<-[0,10,11]]
