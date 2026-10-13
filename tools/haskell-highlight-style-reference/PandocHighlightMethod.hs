{-# LANGUAGE OverloadedStrings, PatternSynonyms, LambdaCase, DeriveDataTypeable, DeriveGeneric #-}
-- Unchanged definitions extracted from pinned Text.Pandoc.Options.
-- Copyright (C) 2012-2024 John MacFarlane, GPL-2.0-or-later.
module PandocHighlightMethod where
import Data.Aeson
import Data.Data(Data)
import Data.Typeable(Typeable)
import GHC.Generics(Generic)
import Data.Text(Text)
import qualified Data.Text as T
import Skylighting.Types(Style)

data HighlightMethod =
    Skylighting Style
  | IdiomaticHighlighting
  | DefaultHighlighting
  | NoHighlighting
  deriving (Show, Read, Eq, Data, Typeable, Generic)

-- | String representation of the idiomatic highlighting option.
pattern IdiomaticHighlightingString :: Text
pattern IdiomaticHighlightingString = "idiomatic"

-- | String representation of the default highlighting option.
pattern DefaultHighlightingString :: Text
pattern DefaultHighlightingString = "default"

-- | String representation of the no highlighting option
pattern NoHighlightingString :: Text
pattern NoHighlightingString = "none"

instance ToJSON HighlightMethod where
  toJSON NoHighlighting        = String NoHighlightingString
  toJSON IdiomaticHighlighting = String IdiomaticHighlightingString
  toJSON DefaultHighlighting   = String DefaultHighlightingString
  toJSON (Skylighting style)   = toJSON style

instance FromJSON HighlightMethod where
  parseJSON = \case
    String NoHighlightingString        -> pure NoHighlighting
    String IdiomaticHighlightingString -> pure IdiomaticHighlighting
    String DefaultHighlightingString   -> pure DefaultHighlighting
    String x           -> fail $ "Unknown highlighting method " <> T.unpack x
    Bool True          -> pure DefaultHighlighting
    Bool False         -> pure NoHighlighting
    Null               -> pure NoHighlighting
    v                  -> Skylighting <$> parseJSON v

