{-# LANGUAGE OverloadedStrings #-}
import SourceAsciiDocWriter
import Text.DocLayout(render)
import Data.Aeson
import Data.Aeson.Types(parseEither)
import qualified Data.ByteString.Lazy.Char8 as B
import qualified Data.Text as T
main=B.getContents>>=mapM_(observe.either error id.eitherDecode).B.lines
observe v=case parseEither (withObject "scalar" $ \o->(,,,) <$> o .: "name" <*> o .: "operation" <*> o .: "input" <*> o .:? "table" .!= False) v of
 Left e->error e
 Right(name,op,input,table)->B.putStrLn$encode$object["name".=(name::T.Text),"result".=(case (op::T.Text)of{"escape"->toJSON(render Nothing(escapeString(if table then InTable else Normal)input));"needs"->toJSON(needsEscaping input);_->error "unknown operation"})]
