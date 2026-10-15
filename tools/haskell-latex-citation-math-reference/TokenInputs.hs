{-# LANGUAGE FlexibleInstances #-}
-- Observes only original test inputs; does not change or evaluate expectations.
module TokenInputs where
import Data.Text(Text)
import Text.Pandoc.Definition
import Text.Pandoc.Builder(Blocks,Inlines)
import Text.Pandoc.Class(PandocPure,runPure)
type TestTree = [(String,Text)]
testGroup name children=[(name++"/"++label,input)|group<-children,(label,input)<-group]
class ToString a
instance ToString Pandoc
instance ToString Blocks
instance ToString Inlines
instance ToString [Block]
instance ToString Block
instance ToString String
instance ToString Text
test :: (ToString c) => (Text->Pandoc) -> String -> (Text,c) -> TestTree
test _ name (input,_)=[(name,input)]
infix 5 =?>
x =?> y = (x,y)
purely :: (b -> PandocPure a) -> b -> a
purely f = either (error . show) id . runPure . f
