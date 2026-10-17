module OrgOracleHelpers(module Tests.Helpers,test)where
import Tests.Helpers hiding(test)
import OrgOracleTree
import Data.Text(Text)
test :: (ToString c,ToPandoc a)=>(a->Text)->String->(a,c)->TestTree
test fn name(input,expected)=Leaf name(toPandoc input)(toString expected)(fn input)
