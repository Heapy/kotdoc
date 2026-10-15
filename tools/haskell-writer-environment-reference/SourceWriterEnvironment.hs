{-# LANGUAGE OverloadedStrings, FlexibleContexts #-}
-- Exact pinned upstream bodies; module/import wiring only. GPL-2.0-or-later.
-- Copyright (C) 2006-2024 John MacFarlane.
module SourceWriterEnvironment where
import qualified Data.Text as T
import Data.Text (Text)
import Data.Maybe (fromMaybe)
import Text.Collate.Lang (Lang(..))
import Text.Pandoc.Definition
import Text.Pandoc.Shared
import Text.Pandoc.Class.CommonState
import Text.Pandoc.Class.PandocMonad
lookupMetaString :: Text -> Meta -> Text
lookupMetaString key meta =
  case lookupMeta key meta of
         Just (MetaString s)    -> s
         Just (MetaInlines ils) -> stringifyInlines ils
         Just (MetaBlocks bs)   -> stringify bs
         Just (MetaBool b)      -> T.pack (show b)
         _                      -> ""

setupTranslations :: PandocMonad m => Meta -> m ()
setupTranslations meta = do
  let defLang = Lang "en" (Just "US") Nothing [] [] []
  lang <- case lookupMetaString "lang" meta of
            "" -> pure defLang
            s  -> fromMaybe defLang <$> toLang (Just s)
  setTranslations lang

setTranslations :: PandocMonad m => Lang -> m ()
setTranslations lang =
  modifyCommonState $ \st ->
    case stTranslations st of
      -- if a translation table is already loaded for this language,
      -- keep it, so we don't have to parse the translation file again:
      Just (l, Just _) | l == lang -> st
      _ -> st{ stTranslations = Just (lang, Nothing) }

