module OracleHelpers (module Tests.Helpers, test, ReaderFunction(..)) where
import Tests.Helpers hiding (test)
import OracleTree
import Data.Text (Text)
import Text.Pandoc.Definition (Pandoc)
data ReaderFunction = ReaderFunction [Text] (Text -> Pandoc)
test :: ToString c => ReaderFunction -> String -> (Text,c) -> TestTree
test (ReaderFunction exts fn) name (input,expected) = Leaf name input (toString expected) (fn input) exts
