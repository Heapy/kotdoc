{-# LANGUAGE OverloadedStrings #-}
import Data.Aeson
import Data.Aeson.Types(parseEither)
import qualified Data.ByteString.Lazy.Char8 as B
import qualified Data.Text as T
import SourceOrgLocator
import qualified SourceOrgWriter as O
import SourceOrgLocatorUtil(splitStrWhen)
import Text.Pandoc.Definition
import Data.Char(isSpace,isPunctuation)
request=withObject "request" $ \o->do
 name<-o.:"name";kind<-o.:?"kind".!= "locator";input<-o.:"input"
 pure(name::T.Text,kind::T.Text,input::[Inline])
main=B.getContents>>=mapM_ observe.B.lines
observe line=case eitherDecode line>>=parseEither request of
 Left e->error e
 Right(name,kind,input)->B.putStrLn$encode$object["name".=name,"result".=case kind of
  "csl"->toJSON(T.concat(map cslJsonify input))
  "split"->toJSON(splitStrWhen(\c->isSpace c||(isPunctuation c&&c/=':'))input)
  "comma"->toJSON(maybeAddComma input)
  _->let(info,rest)=parseLocator O.locmap input in object["info".=fmap(\x->object["raw".=locatorRaw x,"label".=locatorLabel x,"locator".=locatorLoc x])info,"suffix".=rest]]
