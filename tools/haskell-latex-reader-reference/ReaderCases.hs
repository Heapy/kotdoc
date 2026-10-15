{-# LANGUAGE FlexibleInstances #-}
{-# LANGUAGE OverloadedStrings #-}
-- Tests.Helpers ToString/ToPandoc conversions, Pandoc 3.12.1.
-- Copyright (C) 2006-2024 John MacFarlane; GPL-2.0-or-later.
-- Observe both the original expectation and actual; assert their original
-- lines-of-native-output equality before exposing any observation.
module ReaderCases where
import Data.Text (Text,unpack)
import qualified Data.Map as M
import Text.Pandoc
import Text.Pandoc.Builder
import Text.Pandoc.Shared (trimr)
type TestTree = [(String,Text,String,Pandoc)]
testGroup name children=[(name++"/"++label,input,expected,actual)|group<-children,(label,input,expected,actual)<-group]
class ToString a where
 toString :: a -> String
instance ToString Pandoc where
 toString d=unpack $ purely(writeNative def{writerTemplate=s}) d
  where s=case d of Pandoc(Meta m) _ | M.null m->Nothing;_->Just mempty
instance ToString Blocks where
 toString=unpack . purely(writeNative def) . doc
instance ToString [Block] where
 toString=toString . fromList
instance ToString Block where
 toString=toString . singleton
instance ToString Inlines where
 toString=unpack . trimr . purely(writeNative def) . doc . plain
instance ToString String where toString=id
instance ToString Text where toString=unpack
test :: ToString c => (Text->Pandoc) -> String -> (Text,c) -> TestTree
test f name(input,expected)=let actual=f input;golden=toString expected in
 if lines(toString actual)==lines golden then [(name,input,golden,actual)]
 else error("Original reader assertion failed: "++name++"\n"++toString actual++"\n"++golden)
infix 5 =?>
x =?> y=(x,y)
purely f=either(error.show)id . runPure . f
