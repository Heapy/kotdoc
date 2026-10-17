{-# LANGUAGE OverloadedStrings #-}
module DeepRecipes where
import Text.Pandoc.Definition
import qualified Data.Text as T
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
