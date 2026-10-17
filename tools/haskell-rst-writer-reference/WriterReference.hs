{-# LANGUAGE OverloadedStrings #-}
import qualified OriginalTests
import OracleTree
import SourceRstWriter
import Text.Pandoc hiding(writeRST)
import Text.DocTemplates(compileTemplate,toContext)
import qualified Data.Map as M
import qualified Data.Char as C
import Data.Bits((.|.))
import qualified Data.ByteString as BS
import Data.Aeson
import Data.Aeson.Types(parseEither,Parser)
import qualified Data.ByteString.Lazy.Char8 as B
import qualified Data.Text as T
import qualified Data.Text.IO as TIO
import System.Environment(getArgs)
import Control.Monad(forM_)
walk path (Group name xs)=mapM_ (walk(path++[name])) xs
walk path (FlattenLeaf name input actual expected)=if actual/=expected then error(show(name,actual,expected)) else B.putStrLn $ encode $ object["name" .= T.intercalate "/" (map T.pack(path++[name])),"flattenInput" .= input,"flattenActual" .= actual,"flattenExpected" .= expected]
walk path (Leaf name doc opts template expected)=case runPure(writeRST opts doc) of
 Left e->error(show e)
 Right actual->if T.lines actual/=T.lines expected then error(show(path++[name],actual,expected)) else B.putStrLn $ encode $ object["name" .= T.intercalate "/" (map T.pack(path++[name])),"document" .= doc,"options" .= optsValue opts,"template" .= template,"expected" .= expected,"output" .= actual]
main=do
 args<-getArgs
 case args of
  ("--commands":files)->commands files
  ["--original-tests"]->mapM_(walk []) OriginalTests.tests
  ["--classifiers"]->BS.putStr $ BS.pack [fromIntegral $ 32+sum [v | (p,v)<-[(C.isAlphaNum,1),(C.isSpace,2),(\c->C.generalCategory c `elem` [C.OpenPunctuation,C.InitialQuote,C.FinalQuote,C.DashPunctuation,C.OtherPunctuation],4),(\c->C.generalCategory c `elem` [C.ClosePunctuation,C.InitialQuote,C.FinalQuote,C.DashPunctuation,C.OtherPunctuation],8)],p(C.chr n)] | n<-[0..1114111]]
  ["--lowercase-mappings"]->B.putStrLn $ encode [(n,T.toLower(T.singleton(C.chr n))) | n<-[0::Int ..1114111],T.toLower(T.singleton(C.chr n))/=T.singleton(C.chr n)]
  ["--defaults"]->B.putStrLn $ encode (optsValue def)
  _->B.getContents >>= mapM_ observe . B.lines
observe line=case eitherDecode line >>= parseEither request of
 Left e->error e
 Right(name,doc,opts,template)->do
  tpl<-case template of
   Nothing->pure Nothing
   Just t->compileTemplate "oracle.rst" t >>= either error (pure . Just)
  let result=runPure $ do output<-writeRST opts{writerTemplate=tpl} doc;logs<-getLog;pure(output,logs)
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
 pure(name::T.Text,document,options,template::Maybe T.Text)

optsValue opts=object["wrap" .= show(writerWrapText opts),"columns" .= writerColumns opts,"extensions" .= writerExtensions opts,"referenceLocation" .= show(writerReferenceLocation opts),"dpi" .= writerDpi opts,"tabStop" .= writerTabStop opts,"referenceLinks" .= writerReferenceLinks opts,"listTables" .= writerListTables opts,"toc" .= writerTableOfContents opts,"tocDepth" .= writerTOCDepth opts,"numberSections" .= writerNumberSections opts]
parseOptions=withObject "options" $ \o->do
 wrap<-o .:? "wrap" .!= ("WrapAuto"::T.Text)
 columns<-o .:? "columns" .!= 72
 extensions<-o .:? "extensions" .!= mempty
 reference<-o .:? "referenceLocation" .!= ("EndOfDocument"::T.Text)
 dpi<-o .:? "dpi" .!= 96
 tabStop<-o .:? "tabStop" .!= 4
 variables<-o .:? "variables" .!= (M.empty::M.Map T.Text Value)
 referenceLinks<-o .:? "referenceLinks" .!= False
 listTables<-o .:? "listTables" .!= False
 toc<-o .:? "toc" .!= False
 tocDepth<-o .:? "tocDepth" .!= 3
 numberSections<-o .:? "numberSections" .!= False
 pure def{writerReferenceLinks=referenceLinks,writerListTables=listTables,writerTableOfContents=toc,writerTOCDepth=tocDepth,writerNumberSections=numberSections,writerWrapText=case wrap of {"WrapPreserve"->WrapPreserve;"WrapNone"->WrapNone;_->WrapAuto},writerColumns=columns,writerExtensions=extensions,writerReferenceLocation=case reference of {"EndOfBlock"->EndOfBlock;"EndOfSection"->EndOfSection;_->EndOfDocument},writerDpi=dpi,writerTabStop=tabStop,writerVariables=toContext variables}

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

commands files = forM_ files $ \file -> do
  source <- TIO.readFile file
  let Pandoc _ blocks = either (error . show) id $ runPure $ readMarkdown def{readerExtensions=pandocExtensions} source
  forM_ (zip [1::Int ..] [code | CodeBlock _ code <- blocks]) $ \(n,code) -> do
    let ls = lines $ T.unpack code
    let (continuations,r1) = span (\x -> not(null x) && last x=='\\') ls
    let command = dropWhile (==' ') $ drop 1 $ unwords(map init continuations ++ take 1 r1)
    let (inp,rest) = break (=="^D") $ drop 1 r1
    B.putStrLn $ encode $ object ["file" .= file,"number" .= n,"command" .= command,"input" .= unlines inp,"expected" .= unlines (takeWhile (/=".") $ drop 1 rest)]