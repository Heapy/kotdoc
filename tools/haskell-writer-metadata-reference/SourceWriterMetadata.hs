{-# LANGUAGE OverloadedStrings, FlexibleContexts, MultiParamTypeClasses #-}
-- Original Text.Pandoc.Writers.Shared metadata functions, GPL-2.0-or-later.
-- Copyright (C) 2013-2024 John MacFarlane. Module/import wiring only is changed.
module SourceWriterMetadata where
import qualified Data.ByteString.Lazy as BL
import Data.Aeson (ToJSON(..), encode)
import qualified Data.Map as M
import qualified Data.Text as T
import Data.Text (Text)
import Data.Text.Conversions (FromText(..))
import qualified Text.Pandoc.Builder as Builder
import Text.Pandoc.Definition
import PandocOptionsModels
import Text.DocLayout
import Text.DocTemplates (Context(..), Val(..), TemplateTarget, ToContext(..), FromContext(..))
import qualified Text.Pandoc.UTF8 as UTF8
import Text.Pandoc.Walk (Walkable(..))
-- | Create template Context from a 'Meta' and an association list
-- of variables, specified at the command line or in the writer.
-- Variables overwrite metadata fields with the same names.
-- If multiple variables are set with the same name, a list is
-- assigned.  Does nothing if 'writerTemplate' is Nothing.
metaToContext :: (Monad m, TemplateTarget a)
              => WriterOptions
              -> ([Block] -> m (Doc a))
              -> ([Inline] -> m (Doc a))
              -> Meta
              -> m (Context a)
metaToContext opts blockWriter inlineWriter meta =
  case writerTemplate opts of
    Nothing -> return mempty
    Just _  -> addVariablesToContext opts <$>
                metaToContext' blockWriter inlineWriter meta

-- | Like 'metaToContext, but does not include variables and is
-- not sensitive to 'writerTemplate'.
metaToContext' :: (Monad m, TemplateTarget a)
           => ([Block] -> m (Doc a))     -- ^ block writer
           -> ([Inline] -> m (Doc a))    -- ^ inline writer
           -> Meta
           -> m (Context a)
metaToContext' blockWriter inlineWriter (Meta metamap) =
  Context <$> mapM (metaValueToVal blockWriter inlineWriter) metamap

-- | Add variables to a template Context, using monoidal append.
-- Also add `meta-json`.  Note that metadata values are used
-- in template contexts only when like-named variables aren't set.
addVariablesToContext :: TemplateTarget a
                      => WriterOptions -> Context a -> Context a
addVariablesToContext opts c1 =
  c2 <> (fromText <$> writerVariables opts) <> c1
 where
   c2 = Context $
          M.insert "meta-json" (SimpleVal $ literal $ fromText jsonrep)
                               mempty
   jsonrep = UTF8.toText $ BL.toStrict $ encode $ toJSON c1

-- | Converts a 'MetaValue' into a doctemplate 'Val', using the given
-- converter functions.
metaValueToVal :: (Monad m, TemplateTarget a)
               => ([Block] -> m (Doc a))    -- ^ block writer
               -> ([Inline] -> m (Doc a))   -- ^ inline writer
               -> MetaValue
               -> m (Val a)
metaValueToVal blockWriter inlineWriter (MetaMap metamap) =
  MapVal . Context <$> mapM (metaValueToVal blockWriter inlineWriter) metamap
metaValueToVal blockWriter inlineWriter (MetaList xs) = ListVal <$>
  mapM (metaValueToVal blockWriter inlineWriter) xs
metaValueToVal _ _ (MetaBool b) = return $ BoolVal b
metaValueToVal _ inlineWriter (MetaString s) =
   SimpleVal <$> inlineWriter (Builder.toList (Builder.text s))
metaValueToVal blockWriter _ (MetaBlocks bs) = SimpleVal <$> blockWriter bs
metaValueToVal _ inlineWriter (MetaInlines is) = SimpleVal <$> inlineWriter is


-- | Retrieve a field value from a template context.
getField   :: FromContext a b => Text -> Context a -> Maybe b
getField field (Context m) = M.lookup field m >>= fromVal

-- | Set a field of a template context.  If the field already has a value,
-- convert it into a list with the new value appended to the old value(s).
-- This is a utility function to be used in preparing template contexts.
setField   :: ToContext a b => Text -> b -> Context a -> Context a
setField field val (Context m) =
  Context $ M.insertWith combine field (toVal val) m
 where
  combine newval (ListVal xs)   = ListVal (xs ++ [newval])
  combine newval x              = ListVal [x, newval]

-- | Reset a field of a template context.  If the field already has a
-- value, the new value replaces it.
-- This is a utility function to be used in preparing template contexts.
resetField :: ToContext a b => Text -> b -> Context a -> Context a
resetField field val (Context m) =
  Context (M.insert field (toVal val) m)

-- | Set a field of a template context if it currently has no value.
-- If it has a value, do nothing.
-- This is a utility function to be used in preparing template contexts.
defField   :: ToContext a b => Text -> b -> Context a -> Context a
defField field val (Context m) =
  Context (M.insertWith f field (toVal val) m)
  where
    f _newval oldval = oldval

-- | Get the contents of the `lang` metadata field or variable.
getLang :: WriterOptions -> Meta -> Maybe Text
getLang opts meta =
  case lookupContext "lang" (writerVariables opts) of
        Just s -> Just s
        _      ->
          case lookupMeta "lang" meta of
               Just (MetaBlocks [Para [Str s]])  -> Just s
               Just (MetaBlocks [Plain [Str s]]) -> Just s
               Just (MetaInlines [Str s])        -> Just s
               Just (MetaString s)               -> Just s
               _                                 -> Nothing

-- | Retrieve the metadata value for a given @key@
-- and convert to Bool.
lookupMetaBool :: Text -> Meta -> Bool
lookupMetaBool key meta =
  case lookupMeta key meta of
      Just (MetaBlocks bs)  -> not (null bs)
      Just (MetaInlines ils) -> not (null ils)
      Just (MetaString x)  -> not (T.null x)
      Just (MetaBool True) -> True
      _                    -> False

-- | Retrieve the metadata value for a given @key@
-- and extract blocks.
--
-- Note that an empty list is returned for maps, lists, and booleans.
lookupMetaBlocks :: Text -> Meta -> [Block]
lookupMetaBlocks key meta =
  case lookupMeta key meta of
         Just (MetaBlocks bs)   -> bs
         Just (MetaInlines ils) -> [Plain ils]
         Just (MetaString s)    -> [Plain [Str s]]
         _                      -> []

-- | Retrieve the metadata value for a given @key@
-- and extract inlines.
--
-- Note that an empty list is returned for maps and lists.
lookupMetaInlines :: Text -> Meta -> [Inline]
lookupMetaInlines key meta =
  case lookupMeta key meta of
         Just (MetaString s)           -> [Str s]
         Just (MetaInlines ils)        -> ils
         Just (MetaBlocks [Plain ils]) -> ils
         Just (MetaBlocks [Para ils])  -> ils
         _                             -> []

-- | Retrieve the metadata value for a given @key@
-- and convert to String.
--
-- Note that an empty list is returned for maps, lists, and booleans.
lookupMetaString :: Text -> Meta -> Text
lookupMetaString key meta =
  case lookupMeta key meta of
         Just (MetaString s)    -> s
         Just (MetaInlines ils) -> stringifyInlines ils
         Just (MetaBlocks bs)   -> stringify bs
         Just (MetaBool b)      -> T.pack (show b)
         _                      -> ""

stringify :: Walkable Inline a => a -> T.Text
stringify = T.concat . query go . walk fixInlines
  where go :: Inline -> [T.Text]
        go Space        = [" "]
        go SoftBreak    = [" "]
        go (Str x)      = [x]
        go (Code _ x)   = [x]
        go (Math _ x)   = [x]
        go (RawInline (Format "html") t)
          | "<br" `T.isPrefixOf` t = [" "] -- see #2105
        go LineBreak    = [" "]
        go _            = []

        fixInlines :: Inline -> Inline
        fixInlines (Cite _ ils) = Cite [] ils
        fixInlines (Note _) = Note []
        fixInlines (q@Quoted{}) = deQuote q
        fixInlines x = x

-- | Like 'stringify', but specialized to sequences of inlines
-- (e.g. @['Inline']@ or 'Inlines').  Produces the same result as
-- 'stringify' in a single pass, without rebuilding the tree.
stringifyInlines :: Foldable t => t Inline -> T.Text
stringifyInlines ils0 = T.concat $ foldr go [] ils0
  where
    go :: Inline -> [T.Text] -> [T.Text]
    go il acc = case il of
      Str x           -> x : acc
      Space           -> " " : acc
      SoftBreak       -> " " : acc
      Code _ x        -> x : acc
      Math _ x        -> x : acc
      LineBreak       -> " " : acc
      RawInline (Format "html") t
        | "<br" `T.isPrefixOf` t -> " " : acc -- see #2105
      RawInline _ _   -> acc
      Note _          -> acc -- footnotes are skipped
      Cite _ ils      -> foldr go acc ils -- citation metadata is dropped
      Quoted SingleQuote ils -> "\8216" : foldr go ("\8217" : acc) ils
      Quoted DoubleQuote ils -> "\8220" : foldr go ("\8221" : acc) ils
      Emph ils        -> foldr go acc ils
      Underline ils   -> foldr go acc ils
      Strong ils      -> foldr go acc ils
      Strikeout ils   -> foldr go acc ils
      Superscript ils -> foldr go acc ils
      Subscript ils   -> foldr go acc ils
      SmallCaps ils   -> foldr go acc ils
      Span _ ils      -> foldr go acc ils
      Link _ ils _    -> foldr go acc ils
      Image _ ils _   -> foldr go acc ils

-- | Unwrap 'Quoted' inline elements, enclosing the contents with
-- English-style Unicode quotes instead.
deQuote :: Inline -> Inline
deQuote (Quoted SingleQuote xs) =
  Span ("",[],[]) (Str "\8216" : xs ++ [Str "\8217"])
deQuote (Quoted DoubleQuote xs) =
  Span ("",[],[]) (Str "\8220" : xs ++ [Str "\8221"])
deQuote x = x

