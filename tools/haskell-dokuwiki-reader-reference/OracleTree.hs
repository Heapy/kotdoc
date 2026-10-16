module OracleTree where
import Data.Text (Text)
import Text.Pandoc.Definition (Pandoc)
data TestTree = Group String [TestTree] | Leaf String Text String Pandoc
testGroup = Group
