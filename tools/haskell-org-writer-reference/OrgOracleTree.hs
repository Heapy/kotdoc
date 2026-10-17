module OrgOracleTree where
import Data.Text(Text)
import Text.Pandoc.Definition(Pandoc)
data TestTree=Group String [TestTree]|Leaf String Pandoc String Text
testGroup=Group
