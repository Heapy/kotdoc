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
data TestTree = Group String [TestTree] | Unit String Text String Pandoc | Property String Int (Blocks->Bool)
testGroup = Group
newtype QuickCheckTests = QuickCheckTests Int deriving Eq
defaultValue = QuickCheckTests 100
askOption f = f defaultValue
withMaxSuccess n f = (n,f)
testProperty name (n,f) = Property name n f
units (Group name children)=[(name++"/"++label,input,expected,actual)|c<-children,(label,input,expected,actual)<-units c]
units (Unit label input expected actual)=[(label,input,expected,actual)]
units Property{}=[]
properties (Group _ children)=concatMap properties children
properties (Property name n f)=[(name,n,f)]
properties _=[]
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
 if lines(toString actual)==lines golden then Unit name input golden actual
 else error("Original reader assertion failed: "++name++"\n"++toString actual++"\n"++golden)
infix 5 =?>
x =?> y=(x,y)
purely f=either(error.show)id . runPure . f
