{-# LANGUAGE OverloadedStrings #-}
-- Independent oracle using the complete pinned original writer module.
-- Text.Pandoc.Writers.DokuWiki: Copyright (C) 2008-2024 John MacFarlane,
-- GPL version 2 or later. No writer behavior is reimplemented here.
import Data.Aeson
import Data.Aeson.Types (parseEither)
import qualified Data.ByteString.Lazy.Char8 as B
import qualified Data.Text as T
import Text.Pandoc.Class
import Text.Pandoc.Options
import Text.Pandoc.Extensions
import Text.Pandoc.Definition
import Text.Pandoc.Readers.Native (readNative)
import Text.Pandoc.Readers.Muse (readMuse)
import SourceDokuWiki
import Text.DocTemplates (compileTemplate)
import Data.Default
import Control.Monad (forM_)

main :: IO ()
main = B.getContents >>= mapM_ observe . B.lines
observe line = case eitherDecode line of
  Left err -> error err
  Right value -> case parseEither request value of
    Left err -> error err
    Right (name, doc, wrap, raw, dpi, toc, template, deep) -> do
      tpl <- case template of
        Nothing -> pure Nothing
        Just t -> do
          compiled <- compileTemplate "oracle.dokuwiki" t
          either error (pure . Just) compiled
      let opts = def { writerWrapText = wrap, writerExtensions = if raw then enableExtension Ext_raw_html mempty else mempty,
                       writerDpi = dpi, writerTableOfContents = toc, writerTemplate = tpl }
      let result = runPure $ do
            output <- writeDokuWiki opts doc
            logs <- getLog
            pure (output, logs)
      B.putStrLn $ encode $ case result of
        Left err -> object ["name" .= name, "error" .= show err]
        Right (output,logs) -> object $ ["name" .= name, "output" .= output, "logs" .= logs] ++ ["document" .= doc | deep == Nothing]
request = withObject "request" $ \o -> do
  name <- o .: "name"
  deep <- o .:? "deep" >>= traverse (withObject "deep" (\d -> (,) <$> d .: "kind" <*> d .: "depth"))
  native <- o .:? "native"
  reader <- o .:? "reader" .!= ("native" :: T.Text)
  doc <- case deep of
    Just (kind, depth) -> pure $ deepDocument kind depth
    Nothing -> case native of
      Just input -> either (fail . show) pure (runPure $ (if reader == "muse" then readMuse else readNative) def (input :: T.Text))
      Nothing -> o .: "document"
  w <- o .:? "wrap" .!= ("auto" :: T.Text)
  raw <- o .:? "rawHtml" .!= False
  dpi <- o .:? "dpi" .!= 96
  toc <- o .:? "toc" .!= False
  tpl <- o .:? "template"
  pure (name :: T.Text, doc :: Pandoc, case w of "preserve" -> WrapPreserve; "none" -> WrapNone; _ -> WrapAuto, raw, dpi, toc, tpl :: Maybe T.Text, deep :: Maybe (T.Text, Int))

-- Bounded independent AST recipes avoid serializing thousands of nested nodes
-- through an unrelated JSON/native reader before exercising the original writer.
deepDocument :: T.Text -> Int -> Pandoc
deepDocument kind depth
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
      "diagnostic" -> Div nullAttr [b]
      "quote" -> BlockQuote [b]
      "bullet" -> BulletList [[Plain [Str "x"],b]]
      "ordered" -> OrderedList (1, DefaultStyle, DefaultDelim) [[Plain [Str "x"],b]]
      "definition" -> DefinitionList [([Str "term"],[[b]])]
      "table" -> Table nullAttr (Caption Nothing []) [(AlignDefault,ColWidthDefault)] (TableHead nullAttr []) [TableBody nullAttr 0 [] [Row nullAttr [Cell nullAttr AlignDefault 1 1 [b]]]] (TableFoot nullAttr [])
      "mixed" -> BlockQuote [Div nullAttr [BulletList [[Para [Str "x"],b]]]]
      _ -> error "unknown deep recipe"
