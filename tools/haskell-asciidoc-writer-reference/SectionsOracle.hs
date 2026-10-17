{-# LANGUAGE OverloadedStrings #-}
import Text.Pandoc.Shared(makeSections)
import Text.Pandoc.Definition
import Data.Aeson
import Data.Aeson.Types(parseEither)
import qualified Data.ByteString.Lazy.Char8 as B
import qualified Data.Text as T
main=B.getContents>>=mapM_(observe.either error id.eitherDecode).B.lines
observe v=case parseEither(withObject "sections" $ \o -> (,) <$> o .: "name" <*> o .: "document")v of
 Left e->error e
 Right(name,Pandoc m bs)->B.putStrLn$encode$object["name".=(name::T.Text),"document".=Pandoc m(makeSections False Nothing bs)]
