{-# LANGUAGE OverloadedStrings #-}
import qualified OriginalTests
import OracleTree
import SourceMuseWriter
import Text.Pandoc hiding(writeMuse)
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
walk path (Leaf name doc opts expected)=case runPure(writeMuse opts doc) of
 Left e->error(show e)
 Right actual->if T.lines actual/=T.lines expected then error(show(path++[name],actual,expected)) else B.putStrLn $ encode $ object["name" .= T.intercalate "/" (map T.pack(path++[name])),"document" .= doc,"options" .= optsValue opts,"expected" .= expected,"output" .= actual]
main=do
 args<-getArgs
 case args of
  ("--commands":files)->commands files
  ["--original-tests"]->mapM_(walk []) OriginalTests.tests
  ["--classifiers"]->BS.putStr $ BS.pack [fromIntegral $ 32+sum [v | (p,v)<-[(C.isAlphaNum,1),(C.isDigit,2),(C.isSpace,4),(C.isAsciiLower,8),(C.isAsciiUpper,16)],p(C.chr n)] | n<-[0..1114111]]
  ["--defaults"]->B.putStrLn $ encode (optsValue def)
  _->B.getContents >>= mapM_ observe . B.lines
observe line=case eitherDecode line >>= parseEither request of
 Left e->error e
 Right(name,doc,opts,template)->do
  tpl<-case template of
   Nothing->pure Nothing
   Just t->compileTemplate "oracle.muse" t >>= either error (pure . Just)
  let result=runPure $ do output<-writeMuse opts{writerTemplate=tpl} doc;logs<-getLog;pure(output,logs)
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

optsValue opts=object["wrap" .= show(writerWrapText opts),"columns" .= writerColumns opts,"extensions" .= writerExtensions opts,"referenceLocation" .= show(writerReferenceLocation opts),"dpi" .= writerDpi opts,"tabStop" .= writerTabStop opts]
parseOptions=withObject "options" $ \o->do
 wrap<-o .:? "wrap" .!= ("WrapAuto"::T.Text)
 columns<-o .:? "columns" .!= 72
 extensions<-o .:? "extensions" .!= mempty
 reference<-o .:? "referenceLocation" .!= ("EndOfDocument"::T.Text)
 dpi<-o .:? "dpi" .!= 96
 tabStop<-o .:? "tabStop" .!= 4
 variables<-o .:? "variables" .!= (M.empty::M.Map T.Text Value)
 pure def{writerWrapText=case wrap of {"WrapPreserve"->WrapPreserve;"WrapNone"->WrapNone;_->WrapAuto},writerColumns=columns,writerExtensions=extensions,writerReferenceLocation=case reference of {"EndOfBlock"->EndOfBlock;"EndOfSection"->EndOfSection;_->EndOfDocument},writerDpi=dpi,writerTabStop=tabStop,writerVariables=toContext variables}

deepDocument :: T.Text -> Int -> Pandoc
deepDocument kind depth
  | kind == "caption" || kind == "grid-caption" || kind == "grid-caption-poison" = Pandoc nullMeta [Table nullAttr (Caption Nothing (if kind=="grid-caption-poison" then [error "caption forced"] else [nest base])) (replicate (if kind=="caption" then 2 else 1) (AlignDefault,ColWidthDefault)) (TableHead nullAttr []) [TableBody nullAttr 0 [] [Row nullAttr [Cell nullAttr AlignDefault 1 1 [Plain [Str "cell"]]]]] (TableFoot nullAttr [])]
  | kind == "emph" = Pandoc nullMeta [Para [iterate (Emph . pure) (Str "x") !! depth]]
  | kind == "note" = Pandoc nullMeta [Para [iterate (Note . pure . Para . pure) (Str "x") !! depth]]
  | otherwise = Pandoc nullMeta [nest base]
  where
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
    let command = dropWhile (==' ') $ drop 1 $ head ls
    let (inp,rest) = break (=="^D") $ tail ls
    B.putStrLn $ encode $ object ["file" .= file,"number" .= n,"command" .= command,"input" .= unlines inp,"expected" .= unlines (takeWhile (/=".") $ drop 1 rest)]