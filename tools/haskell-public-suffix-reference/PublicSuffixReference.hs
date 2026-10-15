{-# LANGUAGE OverloadedStrings #-}
module Main where
import Control.Exception (try,evaluate)
import Data.Aeson
import qualified Data.Aeson.KeyMap as K
import Data.Aeson.Types (parseMaybe)
import Data.Char (chr)
import Data.Maybe (fromJust)
import qualified Data.Map as M
import qualified Data.Text as T
import Data.Text.Encoding.Error (UnicodeException)
import qualified Data.ByteString as B
import qualified Data.ByteString.Lazy.Char8 as L
import System.IO
import qualified Network.PublicSuffixList.Lookup as P
import qualified Network.PublicSuffixList.DataStructure as D
import qualified Network.PublicSuffixList.Serialize as S
import Network.PublicSuffixList.Types

field :: FromJSON a => Value -> Key -> a
field (Object o) k = fromJust (K.lookup k o >>= parseMaybe parseJSON)
field _ _ = error "object required"
optional :: Value -> Key -> Maybe Value
optional (Object o) k = K.lookup k o
optional _ _ = Nothing
text :: Value -> T.Text
text v = T.pack $ map chr (fromJust $ parseMaybe parseJSON v :: [Int])
tree :: Value -> Tree T.Text
tree (Array xs) = Node $ M.fromList [ (text key,tree child) | Array pair <- foldr (:) [] xs, let [key,child] = foldr (:) [] pair ]
tree _ = error "tree required"
trees :: Value -> DataStructure
trees (Array xs) = let [a,b] = foldr (:) [] xs in (tree a,tree b)
trees _ = error "pair required"
viewTree :: Tree T.Text -> Value
viewTree (Node children') = toJSON [[toJSON (map fromEnum $ T.unpack key),viewTree value] | (key,value) <- M.toList children']
viewData :: DataStructure -> Value
viewData (a,b) = toJSON [viewTree a,viewTree b]
bytes :: B.ByteString -> Value
bytes = toJSON . B.unpack
run :: Value -> Value
run v = case field v "mode" :: String of
  "snapshot" -> bytes $ S.putDataStructure D.dataStructure
  "snapshot-tree" -> viewData D.dataStructure
  "lookup" -> let ds = maybe D.dataStructure trees (optional v "data")
                  domain = text $ fromJust $ optional v "domain"
              in object ["effective" .= P.effectiveTLDPlusOne' ds domain,"suffix" .= P.isSuffix' ds domain]
  "encode" -> let ds = trees $ fromJust $ optional v "data" in object ["bytes" .= bytes (S.putDataStructure ds),"tree" .= viewData ds]
  "decode" -> let ds = S.getDataStructure $ B.pack $ field v "bytes"
              in object ["bytes" .= bytes (S.putDataStructure ds),"tree" .= viewData ds]
  _ -> error "unknown mode"
main :: IO ()
main = do
 hSetBuffering stdout LineBuffering
 let loop = do
       eof <- hIsEOF stdin
       if eof then pure () else do
        input <- B.getLine
        let value = fromJust (decodeStrict input)
        result <- try (evaluate $ let out = encode (run value) in L.length out `seq` out) :: IO (Either UnicodeException L.ByteString)
        L.putStrLn $ either (const $ encode $ object ["error" .= ("decode" :: String)]) id result
        loop
 loop
