{-# LANGUAGE OverloadedStrings, DeriveDataTypeable, DeriveGeneric, GeneralizedNewtypeDeriving #-}
-- Verbatim pinned declarations/defaults only; test transport is in ModelShowReference.
-- Copyright (C) 2012-2024 John MacFarlane. GPL-2.0-or-later; see licenses/.
module PandocOptionsModels where
import Data.Data(Data)
import Data.Typeable(Typeable)
import GHC.Generics(Generic)
import Data.Default(Default(..))
import Data.String(IsString)
import Data.Text(Text)
import qualified Data.Text as T
import qualified Data.Set as Set
import qualified Data.Map as M
import Data.Aeson(ToJSON,FromJSON)
import Skylighting.Types(SyntaxMap,Style)
import Text.DocTemplates(Context(..),Template)
data Extension =
      Ext_abbreviations       -- ^ PHP markdown extra abbreviation definitions
    | Ext_alerts              -- ^ Special block quotes become alerts
    | Ext_all_symbols_escapable  -- ^ Make all non-alphanumerics escapable
    | Ext_amuse -- ^ Enable Text::Amuse extensions to Emacs Muse markup
    | Ext_angle_brackets_escapable  -- ^ Make < and > escapable
    | Ext_ascii_identifiers   -- ^ ascii-only identifiers for headers;
                              -- presupposes Ext_auto_identifiers
    | Ext_attributes          -- ^ Generic attribute syntax
    | Ext_auto_identifiers    -- ^ Automatic identifiers for headers
    | Ext_autolink_bare_uris  -- ^ Make all absolute URIs into links
    | Ext_backtick_code_blocks    -- ^ GitHub style ``` code blocks
    | Ext_blank_before_blockquote -- ^ Require blank line before a blockquote
    | Ext_blank_before_header     -- ^ Require blank line before a header
    | Ext_bracketed_spans         -- ^ Bracketed spans with attributes
    | Ext_citations           -- ^ Pandoc/citeproc citations
    | Ext_definition_lists    -- ^ Definition lists as in pandoc, mmd, php
    | Ext_east_asian_line_breaks  -- ^ Newlines in paragraphs are ignored between
                                  --   East Asian wide characters. Note: this extension
                                  --   does not affect readers/writers directly; it causes
                                  --   the eastAsianLineBreakFilter to be applied after
                                  --   parsing, in Text.Pandoc.App.convertWithOpts.
    | Ext_element_citations   -- ^ Use element-citation elements for JATS citations
    | Ext_emoji               -- ^ Support emoji like :smile:
    | Ext_empty_paragraphs -- ^ Allow empty paragraphs
    | Ext_epub_html_exts      -- ^ Recognise the EPUB extended version of HTML
    | Ext_escaped_line_breaks     -- ^ Treat a backslash at EOL as linebreak
    | Ext_example_lists       -- ^ Markdown-style numbered examples
    | Ext_fancy_lists         -- ^ Enable fancy list numbers and delimiters
    | Ext_fenced_code_attributes  -- ^ Allow attributes on fenced code blocks
    | Ext_fenced_code_blocks  -- ^ Parse fenced code blocks
    | Ext_fenced_divs             -- ^ Allow fenced div syntax :::
    | Ext_footnotes           -- ^ Pandoc\/PHP\/MMD style footnotes
    | Ext_four_space_rule     -- ^ Require 4-space indent for list contents
    | Ext_gfm_auto_identifiers  -- ^ Use GitHub's method for generating
                              -- header identifiers; presupposes
                              -- Ext_auto_identifiers
    | Ext_grid_tables         -- ^ Grid tables (pandoc, reST)
    | Ext_gutenberg           -- ^ Use Project Gutenberg conventions for plain
    | Ext_hard_line_breaks    -- ^ All newlines become hard line breaks
    | Ext_header_attributes   -- ^ Explicit header attributes {#id .class k=v}
    | Ext_table_attributes    -- ^ Explicit table attributes after caption
    | Ext_ignore_line_breaks  -- ^ Newlines in paragraphs are ignored
    | Ext_implicit_figures    -- ^ A paragraph with just an image is a figure
    | Ext_implicit_header_references -- ^ Implicit reference links for headers
    | Ext_inline_code_attributes  -- ^ Allow attributes on inline code
    | Ext_inline_notes        -- ^ Pandoc-style inline notes
    | Ext_intraword_underscores  -- ^ Treat underscore inside word as literal
    | Ext_latex_macros        -- ^ Parse LaTeX macro definitions (for math only)
    | Ext_line_blocks         -- ^ RST style line blocks
    | Ext_link_attributes         -- ^ link and image attributes
    | Ext_lists_without_preceding_blankline -- ^ Allow lists without preceding blank
    | Ext_literate_haskell    -- ^ Enable literate Haskell conventions
    | Ext_mark                -- ^ Enable ==mark== syntax to highlight text
    | Ext_markdown_attribute      -- ^ Interpret text inside HTML as markdown iff
                                  --   container has attribute 'markdown'
    | Ext_markdown_in_html_blocks -- ^ Interpret as markdown inside HTML blocks
    | Ext_mmd_header_identifiers -- ^ Multimarkdown style header identifiers [myid]
    | Ext_mmd_link_attributes     -- ^ MMD style reference link attributes
    | Ext_mmd_title_block     -- ^ Multimarkdown metadata block
    | Ext_multiline_tables    -- ^ Pandoc-style multiline tables
    | Ext_native_divs             -- ^ Use Div blocks for contents of <div> tags
    | Ext_native_spans            -- ^ Use Span inlines for contents of <span>
    | Ext_native_numbering    -- ^ Use output format's native numbering for figures and tables
    | Ext_ntb                 -- ^ ConTeXt Natural Tables
    | Ext_old_dashes          -- ^ -- = em, - before number = en
    | Ext_pandoc_title_block  -- ^ Pandoc title block
    | Ext_pipe_tables         -- ^ Pipe tables (as in PHP markdown extra)
    | Ext_raw_attribute           -- ^ Allow explicit raw blocks/inlines
    | Ext_raw_html            -- ^ Allow raw HTML
    | Ext_raw_tex             -- ^ Allow raw TeX (other than math)
    | Ext_raw_markdown        -- ^ Parse markdown in ipynb as raw markdown
    | Ext_rebase_relative_paths -- ^ Rebase relative image and link paths,
                                -- relative to directory of containing file
    | Ext_short_subsuperscripts -- ^ sub-&superscripts w/o closing char (v~i)
    | Ext_shortcut_reference_links -- ^ Shortcut reference links
    | Ext_simple_tables       -- ^ Pandoc-style simple tables
    | Ext_smart               -- ^ "Smart" quotes, apostrophes, ellipses, dashes
    | Ext_smart_quotes        -- ^ "Smart" quotes
    | Ext_special_strings     -- ^ Treat certain strings like special characters
    | Ext_sourcepos           -- ^ Include source position attributes
    | Ext_space_in_atx_header -- ^ Require space between # and header text
    | Ext_spaced_reference_links -- ^ Allow space between two parts of ref link
    | Ext_startnum            -- ^ Make start number of ordered list significant
    | Ext_strikeout           -- ^ Strikeout using ~~this~~ syntax
    | Ext_subscript           -- ^ Subscript using ~this~ syntax
    | Ext_superscript         -- ^ Superscript using ^this^ syntax
    | Ext_styles              -- ^ Read styles that pandoc doesn't know
    | Ext_tagging             -- ^ Output optimized for PDF tagging
    | Ext_task_lists          -- ^ Parse certain list items as task list items
    | Ext_table_captions      -- ^ Pandoc-style table captions
    | Ext_tex_math_dollars    -- ^ TeX math between $..$ or $$..$$
    | Ext_tex_math_gfm        -- ^ Additional TeX math style used in GFM
    | Ext_tex_math_double_backslash  -- ^ TeX math btw \\(..\\) \\[..\\]
    | Ext_tex_math_single_backslash  -- ^ TeX math btw \(..\) \[..\]
    | Ext_wikilinks_title_after_pipe -- ^ Support wikilinks of style
                                     -- [[target|title]]
    | Ext_wikilinks_title_before_pipe  -- ^ Support wikilinks of style
                                       -- [[title|target]]
    | Ext_xrefs_name          -- ^ Use xrefs with names
    | Ext_xrefs_number        -- ^ Use xrefs with numbers
    | Ext_yaml_metadata_block -- ^ YAML metadata block
    | CustomExtension T.Text  -- ^ Custom extension
    deriving (Show, Read, Eq, Ord, Data, Typeable, Generic)

newtype Extensions = Extensions (Set.Set Extension)
  deriving (Show, Read, Eq, Ord, Data, Typeable, Generic)

newtype PathTemplate =
  PathTemplate { unPathTemplate :: Text }
  deriving (Show, IsString, Data, Typeable, Generic, ToJSON, FromJSON)

data MathMethod = PlainMath
                | WebTeX Text               -- url of TeX->image script.
                | GladTeX
                | MathML
                | MathJax Text              -- url of MathJax.js
                | KaTeX Text                -- url of KaTeX files
                deriving (Show, Read, Eq, Data, Typeable, Generic)

data CiteMethod = Citeproc                        -- use citeproc to render them
                  | Natbib                        -- output natbib cite commands
                  | Biblatex                      -- output biblatex cite commands
                deriving (Show, Read, Eq, Data, Typeable, Generic)

data ObfuscationMethod = NoObfuscation
                       | ReferenceObfuscation
                       | JavascriptObfuscation
                       deriving (Show, Read, Eq, Data, Typeable, Generic)

data HighlightMethod =
    Skylighting Style
  | IdiomaticHighlighting
  | DefaultHighlighting
  | NoHighlighting
  deriving (Show, Read, Eq, Data, Typeable, Generic)

data WrapOption = WrapAuto        -- ^ Automatically wrap to width
                | WrapNone        -- ^ No non-semantic newlines
                | WrapPreserve    -- ^ Preserve wrapping of input source
                deriving (Show, Read, Eq, Data, Typeable, Generic)

data TopLevelDivision = TopLevelPart      -- ^ Top-level headers become parts
                      | TopLevelChapter   -- ^ Top-level headers become chapters
                      | TopLevelSection   -- ^ Top-level headers become sections
                      | TopLevelDefault   -- ^ Top-level type is determined via
                                          --   heuristics
                      deriving (Show, Read, Eq, Data, Typeable, Generic)

data ReferenceLocation = EndOfBlock    -- ^ End of block
                       | EndOfSection  -- ^ prior to next section header (or end of document)
                       | EndOfDocument -- ^ at end of document
                       deriving (Show, Read, Eq, Data, Typeable, Generic)

data CaptionPosition = CaptionAbove -- ^ above figure or table
                     | CaptionBelow -- ^ below figure or table
                     deriving (Show, Read, Eq, Data, Typeable, Generic)

data WriterOptions = WriterOptions
  { writerTemplate          :: Maybe (Template Text) -- ^ Template to use
  , writerVariables         :: Context Text -- ^ Variables to set in template
  , writerTabStop           :: Int    -- ^ Tabstop for conversion btw spaces and tabs
  , writerTableOfContents   :: Bool   -- ^ Include table of contents
  , writerListOfFigures     :: Bool   -- ^ Include list of figures
  , writerListOfTables      :: Bool   -- ^ Include list of tables
  , writerIncremental       :: Bool   -- ^ True if lists should be incremental
  , writerMathMethod        :: MathMethod  -- ^ How to print math in HTML
  , writerNumberSections    :: Bool   -- ^ Number sections in LaTeX
  , writerNumberOffset      :: [Int]  -- ^ Starting number for section, subsection, ...
  , writerSectionDivs       :: Bool   -- ^ Put sections in div tags in HTML
  , writerExtensions        :: Extensions -- ^ Markdown extensions that can be used
  , writerReferenceLinks    :: Bool   -- ^ Use reference links in writing markdown, rst
  , writerDpi               :: Int    -- ^ Dpi for pixel to\/from inch\/cm conversions
  , writerWrapText          :: WrapOption  -- ^ Option for wrapping text
  , writerColumns           :: Int    -- ^ Characters in a line (for text wrapping)
  , writerEmailObfuscation  :: ObfuscationMethod -- ^ How to obfuscate emails
  , writerIdentifierPrefix  :: Text -- ^ Prefix for section & note ids in HTML
                                     -- and for footnote marks in markdown
  , writerCiteMethod        :: CiteMethod -- ^ How to print cites
  , writerHtmlQTags         :: Bool       -- ^ Use @<q>@ tags for quotes in HTML
  , writerSlideLevel        :: Maybe Int  -- ^ Force header level of slides
  , writerTopLevelDivision  :: TopLevelDivision -- ^ Type of top-level divisions
  , writerHighlightMethod   :: HighlightMethod  -- ^ Style to use for highlighting
  , writerSetextHeaders     :: Bool       -- ^ Use setext headers for levels 1-2 in markdown
  , writerListTables        :: Bool       -- ^ Use list tables for RST tables
  , writerEpubSubdirectory  :: Text       -- ^ Subdir for epub in OCF
  , writerEpubMetadata      :: Maybe Text -- ^ Metadata to include in EPUB
  , writerEpubFonts         :: [FilePath] -- ^ Paths to fonts to embed
  , writerEpubTitlePage     :: Bool           -- ^ Include title page in epub
  , writerSplitLevel        :: Int        -- ^ Header level at which to split EPUB or chunked HTML into separate files
  , writerChunkTemplate     :: PathTemplate  -- ^ Template for filenames in chunked HTML
  , writerTOCDepth          :: Int            -- ^ Number of levels to include in TOC
  , writerReferenceDoc      :: Maybe FilePath -- ^ Path to reference document if specified
  , writerReferenceLocation :: ReferenceLocation    -- ^ Location of footnotes and references for writing markdown
  , writerFigureCaptionPosition :: CaptionPosition -- ^ Position of figure caption
  , writerTableCaptionPosition :: CaptionPosition -- ^ Position of table caption
  , writerSyntaxMap         :: SyntaxMap
  , writerPreferAscii       :: Bool           -- ^ Prefer ASCII representations of characters when possible
  , writerLinkImages        :: Bool           -- ^ Use links rather than embedding ODT images
  } deriving (Show, Data, Typeable, Generic)

instance Default WriterOptions where
  def = WriterOptions { writerTemplate         = Nothing
                      , writerVariables        = mempty
                      , writerTabStop          = 4
                      , writerTableOfContents  = False
                      , writerListOfFigures    = False
                      , writerListOfTables     = False
                      , writerIncremental      = False
                      , writerMathMethod       = MathML
                      , writerNumberSections   = False
                      , writerNumberOffset     = [0,0,0,0,0,0]
                      , writerSectionDivs      = False
                      , writerExtensions       = emptyExtensions
                      , writerReferenceLinks   = False
                      , writerDpi              = 96
                      , writerWrapText         = WrapAuto
                      , writerColumns          = 72
                      , writerEmailObfuscation = NoObfuscation
                      , writerIdentifierPrefix = ""
                      , writerCiteMethod       = Citeproc
                      , writerHtmlQTags        = False
                      , writerSlideLevel       = Nothing
                      , writerTopLevelDivision = TopLevelDefault
                      , writerHighlightMethod  = DefaultHighlighting
                      , writerSetextHeaders    = False
                      , writerListTables       = False
                      , writerEpubSubdirectory = "EPUB"
                      , writerEpubMetadata     = Nothing
                      , writerEpubFonts        = []
                      , writerEpubTitlePage    = True
                      , writerSplitLevel       = 1
                      , writerChunkTemplate    = "%s-%i.html"
                      , writerTOCDepth         = 3
                      , writerReferenceDoc     = Nothing
                      , writerReferenceLocation = EndOfDocument
                      , writerFigureCaptionPosition = CaptionBelow
                      , writerTableCaptionPosition  = CaptionAbove
                      , writerSyntaxMap        = defaultSyntaxMap
                      , writerPreferAscii      = False
                      , writerLinkImages       = False
                      }

-- Test adapter supplies the actual original XML map when requested.
defaultSyntaxMap :: SyntaxMap
defaultSyntaxMap = M.empty
emptyExtensions = Extensions Set.empty
