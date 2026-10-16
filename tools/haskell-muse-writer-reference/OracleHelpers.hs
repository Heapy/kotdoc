module OracleHelpers (module Tests.Helpers, test, WriterFunction(..)) where
import Tests.Helpers hiding(test)
import OracleTree
import Text.Pandoc
import qualified Data.Text as T
data WriterFunction = WriterFunction WriterOptions
test :: (ToPandoc a, ToString b) => WriterFunction -> String -> (a,b) -> TestTree
test (WriterFunction opts) name (input,expected) = Leaf name (toPandoc input) opts (T.pack $ toString expected)
