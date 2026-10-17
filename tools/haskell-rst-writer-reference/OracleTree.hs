module OracleTree where
import Text.Pandoc
import Data.Text(Text)
data TestTree = Group String [TestTree] | Leaf String Pandoc WriterOptions (Maybe Text) Text | FlattenLeaf String Inline [Inline] [Inline]
testGroup = Group
