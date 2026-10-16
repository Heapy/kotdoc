{-# LANGUAGE OverloadedStrings #-}
import OriginalRemainingProperties
import Text.Pandoc.Arbitrary ()
import Text.Pandoc.Definition
import qualified Text.Pandoc.Builder as Builder
import qualified Test.QuickCheck as QC
import Test.QuickCheck.Gen (unGen)
import Test.QuickCheck.Random (mkQCGen)
import Data.Aeson
import qualified Data.ByteString.Lazy as B
import qualified Data.ByteString.Lazy.Char8 as BC
import qualified Data.Text.Encoding as T
import System.Environment (getArgs)

generated :: QC.Gen a -> Int -> Int -> a
generated g seed size = unGen g (mkQCGen seed) size
headCase :: Int -> Int -> Value
headCase seed width = let x=generated QC.arbitrary seed 3 :: TableHead
                          y=Builder.normalizeTableHead width x
                          project (TableHead _ rows)=rows
 in object ["kind" .= ("head" :: String),"seed" .= seed,"width" .= width,"input" .= x,"expected" .= y,
            "idempotent" .= sourceWidthNormIsIdempotent Builder.normalizeTableHead width x,
            "subset" .= sourceNormIsSubset Builder.normalizeTableHead project width x,
            "rowWidth" .= sourceRowCorrectWidth Builder.normalizeTableHead project width x]
bodyCase :: Int -> Int -> Value
bodyCase seed width = let x=generated QC.arbitrary seed 3 :: TableBody
                          y=Builder.normalizeTableBody width x
 in object ["kind" .= ("body" :: String),"seed" .= seed,"width" .= width,"input" .= x,"expected" .= y,
            "idempotent" .= sourceWidthNormIsIdempotent Builder.normalizeTableBody width x,
            "subset" .= sourceBodyIsSubset width x,"rowWidth" .= sourceBodyRowWidth width x]
footCase :: Int -> Int -> Value
footCase seed width = let x=generated QC.arbitrary seed 3 :: TableFoot
                          y=Builder.normalizeTableFoot width x
                          project (TableFoot _ rows)=rows
 in object ["kind" .= ("foot" :: String),"seed" .= seed,"width" .= width,"input" .= x,"expected" .= y,
            "idempotent" .= sourceWidthNormIsIdempotent Builder.normalizeTableFoot width x,
            "subset" .= sourceNormIsSubset Builder.normalizeTableFoot project width x,
            "rowWidth" .= sourceRowCorrectWidth Builder.normalizeTableFoot project width x]
figureCase :: Int -> Int -> String -> [Inline] -> Value
figureCase seed size label xs = object
 ["kind" .= ("figure" :: String),"seed" .= seed,"size" .= size,"witness" .= label,
  "input" .= xs,"expected" .= Builder.toList (Builder.simpleFigureWith ("test",[],[]) (Builder.fromList xs) "url" "title"),
  "predicate" .= sourceFigureRepresentation xs]
jsonCase :: Int -> Int -> Value
jsonCase seed size = let x=generated QC.arbitrary seed size :: Pandoc; wire=encode x
 in object ["kind" .= ("json" :: String),"seed" .= seed,"size" .= size,"input" .= x,
            "encoded" .= T.decodeUtf8 (B.toStrict wire),"decoded" .= (decode wire :: Maybe Pandoc),
            "predicate" .= prop_roundtrip x]
observations = concat [[headCase seed width,bodyCase seed width,footCase seed width] | seed<-[1..100],width<-[2..16]] ++
 [figureCase seed size ("" :: String) (generated QC.arbitrary seed size :: [Inline]) | seed<-[1..50],size<-[0,1,3,8,30,100]] ++
 [figureCase 0 0 label xs | (label,xs)<-[("empty",[]),("space",[Space]),("softbreak",[SoftBreak]),("linebreak",[LineBreak]),("emptyStr",[Str ""])]] ++
 [jsonCase seed ([0,1,3,8,30] !! (seed `mod` 5)) | seed<-[1..500]]
originalProperties :: [(String,QC.Property)]
originalProperties =
 [("p_tableNormHeadIdempotent",p_tableNormHeadIdempotent),("p_tableNormBodyIdempotent",p_tableNormBodyIdempotent),
  ("p_tableNormFootIdempotent",p_tableNormFootIdempotent),("p_tableNormHeadIsSubset",p_tableNormHeadIsSubset),
  ("p_tableNormBodyIsSubset",p_tableNormBodyIsSubset),("p_tableNormFootIsSubset",p_tableNormFootIsSubset),
  ("p_tableNormHeadRowWidth",p_tableNormHeadRowWidth),("p_tableNormBodyRowWidth",p_tableNormBodyRowWidth),
  ("p_tableNormFootRowWidth",p_tableNormFootRowWidth),("p_figureRepresentation figure representation",p_figureRepresentation),
  ("round-trip",QC.property prop_roundtrip)]
suite = mapM_ (\(name,prop) -> do
  result<-QC.quickCheckWithResult QC.stdArgs{QC.replay=Just(mkQCGen 611,0),QC.maxSuccess=1000,QC.maxSize=100,QC.chatty=False} prop
  BC.putStrLn $ encode $ object ["name" .= (name :: String),"success" .= QC.isSuccess result,"tests" .= QC.numTests result]) originalProperties
main = do
 args<-getArgs
 if args==["suite"] then suite else mapM_ (BC.putStrLn . encode) observations
