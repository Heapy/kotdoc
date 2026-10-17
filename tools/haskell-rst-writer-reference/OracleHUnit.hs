module OracleHUnit where
import OracleTree
import Text.Pandoc
import SourceRstWriter(flatten)
data FlattenProbe=FlattenProbe Inline
probeFlatten=FlattenProbe
infix 1 @?=
(@?=) :: FlattenProbe -> [Inline] -> TestTree
FlattenProbe input @?= expected = FlattenLeaf "" input (flatten input) expected
testCase name (FlattenLeaf _ input actual expected)=FlattenLeaf name input actual expected
