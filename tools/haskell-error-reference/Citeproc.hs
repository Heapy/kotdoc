{-# LANGUAGE StrictData, OverloadedStrings #-}
-- Exact source declaration/functions; remaining Citeproc implementation is not a shim.
module Citeproc (CiteprocError(..), prettyCiteprocError) where
import Data.Text (Text)

data CiteprocError =
    CiteprocXMLError Text
  | CiteprocParseError Text
  | CiteprocLocaleNotFound Text
  deriving (Show, Eq)

prettyCiteprocError :: CiteprocError -> Text
prettyCiteprocError (CiteprocXMLError t) =
  "CiteprocXMLError: " <> t
prettyCiteprocError (CiteprocParseError t) =
  "CiteprocParseError: " <> t
prettyCiteprocError (CiteprocLocaleNotFound t) =
  "CiteprocLocaleNotFound: " <> t
