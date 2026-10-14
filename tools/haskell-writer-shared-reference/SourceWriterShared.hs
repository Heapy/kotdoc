{-# LANGUAGE OverloadedStrings, FlexibleContexts #-}
-- Original Text.Pandoc.Writers.Shared bodies, GPL-2.0-or-later.
-- Copyright (C) 2013-2024 John MacFarlane; only module/import wiring changed.
module SourceWriterShared where
import Data.Char (chr,ord,isSpace,isLetter,isUpper)
import Data.List (groupBy)
import qualified Data.Set as Set
import qualified Data.Text as T
import Data.Text (Text)
import Control.Monad (MonadPlus,mzero)
import Safe (lastMay)
import Text.DocLayout
import Text.Pandoc.Definition
import Text.Pandoc.Walk (Walkable(..))
import PandocOptionsModels
class HasSyntaxExtensions a where
  getExtensions :: a -> Extensions

instance HasSyntaxExtensions WriterOptions where
  getExtensions opts = writerExtensions opts

-- | Returns True if the given extension is enabled.
isEnabled :: HasSyntaxExtensions a => Extension -> a -> Bool
isEnabled ext opts = ext `extensionEnabled` getExtensions opts

extensionEnabled :: Extension -> Extensions -> Bool
extensionEnabled x (Extensions exts) = x `Set.member` exts

-- | Returns 'True' iff the argument is an inline 'Math' element of type
-- 'DisplayMath'.
isDisplayMath :: Inline -> Bool
isDisplayMath (Math DisplayMath _)          = True
isDisplayMath (Span _ [Math DisplayMath _]) = True
isDisplayMath _                             = False

-- | Remove leading and trailing 'Space' and 'SoftBreak' elements.
stripLeadingTrailingSpace :: [Inline] -> [Inline]
stripLeadingTrailingSpace = go . reverse . go . reverse
  where go (Space:xs)     = go xs
        go (SoftBreak:xs) = go xs
        go xs             = xs

-- | Put display math in its own block (for ODT/DOCX).
fixDisplayMath :: Block -> Block
fixDisplayMath (Plain lst)
  | any isDisplayMath lst && not (all isDisplayMath lst) =
    -- chop into several paragraphs so each displaymath is its own
    Div ("",["math"],[]) $
       map Plain $
       filter (not . null) $
       map stripLeadingTrailingSpace $
       groupBy (\x y -> (isDisplayMath x && isDisplayMath y) ||
                         not (isDisplayMath x || isDisplayMath y)) lst
fixDisplayMath (Para lst)
  | any isDisplayMath lst && not (all isDisplayMath lst) =
    -- chop into several paragraphs so each displaymath is its own
    Div ("",["math"],[]) $
       map Para $
       filter (not . null) $
       map stripLeadingTrailingSpace $
       groupBy (\x y -> (isDisplayMath x && isDisplayMath y) ||
                         not (isDisplayMath x || isDisplayMath y)) lst
fixDisplayMath x = x

-- | Converts a Unicode character into the ASCII sequence used to
-- represent the character in "smart" Markdown.
unsmartify :: WriterOptions -> Text -> Text
unsmartify opts = T.concatMap $ \c -> case c of
  '\8217' -> "'"
  '\8230' -> "..."
  '\8211'
    | isEnabled Ext_old_dashes opts -> "-"
    | otherwise                     -> "--"
  '\8212'
    | isEnabled Ext_old_dashes opts -> "--"
    | otherwise                     -> "---"
  '\8220' -> "\""
  '\8221' -> "\""
  '\8216' -> "'"
  _       -> T.singleton c

-- | Tries to convert a character into a unicode superscript version of
-- the character.
toSuperscript :: Char -> Maybe Char
toSuperscript '1' = Just '\x00B9'
toSuperscript '2' = Just '\x00B2'
toSuperscript '3' = Just '\x00B3'
toSuperscript '+' = Just '\x207A'
toSuperscript '-' = Just '\x207B'
toSuperscript '\x2212' = Just '\x207B' -- unicode minus
toSuperscript '=' = Just '\x207C'
toSuperscript '(' = Just '\x207D'
toSuperscript ')' = Just '\x207E'
toSuperscript c
  | c >= '0' && c <= '9' =
                 Just $ chr (0x2070 + (ord c - 48))
  | isSpace c = Just c
  | otherwise = Nothing

-- | Tries to convert a character into a unicode subscript version of
-- the character.
toSubscript :: Char -> Maybe Char
toSubscript '+' = Just '\x208A'
toSubscript '-' = Just '\x208B'
toSubscript '=' = Just '\x208C'
toSubscript '(' = Just '\x208D'
toSubscript ')' = Just '\x208E'
toSubscript '\x2212' = Just '\x208B' -- unicode minus
toSubscript c
  | c >= '0' && c <= '9' =
                 Just $ chr (0x2080 + (ord c - 48))
  | isSpace c = Just c
  | otherwise = Nothing

toSubscriptInline :: Inline -> Maybe Inline
toSubscriptInline Space = Just Space
toSubscriptInline (Span attr ils) = Span attr <$> traverse toSubscriptInline ils
toSubscriptInline (Str s) = Str . T.pack <$> traverse toSubscript (T.unpack s)
toSubscriptInline LineBreak = Just LineBreak
toSubscriptInline SoftBreak = Just SoftBreak
toSubscriptInline _ = Nothing

toSuperscriptInline :: Inline -> Maybe Inline
toSuperscriptInline Space = Just Space
toSuperscriptInline (Span attr ils) = Span attr <$> traverse toSuperscriptInline ils
toSuperscriptInline (Str s) = Str . T.pack <$> traverse toSuperscript (T.unpack s)
toSuperscriptInline LineBreak = Just LineBreak
toSuperscriptInline SoftBreak = Just SoftBreak
toSuperscriptInline _ = Nothing

-- | Returns 'True' iff the list of blocks has a @'Plain'@ as its last
-- element.
endsWithPlain :: [Block] -> Bool
endsWithPlain xs =
  case lastMay xs of
    Just Plain{} -> True
    Just (BulletList is) -> maybe False endsWithPlain (lastMay is)
    Just (OrderedList _ is) -> maybe False endsWithPlain (lastMay is)
    Just (DefinitionList defs) ->
      case lastMay defs of
        Just (_, ds) -> maybe False endsWithPlain (lastMay ds)
        Nothing      -> False
    _ -> False

splitSentences :: Doc Text -> Doc Text
splitSentences = go . toList
 where
  go [] = mempty
  go (Text len t : AfterBreak _ : BreakingSpace : xs)
    | isSentenceEnding t = Text len t <> NewLine <> go xs
  go (Text len t : BreakingSpace : xs)
    | isSentenceEnding t = Text len t <> NewLine <> go xs
  go (x:xs) = x <> go xs

  toList (Concat (Concat a b) c) = toList (Concat a (Concat b c))
  toList (Concat a b) = a : toList b
  toList x = [x]

  isSentenceEnding t =
    case T.unsnoc t of
      Just (t',c)
        | c == '.'
        , not (isInitial t') -> True
        | c == '!' || c == '?' -> True
        | c == ')' || c == ']' || c == '"' || c == '\x201D' ->
           case T.unsnoc t' of
             Just (t'',d) -> (d == '.' && not (isInitial t''))
                               || d == '!' || d == '?'
             _ -> False
      _ -> False
   where
    isInitial x = T.length x == 1 && T.all isUpper x

-- | Ensure that all identifiers start with a letter,
-- and modify internal links accordingly. (Yes, XML allows an
-- underscore, but HTML 4 doesn't, so we are more conservative.)
ensureValidXmlIdentifiers :: Pandoc -> Pandoc
ensureValidXmlIdentifiers = walk goInline . walk goBlock
 where
  fixAttr (ident, classes, kvs) =
    (case T.uncons ident of
      Nothing -> ident
      Just (c, _) | isLetter c -> ident
      _ -> "id_" <> ident,
     classes, kvs)
  fixSrc src =
    case T.uncons src of
      Just ('#',t) ->
        case T.uncons t of
          Just (c,_) | not (isLetter c) -> "#id_" <> t
          _ -> src
      _ -> src

  goInline (Span attr ils) = Span (fixAttr attr) ils
  goInline (Link attr ils (src, tit)) = Link (fixAttr attr) ils (fixSrc src, tit)
  goInline (Image attr ils (src, tit)) =
    Image (fixAttr attr) ils (fixSrc src, tit)
  goInline (Code attr txt) = Code (fixAttr attr) txt
  goInline x = x

  goBlock (Header lev attr ils) = Header lev (fixAttr attr) ils
  goBlock (CodeBlock attr txt) = CodeBlock (fixAttr attr) txt
  goBlock (Table attr cap colspecs thead tbodies tfoot) =
    Table (fixAttr attr) cap colspecs
      (goTableHead thead) (map goTableBody tbodies) (goTableFoot tfoot)
  goBlock (Div attr bs) = Div (fixAttr attr) bs
  goBlock (Figure attr cap bs) = Figure (fixAttr attr) cap bs
  goBlock x = x

  goTableHead (TableHead attr rows) = TableHead (fixAttr attr) (map goRow rows)
  goTableBody (TableBody attr rhc hd bd) =
    TableBody (fixAttr attr) rhc (map goRow hd) (map goRow bd)
  goTableFoot (TableFoot attr rows) = TableFoot (fixAttr attr) (map goRow rows)
  goRow (Row attr cells) = Row (fixAttr attr) (map goCell cells)
  goCell (Cell attr align rowspan colspan bs) =
    Cell (fixAttr attr) align rowspan colspan bs

-- | Convert links to spans; most useful when writing elements that must not
-- contain links, e.g. to avoid nested links.
removeLinks :: [Inline] -> [Inline]
removeLinks = walk go
 where
  go (Link attr ils _) = Span attr ils
  go x = x

toTaskListItem :: MonadPlus m => [Block] -> m (Bool, [Block])
toTaskListItem (Plain (Str "☐":Space:ils):xs) = pure (False, Plain ils:xs)
toTaskListItem (Plain (Str "☒":Space:ils):xs) = pure (True, Plain ils:xs)
toTaskListItem (Plain [Str "☐"]:xs)           = pure (False, Plain []:xs)
toTaskListItem (Plain [Str "☒"]:xs)           = pure (True, Plain []:xs)
toTaskListItem (Para  (Str "☐":Space:ils):xs) = pure (False, Para ils:xs)
toTaskListItem (Para  (Str "☒":Space:ils):xs) = pure (True, Para ils:xs)
toTaskListItem (Para  [Str "☐"]:xs)           = pure (False, Para []:xs)
toTaskListItem (Para  [Str "☒"]:xs)           = pure (True, Para []:xs)
toTaskListItem _                              = mzero

-- | Add an opener and closer to a Doc. If the Doc begins or ends
-- with whitespace, export this outside the opener or closer.
-- This is used for formats, like Markdown, which don't allow spaces
-- after opening or before closing delimiters.
delimited :: Doc Text -> Doc Text -> Doc Text -> Doc Text
delimited opener closer content =
  mconcat initialWS <> opener <> mconcat middle <> closer <> mconcat finalWS
 where
  contents = toList content
  (initialWS, rest) = span isWS contents
  (reverseFinalWS, reverseMiddle) = span isWS (reverse rest)
  finalWS = reverse reverseFinalWS
  middle = reverse reverseMiddle
  isWS NewLine = True
  isWS CarriageReturn = True
  isWS BreakingSpace = True
  isWS BlankLines{} = True
  isWS _ = False
  toList (Concat (Concat a b) c) = toList (Concat a (Concat b c))
  toList (Concat a b) = a : toList b
  toList x = [x]

