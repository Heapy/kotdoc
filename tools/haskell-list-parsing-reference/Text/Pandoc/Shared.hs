-- Unchanged safeRead/safeStrRead from Pandoc 3.12.1, GPL-2.0-or-later.
-- Copyright (C) 2006-2024 John MacFarlane.
module Text.Pandoc.Shared (safeRead) where
import Control.Monad (MonadPlus,mzero)
import Data.Char (isSpace)
import qualified Data.Text as T
safeRead :: (MonadPlus m, Read a) => T.Text -> m a
safeRead = safeStrRead . T.unpack

safeStrRead :: (MonadPlus m, Read a) => String -> m a
safeStrRead s = case reads s of
                  (d,x):_
                    | all isSpace x -> return d
                  _                 -> mzero
