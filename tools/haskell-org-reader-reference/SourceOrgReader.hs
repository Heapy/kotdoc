{- |
   Module      : SourceOrgReader
   Copyright   : Copyright (C) 2014-2024 Albert Krewinkel
   License     : GNU GPL, version 2 or above

   Maintainer  : Albert Krewinkel <albert+pandoc@tarleb.com>

Conversion of org-mode formatted plain text to 'Pandoc' document.
-}
module SourceOrgReader ( readOrg ) where

import SourceOrgReader.Blocks (blockList, meta)
import SourceOrgReader.ParserState (optionsToParserState)
import SourceOrgReader.Parsing (OrgParser, readWithM)

import Text.Pandoc.Class (PandocMonad)
import Text.Pandoc.Definition
import Text.Pandoc.Options
import Text.Pandoc.Parsing (reportLogMessages)
import Text.Pandoc.Sources (ToSources(..), ensureFinalNewlines)
import Control.Monad.Except (throwError)
import Control.Monad.Reader (runReaderT)

-- | Parse org-mode string and return a Pandoc document.
readOrg :: (PandocMonad m, ToSources a)
        => ReaderOptions -- ^ Reader options
        -> a
        -> m Pandoc
readOrg opts s = do
  parsed <- flip runReaderT def $
            readWithM parseOrg (optionsToParserState opts)
            (ensureFinalNewlines 2 (toSources s))
  case parsed of
    Right result -> return result
    Left  e      -> throwError e

--
-- Parser
--
parseOrg :: PandocMonad m => OrgParser m Pandoc
parseOrg = do
  blocks' <- blockList
  meta'   <- meta
  reportLogMessages
  return $ Pandoc meta' blocks'
