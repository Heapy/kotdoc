{-# LANGUAGE OverloadedStrings #-}
-- Original instances executed unchanged. pandoc-types BSD-3-Clause; licenses/.
import Text.Pandoc.Arbitrary ()
import Text.Pandoc.Definition
import qualified Text.Pandoc.Builder as B
import qualified Data.Text as T
import Data.Char (chr,ord)
import Data.Aeson
import qualified Data.ByteString.Lazy.Char8 as BC
import Test.QuickCheck (Arbitrary,shrink,shrinkList)
import System.Environment (getArgs)
import Control.Exception (SomeException,evaluate,try)

attr = ("B",["B"],[("B","B")])
ils = [Emph [Str "a"],Str "B"]
blks = [BlockQuote [Plain [Str "a"]],Para [Str "B"]]
cap = Caption (Just ils) blks
cell = Cell attr AlignRight 0 3 blks
row = Row attr [cell,Cell nullAttr AlignDefault 2 0 []]
hd = TableHead attr [row]
bd = TableBody attr 2 [row] [row]
ft = TableFoot attr [row]
cit = Citation "id" ils ils NormalCitation 2 3
inlineCases = [Str "B1 ",Emph ils,Underline ils,Strong ils,Strikeout ils,Superscript ils,
 Subscript ils,SmallCaps ils,Quoted SingleQuote ils,Cite [cit,cit] ils,Code attr "B1 ",Space,SoftBreak,
 LineBreak,Math DisplayMath "B1 ",RawInline (Format "html") "B1 ",Link attr ils ("B","B"),
 Image attr ils ("B","B"),Note blks,Span attr ils]
blockCases = [Plain ils,Para ils,LineBlock [ils,ils],CodeBlock attr "B1 ",RawBlock (Format "html") "B1 ",
 BlockQuote blks,OrderedList (3,UpperRoman,TwoParens) [blks,blks],BulletList [blks,blks],
 DefinitionList [(ils,[blks]),(ils,[blks])],Header 3 attr ils,HorizontalRule,
 Table attr cap [(AlignRight,ColWidth 0.3)] hd [bd] ft,Figure attr cap blks,Div attr blks]
constructor x = takeWhile (/=' ') (show x)
record :: (ToJSON a, Arbitrary a) => String -> String -> Maybe Int -> a -> Value
record kind name limit x = let xs=shrink x; kept=maybe xs (`take` xs) limit in object
 ["kind" .= kind,"name" .= name,"input" .= x,"expected" .= kept,
  "limit" .= limit,"truncated" .= maybe False (\n -> not $ null $ drop n xs) limit]
recordInlines name x = let xs=map B.toList $ shrink (B.fromList x :: B.Inlines) in object
 ["kind" .= ("inlines"::String),"name" .= (name::String),"input" .= x,"expected" .= xs,"limit" .= Null,"truncated" .= False]
recordBlocks name x = let xs=map B.toList $ shrink (B.fromList x :: B.Blocks) in object
 ["kind" .= ("blocks"::String),"name" .= (name::String),"input" .= x,"expected" .= xs,"limit" .= Null,"truncated" .= False]
finite = [record "inline" (constructor x) Nothing x | x<-inlineCases] ++
 [record "block" (constructor x) Nothing x | x<-blockCases] ++
 [record "row" "Row" Nothing row,record "cell" "Cell" Nothing cell,record "head" "TableHead" Nothing hd,
  record "body" "TableBody" Nothing bd,record "foot" "TableFoot" Nothing ft,
  record "caption" "CaptionJust" Nothing cap,record "caption" "CaptionNothing" Nothing (Caption Nothing blks)] ++
 [recordInlines ("flatten-"++constructor x) [x] | x<-inlineCases] ++
 [recordBlocks ("flatten-"++constructor x) [x] | x<-blockCases] ++
 concat [[recordInlines ("length"++show n) (take n $ cycle ils),recordBlocks ("length"++show n) (take n $ cycle blks),
  record "inline" ("plain-list-Span-length"++show n) Nothing (Span nullAttr (take n $ cycle ils)),
  record "block" ("definition-list-length"++show n) Nothing (DefinitionList $ replicate n (ils,[blks]))]
  | n<-[0,1,2,3,4,7,8,9]] ++
 [record "inline" ("deep-"++show n) (Just 32) (iterate (Emph . (:[])) (Str "B") !! n) | n<-[32,128,256]] ++
 [record "block" ("deep-"++show n) (Just 32) (iterate (BlockQuote . (:[])) (Para [Str "B"]) !! n) | n<-[32,128,256]] ++
 [record "inline" ("text-length"++show n) Nothing (Str $ T.pack $ take n $ cycle ['B','\x1f600','\0','\xD800']) | n<-[0,1,2,3,4,7,8,9]] ++
 [record "inline" ("attr-mask"++show mask) Nothing (Code (if a then "B" else "",if b then ["B"] else [],if c then [("B","B")] else []) "")
  | (mask,(a,b,c))<-zip [0::Int ..] [(a,b,c)|a<-[False,True],b<-[False,True],c<-[False,True]]] ++
 [record "caption" "CaptionJustEmpty" Nothing (Caption (Just []) []),record "caption" "CaptionNothingEmpty" Nothing (Caption Nothing [])] ++
 [record "inline" "QuotedDouble" Nothing (Quoted DoubleQuote ils),record "inline" "MathInline" Nothing (Math InlineMath "B")] ++
 [record "inline" "cartesian-prefix" (Just 32) (Code ("B1 B1",["B1","B1"],[("B1","B1"),("B1","B1")]) "")]
charBatch start = let end=min 0x10ffff (start+1023)
                      expected = [ [ map ord (T.unpack t) | Str t <- shrink (Str $ T.singleton $ chr p)] | p<-[start..end] ]
 in object ["start" .= start,"end" .= end,"expected" .= expected]
out = BC.putStrLn . encode
probe :: String -> Int -> IO ()
probe name x = do
 result <- try (evaluate x) :: IO (Either SomeException Int)
 out $ object ["name" .= name,"value" .= either (const Nothing) Just result]
demand = do
 let xs=replicate 100000 (error "element demanded" :: Int)
 probe "removed-all-size" (length $ head $ shrinkList (const []) xs)
 probe "removed-half-size" (length $ shrinkList (const []) xs !! 1)
 probe "retained-element-error" (head $ shrinkList (const []) xs !! 1)
 probe "last-small-removal-size" (length $ last $ shrinkList (const []) (take 9 xs))
 let defs=replicate 100000 (error "definition demanded" :: ([Inline],[[Block]]))
 probe "definition-first-size" (case head $ shrink $ DefinitionList defs of DefinitionList ys -> length ys; _ -> -1)
 probe "citation-noop-list-count" (length $ shrink [error "citation demanded" :: Citation])
main = do
 args<-getArgs
 case args of
  ["chars"] -> mapM_ (out . charBatch) [0,1024..0x10ffff]
  ["demand"] -> demand
  _ -> mapM_ out finite
