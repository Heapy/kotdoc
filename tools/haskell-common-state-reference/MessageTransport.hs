{-# LANGUAGE OverloadedStrings #-}
module MessageTransport (message, chars, txt, arr, as, str) where
import Data.Aeson
import qualified Data.Vector as V
import qualified Data.Text as T
import Data.Char (chr)
import Text.Pandoc.Definition
import Text.Pandoc.Logging
import Text.Parsec.Pos
as :: FromJSON a => Value -> a
as v = case fromJSON v of Success x -> x; Error e -> error e
arr (Array v) = V.toList v
arr _ = error "array"
str (String s) = T.unpack s
str _ = error "string"
chars v = map (chr . as) (arr v)
txt = T.pack . chars
position v = case arr v of [n,l,c] -> newPos (chars n) (as l) (as c); _ -> error "position"
optional Null = Nothing
optional v = Just (position v)
message :: Value -> LogMessage
message v=case arr v of
 [String "SkippedContent",a0,a1] -> SkippedContent (txt a0) (position a1)
 [String "IgnoredElement",a0] -> IgnoredElement (txt a0)
 [String "DuplicateLinkReference",a0,a1] -> DuplicateLinkReference (txt a0) (position a1)
 [String "DuplicateNoteReference",a0,a1] -> DuplicateNoteReference (txt a0) (position a1)
 [String "NoteDefinedButNotUsed",a0,a1] -> NoteDefinedButNotUsed (txt a0) (position a1)
 [String "DuplicateIdentifier",a0,a1] -> DuplicateIdentifier (txt a0) (position a1)
 [String "ReferenceNotFound",a0,a1] -> ReferenceNotFound (txt a0) (position a1)
 [String "CircularReference",a0,a1] -> CircularReference (txt a0) (position a1)
 [String "UndefinedToggle",a0,a1] -> UndefinedToggle (txt a0) (position a1)
 [String "ParsingUnescaped",a0,a1] -> ParsingUnescaped (txt a0) (position a1)
 [String "CouldNotLoadIncludeFile",a0,a1] -> CouldNotLoadIncludeFile (txt a0) (position a1)
 [String "CouldNotParseIncludeFile",a0,a1] -> CouldNotParseIncludeFile (txt a0) (position a1)
 [String "MacroAlreadyDefined",a0,a1] -> MacroAlreadyDefined (txt a0) (position a1)
 [String "InlineNotRendered",a0] -> InlineNotRendered (as a0)
 [String "BlockNotRendered",a0] -> BlockNotRendered (as a0)
 [String "DocxParserWarning",a0] -> DocxParserWarning (txt a0)
 [String "PowerpointTemplateWarning",a0] -> PowerpointTemplateWarning (txt a0)
 [String "IgnoredIOError",a0] -> IgnoredIOError (txt a0)
 [String "CouldNotFetchResource",a0,a1] -> CouldNotFetchResource (txt a0) (txt a1)
 [String "CouldNotDetermineImageSize",a0,a1] -> CouldNotDetermineImageSize (txt a0) (txt a1)
 [String "CouldNotConvertImage",a0,a1] -> CouldNotConvertImage (txt a0) (txt a1)
 [String "CouldNotDetermineMimeType",a0] -> CouldNotDetermineMimeType (txt a0)
 [String "CouldNotConvertTeXMath",a0,a1] -> CouldNotConvertTeXMath (txt a0) (txt a1)
 [String "CouldNotParseCSS",a0] -> CouldNotParseCSS (txt a0)
 [String "Fetching",a0] -> Fetching (txt a0)
 [String "Extracting",a0] -> Extracting (txt a0)
 [String "LoadedResource",a0,a1] -> LoadedResource (chars a0) (chars a1)
 [String "ScriptingInfo",a0,a1] -> ScriptingInfo (txt a0) (optional a1)
 [String "ScriptingWarning",a0,a1] -> ScriptingWarning (txt a0) (optional a1)
 [String "NoTitleElement",a0] -> NoTitleElement (txt a0)
 [String "NoLangSpecified"] -> NoLangSpecified
 [String "InvalidLang",a0] -> InvalidLang (txt a0)
 [String "CouldNotHighlight",a0] -> CouldNotHighlight (txt a0)
 [String "MissingCharacter",a0] -> MissingCharacter (txt a0)
 [String "Deprecated",a0,a1] -> Deprecated (txt a0) (txt a1)
 [String "NoTranslation",a0] -> NoTranslation (txt a0)
 [String "CouldNotLoadTranslations",a0,a1] -> CouldNotLoadTranslations (txt a0) (txt a1)
 [String "UnusualConversion",a0] -> UnusualConversion (txt a0)
 [String "UnexpectedXmlElement",a0,a1] -> UnexpectedXmlElement (txt a0) (txt a1)
 [String "UnknownOrgExportOption",a0] -> UnknownOrgExportOption (txt a0)
 [String "CouldNotDeduceFormat",a0,a1] -> CouldNotDeduceFormat ((map txt . arr) a0) (txt a1)
 [String "RunningFilter",a0] -> RunningFilter (chars a0)
 [String "FilterCompleted",a0,a1] -> FilterCompleted (chars a0) ((read . str) a1)
 [String "CiteprocWarning",a0] -> CiteprocWarning (txt a0)
 [String "ATXHeadingInLHS",a0,a1] -> ATXHeadingInLHS (as a0) (txt a1)
 [String "EnvironmentVariableUndefined",a0] -> EnvironmentVariableUndefined (txt a0)
 [String "DuplicateAttribute",a0,a1] -> DuplicateAttribute (txt a0) (txt a1)
 [String "NotUTF8Encoded",a0] -> NotUTF8Encoded (chars a0)
 [String "MakePDFInfo",a0,a1] -> MakePDFInfo (txt a0) (txt a1)
 [String "MakePDFWarning",a0] -> MakePDFWarning (txt a0)
 [String "UnclosedDiv",a0,a1] -> UnclosedDiv (position a0) (position a1)
 [String "UnsupportedCodePage",a0] -> UnsupportedCodePage (as a0)
 [String "YamlWarning",a0,a1] -> YamlWarning (position a0) (txt a1)
 [String "UnsupportedPdfStandard",a0] -> UnsupportedPdfStandard (txt a0)
 _ -> error "LogMessage constructor"
