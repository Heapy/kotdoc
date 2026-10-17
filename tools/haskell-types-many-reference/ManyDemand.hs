{-# LANGUAGE ScopedTypeVariables, OverloadedStrings #-}
-- Original Builder/Seq/Foldable/Traversable demand oracle; no Kotlin expectations.
import qualified Text.Pandoc.Builder as P
import Text.Pandoc.Definition
import qualified Data.Sequence.Internal as I
import qualified Data.Foldable as F
import Data.Monoid
import Data.Semigroup
import Data.Functor.Identity
import Control.Exception
import Data.Aeson
import qualified Data.ByteString.Lazy.Char8 as B
newtype Direction=Direction Int
instance Eq Direction where Direction x == Direction y=x<y
newtype Choice=Choice Int deriving Eq
instance Ord Choice where
 compare _ _=error "compare"
 _ >= _=True
 _ <= _=False
 max _ _=error "max"
 min _ _=error "min"
newtype NoCompare=NoCompare Int deriving Eq
instance Ord NoCompare where compare _ _=error "compare";_ >= _=error "greater";_ <= _=error "less"
newtype Numeric=Numeric Int
instance Num Numeric where
 _+_=Numeric 7
 _*_=Numeric 9
 abs=id
 signum=id
 negate=id
 fromInteger=Numeric . fromInteger
partial :: P.Many Int
partial=P.Many(I.Seq(I.Deep 10 (I.One(I.Elem 1))(error "middle")(I.One(I.Elem 2))))
partialBlocks :: P.Blocks
partialBlocks=P.Many(I.Seq(I.Deep 10 (I.Two(I.Elem HorizontalRule)(I.Elem HorizontalRule))(error "middle")(I.Two(I.Elem HorizontalRule)(I.Elem HorizontalRule))))
cases :: [(String,String)]
cases=
 [("fmap-head",show(take 1(P.toList(fmap(const 7)(P.fromList[error "head",2]::P.Many Int)))))
 ,("replace-head",show(take 1(P.toList(7 <$ (P.fromList[error "head",2]::P.Many Int)))))
 ,("foldr-ignore",show(F.foldr(\_ _->7)(error "seed")(P.fromList[error "head",2]::P.Many Int)))
 ,("foldl-ignore",show(F.foldl(\_ _->7)(error "seed")(P.fromList[error "head",2]::P.Many Int)))
 ,("foldr-prime-ignore",show(F.foldr'(\_ _->7)(error "seed")(P.fromList[error "head",2]::P.Many Int)))
 ,("foldl-prime-ignore",show(F.foldl'(\_ _->7)(error "seed")(P.fromList[error "head",2]::P.Many Int)))
 ,("foldr1-ignore",show(F.foldr1 const(P.fromList[7,error "tail"]::P.Many Int)))
 ,("foldl1-ignore",show(F.foldl1(\_ y->y)(P.fromList[error "head",7]::P.Many Int)))
 ,("foldmap-ignore",show(getAny(F.foldMap(Any . (==1))(P.fromList[1,error "tail"]::P.Many Int))))
 ,("foldmap-prime-ignore",show(getAny(F.foldMap'(Any . (==1))(P.fromList[1,error "tail"]::P.Many Int))))
 ,("elem-ignore",show(F.elem 1(P.fromList[1,error "tail"]::P.Many Int)))
 ,("identity-length",show(F.length(runIdentity(traverse(\_ ->Identity(error "mapped"))(P.fromList[1,2,3]::P.Many Int)))))
 ,("maybe-fail",case traverse(\x->if x==1 then Nothing else Just x)(P.fromList[1,error "tail"]::P.Many Int)of Nothing->"Nothing";Just _->"Just")
 ,("partial-null",show(F.null partial))
 ,("partial-length",show(F.length partial))
 ,("partial-fmap-length",show(F.length(fmap(const 7)partial)))
 ,("partial-replace-length",show(F.length(7 <$ partial)))
 ,("partial-foldr",show(F.foldr(\x _->x)(error "seed")partial))
 ,("partial-foldl",show(F.foldl(\_ x->x)(error "seed")partial))
 ,("partial-foldr1",show(F.foldr1 const partial))
 ,("partial-foldl1",show(F.foldl1(\_ y->y)partial))
 ,("partial-foldmap",show(getAny(F.foldMap(Any . (==1))partial)))
 ,("partial-foldmap-prime",show(getAny(F.foldMap'(Any . (==1))partial)))
 ,("partial-identity-length",show(F.length(runIdentity(traverse(\_ ->Identity 7)partial))))
 ,("blocks-negative-root",show(F.length(stimes (-1::Int)(P.Many(error "root")::P.Blocks))))
 ,("inline-negative-root",show(F.length(stimes (-1::Int)(P.Many(error "root")::P.Inlines))))
 ,("blocks-huge-length",show(F.length(stimes (1099511627776::Int)(P.singleton(error "head")::P.Blocks))))
 ,("blocks-huge-list-head",show(take 1(P.toList(stimes (1099511627776::Int)(P.singleton HorizontalRule)))))
 ,("blocks-integer-wrap",show(F.length(stimes (18446744073709551618::Integer)(P.singleton HorizontalRule))))
 ,("blocks-integer-negative-wrap",show(F.length(stimes (18446744073709551615::Integer)(P.singleton HorizontalRule))))
 ,("inline-integer-space",show(F.length(stimes (1267650600228229401496703205376::Integer)(P.singleton Space))))
 ,("inline-huge-space",show(F.length(stimes (1099511627776::Int)(P.singleton Space))))
 ,("inline-empty-right",show(F.length((P.singleton(error "head")::P.Inlines)<>mempty)))
 ,("blocks-partial-append-length",show(F.length(partialBlocks<>partialBlocks)))
 ,("blocks-partial-repeat-length",show(F.length(stimes (2::Int)partialBlocks)))
 ,("inline-emph-head",show(F.length(P.singleton(Emph[error "head"])<>P.singleton(Emph[]))))
 ,("custom-eq-direction",show(F.elem(Direction 2)(P.fromList[Direction 3])))
 ,("custom-max-method",case F.maximum(P.fromList[Choice 1,Choice 2])of Choice x->show x)
 ,("custom-min-method",case F.minimum(P.fromList[Choice 1,Choice 2])of Choice x->show x)
 ,("max-singleton-method",case F.maximum(P.singleton(NoCompare 1))of NoCompare x->show x)
 ,("min-singleton-method",case F.minimum(P.singleton(NoCompare 1))of NoCompare x->show x)
 ,("max-head-before-middle",show(F.maximum(P.Many(I.Seq(I.Deep 10(I.Two(I.Elem(error "head"))(I.Elem(2::Int)))(error "middle")(I.One(I.Elem 3)))))))
 ,("min-head-before-middle",show(F.minimum(P.Many(I.Seq(I.Deep 10(I.Two(I.Elem(error "head"))(I.Elem(2::Int)))(error "middle")(I.One(I.Elem 3)))))))
 ,("custom-min-payload",case F.minimum(P.fromList[Choice 1,error "payload",Choice 3])of Choice x->show x)
 ,("custom-sum-demand",case F.sum(P.fromList[error "head",error "tail"]::P.Many Numeric)of Numeric x->show x)
 ,("custom-product-demand",case F.product(P.fromList[error "head",error "tail"]::P.Many Numeric)of Numeric x->show x)
 ,("inline-emph-content",show(F.length(P.singleton(Emph(error "content"))<>P.singleton(Emph[]))))
 ]
main=mapM_ (\(name,value)->do
 result<-try(evaluate(let bytes=encode value in B.length bytes `seq` bytes))::IO(Either SomeException B.ByteString)
 B.putStrLn(encode name)
 B.putStrLn(either(encode.object.pure.("exception" .=).takeWhile(/='\n').displayException)id result)) cases
