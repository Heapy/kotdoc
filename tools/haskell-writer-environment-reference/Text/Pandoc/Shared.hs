{-# LANGUAGE OverloadedStrings, FlexibleContexts #-}
-- Exact pinned upstream bodies; module/import wiring only. GPL-2.0-or-later.
-- Copyright (C) 2006-2024 John MacFarlane.
module Text.Pandoc.Shared where
import qualified Data.Text as T
import Data.Time (Day,formatTime,defaultTimeLocale,parseTimeM,toGregorian)
import Control.Monad (msum)
import System.FilePath (splitDirectories)
import qualified System.FilePath.Posix as Posix
import qualified Data.List as L
import Text.Pandoc.Definition
import Text.Pandoc.Walk

makeCanonical :: FilePath -> FilePath
makeCanonical = Posix.joinPath . transformPathParts . splitDirectories
 where  transformPathParts = reverse . L.foldl' go []
        go as        "."  = as
        go ("..":as) ".." = ["..", ".."] <> as
        go (_:as)    ".." = as
        go as        x    = x : as


tshow :: Show a => a -> T.Text
tshow = T.pack . show

normalizeDate :: T.Text -> Maybe T.Text
normalizeDate = fmap T.pack . normalizeDate' . T.unpack

-- | Like @'normalizeDate'@, but acts on 'String' instead of 'T.Text'.
normalizeDate' :: String -> Maybe String
normalizeDate' s = fmap (formatTime defaultTimeLocale "%F")
  (msum $ map (\fs -> parsetimeWith fs s >>= rejectBadYear) formats :: Maybe Day)
  where rejectBadYear day = case toGregorian day of
          (y, _, _) | y >= 1601 && y <= 9999 -> Just day
          _         -> Nothing
        parsetimeWith = parseTimeM True defaultTimeLocale
        formats = ["%x","%m/%d/%Y", "%D","%F", "%d %b %Y",
                    "%e %B %Y", "%b. %e, %Y", "%B %e, %Y",
                    "%Y%m%d", "%Y%m", "%Y"]

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

