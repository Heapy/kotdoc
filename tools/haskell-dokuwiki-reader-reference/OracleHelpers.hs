module OracleHelpers (module Tests.Helpers, test) where
import Tests.Helpers hiding (test)
import OracleTree
import Data.Text (Text)
import Text.Pandoc.Definition (Pandoc)
test :: ToString c => (Text -> Pandoc) -> String -> (Text,c) -> TestTree
test fn name (input,expected) = Leaf name input (toString expected) (fn input)
