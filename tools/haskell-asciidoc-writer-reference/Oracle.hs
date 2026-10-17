{-# LANGUAGE OverloadedStrings #-}
import qualified OriginalAsciiDocTests
import AsciiDocOracleTree
import SourceAsciiDocWriter
import Data.Aeson
import Data.Aeson.Types(parseEither)
import Text.DocTemplates(compileTemplate,toContext)
import qualified Data.Map as M
import qualified Data.Text as T
import qualified Data.ByteString as BS
import qualified Data.ByteString.Lazy.Char8 as B
import Text.Pandoc.Options
import Text.Pandoc.Extensions
import Text.Pandoc.Definition
import Text.Pandoc.Class
import Data.Default
import System.Environment(getArgs)
import qualified Data.Char as C
walk path(Group name xs)=mapM_(walk(path++[name]))xs
walk path(Leaf name doc expected actual)=do
 if lines expected/=lines actual then error("Original assertion failed: "++show(path++[name]))else pure()
 let opts=def
 let isLegacy=name/="bullet task list"
 B.putStrLn$encode$object["name".=T.intercalate "/"(map T.pack(path++[name])),"document".=doc,"legacy".=isLegacy,"options".=optsValue opts,"expected".=expected,"actual".=actual]
main=do
 args<-getArgs
 if args==["--original-tests"]then mapM_(walk[])OriginalAsciiDocTests.tests
 else if args==["--defaults"]then B.putStrLn$encode(optsValue(def::WriterOptions))
 else if args==["--classifiers"]then BS.putStr $ BS.pack [fromIntegral $ sum [v|(p,v)<-[(C.isSpace,1),(C.isPunctuation,2),(C.isDigit,4),(C.isAlphaNum,8)],p(C.chr n)]|n<-[0..1114111]]
 else if args==["--lower-mappings"]then B.putStrLn$encode[(n,T.toLower(T.singleton(C.chr n)))|n<-[0::Int ..1114111],T.toLower(T.singleton(C.chr n))/=T.singleton(C.chr n)]
 else if args==["--upper-mappings"]then B.putStrLn$encode[(n,T.toUpper(T.singleton(C.chr n)))|n<-[0::Int ..1114111],T.toUpper(T.singleton(C.chr n))/=T.singleton(C.chr n)]
 else if args==["--casefold-mappings"]then B.putStrLn$encode[(n,T.toCaseFold(T.singleton(C.chr n)))|n<-[0::Int ..1114111],T.toCaseFold(T.singleton(C.chr n))/=T.singleton(C.chr n)]
 else B.getContents >>= mapM_ observe . B.lines

optsValue opts=object["wrap".=show(writerWrapText opts),"columns".=writerColumns opts,"extensions".=writerExtensions opts]

observe line=case eitherDecode line >>= parseEither request of
 Left e->error e
 Right(name,doc,opts,template,isLegacy)->do
  tpl<-case template of
   Nothing->pure Nothing
   Just t->compileTemplate "oracle.asciidoc" t >>= either error (pure . Just)
  let result=runPure $ do output<-(if isLegacy then writeAsciiDocLegacy else writeAsciiDoc) opts{writerTemplate=tpl} doc;logs<-getLog;pure(output,logs)
  B.putStrLn $ encode $ case result of
   Left e->object["name" .= name,"error" .= show e]
   Right(output,logs)->object["name" .= name,"output" .= output,"logs" .= logs]
request=withObject "request" $ \o->do
 name<-o .: "name"
 deep<-o .:? "deep"
 document<-case deep of
  Nothing->o .: "document"
  Just v->withObject "deep" (\d->deepDocument <$> d .: "kind" <*> d .: "depth") v
 options<-o .:? "options" .!= object [] >>= parseOptions
 template<-o .:? "template"
 isLegacy<-o .:? "legacy" .!= False
 pure(name::T.Text,document::Pandoc,options,template::Maybe T.Text,isLegacy::Bool)
parseOptions=withObject "options" $ \o->do
 wrap<-o .:? "wrap" .!= ("WrapAuto"::T.Text)
 columns<-o .:? "columns" .!= 72
 extensions<-o .:? "extensions" .!= mempty
 toc<-o .:? "toc" .!= False
 dpi<-o .:? "dpi" .!= 96
 variables<-o .:? "variables" .!= (M.empty::M.Map T.Text Value)
 pure def{writerTableOfContents=toc,writerDpi=dpi,writerWrapText=case wrap of {"WrapPreserve"->WrapPreserve;"WrapNone"->WrapNone;_->WrapAuto},writerColumns=columns,writerExtensions=extensions,writerVariables=toContext variables}

deepDocument :: T.Text -> Int -> Pandoc
deepDocument kind depth
  | kind == "caption" || kind == "grid-caption" || kind == "grid-caption-poison" = Pandoc nullMeta [Table nullAttr (Caption Nothing (if kind=="grid-caption-poison" then [error "caption forced"] else [nest base])) (replicate (if kind=="caption" then 2 else 1) (AlignDefault,ColWidthDefault)) (TableHead nullAttr []) [TableBody nullAttr 0 [] [Row nullAttr [Cell nullAttr AlignDefault 1 1 [Plain [Str "cell"]]]]] (TableFoot nullAttr [])]
  | "list-width-" `T.isPrefixOf` kind = Pandoc nullMeta [Table nullAttr (Caption Nothing []) [(AlignDefault,ColWidth width)] (TableHead nullAttr []) [TableBody nullAttr 0 [] [Row nullAttr [Cell nullAttr AlignDefault 1 1 [Plain [Str "x"]]]]] (TableFoot nullAttr [])]
  | kind == "span" = Pandoc nullMeta [Para [iterate (Span nullAttr . pure) (Str "x") !! depth]]
  | kind == "emph" = Pandoc nullMeta [Para [iterate (Emph . pure) (Str "x") !! depth]]
  | kind == "note" = Pandoc nullMeta [Para [iterate (Note . pure . Para . pure) (Str "x") !! depth]]
  | otherwise = Pandoc nullMeta [nest base]
  where
    width = case kind of { "list-width-nan" -> 0/0; "list-width-inf" -> 1/0; "list-width-neg-inf" -> -1/0; _ -> 1.0e308 }
    base | kind == "diagnostic" = RawBlock (Format "unsupported") "leaf"
         | kind == "bullet" = BulletList [[Plain [Str "x"]]]
         | kind == "ordered" = OrderedList (1, DefaultStyle, DefaultDelim) [[Plain [Str "x"]]]
         | otherwise = Para [Str "x"]
    nest b = iterate step b !! depth
    step b = case kind of
      "div" -> Div nullAttr [b]
      "caption" -> Div nullAttr [b]
      "grid-caption" -> Div nullAttr [b]
      "diagnostic" -> Div nullAttr [b]
      "quote" -> BlockQuote [b]
      "bullet" -> BulletList [[Plain [Str "x"],b]]
      "ordered" -> OrderedList (1, DefaultStyle, DefaultDelim) [[Plain [Str "x"],b]]
      "definition" -> DefinitionList [([Str "term"],[[b]])]
      "table" -> Table nullAttr (Caption Nothing []) [(AlignDefault,ColWidthDefault)] (TableHead nullAttr []) [TableBody nullAttr 0 [] [Row nullAttr [Cell nullAttr AlignDefault 1 1 [b]]]] (TableFoot nullAttr [])
      "mixed" -> BlockQuote [Div nullAttr [BulletList [[Para [Str "x"],b]]]]
      _ -> error "unknown deep recipe"
