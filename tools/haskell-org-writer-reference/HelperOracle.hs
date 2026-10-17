{-# LANGUAGE OverloadedStrings #-}
import SourceOrgWriter
import Text.Pandoc.Definition
import Text.DocLayout(render)
import Data.Aeson
import Data.Aeson.Types(parseEither)
import qualified Data.ByteString.Lazy.Char8 as B
import qualified Data.Text as T
request=withObject "request" $ \o->(,,) <$> o.:"name" <*> o.:"kind" <*> o.:"input"
main=B.getContents>>=mapM_ observe.B.lines
observe line=case eitherDecode line>>=parseEither request of
 Left e->error e
 Right(name,kind,input)->B.putStrLn$encode$object["name".=(name::T.Text),"result".=case(kind::T.Text)of
  "target"->escapeLinkTarget input
  "description"->escapeLinkDescription input
  "path"->orgPath input
  "language"->pandocLangToOrg input
  "special"->replaceSpecialStrings input
  "escape"->render Nothing(escapeString input)
  _->error "unknown helper"]
