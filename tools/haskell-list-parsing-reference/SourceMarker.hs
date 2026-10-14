-- Original Writers.Shared body, GPL-2.0-or-later.
-- Copyright (C) 2013-2024 John MacFarlane.
module SourceMarker where
import Data.Text (Text)
import qualified Data.Text as T
import Data.Either (isRight)
import Text.Parsec (runParser,eof)
import Text.Pandoc.Parsing.Lists (anyOrderedListMarker)
import Text.Pandoc.Parsing.State (defaultParserState)
isOrderedListMarker :: Text -> Bool
isOrderedListMarker xs = not (T.null xs) && (T.last xs `elem` ['.',')']) &&
              isRight (runParser (anyOrderedListMarker >> eof)
                       defaultParserState "" xs)

