{-# LANGUAGE FlexibleContexts #-}
{-# LANGUAGE OverloadedStrings #-}
-- Observation adapter: retain and execute the original helper's assertion;
-- additionally serialize its original input/expected value for the Kotlin port.
module Tests.Helpers (module OriginalHelpers,test) where
import OriginalHelpers hiding(test)
import qualified OriginalHelpers as Original
import Test.Tasty
import Test.Tasty.HUnit
import Data.Aeson
import qualified Data.ByteString.Lazy.Char8 as BL
import System.Environment(lookupEnv)

test :: (ToString a,ToString b,ToString c,ToPandoc a,HasCallStack) => (a->b)->String->(a,c)->TestTree
test fn name pair@(input,expected)=testGroup name
 [Original.test fn name pair
 ,testCase "serialize original input and expectation" $ do
   path<-lookupEnv "KOTDOC_HTML_UNIT_CAPTURE"
   case path of
    Nothing->pure()
    Just file->BL.appendFile file(encode(object["name" .= name,"document" .= toPandoc input,"expected" .= toString expected,"native" .= toString(fn input)]) <> BL.pack "\n")]
