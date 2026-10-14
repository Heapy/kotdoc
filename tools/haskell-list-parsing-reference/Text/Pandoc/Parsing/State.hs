-- Test-only projection of the two ParserState fields accessed by Lists.
-- The full ParserState and its dependencies remain pending; no replacement
-- parser algorithm is used. The extra field checks preservation of caller state.
{-# LANGUAGE OverloadedStrings #-}
module Text.Pandoc.Parsing.State where
import qualified Data.Map as M
import Data.Text (Text)
data ParserState = ParserState { stateNextExample :: Int, stateExamples :: M.Map Text Int, untouched :: Text }
defaultParserState :: ParserState
defaultParserState = ParserState 1 M.empty "retained"
