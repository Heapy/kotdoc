module AsciiDocOracleTree where
import Text.Pandoc.Definition(Pandoc)
data TestTree=Group String [TestTree]|Leaf String Pandoc String String
testGroup=Group
