{-# LANGUAGE OverloadedStrings #-}
import Text.Pandoc.Shared(uniqueIdent)
import Text.Pandoc.Definition
import Text.Pandoc.Extensions
import qualified Data.Set as S
import Data.Aeson
import Data.Aeson.Types(parseEither)
import qualified Data.ByteString.Lazy.Char8 as B
import qualified Data.Text as T
main=B.getContents>>=mapM_(observe.either error id.eitherDecode).B.lines
observe v=case parseEither (withObject "identifier" $ \o->(,,,) <$> o .: "name" <*> o .: "inlines" <*> o .: "extensions" <*> o .: "used") v of
 Left e->error e
 Right(name,xs,ext,used)->B.putStrLn$encode$object["name".=(name::T.Text),"identifier".=uniqueIdent ext (xs::[Inline]) (S.fromList used)]
