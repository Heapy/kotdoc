{-# LANGUAGE OverloadedStrings #-}
-- JSON observation boundary; complete unchanged Pandoc library is the oracle.
module Main where
import Control.Monad (unless)
import System.Environment(getArgs,withArgs)
import Test.Tasty(defaultMain,testGroup)
import qualified Tests.Writers.HTML as OriginalHTML
import Data.Default
import Data.Maybe (fromMaybe)
import Control.Monad.Except (throwError)
import qualified Data.Aeson.KeyMap as KM
import qualified Data.Aeson.Key as K
import Data.Char (chr,ord)
import Text.DocTemplates (toContext)
import Text.Pandoc.Templates (runWithDefaultPartials,compileTemplate,getDefaultTemplate)
import Text.Pandoc.Extensions
import Text.Pandoc.Error
import Data.Aeson
import qualified Data.Vector as V
import qualified Data.ByteString as B
import qualified Data.ByteString.Lazy as BL
import qualified Data.ByteString.Lazy.Char8 as L
import qualified Data.Text as T
import qualified Data.Text.Lazy as TL
import System.IO
import Text.Pandoc.Definition
import Text.Pandoc.Options
import Text.Pandoc.Class
import Text.Pandoc.Writers.HTML
import Text.Blaze.Html.Renderer.Text (renderHtml)
as :: FromJSON a=>Value->a
as v=case fromJSON v of Success x->x;Error e->error e
readOptions :: Value -> PandocPure WriterOptions
readOptions (Object o)=do
 let val k=KM.lookup(K.fromText k)o
     get k d=maybe d as(val k)
     optional k=val k >>= \x->if x==Null then Nothing else Just(as x)
 template<-case (optional "defaultTemplate",optional "template") of
  (Nothing,Nothing)->pure Nothing
  (format,custom)->do
   t<-maybe (getDefaultTemplate(fromMaybe "html5" format)) pure custom
   compiled<-runWithDefaultPartials(compileTemplate (maybe "fixture.html" (\f->"templates/default."<>T.unpack f)format) t)
   either (throwError . PandocTemplateError . T.pack) (pure . Just) compiled
 pure def{writerTemplate=template,writerVariables=toContext(fromMaybe(Object mempty)(val "variables")),
  writerTabStop=get "tabStop" 4,writerTableOfContents=get "tableOfContents" False,
  writerListOfFigures=get "listOfFigures" False,writerListOfTables=get "listOfTables" False,
  writerIncremental=get "incremental" False,writerMathMethod=get "mathMethod" MathML,
  writerNumberSections=get "numberSections" False,writerNumberOffset=get "numberOffset" [0,0,0,0,0,0],
  writerSectionDivs=get "sectionDivs" False,writerExtensions=get "extensions" mempty,
  writerReferenceLinks=get "referenceLinks" False,writerDpi=get "dpi" 96,writerWrapText=get "wrapText" WrapNone,
  writerColumns=get "columns" 72,writerEmailObfuscation=get "emailObfuscation" NoObfuscation,
  writerIdentifierPrefix=get "identifierPrefix" "",writerCiteMethod=get "citeMethod" Citeproc,
  writerHtmlQTags=get "htmlQTags" False,writerSlideLevel=optional "slideLevel",
  writerTopLevelDivision=get "topLevelDivision" TopLevelDefault,writerHighlightMethod=get "highlightMethod" DefaultHighlighting,
  writerSetextHeaders=get "setextHeaders" False,writerListTables=get "listTables" False,
  writerEpubSubdirectory=get "epubSubdirectory" "EPUB",writerEpubMetadata=optional "epubMetadata",
  writerEpubFonts=get "epubFonts" [],writerEpubTitlePage=get "epubTitlePage" True,
  writerSplitLevel=get "splitLevel" 1,writerTOCDepth=get "tocDepth" 3,
  writerReferenceDoc=optional "referenceDoc",writerReferenceLocation=get "referenceLocation" EndOfDocument,
  writerFigureCaptionPosition=get "figureCaptionPosition" CaptionBelow,writerTableCaptionPosition=get "tableCaptionPosition" CaptionAbove,
  writerPreferAscii=get "preferAscii" False,writerLinkImages=get "linkImages" False}
readOptions _=error "options"
runWriter name doc options=do
  opts<-readOptions options
  let d=as doc
  result<-case name of
   "html4"->writeHtml4String opts d
   "html5"->writeHtml5String opts d
   "html4-markup"->TL.toStrict . renderHtml <$> writeHtml4 opts d
   "html5-markup"->TL.toStrict . renderHtml <$> writeHtml5 opts d
   "epub2"->writeHtmlStringForEPUB EPUB2 opts d
   "epub3"->writeHtmlStringForEPUB EPUB3 opts d
   "s5"->writeS5 opts d
   "slidy"->writeSlidy opts d
   "slideous"->writeSlideous opts d
   "dzslides"->writeDZSlides opts d
   "revealjs"->writeRevealJs opts d
   _->error "writer"
  messages<-getLog
  pure(object["output" .= result,"log" .= messages])
response (Array v)=either (\e->object["error" .= show e]) id $ runPure $ case V.toList v of
 [String "tag",options,h5,closing,String name,attrs]->do
  opts<-readOptions options
  pure(toJSON(tagWithAttributes opts(as h5)(as closing)name(as attrs)))
 [String "sequence",Array requests]->toJSON <$> mapM (\r->case V.toList r of [String name,doc,options]->runWriter name doc options;_->error "sequence request") [r|Array r<-V.toList requests]
 [String name,doc,options]->runWriter name doc options
 _->error "request"
response _=error "request"
main=do
 args<-getArgs
 if args==["--upstream-tests"] then withArgs [] (defaultMain(testGroup "Original HTML" OriginalHTML.tests)) else loop
 where
 loop=do
  end<-isEOF
  unless end $ do
   line<-B.getLine
   L.putStrLn(encode(response(either error id(eitherDecode(BL.fromStrict line)))))
   hFlush stdout;loop
