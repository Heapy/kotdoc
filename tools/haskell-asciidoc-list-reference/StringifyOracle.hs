{-# LANGUAGE OverloadedStrings #-}
-- Pinned Shared.stringify source closure; Pandoc authors, GPL-2.0-or-later.
import Text.Pandoc.Definition
import Text.Pandoc.Shared(stringify)
import Data.Aeson
import Data.Aeson.Types(parseEither)
import qualified Data.ByteString.Lazy.Char8 as B
import qualified Data.Text as T
request=withObject "request" $ \o->(,) <$> o .: "name" <*> o .: "blocks"
main=B.getContents >>= mapM_ observe . B.lines
observe line=case eitherDecode line >>= parseEither request of
 Left e->error e
 Right(name,values)->B.putStrLn$encode$object["name".=(name::T.Text),"text".=stringify(values::[Block])]
