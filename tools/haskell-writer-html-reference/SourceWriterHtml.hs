{-# LANGUAGE OverloadedStrings, LambdaCase, FlexibleContexts #-}
-- Original Text.Pandoc.Writers.Shared HTML bodies, GPL-2.0-or-later.
-- Copyright (C) 2013-2024 John MacFarlane; upstream/ retains the notice.
module SourceWriterHtml where
import Data.Text (Text)
import qualified Data.Text as T
import qualified Data.Set as Set
import Data.Text.Conversions (FromText(..))
import Text.DocLayout
import Text.Pandoc.Definition
import Text.Pandoc.XML (escapeStringForXML, rdfaAttributes, html5Attributes)
import SourceCss (cssAttributes)
-- | Produce an HTML tag with the given pandoc attributes.
tagWithAttrs :: (HasChars a, FromText a) => a -> Attr -> Doc a
tagWithAttrs tag attr = "<" <> literal tag <> (htmlAttrs attr) <> ">"

-- | Produce HTML for the given pandoc attributes, to be used in HTML tags
htmlAttrs :: (HasChars a, FromText a) => Attr -> Doc a
htmlAttrs (ident, classes, kvs) = addSpaceIfNotEmpty (hsep [
  if T.null ident
      then empty
      else "id=" <> doubleQuotes (literal $ fromText (escapeStringForXML ident))
  ,if null classes
      then empty
      else "class=" <> doubleQuotes
             (literal $ fromText . escapeStringForXML $ T.unwords classes)
  ,hsep (map (\(k,v) -> formatKey k <> "=" <>
                doubleQuotes (literal $ fromText (escapeStringForXML v))) kvs)
  ])
 where
   formatKey x = literal . fromText $
        if ((x `Set.member` html5Attributes || x `Set.member` rdfaAttributes)
            && x /= "label") -- #10048
             || T.any (== ':') x -- e.g. epub: namespace
             || "data-" `T.isPrefixOf` x
             || "aria-" `T.isPrefixOf` x
             || x == "markdown" -- #11685
           then x
           else "data-" <> x

addSpaceIfNotEmpty :: HasChars a => Doc a -> Doc a
addSpaceIfNotEmpty f = if isEmpty f then f else " " <> f

-- | Adds a key-value pair to the @style@ attribute.
htmlAddStyle :: (Text, Text) -> [(Text, Text)] -> [(Text, Text)]
htmlAddStyle (key, value) kvs =
  let cssToStyle = T.intercalate " " . map (\(k, v) -> k <> ": " <> v <> ";")
  in case break ((== "style") . fst) kvs of
    (_, []) ->
      -- no style attribute yet, add new one
      ("style", cssToStyle [(key, value)]) : kvs
    (xs, (_,cssStyles):rest) ->
      -- modify the style attribute
      xs ++ ("style", cssToStyle modifiedCssStyles) : rest
      where
        modifiedCssStyles =
          case break ((== key) . fst) $ cssAttributes cssStyles of
            (cssAttribs, []) -> (key, value) : cssAttribs
            (pre, _:post)    -> pre ++ (key, value) : post

-- | Get the html representation of an alignment key
htmlAlignmentToString :: Alignment -> Maybe Text
htmlAlignmentToString = \case
  AlignLeft    -> Just "left"
  AlignRight   -> Just "right"
  AlignCenter  -> Just "center"
  AlignDefault -> Nothing

