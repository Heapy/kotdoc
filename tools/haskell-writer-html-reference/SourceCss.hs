{-# LANGUAGE OverloadedStrings #-}
-- Original Text.Pandoc.CSS + Shared.trim/isWS bodies, GPL-2.0-or-later.
-- Copyright (C) 2006-2024 John MacFarlane, 2015-2016 Mauro Bieg,
-- 2015 Ophir Lifshitz. Original notices retained in upstream/.
module SourceCss where
import Data.Either (fromRight)
import Data.Maybe (mapMaybe, listToMaybe)
import Data.Text (Text, pack)
import qualified Data.Text as T
import Text.Parsec
isWS :: Char -> Bool
isWS ' '  = True
isWS '\r' = True
isWS '\n' = True
isWS '\t' = True
isWS _    = False

-- | Remove leading and trailing space (including newlines) from string.
trim :: T.Text -> T.Text
trim = T.dropAround isWS

type Parser = Parsec Text ()

ruleParser :: Parser (Text, Text)
ruleParser = do
    p <- many1 (noneOf ":")  <* char ':'
    v <- many1 (noneOf ":;") <* optional (char ';') <* spaces
    return (trim $ pack p, trim $ pack v)

styleAttrParser :: Parser [(Text, Text)]
styleAttrParser = many1 ruleParser

-- | Parses a style string, returning the CSS attributes.
-- Returns an empty list on failure.
cssAttributes :: Text -> [(Text, Text)]
cssAttributes styleString =
  fromRight [] $ parse styleAttrParser "" styleString

-- | takes a list of keys/properties and a CSS string and
-- returns the corresponding key-value-pairs.
pickStylesToKVs :: [Text] -> Text -> [(Text, Text)]
pickStylesToKVs props styleAttr =
  filter (\s -> fst s `elem` props) $ cssAttributes styleAttr

-- | takes a list of key/property synonyms and a CSS string and maybe
-- returns the value of the first match (in order of the supplied list)
pickStyleAttrProps :: [Text] -> Text -> Maybe Text
pickStyleAttrProps lookupProps styleAttr = do
    styles <- either (const Nothing) Just $ parse styleAttrParser "" styleAttr
    listToMaybe $ mapMaybe (`lookup` styles) lookupProps
