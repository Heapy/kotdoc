{-# LANGUAGE OverloadedStrings #-}
module OracleHelpers(module Tests.Helpers,test,testProfile,testFiles)where
import Tests.Helpers hiding(test)
import OracleTree
import Data.Text(Text)
import Text.Pandoc.Definition(Pandoc)
import Text.Pandoc.Options
import Text.Pandoc.Extensions
import Data.Aeson
import qualified Data.ByteString as BS
test :: ToString c => (Text->Pandoc)->String->(Text,c)->TestTree
test=testProfile "default"
testProfile profile fn name(input,expected)=Leaf name(object["input".=input,"extensions".=extensionsToList exts]) (toString expected)(fn input)
 where exts=case profile of
        "smart"->enableExtension Ext_smart(getDefaultExtensions "org")
        "fancy_lists"->enableExtension Ext_fancy_lists(getDefaultExtensions "org")
        _->getDefaultExtensions "org"
testFiles files fn name(input,expected)=Leaf name(object["input".=input,"extensions".=extensionsToList(getDefaultExtensions "org"),"files".=[object["path".=p,"bytes".=BS.unpack bs]|(p,bs)<-files]])(toString expected)(fn input)
