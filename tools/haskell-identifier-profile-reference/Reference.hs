{-# LANGUAGE OverloadedStrings #-}
-- Development observations of the pinned original Shared helper and format modules.
import Text.Pandoc
import Text.Pandoc.Shared(uniqueIdent)
import Data.Aeson
import Data.Aeson.Types(parseEither,Parser)
import qualified Data.ByteString.Lazy.Char8 as B
import qualified Data.Set as S
import qualified Data.Text as T
import qualified Data.Char as C
import System.Environment(getArgs)
main=do
 args<-getArgs
 case args of
  ["--lowercase-mappings"]->B.putStrLn $ encode [(n,T.toLower(T.singleton(C.chr n)))|n<-[0::Int ..1114111],T.toLower(T.singleton(C.chr n))/=T.singleton(C.chr n)]
  _->B.getContents >>= mapM_ observe . B.lines
observe line=case eitherDecode line >>= parseEither request of
 Left e->error e
 Right(name,xs,used,exts)->B.putStrLn $ encode $ object["name" .= name,"identifier" .= uniqueIdent(extensionsFromList exts)xs(S.fromList used)]
request :: Value -> Parser (T.Text,[Inline],[T.Text],[Extension])
request=withObject "request" $ \o->(,,,) <$> o .: "name" <*> o .: "inlines" <*> o .:? "used" .!= [] <*> o .:? "extensions" .!= []
