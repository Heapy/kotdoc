module OracleTree where
import Data.Text(Text)
import Text.Pandoc.Definition(Pandoc)
import Data.Aeson(Value)
data TestTree=Group String [TestTree]|Leaf String Value String Pandoc
testGroup=Group
