{-# LANGUAGE OverloadedStrings #-}
-- Development-only registration fold; never runs tests or resource actions.
module Main (main) where
import Data.Aeson (encode, object, (.=))
import qualified Data.ByteString.Lazy.Char8 as BL
import GHC.IO.Encoding (setLocaleEncoding, utf8)
import System.Directory (withCurrentDirectory)
import System.Environment (getArgs)
import Test.Tasty.Runners (foldTestTree, trivialFold, TreeFold(..), testsNames)
import qualified InventoryOriginalMain as Original

main :: IO ()
main = do
  setLocaleEncoding utf8
  [directory, originalTestBinary] <- getArgs
  withCurrentDirectory directory $ do
    let tree = Original.tests originalTestBinary
        paths :: [[String]]
        paths = foldTestTree
          trivialFold
            { foldSingle = \_ name _ -> [[name]]
            , foldGroup = \_ groupName children -> map (groupName :) (concat children)
            }
          mempty tree
    BL.putStrLn $ encode $ object
      [ "paths" .= paths
      , "names" .= testsNames mempty tree
      ]
