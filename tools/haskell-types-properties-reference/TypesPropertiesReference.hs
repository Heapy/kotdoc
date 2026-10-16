{-# LANGUAGE OverloadedStrings, FlexibleContexts #-}
-- Original test predicates: Copyright (c) 2006-2023 John MacFarlane.
-- BSD-3-Clause; see licenses/pandoc-types-BSD-3-Clause.txt.
import Text.Pandoc.Arbitrary ()
import Text.Pandoc.Definition
import System.Environment (getArgs)
import qualified Text.Pandoc.Builder as Builder
import Text.Pandoc.Walk
import Data.Generics
import Data.List (tails)
import Data.Text (Text)
import qualified Data.Text as T
import qualified Data.Map as M
import qualified Data.Monoid as Monoid
import Data.Aeson
import qualified Data.ByteString.Lazy.Char8 as B
import Test.QuickCheck
import Test.QuickCheck.Gen (unGen)
import Test.QuickCheck.Random (mkQCGen)
p_walk :: (Typeable a, Walkable a Pandoc)
       => (a -> a) -> Pandoc -> Bool
p_walk f d = everywhere (mkT f) d == walk f d

p_walkList :: (Typeable a, Walkable [a] Pandoc)
       => ([a] -> [a]) -> Pandoc -> Bool
p_walkList f d = everywhere (mkT f) d == walk (foldr g []) d
  where g x ys = f (x:ys)

p_query :: (Eq a, Typeable a1, Monoid a, Walkable a1 Pandoc)
        => (a1 -> a) -> Pandoc -> Bool
p_query f d = everything mappend (mempty `mkQ` f) d == query f d

p_queryList :: (Eq a, Typeable a1, Monoid a, Walkable [a1] Pandoc)
            => ([a1] -> a) -> Pandoc -> Bool
p_queryList f d = everything mappend (mempty `mkQ` f) d ==
                  query (mconcat . map f . tails) d

inlineTrans :: Inline -> Inline
inlineTrans (Str xs) = Str $ T.toUpper xs
inlineTrans (Emph xs) = Strong xs
inlineTrans x = x

inlinesTrans :: [Inline] -> [Inline]
inlinesTrans ys | all whitespaceInline ys = []
  where
    whitespaceInline Space = True
    whitespaceInline LineBreak = True
    whitespaceInline SoftBreak = True
    whitespaceInline (Str "") = True
    whitespaceInline _ = False
inlinesTrans ys = ys

blockTrans :: Block -> Block
blockTrans (Plain xs) = Para xs
blockTrans (BlockQuote xs) = Div ("",["special"],[]) xs
blockTrans x = x

blocksTrans :: [Block] -> [Block]
blocksTrans [CodeBlock {}] = []
blocksTrans [BlockQuote xs] = xs
blocksTrans [Div _ xs] = xs
blocksTrans xs = xs

metaValueTrans :: MetaValue -> MetaValue
metaValueTrans (MetaBool x) = MetaBool $ not x
metaValueTrans (MetaString xs) = MetaString $ T.toUpper xs
metaValueTrans x = x

metaTrans :: Meta -> Meta
metaTrans (Meta metamap) = Meta $ M.mapKeys T.toUpper metamap

inlineQuery :: Inline -> Text
inlineQuery (Str xs) = xs
inlineQuery _ = ""

inlinesQuery :: [Inline] -> Monoid.Sum Int
inlinesQuery = Monoid.Sum . length

blockQuery :: Block -> [Int]
blockQuery (Header lev _ _) = [lev]
blockQuery _ = []

blocksQuery :: [Block] -> Monoid.Sum Int
blocksQuery = Monoid.Sum . length

metaValueQuery :: MetaValue -> Text
metaValueQuery (MetaString xs) = xs
metaValueQuery _ = ""

metaQuery :: Meta -> Monoid.Sum Int
metaQuery (Meta metamap) = Monoid.Sum $ M.size metamap


observe seed size = let d = unGen (arbitrary :: Gen Pandoc) (mkQCGen seed) size in object
  [ "seed" .= seed, "size" .= size, "input" .= d
  , "inlineTrans" .= everywhere (mkT inlineTrans) d
  , "blockTrans" .= everywhere (mkT blockTrans) d
  , "metaValueTrans" .= everywhere (mkT metaValueTrans) d
  , "metaTrans" .= everywhere (mkT metaTrans) d
  , "inlinesTrans" .= everywhere (mkT inlinesTrans) d
  , "blocksTrans" .= everywhere (mkT blocksTrans) d
  , "inlineQuery" .= everything mappend (mempty `mkQ` inlineQuery) d
  , "blockQuery" .= everything mappend (mempty `mkQ` blockQuery) d
  , "metaValueQuery" .= everything mappend (mempty `mkQ` metaValueQuery) d
  , "metaQuery" .= Monoid.getSum (everything mappend (mempty `mkQ` metaQuery) d)
  , "inlinesQuery" .= Monoid.getSum (everything mappend (mempty `mkQ` inlinesQuery) d)
  , "blocksQuery" .= Monoid.getSum (everything mappend (mempty `mkQ` blocksQuery) d)
  , "originalPredicates" .= and [p_walk inlineTrans d, p_walk blockTrans d,
      p_walk metaValueTrans d, p_walk metaTrans d, p_query inlineQuery d,
      p_query blockQuery d, p_query metaValueQuery d, p_query metaQuery d,
      p_walkList inlinesTrans d, p_walkList blocksTrans d,
      p_queryList inlinesQuery d, p_queryList blocksQuery d]
  ]
walkMain = mapM_ (B.putStrLn . encode . uncurry observe) [(seed, [0,1,3,8,30] !! (seed `mod` 5)) | seed <- [1..500]]

cleanAttr :: Attr -> Attr
cleanAttr _ = nullAttr
small :: Data a => a -> a
small = everywhere (mkT (T.take 1)) . everywhere (mkT cleanAttr)
      . everywhere (mkT (take 1 :: [Inline] -> [Inline]))
      . everywhere (mkT (take 1 :: [Block] -> [Block]))
      . everywhere (mkT (take 1 :: [Row] -> [Row]))
      . everywhere (mkT (take 1 :: [Cell] -> [Cell]))
      . everywhere (mkT (take 1 :: [TableBody] -> [TableBody]))
shrinkObservation :: (Arbitrary a, ToJSON a, Data a) => String -> Gen a -> Int -> Value
shrinkObservation kind gen seed = let x = small (unGen gen (mkQCGen seed) 3) in object
  ["kind" .= kind, "seed" .= seed, "input" .= x, "expected" .= shrink x]
shrinkPrefixObservation :: (Arbitrary a, ToJSON a) => String -> Gen a -> Int -> Value
shrinkPrefixObservation kind gen seed = let x = unGen gen (mkQCGen seed) 3; ys = shrink x in object
  ["kind" .= kind, "seed" .= seed, "input" .= x, "expected" .= take 32 ys,
   "limit" .= (32 :: Int), "truncated" .= (not $ null $ drop 32 ys)]
literalShrink :: (Arbitrary a, ToJSON a) => String -> Int -> a -> Value
literalShrink kind seed x = object ["kind" .= kind, "seed" .= seed, "input" .= x, "expected" .= shrink x]
shrinkMain = mapM_ (B.putStrLn . encode) $ [
    literalShrink "inline" (-1) (Code ("x",["a"],[("b","c")]) "A"),
    literalShrink "inline" (-2) (Span ("x",["a"],[("b","c")]) [Emph [Str "A",Space],Str "b"]),
    literalShrink "inline" (-3) (Cite [Citation "x" [Str "a"] [] NormalCitation 2 3] [Emph [Str "A"],Space]),
    literalShrink "block" (-4) (DefinitionList [([Str "A"],[[BlockQuote [Para [Str "b"]]]]),([Str "c"],[[Plain []]])])
    ] ++ concat
  [[shrinkPrefixObservation "inline" (arbitrary :: Gen Inline) seed,
    shrinkPrefixObservation "block" (arbitrary :: Gen Block) seed,
    shrinkPrefixObservation "caption" (arbitrary :: Gen Caption) seed,
    shrinkPrefixObservation "row" (arbitrary :: Gen Row) seed,
    shrinkPrefixObservation "cell" (arbitrary :: Gen Cell) seed,
    shrinkPrefixObservation "head" (arbitrary :: Gen TableHead) seed,
    shrinkPrefixObservation "body" (arbitrary :: Gen TableBody) seed,
    shrinkPrefixObservation "foot" (arbitrary :: Gen TableFoot) seed] | seed <- [1..10]] ++ concat
  [ [shrinkObservation "inline" (arbitrary :: Gen Inline) seed,
     shrinkObservation "block" (arbitrary :: Gen Block) seed,
     shrinkObservation "caption" (arbitrary :: Gen Caption) seed,
     shrinkObservation "row" (arbitrary :: Gen Row) seed,
     shrinkObservation "cell" (arbitrary :: Gen Cell) seed,
     shrinkObservation "head" (arbitrary :: Gen TableHead) seed,
     shrinkObservation "body" (arbitrary :: Gen TableBody) seed,
     shrinkObservation "foot" (arbitrary :: Gen TableFoot) seed,
     let x = small (unGen (arbitrary :: Gen Builder.Inlines) (mkQCGen seed) 3) in object
       ["kind" .= ("inlines" :: String), "seed" .= seed, "input" .= Builder.toList x, "expected" .= map Builder.toList (shrink x)],
     let x = small (unGen (arbitrary :: Gen Builder.Blocks) (mkQCGen seed) 3) in object
       ["kind" .= ("blocks" :: String), "seed" .= seed, "input" .= Builder.toList x, "expected" .= map Builder.toList (shrink x)]
    ] | seed <- [1..10]]
main = do
  args <- getArgs
  if args == ["shrink"] then shrinkMain else walkMain
