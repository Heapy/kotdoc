module OracleTree where
import Text.Pandoc
import Data.Text(Text)
data TestTree = Group String [TestTree] | Leaf String Pandoc WriterOptions Text
testGroup = Group
