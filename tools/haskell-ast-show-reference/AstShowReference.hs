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
import GHC.Float (castWord64ToDouble)
import Text.Read (readMaybe)
import Text.Pandoc.Definition
import OriginalExamples (examples)
import Control.Exception (SomeException, try, evaluate, displayException)
import System.IO (stdout, hSetBuffering, BufferMode(LineBuffering))
as :: FromJSON a => Value -> a
as v = case fromJSON v of Success x -> x; Error e -> error e
arr (Array a) = V.toList a
arr _ = error "array"
shown :: forall a. (FromJSON a, Show a) => Int -> Value -> a -> String
shown p v _ = showsPrec p (as v :: a) ""
showValue p kind v = case kind :: String of
 "pandoc" -> shown p v (undefined :: Pandoc)
 "meta" -> shown p v (undefined :: Meta)
 "metaValue" -> shown p v (undefined :: MetaValue)
 "inline" -> shown p v (undefined :: Inline)
 "block" -> shown p v (undefined :: Block)
 "attr" -> shown p v (undefined :: Attr)
 "target" -> shown p v (undefined :: Target)
 "listAttributes" -> shown p v (undefined :: ListAttributes)
 "style" -> shown p v (undefined :: ListNumberStyle)
 "delim" -> shown p v (undefined :: ListNumberDelim)
 "alignment" -> shown p v (undefined :: Alignment)
 "quote" -> shown p v (undefined :: QuoteType)
 "math" -> shown p v (undefined :: MathType)
 "citationMode" -> shown p v (undefined :: CitationMode)
 "citation" -> shown p v (undefined :: Citation)
 "format" -> shown p v (undefined :: Format)
 "rowHeadColumns" -> shown p v (undefined :: RowHeadColumns)
 "rowSpan" -> shown p v (undefined :: RowSpan)
 "colSpan" -> shown p v (undefined :: ColSpan)
 "colWidth" -> shown p v (undefined :: ColWidth)
 "colSpec" -> shown p v (undefined :: ColSpec)
 "row" -> shown p v (undefined :: Row)
 "cell" -> shown p v (undefined :: Cell)
 "caption" -> shown p v (undefined :: Caption)
 "tableHead" -> shown p v (undefined :: TableHead)
 "tableBody" -> shown p v (undefined :: TableBody)
 "tableFoot" -> shown p v (undefined :: TableFoot)
 _ -> error kind
partial name n = take n $ case name :: String of
 "inline-head" -> show (Emph [error "inline head"])
 "inline-tail" -> show (Emph (Space : error "inline tail"))
 "caption-head" -> show (Caption (Just [error "caption head"]) [])
 "citation-head" -> show (Citation "id" [error "citation head"] [] NormalCitation 0 0)
 "infinite-list" -> show (Emph (repeat Space))
 "infinite-node" -> let x = Emph [x] in show x
 "infinite-block" -> let x = BlockQuote [x] in show x
 _ -> error name
deep name n = case name :: String of
 "inline" -> show (foldl' (\x _ -> Emph [x]) Space [1..n])
 "block" -> show (foldl' (\x _ -> BlockQuote [x]) HorizontalRule [1..n])
 "metaValue" -> show (foldl' (\x _ -> MetaList [x]) (MetaBool True) [1..n])
 "mixed" -> show (foldl' (\x _ -> Note [Plain [x]]) Space [1..n])
 _ -> error name
operation [String "examples"] = toJSON examples
operation [String "show",kind,p,v] = toJSON (showValue (as p) (as kind) v)
operation [String "prefix",kind,p,n,v] = toJSON (take (as n) (showValue (as p) (as kind) v))
operation [String "partial",name,n] = toJSON (partial (as name) (as n))
operation [String "deep",name,n] = toJSON (deep (as name) (as n :: Int))
operation [String "double",p,bits] = toJSON (showsPrec (as p) (ColWidth (castWord64ToDouble (as bits :: Word64))) "")
operation [String "text",kind,p,points] =
 let t = T.pack (map chr (as points :: [Int])); prec = as p
 in toJSON $ case as kind :: String of
  "inline" -> showsPrec prec (Str t) ""
  "metaValue" -> showsPrec prec (MetaString t) ""
  "format" -> showsPrec prec (Format t) ""
  _ -> error "text kind"
operation [String "native",txt] =
 let t = as txt :: String
     doc = case readMaybe t :: Maybe Pandoc of
            Just x -> Just x
            Nothing -> Pandoc nullMeta <$> (readMaybe t :: Maybe [Block])
 in maybe Null (\x -> object ["document" .= x, "show" .= show x]) doc
operation _ = error "operation"
main = do
 hSetBuffering stdout LineBuffering
 B.getContents >>= mapM_ one . B.split 10
 where one line | B.null line = pure ()
                | otherwise = case eitherDecode line of
                   Left e -> error e
                   Right req -> do
                    r <- try (evaluate (let b = encode (operation (arr req)) in B.length b `seq` b)) :: IO (Either SomeException B.ByteString)
                    B.putStr (case r of Right b -> b; Left e -> encode (object ["exception" .= takeWhile (/='\n') (displayException e)]))
                    B.putStr "\n"
