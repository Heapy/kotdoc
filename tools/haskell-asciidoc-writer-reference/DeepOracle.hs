{-# LANGUAGE OverloadedStrings #-}
import SourceAsciiDocWriter
import DeepRecipes
import Text.Pandoc.Class
import Text.Pandoc.Definition
import qualified Text.Pandoc.Logging as L
import Data.Default
import Data.Aeson
import Data.Aeson.Types(parseEither)
import qualified Data.ByteString.Lazy.Char8 as B
import qualified Data.Text as T
main=B.getContents>>=mapM_(observe.either error id.eitherDecode).B.lines
observe v=case parseEither (withObject "deep" $ \o->(,,,) <$> o .: "name" <*> o .: "kind" <*> o .: "depth" <*> o .:? "legacy" .!= False) v of
 Left e->error e
 Right(name,kind,depth,legacy)->do
  let doc=deepDocument kind depth
  let result=runPure$do{out<-(if legacy then writeAsciiDocLegacy else writeAsciiDoc)def doc;logs<-getLog;pure(out,map logTag logs)}
  B.putStrLn$encode$case result of{Left e->object["name".=(name::T.Text),"error".=show e];Right(out,logs)->object["name".=name,"output".=out,"logs".=logs]}
logTag(L.BlockNotRendered _)="BlockNotRendered"::T.Text
logTag(L.InlineNotRendered _)="InlineNotRendered"
logTag _=error"unexpected deep log"
