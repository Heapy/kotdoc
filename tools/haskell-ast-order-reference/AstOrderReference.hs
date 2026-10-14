{-# LANGUAGE OverloadedStrings, ScopedTypeVariables #-}
module Main where
import Data.Aeson
import qualified Data.ByteString.Lazy as B
import qualified Data.Text as T
import qualified Data.Vector as V
import qualified Data.Map as M
import Data.List (foldl')
import Data.Word (Word64)
import Data.Char (chr)
import GHC.Float (castWord64ToDouble, castDoubleToWord64)
import Text.Pandoc.Definition
import Text.Pandoc.Arbitrary ()
import Test.QuickCheck (Arbitrary, arbitrary, resize, vectorOf)
import Test.QuickCheck.Gen (unGen)
import Test.QuickCheck.Random (mkQCGen)
import Control.Exception (SomeException, try, evaluate, displayException)
import System.IO (stdout, hSetBuffering, BufferMode(LineBuffering))
as :: FromJSON a => Value -> a
as v = case fromJSON v of Success x -> x; Error e -> error e
arr (Array a) = V.toList a
arr _ = error "array"
relationFields a b = ["eq" .= (a == b), "ne" .= (a /= b), "compare" .= show (compare a b),
                     "lt" .= (a < b), "le" .= (a <= b), "gt" .= (a > b), "ge" .= (a >= b)]
relations :: (Eq a, Ord a, Show a) => a -> a -> Value
relations a b = object (relationFields a b ++ ["minimum" .= show (min a b), "maximum" .= show (max a b)])
against :: forall a. (FromJSON a, Eq a, Ord a, Show a) => Value -> Value -> a -> Value
against v w _ = relations (as v :: a) (as w :: a)
ordinary kind v w = case kind :: String of
 "pandoc" -> against v w (undefined :: Pandoc)
 "meta" -> against v w (undefined :: Meta)
 "metaValue" -> against v w (undefined :: MetaValue)
 "inline" -> against v w (undefined :: Inline)
 "block" -> against v w (undefined :: Block)
 "attr" -> against v w (undefined :: Attr)
 "target" -> against v w (undefined :: Target)
 "listAttributes" -> against v w (undefined :: ListAttributes)
 "style" -> against v w (undefined :: ListNumberStyle)
 "delim" -> against v w (undefined :: ListNumberDelim)
 "alignment" -> against v w (undefined :: Alignment)
 "quote" -> against v w (undefined :: QuoteType)
 "math" -> against v w (undefined :: MathType)
 "citationMode" -> against v w (undefined :: CitationMode)
 "citation" -> against v w (undefined :: Citation)
 "format" -> against v w (undefined :: Format)
 "rowHeadColumns" -> against v w (undefined :: RowHeadColumns)
 "rowSpan" -> against v w (undefined :: RowSpan)
 "colSpan" -> against v w (undefined :: ColSpan)
 "colWidth" -> against v w (undefined :: ColWidth)
 "colSpec" -> against v w (undefined :: ColSpec)
 "row" -> against v w (undefined :: Row)
 "cell" -> against v w (undefined :: Cell)
 "caption" -> against v w (undefined :: Caption)
 "tableHead" -> against v w (undefined :: TableHead)
 "tableBody" -> against v w (undefined :: TableBody)
 "tableFoot" -> against v w (undefined :: TableFoot)
 _ -> error kind
listValues kind v w = case kind :: String of
 "inline" -> against v w (undefined :: [Inline])
 "block" -> against v w (undefined :: [Block])
 "metaValue" -> against v w (undefined :: [MetaValue])
 "colSpec" -> against v w (undefined :: [ColSpec])
 "citation" -> against v w (undefined :: [Citation])
 "row" -> against v w (undefined :: [Row])
 "cell" -> against v w (undefined :: [Cell])
 "text" -> against v w (undefined :: [T.Text])
 _ -> error kind
sample :: forall a. (Arbitrary a, ToJSON a, Eq a, Ord a, Show a) => String -> Int -> Int -> a -> Value
sample kind seed count _ = toJSON [object ["request" .= toJSON [toJSON ("relations"::String), toJSON kind, toJSON a, toJSON b], "expected" .= relations a b] | (a,b) <- pairs]
 where pairs = unGen (resize 4 (vectorOf count arbitrary)) (mkQCGen seed) 4 :: [(a,a)]
samples kind seed count = case kind :: String of
 "pandoc" -> sample kind seed count (undefined :: Pandoc)
 "meta" -> sample kind seed count (undefined :: Meta)
 "inline" -> sample kind seed count (undefined :: Inline)
 "block" -> sample kind seed count (undefined :: Block)
 "citation" -> sample kind seed count (undefined :: Citation)
 "row" -> sample kind seed count (undefined :: Row)
 "cell" -> sample kind seed count (undefined :: Cell)
 "caption" -> sample kind seed count (undefined :: Caption)
 "tableHead" -> sample kind seed count (undefined :: TableHead)
 "tableBody" -> sample kind seed count (undefined :: TableBody)
 "tableFoot" -> sample kind seed count (undefined :: TableFoot)
 "style" -> sample kind seed count (undefined :: ListNumberStyle)
 "delim" -> sample kind seed count (undefined :: ListNumberDelim)
 "alignment" -> sample kind seed count (undefined :: Alignment)
 "quote" -> sample kind seed count (undefined :: QuoteType)
 "math" -> sample kind seed count (undefined :: MathType)
 "citationMode" -> sample kind seed count (undefined :: CitationMode)
 _ -> error kind
widthRelations :: (Eq a, Ord a, Show a) => (a -> Double) -> a -> a -> Value
widthRelations extract a b = object (relationFields a b ++ ["minimum" .= show (min a b), "maximum" .= show (max a b),
 "minimumBits" .= castDoubleToWord64 (extract (min a b)), "maximumBits" .= castDoubleToWord64 (extract (max a b))])
unWidth (ColWidth d) = d
unWidth _ = error "width"
table d = Table nullAttr (Caption Nothing []) [(AlignDefault,ColWidth d)] (TableHead nullAttr []) [] (TableFoot nullAttr [])
unTable (Table _ _ cs _ _ _) = unWidth (snd (head cs))
unTable _ = error "table"
meta d = Meta (M.singleton "x" (MetaBlocks [table d]))
unMetaWidth (Meta m) = case m M.! "x" of MetaBlocks [b] -> unTable b; _ -> error "meta"
widthCell d = Cell nullAttr AlignDefault 1 1 [table d]
unCell (Cell _ _ _ _ [t]) = unTable t
widthRow d = Row nullAttr [widthCell d]
unRow (Row _ [c]) = unCell c
width kind a b = case kind :: String of
 "double" -> widthRelations id a b
 "colWidth" -> widthRelations unWidth (ColWidth a) (ColWidth b)
 "colSpec" -> widthRelations (unWidth . snd) (AlignDefault,ColWidth a) (AlignDefault,ColWidth b)
 "colSpecs" -> widthRelations (unWidth . snd . head) [(AlignDefault,ColWidth a)] [(AlignDefault,ColWidth b)]
 "block" -> widthRelations unTable (table a) (table b)
 "inline" -> widthRelations (\(Note [t]) -> unTable t) (Note [table a]) (Note [table b])
 "metaValue" -> widthRelations (\(MetaBlocks [t]) -> unTable t) (MetaBlocks [table a]) (MetaBlocks [table b])
 "meta" -> widthRelations unMetaWidth (meta a) (meta b)
 "pandoc" -> widthRelations (\(Pandoc m _) -> unMetaWidth m) (Pandoc (meta a) [table a]) (Pandoc (meta b) [table b])
 "pandoc-blocks" -> widthRelations (\(Pandoc _ [t]) -> unTable t) (Pandoc nullMeta [table a]) (Pandoc nullMeta [table b])
 "caption" -> widthRelations (\(Caption _ [t]) -> unTable t) (Caption Nothing [table a]) (Caption Nothing [table b])
 "caption-short" -> widthRelations (\(Caption (Just [Note [t]]) _) -> unTable t) (Caption (Just [Note [table a]]) []) (Caption (Just [Note [table b]]) [])
 "cell" -> widthRelations unCell (widthCell a) (widthCell b)
 "row" -> widthRelations unRow (widthRow a) (widthRow b)
 "tableHead" -> widthRelations (\(TableHead _ [r]) -> unRow r) (TableHead nullAttr [widthRow a]) (TableHead nullAttr [widthRow b])
 "tableFoot" -> widthRelations (\(TableFoot _ [r]) -> unRow r) (TableFoot nullAttr [widthRow a]) (TableFoot nullAttr [widthRow b])
 "tableBody" -> widthRelations (\(TableBody _ _ _ [r]) -> unRow r) (TableBody nullAttr 0 [] [widthRow a]) (TableBody nullAttr 0 [] [widthRow b])
 "tableBody-head" -> widthRelations (\(TableBody _ _ [r] _) -> unRow r) (TableBody nullAttr 0 [widthRow a] []) (TableBody nullAttr 0 [widthRow b] [])
 _ -> error kind
one :: (Eq a, Ord a) => String -> a -> a -> Value
one op a b = case op of
 "eq" -> toJSON (a == b); "ne" -> toJSON (a /= b); "compare" -> toJSON (show (compare a b))
 "lt" -> toJSON (a < b); "le" -> toJSON (a <= b); "gt" -> toJSON (a > b); "ge" -> toJSON (a >= b)
 _ -> error op
partial name op = case name :: String of
 "inline-tag" -> one op (Emph [error "inline head"]) (Str "x")
 "inline-element" -> one op (Emph [error "inline head"]) (Emph [Space])
 "inline-length" -> one op (Emph []) (Emph [error "unused head"])
 "inline-first" -> one op (Emph (Space:error "left tail")) (Emph (LineBreak:error "right tail"))
 "inline-self" -> let x = Emph [error "same head"] in one op x x
 "inline-tail" -> one op (Emph (Space:error "inline tail")) (Emph [Space])
 "citation-hash" -> one op (Citation "id" [error "citation head"] [] NormalCitation 0 7) (Citation "id" [Space] [] NormalCitation 0 8)
 "citation-self" -> let x = Citation "id" [error "citation head"] [] NormalCitation 0 7 in one op x x
 "meta-size" -> one op (Meta (M.singleton "a" (error "unused value"))) nullMeta
 "meta-size-more" -> one op (Meta (M.singleton "a" (error "first value"))) (Meta (M.fromList [("a",MetaBool True),("b",MetaBool False)]))
 "meta-key" -> one op (Meta (M.singleton "a" (error "unused left"))) (Meta (M.singleton "b" (error "unused right")))
 "meta-first" -> one op (Meta (M.fromList [("a",MetaBool False),("b",error "unused value")])) (Meta (M.fromList [("a",MetaBool True),("b",error "unused value")]))
 "meta-demand" -> one op (Meta (M.singleton "a" (error "map value"))) (Meta (M.singleton "a" (MetaBool False)))
 "pandoc-meta" -> one op (Pandoc (Meta (M.singleton "a" (MetaBool False))) [error "unused block"]) (Pandoc (Meta (M.singleton "b" (MetaBool False))) [error "unused block"])
 "caption-short" -> one op (Caption (Just [Space]) [error "unused block"]) (Caption (Just [LineBreak]) [error "unused block"])
 "caption-short-length" -> one op (Caption Nothing [error "unused block"]) (Caption (Just [error "unused inline"]) [error "unused block"])
 "row-attr" -> one op (Row ("a",[],[]) [error "unused cell"]) (Row ("b",[],[]) [error "unused cell"])
 "cell-attr" -> one op (Cell ("a",[],[]) AlignDefault 1 1 [error "unused block"]) (Cell ("b",[],[]) AlignDefault 1 1 [error "unused block"])
 "block-attr" -> one op (Div ("a",[],[]) [error "unused block"]) (Div ("b",[],[]) [error "unused block"])
 _ -> error name
shortRelations a b = object (relationFields a b)
deep kind n different = case kind :: String of
 "inline" -> let f = foldl' (\x _ -> Emph [x]) in shortRelations (f Space [1..n]) (f (if different then LineBreak else Space) [1..n])
 "block" -> let f = foldl' (\x _ -> BlockQuote [x]) in shortRelations (f HorizontalRule [1..n]) (f (if different then Plain [] else HorizontalRule) [1..n])
 "metaValue" -> let f = foldl' (\x _ -> MetaList [x]) in shortRelations (f (MetaBool False) [1..n]) (f (MetaBool different) [1..n])
 "mixed" -> let f = foldl' (\x _ -> Note [Plain [x]]) in shortRelations (f Space [1..n]) (f (if different then LineBreak else Space) [1..n])
 _ -> error kind
operation [String "relations",kind,a,b] = ordinary (as kind) a b
operation [String "list",kind,a,b] = listValues (as kind) a b
operation [String "sample",kind,seed,count] = samples (as kind) (as seed) (as count)
operation [String "width",kind,a,b] = width (as kind) (castWord64ToDouble (as a :: Word64)) (castWord64ToDouble (as b :: Word64))
operation [String "partial",name,op] = partial (as name) (as op)
operation [String "deep",kind,n,different] = deep (as kind) (as n :: Int) (as different)
operation [String "infinite",n] = shortRelations (repeat Space) (replicate (as n) Space ++ LineBreak:repeat Space)
operation [String "text",kind,a,b] = let ta = T.pack (map chr (as a :: [Int])); tb = T.pack (map chr (as b :: [Int])) in
 case as kind :: String of
  "text" -> relations ta tb
  "format" -> relations (Format ta) (Format tb)
  "inline" -> relations (Str ta) (Str tb)
  _ -> error "text kind"
operation _ = error "operation"
main = do
 hSetBuffering stdout LineBuffering
 B.getContents >>= mapM_ oneLine . B.split 10
 where oneLine line | B.null line = pure ()
                    | otherwise = case eitherDecode line of
                       Left e -> error e
                       Right req -> do
                        r <- try (evaluate (let b = encode (operation (arr req)) in B.length b `seq` b)) :: IO (Either SomeException B.ByteString)
                        B.putStr (case r of Right b -> b; Left e -> encode (object ["exception" .= takeWhile (/='\n') (displayException e)]))
                        B.putStr "\n"
