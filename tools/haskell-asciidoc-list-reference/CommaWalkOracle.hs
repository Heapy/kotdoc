{-# LANGUAGE OverloadedStrings #-}
-- Exact Writers.AsciiDoc fixCommas expression and pinned pandoc-types Walk.
-- Pandoc authors; GPL-2.0-or-later / Walk BSD-3-Clause.
import Text.Pandoc.Definition
import Text.Pandoc.Walk
import Data.Aeson
import Data.Aeson.Types(parseEither)
import qualified Data.ByteString.Lazy.Char8 as B
import qualified Data.Text as T
import Data.List(intersperse)
fixCommas (Str t)=intersperse(RawInline(Format "asciidoc")"&#44;")$map Str$T.splitOn "," t
fixCommas x=[x]
request=withObject "request" $ \o->(,) <$> o .: "name" <*> o .: "inlines"
main=B.getContents >>= mapM_ observe . B.lines
observe line=case eitherDecode line >>= parseEither request of
 Left e->error e
 Right(name,values)->B.putStrLn$encode$object["name".=(name::T.Text),"inlines".=walk(concatMap fixCommas)(values::[Inline])]
