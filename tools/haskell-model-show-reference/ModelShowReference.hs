{-# LANGUAGE OverloadedStrings, ScopedTypeVariables #-}
module Main where
import qualified PandocOptionsModels as P
import qualified TemplateAdapter as A
import qualified SyntaxAdapter as S
import qualified Skylighting.Core as K
import qualified Text.DocLayout as D
import Text.DocTemplates.Internal(Border(..))
import qualified Data.Aeson.Key as Key
import qualified Data.Aeson.KeyMap as KM
import Data.Aeson
import qualified Data.ByteString.Lazy.Char8 as B
import qualified Data.Map as M
import qualified Data.Set as Set
import qualified Data.Text as T
import Text.DocTemplates.Internal(Context(..),Val(..))
import Data.Char(chr)
import Data.Default(def)
import System.IO(hSetBuffering,stdout,BufferMode(LineBuffering))

as v=case fromJSON v of Success a->a;Error e->error e
arr=A.arr
shown :: Show a => Int -> a -> Value
shown p a=toJSON(showsPrec p a "")
readEnum v=read(T.unpack(as v))
extension v=case arr v of
 [String "builtin",n]->read("Ext_"++T.unpack(as n))
 [String "custom",n]->P.CustomExtension(as n)
 _->error "extension"
math v=case arr v of
 [String "PlainMath"]->P.PlainMath
 [String "WebTeX",s]->P.WebTeX(as s)
 [String "GladTeX"]->P.GladTeX
 [String "MathML"]->P.MathML
 [String "MathJax",s]->P.MathJax(as s)
 [String "KaTeX",s]->P.KaTeX(as s)
 _->error "math"
highlight v=case v of
 String s->read(T.unpack s)
 _->P.Skylighting(as v)
writer builtin v=foldl update ((def::P.WriterOptions){P.writerSyntaxMap=M.empty})(KM.toList(as v))
 where update o (key,x)=case Key.toString key of
        "template"->o{P.writerTemplate=if x==Null then Nothing else Just(A.template x)}
        "variables"->o{P.writerVariables=case A.val x of MapVal c->c;_->error "context"}
        "tabStop"->o{P.writerTabStop=as x}
        "tableOfContents"->o{P.writerTableOfContents=as x}
        "listOfFigures"->o{P.writerListOfFigures=as x}
        "listOfTables"->o{P.writerListOfTables=as x}
        "incremental"->o{P.writerIncremental=as x}
        "mathMethod"->o{P.writerMathMethod=math x}
        "numberSections"->o{P.writerNumberSections=as x}
        "numberOffset"->o{P.writerNumberOffset=as x}
        "sectionDivs"->o{P.writerSectionDivs=as x}
        "extensions"->o{P.writerExtensions=P.Extensions(Set.fromList(map extension(arr x)))}
        "referenceLinks"->o{P.writerReferenceLinks=as x}
        "dpi"->o{P.writerDpi=as x}
        "wrapText"->o{P.writerWrapText=readEnum x}
        "columns"->o{P.writerColumns=as x}
        "emailObfuscation"->o{P.writerEmailObfuscation=readEnum x}
        "identifierPrefix"->o{P.writerIdentifierPrefix=as x}
        "citeMethod"->o{P.writerCiteMethod=readEnum x}
        "htmlQTags"->o{P.writerHtmlQTags=as x}
        "slideLevel"->o{P.writerSlideLevel=as x}
        "topLevelDivision"->o{P.writerTopLevelDivision=readEnum x}
        "highlightMethod"->o{P.writerHighlightMethod=highlight x}
        "setextHeaders"->o{P.writerSetextHeaders=as x}
        "listTables"->o{P.writerListTables=as x}
        "epubSubdirectory"->o{P.writerEpubSubdirectory=as x}
        "epubMetadata"->o{P.writerEpubMetadata=as x}
        "epubFonts"->o{P.writerEpubFonts=as x}
        "epubTitlePage"->o{P.writerEpubTitlePage=as x}
        "splitLevel"->o{P.writerSplitLevel=as x}
        "chunkTemplate"->o{P.writerChunkTemplate=P.PathTemplate(as x)}
        "tocDepth"->o{P.writerTOCDepth=as x}
        "referenceDoc"->o{P.writerReferenceDoc=as x}
        "referenceLocation"->o{P.writerReferenceLocation=readEnum x}
        "figureCaptionPosition"->o{P.writerFigureCaptionPosition=readEnum x}
        "tableCaptionPosition"->o{P.writerTableCaptionPosition=readEnum x}
        "syntaxMap"->o{P.writerSyntaxMap=if x==String "builtin" then builtin else S.mapfrom S.syntax x}
        "preferAscii"->o{P.writerPreferAscii=as x}
        "linkImages"->o{P.writerLinkImages=as x}
        _->error("writer field "++Key.toString key)
process builtin (op,p,v)=case op::String of
 "writer"->shown p(writer builtin v)
 "writer-path-points"->let path=map(chr.as)(arr v);o=writer builtin (object[]) in
    shown p(o{P.writerReferenceDoc=Just path,P.writerEpubFonts=[path]})
 "writer-text-points"->let t=T.pack(map(chr.as)(arr v));o=writer builtin (object[]) in
    shown p(o{P.writerIdentifierPrefix=t,P.writerEpubSubdirectory=t,P.writerEpubMetadata=Just t,P.writerChunkTemplate=P.PathTemplate t,P.writerMathMethod=P.WebTeX t})
 "template"->shown p(A.template v)
 "value"->shown p(A.val v)
 "context"->case A.val v of MapVal c->shown p c;_->error "context"
 "doc"->shown p(A.doc v)
 "variable"->shown p(A.variable v)
 "pipe"->shown p(A.pipe v)
 "border"->case arr v of [l,r]->shown p(Border(as l)(as r));_->error "border"
 "font"->shown p(A.font v)
 "style"->shown p(A.style v)
 "extension"->shown p(extension v)
 "extensions"->shown p(P.Extensions(Set.fromList(map extension(arr v))))
 "math"->shown p(math v)
 "highlight"->shown p(highlight v)
 "doc-int"->shown p(fmap (read.T.unpack)(A.doc v)::D.Doc Int)
 "doc-string-points"->case arr v of [n,ps]->shown p(D.Text(as n)(map(chr.as)(arr ps))::D.Doc String);_->error "points"
 _->error op
main=do
 hSetBuffering stdout LineBuffering
 result<-K.loadSyntaxesFromDir "vendor/skylighting-core-0.15/xml"
 let builtin=either error id result
 B.getContents>>=mapM_(\line->case eitherDecode line of Right req->B.putStrLn(encode(process builtin req));Left e->error e).B.lines
