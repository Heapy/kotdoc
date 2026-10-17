module AsciiDocOracleHelpers(module Tests.Helpers,test)where
import Tests.Helpers hiding(test)
import AsciiDocOracleTree
test :: (ToString c,ToPandoc a)=>(a->String)->String->(a,c)->TestTree
test fn name(input,expected)=Leaf name(toPandoc input)(toString expected)(fn input)
