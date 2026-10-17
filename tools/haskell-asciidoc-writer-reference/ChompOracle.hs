{-# LANGUAGE OverloadedStrings #-}
import Text.DocLayout
import Data.Aeson
import Data.Aeson.Types(parseEither,Parser)
import qualified Data.ByteString.Lazy.Char8 as B
import qualified Data.Text as T
parseDoc :: Value -> Parser(Doc T.Text)
parseDoc=withObject "doc" $ \o->do
 kind<-o .: "kind"
 case(kind::T.Text)of
  "text"->literal <$> o .: "text"
  "space"->pure BreakingSpace
  "cr"->pure CarriageReturn
  "newline"->pure NewLine
  "blank"->BlankLines <$> o .: "count"
  "empty"->pure Empty
  "concat"->Concat <$> (o .: "left" >>= parseDoc) <*> (o .: "right" >>= parseDoc)
  "prefix"->Prefixed <$> o .: "prefix" <*> (o .: "doc" >>= parseDoc)
  "flush"->Flush <$> (o .: "doc" >>= parseDoc)
  "before"->BeforeNonBlank <$> (o .: "doc" >>= parseDoc)
  "link"->Linked "target" <$> (o .: "doc" >>= parseDoc)
  _->error"unknown doc"
serialize :: Doc T.Text -> Value
serialize d=case d of
 Empty->object["kind".=("empty"::T.Text)]
 Text w t->object["kind".=("text"::T.Text),"width".=w,"text".=t]
 BreakingSpace->object["kind".=("space"::T.Text)]
 CarriageReturn->object["kind".=("cr"::T.Text)]
 NewLine->object["kind".=("newline"::T.Text)]
 BlankLines n->object["kind".=("blank"::T.Text),"count".=n]
 Concat x y->object["kind".=("concat"::T.Text),"left".=serialize x,"right".=serialize y]
 Prefixed s x->object["kind".=("prefix"::T.Text),"prefix".=s,"doc".=serialize x]
 Flush x->object["kind".=("flush"::T.Text),"doc".=serialize x]
 BeforeNonBlank x->object["kind".=("before"::T.Text),"doc".=serialize x]
 Linked t x->object["kind".=("link"::T.Text),"target".=t,"doc".=serialize x]
 _->error"unexpected doc"
main=B.getContents>>=mapM_(observe.either error id.eitherDecode).B.lines
observe v=case parseEither(withObject "request" $ \o->(,) <$> o .: "name" <*> (o .: "doc" >>= parseDoc))v of
 Left e->error e
 Right(name,d)->let c=chomp d in B.putStrLn$encode$object["name".=(name::T.Text),"doc".=serialize c,"output".=render Nothing c]
