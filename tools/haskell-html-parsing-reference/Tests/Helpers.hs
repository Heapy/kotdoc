{-# LANGUAGE FlexibleContexts, OverloadedStrings #-}
-- Original test assertions are retained; this adds observation serialization.
module Tests.Helpers (module OriginalHelpers,test) where
import OriginalHelpers hiding(test)
import qualified OriginalHelpers as Original
import Test.Tasty
import Test.Tasty.HUnit
import Data.Aeson
import qualified Data.ByteString.Lazy.Char8 as BL
import System.Environment(lookupEnv)

test :: (ToString a,ToString b,ToString c,HasCallStack) => (a->b)->String->(a,c)->TestTree
test fn name pair@(input,expected)=testGroup name
 [Original.test fn name pair
 ,testCase "serialize original input and expectation" $ do
   path<-lookupEnv "KOTDOC_HTML_PARSING_CAPTURE"
   case path of
    Nothing->pure()
    Just file->BL.appendFile file(encode(object["name" .= name,"input" .= toString input,"expectedNative" .= toString expected,"actual" .= toString(fn input)]) <> BL.pack "\n")]
