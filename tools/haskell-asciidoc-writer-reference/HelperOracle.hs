{-# LANGUAGE OverloadedStrings #-}
import SourceAsciiDocWriter
import Text.Pandoc.Definition
import Data.Aeson
import Data.Aeson.Types(parseEither)
import qualified Data.ByteString.Lazy.Char8 as B
import qualified Data.Text as T
main=B.getContents>>=mapM_(observe.either error id.eitherDecode).B.lines
observe v=case parseEither (withObject "helper" $ \o->(,,) <$> o .: "name" <*> o .: "operation" <*> o .: "rows") v of
 Left e->error e
 Right(name,op,rows)->B.putStrLn$encode$object["name".=(name::T.Text),"rows".=(case (op::T.Text) of {"headers"->adjustHeaders rows;"footers"->adjustFooters rows;"empty"->adjustEmptyRows rows;_->error "unknown operation"})]
