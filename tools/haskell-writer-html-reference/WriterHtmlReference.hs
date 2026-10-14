{-# LANGUAGE OverloadedStrings, ScopedTypeVariables #-}
module Main where
import Data.Aeson
import qualified Data.ByteString.Lazy.Char8 as L
import qualified Data.Text as T
import qualified Data.Vector as V
import qualified Data.Set as Set
import Data.Char (chr, ord)
import Text.Pandoc.Definition
import qualified Text.Pandoc.XML as X
import qualified SourceCss as C
import qualified SourceWriterHtml as W
import qualified Text.DocLayout as D
import System.IO(hSetBuffering, stdout, BufferMode(LineBuffering))

as :: FromJSON a => Value -> a
as v = case fromJSON v of Success x -> x; Error e -> error e
arr (Array a) = V.toList a
arr _ = error "array"
j :: ToJSON a => a -> Value
j = toJSON
points :: Value -> String
points v = map (chr . as) (arr v)
text = T.pack . points

encDoc :: (a -> Value) -> D.Doc a -> Value
encDoc f d = case d of
 D.Text w x -> j[j("text"::T.Text), j w, f x]
 D.Prefixed p x -> j[j("prefix"::T.Text), j p, encDoc f x]
 D.Flush x -> j[j("flush"::T.Text), encDoc f x]
 D.BeforeNonBlank x -> j[j("before"::T.Text), encDoc f x]
 D.BreakingSpace -> j[j("space"::T.Text)]
 D.AfterBreak x -> j[j("after"::T.Text), j x]
 D.CarriageReturn -> j[j("cr"::T.Text)]
 D.NewLine -> j[j("newline"::T.Text)]
 D.BlankLines n -> j[j("blank"::T.Text), j n]
 D.Concat a b -> j[j("concat"::T.Text), encDoc f a, encDoc f b]
 D.Empty -> j[j("empty"::T.Text)]
 _ -> error "unsupported adapter doc"

unDoc :: D.HasChars a => (Value -> a) -> Value -> D.Doc a
unDoc f v = case arr v of
 [String "literal", x] -> D.literal(f x)
 [String "raw", w, x] -> D.Text(as w)(f x)
 [String "concat", x, y] -> D.Concat(unDoc f x)(unDoc f y)
 [String "prefix", p, x] -> D.Prefixed(as p)(unDoc f x)
 [String "flush", x] -> D.Flush(unDoc f x)
 [String "before", x] -> D.BeforeNonBlank(unDoc f x)
 [String "after", x] -> D.AfterBreak(as x)
 [String "blank", n] -> D.BlankLines(as n)
 [String "space"] -> D.BreakingSpace
 [String "cr"] -> D.CarriageReturn
 [String "newline"] -> D.NewLine
 [String "empty"] -> D.Empty
 _ -> error "doc"

observation :: D.HasChars a => (a -> Value) -> D.Doc a -> Value
observation f d = object ["doc" .= encDoc f d, "renders" .= map (f . (`D.render` d)) [Nothing,Just 0,Just 1,Just 12,Just 40]]

process :: (T.Text, Value) -> Value
process (op,v) = case op of
 "registries" -> object["html4Attributes" .= Set.toAscList X.html4Attributes,"html5Attributes" .= Set.toAscList X.html5Attributes,"rdfaAttributes" .= Set.toAscList X.rdfaAttributes]
 "css" -> case arr v of
   [props, s] -> let p=as props;t=text s in object["attributes" .= C.cssAttributes t,"picked" .= C.pickStylesToKVs p t,"first" .= C.pickStyleAttrProps p t]
   _ -> error "css"
 "style" -> case arr v of [p,kvs] -> j(W.htmlAddStyle(as p)(as kvs)); _ -> error "style"
 "alignment" -> j(W.htmlAlignmentToString(as v))
 "attrs" -> case arr v of
   [tag, attr] -> let a=as attr in object[
     "text" .= observation j (W.tagWithAttrs(text tag)a),
     "string" .= observation (j . map ord) (W.tagWithAttrs(points tag)a),
     "attributes" .= observation j (W.htmlAttrs a::D.Doc T.Text)]
   _ -> error "attrs"
 "xml-tag" -> case arr v of
   [name,attrs,contents] -> let { n=text name; a=as attrs;
                                td=unDoc text contents; sd=unDoc points contents }
                          in object[
     "text" .= j[observation j(X.inTags False n a td),observation j(X.inTags True n a td),observation j(X.selfClosingTag n a::D.Doc T.Text),observation j(X.inTagsSimple n td),observation j(X.inTagsIndented n td)],
     "string" .= j[observation (j.map ord)(X.inTags False n a sd),observation (j.map ord)(X.inTags True n a sd),observation (j.map ord)(X.selfClosingTag n a::D.Doc String),observation (j.map ord)(X.inTagsSimple n sd),observation (j.map ord)(X.inTagsIndented n sd)]]
   _ -> error "xml-tag"
 "xml-text" -> let t=text v in object["escaped" .= X.escapeStringForXML t,"ncname" .= X.escapeNCName t,"entities" .= X.toEntities t,"html5" .= X.toHtml5Entities t,"decoded" .= X.fromEntities t,"lookup" .= X.lookupEntity t]
 "unicode" -> let cs=points v;t=T.pack cs in object[
    "escaped" .= X.escapeStringForXML t,"char" .= T.concat(map X.escapeCharForXML cs),
    "start" .= T.concat(map (X.escapeNCName.T.singleton)cs),
    "body" .= T.concat(map (T.drop 1.X.escapeNCName.T.cons 'a'.T.singleton)cs),
    "entities" .= X.toEntities t,"html5" .= X.toHtml5Entities t,
    "numeric" .= T.concat(map (maybe (error "numeric") id.X.lookupEntity.T.pack.(('#':).(++";").show.ord))cs)]
 _ -> error "operation"

main = do
 hSetBuffering stdout LineBuffering
 L.getContents >>= mapM_ (\line -> case eitherDecode line of Right req -> L.putStrLn(encode(process req)); Left e -> error e) . L.lines
