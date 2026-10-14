-- Verbatim examples from pandoc-types test/test-pandoc-types.hs.
-- Copyright (c) 2006-2023 John MacFarlane. BSD-3-Clause; see licenses/.
{-# LANGUAGE OverloadedStrings, QuasiQuotes #-}
module OriginalExamples where
import Text.Pandoc.Definition
import Data.Aeson (Value, ToJSON, toJSON, object, (.=))
import qualified Data.Map as M
import Data.ByteString.Lazy (ByteString)
import Data.String.QQ
t_meta :: (Meta, ByteString)
t_meta = ( Meta $ M.fromList [("foo", MetaBool True)]
         , [s|{"foo":{"t":"MetaBool","c":true}}|]
         )

t_metamap :: (MetaValue, ByteString)
t_metamap = ( MetaMap $
              M.fromList [("foo", MetaBool True)]
            , [s|{"t":"MetaMap","c":{"foo":{"t":"MetaBool","c":true}}}|]
            )

t_metalist :: (MetaValue, ByteString)
t_metalist = ( MetaList [MetaBool True, MetaString "baz"]
             , [s|{"t":"MetaList","c":[{"t":"MetaBool","c":true},{"t":"MetaString","c":"baz"}]}|]
             )

t_metabool :: (MetaValue, ByteString)
t_metabool = ( MetaBool False, [s|{"t":"MetaBool","c":false}|] )

t_metastring :: (MetaValue, ByteString)
t_metastring = ( MetaString "Hello", [s|{"t":"MetaString","c":"Hello"}|] )

t_metainlines :: (MetaValue, ByteString)
t_metainlines = ( MetaInlines [Space, SoftBreak]
                , [s|{"t":"MetaInlines","c":[{"t":"Space"},{"t":"SoftBreak"}]}|]
                )

t_metablocks :: (MetaValue, ByteString)
t_metablocks = ( MetaBlocks [HorizontalRule,HorizontalRule], [s|{"t":"MetaBlocks","c":[{"t":"HorizontalRule"},{"t":"HorizontalRule"}]}|])

t_singlequote :: (QuoteType, ByteString)
t_singlequote = (SingleQuote, [s|{"t":"SingleQuote"}|])

t_doublequote :: (QuoteType, ByteString)
t_doublequote = (DoubleQuote, [s|{"t":"DoubleQuote"}|])

t_authorintext :: (CitationMode, ByteString)
t_authorintext = (AuthorInText, [s|{"t":"AuthorInText"}|])

t_suppressauthor :: (CitationMode, ByteString)
t_suppressauthor = (SuppressAuthor, [s|{"t":"SuppressAuthor"}|])

t_normalcitation :: (CitationMode, ByteString)
t_normalcitation = (NormalCitation, [s|{"t":"NormalCitation"}|])

t_citation :: (Citation, ByteString)
t_citation = ( Citation { citationId = "jameson:unconscious",
                          citationPrefix = [Str "cf"],
                          citationSuffix = [Space,Str "123"],
                          citationMode = NormalCitation,
                          citationNoteNum = 0,
                          citationHash = 0}
             , [s|{"citationId":"jameson:unconscious","citationPrefix":[{"t":"Str","c":"cf"}],"citationSuffix":[{"t":"Space"},{"t":"Str","c":"123"}],"citationMode":{"t":"NormalCitation"},"citationNoteNum":0,"citationHash":0}|]
             )

t_displaymath :: (MathType, ByteString)
t_displaymath = ( DisplayMath, [s|{"t":"DisplayMath"}|])

t_inlinemath :: (MathType, ByteString)
t_inlinemath = ( InlineMath, [s|{"t":"InlineMath"}|])


t_str :: (Inline, ByteString)
t_str = ( Str "Hello"
        , [s|{"t":"Str","c":"Hello"}|]
        )

t_emph :: (Inline, ByteString)
t_emph = ( Emph [Str "Hello"]
         , [s|{"t":"Emph","c":[{"t":"Str","c":"Hello"}]}|]
         )

t_underline :: (Inline, ByteString)
t_underline = ( Underline [Str "Hello"]
         , [s|{"t":"Underline","c":[{"t":"Str","c":"Hello"}]}|]
         )

t_strong :: (Inline, ByteString)
t_strong = ( Strong [Str "Hello"]
           , [s|{"t":"Strong","c":[{"t":"Str","c":"Hello"}]}|]
           )

t_strikeout :: (Inline, ByteString)
t_strikeout = ( Strikeout [Str "Hello"]
              , [s|{"t":"Strikeout","c":[{"t":"Str","c":"Hello"}]}|]
              )

t_superscript :: (Inline, ByteString)
t_superscript = ( Superscript [Str "Hello"]
                , [s|{"t":"Superscript","c":[{"t":"Str","c":"Hello"}]}|]
                )

t_subscript :: (Inline, ByteString)
t_subscript = ( Subscript [Str "Hello"]
              , [s|{"t":"Subscript","c":[{"t":"Str","c":"Hello"}]}|]
              )

t_smallcaps :: (Inline, ByteString)
t_smallcaps = ( SmallCaps [Str "Hello"]
              , [s|{"t":"SmallCaps","c":[{"t":"Str","c":"Hello"}]}|]
              )

t_quoted :: (Inline, ByteString)
t_quoted = ( Quoted SingleQuote [Str "Hello"]
           , [s|{"t":"Quoted","c":[{"t":"SingleQuote"},[{"t":"Str","c":"Hello"}]]}|]
           )

t_cite :: (Inline, ByteString)
t_cite = ( Cite [Citation { citationId = "jameson:unconscious"
                          , citationPrefix = [Str "cf"]
                          , citationSuffix = [Space,Str "12"]
                          , citationMode = NormalCitation
                          , citationNoteNum = 0
                          , citationHash = 0}]
                [ Str "[cf"
                , Space
                , Str "@jameson:unconscious"
                , Space
                , Str "12]"]
         ,[s|{"t":"Cite","c":[[{"citationId":"jameson:unconscious","citationPrefix":[{"t":"Str","c":"cf"}],"citationSuffix":[{"t":"Space"},{"t":"Str","c":"12"}],"citationMode":{"t":"NormalCitation"},"citationNoteNum":0,"citationHash":0}],[{"t":"Str","c":"[cf"},{"t":"Space"},{"t":"Str","c":"@jameson:unconscious"},{"t":"Space"},{"t":"Str","c":"12]"}]]}|]
             )

t_code :: (Inline, ByteString)
t_code = ( Code ("", [], [("language", "haskell")]) "foo bar"
         , [s|{"t":"Code","c":[["",[],[["language","haskell"]]],"foo bar"]}|]
         )

t_space :: (Inline, ByteString)
t_space = ( Space, [s|{"t":"Space"}|] )

t_softbreak :: (Inline, ByteString)
t_softbreak = ( SoftBreak, [s|{"t":"SoftBreak"}|] )

t_linebreak :: (Inline, ByteString)
t_linebreak = ( LineBreak, [s|{"t":"LineBreak"}|] )

t_rawinline :: (Inline, ByteString)
t_rawinline = ( RawInline (Format "tex") "\\foo{bar}"
              , [s|{"t":"RawInline","c":["tex","\\foo{bar}"]}|]
              )

t_link :: (Inline, ByteString)
t_link = ( Link ("id",["kls"],[("k1", "v1"), ("k2", "v2")])
           [ Str "a", Space, Str "famous", Space, Str "site"]
           ("https://www.google.com","google")
         , [s|{"t":"Link","c":[["id",["kls"],[["k1","v1"],["k2","v2"]]],[{"t":"Str","c":"a"},{"t":"Space"},{"t":"Str","c":"famous"},{"t":"Space"},{"t":"Str","c":"site"}],["https://www.google.com","google"]]}|]
         )

t_image :: (Inline, ByteString)
t_image = ( Image ("id",["kls"],[("k1", "v1"), ("k2", "v2")])
           [ Str "a", Space, Str "famous", Space, Str "image"]
           ("my_img.png","image")
         , [s|{"t":"Image","c":[["id",["kls"],[["k1","v1"],["k2","v2"]]],[{"t":"Str","c":"a"},{"t":"Space"},{"t":"Str","c":"famous"},{"t":"Space"},{"t":"Str","c":"image"}],["my_img.png","image"]]}|]
         )

t_note :: (Inline, ByteString)
t_note = ( Note [Para [Str "Hello"]]
         , [s|{"t":"Note","c":[{"t":"Para","c":[{"t":"Str","c":"Hello"}]}]}|]
         )

t_span :: (Inline, ByteString)
t_span = ( Span ("id", ["kls"], [("k1", "v1"), ("k2", "v2")]) [Str "Hello"]
         , [s|{"t":"Span","c":[["id",["kls"],[["k1","v1"],["k2","v2"]]],[{"t":"Str","c":"Hello"}]]}|]
         )

t_plain :: (Block, ByteString)
t_plain = ( Plain [Str "Hello"]
          , [s|{"t":"Plain","c":[{"t":"Str","c":"Hello"}]}|]
          )

t_para :: (Block, ByteString)
t_para = ( Para [Str "Hello"]
          , [s|{"t":"Para","c":[{"t":"Str","c":"Hello"}]}|]
          )

t_lineblock :: (Block, ByteString)
t_lineblock = ( LineBlock [[Str "Hello"], [Str "Moin"]]
              , [s|{"t":"LineBlock","c":[[{"t":"Str","c":"Hello"}],[{"t":"Str","c":"Moin"}]]}|]
              )

t_codeblock :: (Block, ByteString)
t_codeblock = ( CodeBlock ("id", ["kls"], [("k1", "v1"), ("k2", "v2")]) "Foo Bar"
              , [s|{"t":"CodeBlock","c":[["id",["kls"],[["k1","v1"],["k2","v2"]]],"Foo Bar"]}|]
              )

t_rawblock :: (Block, ByteString)
t_rawblock = ( RawBlock (Format "tex") "\\foo{bar}"
              , [s|{"t":"RawBlock","c":["tex","\\foo{bar}"]}|]
              )

t_blockquote :: (Block, ByteString)
t_blockquote = ( BlockQuote [Para [Str "Hello"]]
         , [s|{"t":"BlockQuote","c":[{"t":"Para","c":[{"t":"Str","c":"Hello"}]}]}|]
         )

t_orderedlist :: (Block, ByteString)
t_orderedlist = (OrderedList (1,Decimal,Period)
                 [[Para [Str "foo"]]
                 ,[Para [Str "bar"]]]
                , [s|{"t":"OrderedList","c":[[1,{"t":"Decimal"},{"t":"Period"}],[[{"t":"Para","c":[{"t":"Str","c":"foo"}]}],[{"t":"Para","c":[{"t":"Str","c":"bar"}]}]]]}|]
                    )

t_bulletlist :: (Block, ByteString)
t_bulletlist = (BulletList
                 [[Para [Str "foo"]]
                 ,[Para [Str "bar"]]]
               , [s|{"t":"BulletList","c":[[{"t":"Para","c":[{"t":"Str","c":"foo"}]}],[{"t":"Para","c":[{"t":"Str","c":"bar"}]}]]}|]
                   )

t_definitionlist :: (Block, ByteString)
t_definitionlist = (DefinitionList
                    [([Str "foo"],
                      [[Para [Str "bar"]]])
                    ,([Str "fizz"],
                      [[Para [Str "pop"]]])]
                   , [s|{"t":"DefinitionList","c":[[[{"t":"Str","c":"foo"}],[[{"t":"Para","c":[{"t":"Str","c":"bar"}]}]]],[[{"t":"Str","c":"fizz"}],[[{"t":"Para","c":[{"t":"Str","c":"pop"}]}]]]]}|]
                    )

t_header :: (Block, ByteString)
t_header = ( Header 2 ("id", ["kls"], [("k1", "v1"), ("k2", "v2")]) [Str "Head"]
           , [s|{"t":"Header","c":[2,["id",["kls"],[["k1","v1"],["k2","v2"]]],[{"t":"Str","c":"Head"}]]}|]
           )

t_row :: (Row, ByteString)
t_row = (Row ("id",["kls"],[("k1", "v1"), ("k2", "v2")])
         [Cell ("", [], []) AlignRight 2 3 [Para [Str "bar"]]]
        ,[s|[["id",["kls"],[["k1","v1"],["k2","v2"]]],[[["",[],[]],{"t":"AlignRight"},2,3,[{"t":"Para","c":[{"t":"Str","c":"bar"}]}]]]]|])

t_caption :: (Caption, ByteString)
t_caption = (Caption (Just [Str "foo"]) [Para [Str "bar"]]
            ,[s|[[{"t":"Str","c":"foo"}],[{"t":"Para","c":[{"t":"Str","c":"bar"}]}]]|])

t_tablehead :: (TableHead, ByteString)
t_tablehead = (TableHead ("id",["kls"],[("k1", "v1"), ("k2", "v2")])
               [Row ("id",["kls"],[("k1", "v1"), ("k2", "v2")]) []]
              ,[s|[["id",["kls"],[["k1","v1"],["k2","v2"]]],[[["id",["kls"],[["k1","v1"],["k2","v2"]]],[]]]]|])

t_tablebody :: (TableBody, ByteString)
t_tablebody = (TableBody ("id",["kls"],[("k1", "v1"), ("k2", "v2")]) 3
               [Row ("id",["kls"],[("k1", "v1"), ("k2", "v2")]) []]
               [Row ("id'",["kls'"],[("k1", "v1"), ("k2", "v2")]) []]
              ,[s|[["id",["kls"],[["k1","v1"],["k2","v2"]]],3,[[["id",["kls"],[["k1","v1"],["k2","v2"]]],[]]],[[["id'",["kls'"],[["k1","v1"],["k2","v2"]]],[]]]]|])

t_tablefoot :: (TableFoot, ByteString)
t_tablefoot = (TableFoot ("id",["kls"],[("k1", "v1"), ("k2", "v2")])
               [Row ("id",["kls"],[("k1", "v1"), ("k2", "v2")]) []]
              ,[s|[["id",["kls"],[["k1","v1"],["k2","v2"]]],[[["id",["kls"],[["k1","v1"],["k2","v2"]]],[]]]]|])

t_cell :: (Cell, ByteString)
t_cell = (Cell ("id",["kls"],[("k1", "v1"), ("k2", "v2")]) AlignLeft 1 1
          [Para [Str "bar"]]
         ,[s|[["id",["kls"],[["k1","v1"],["k2","v2"]]],{"t":"AlignLeft"},1,1,[{"t":"Para","c":[{"t":"Str","c":"bar"}]}]]|])

t_rowheadcolumns :: (RowHeadColumns, ByteString)
t_rowheadcolumns = (1
                   ,[s|1|])

t_rowspan :: (RowSpan, ByteString)
t_rowspan = (1
            ,[s|1|])

t_colspan :: (ColSpan, ByteString)
t_colspan = (1
            ,[s|1|])

t_table :: (Block, ByteString)
t_table = ( Table
            ("id", ["kls"], [("k1", "v1"), ("k2", "v2")])
            (Caption
             (Just [Str "short"])
             [Para [Str "Demonstration"
                   ,Space
                   ,Str "of"
                   ,Space
                   ,Str "simple"
                   ,Space
                   ,Str "table"
                   ,Space
                   ,Str "syntax."]])
            [(AlignDefault,ColWidthDefault)
            ,(AlignRight,ColWidthDefault)
            ,(AlignLeft,ColWidthDefault)
            ,(AlignCenter,ColWidthDefault)
            ,(AlignDefault,ColWidthDefault)]
            (TableHead ("idh", ["klsh"], [("k1h", "v1h"), ("k2h", "v2h")])
             [tRow
              [tCell [Str "Head"]
              ,tCell [Str "Right"]
              ,tCell [Str "Left"]
              ,tCell [Str "Center"]
              ,tCell [Str "Default"]]])
            [TableBody ("idb", ["klsb"], [("k1b", "v1b"), ("k2b", "v2b")]) 1
             [tRow
              [tCell [Str "ihead12"]
              ,tCell [Str "i12"]
              ,tCell [Str "i12"]
              ,tCell [Str "i12"]
              ,tCell [Str "i12"]]]
             [tRow
              [tCell [Str "head12"]
              ,tCell' [Str "12"]
              ,tCell [Str "12"]
              ,tCell' [Str "12"]
              ,tCell [Str "12"]]
            ,tRow
              [tCell [Str "head123"]
              ,tCell [Str "123"]
              ,tCell [Str "123"]
              ,tCell [Str "123"]
              ,tCell [Str "123"]]
            ,tRow
              [tCell [Str "head1"]
              ,tCell [Str "1"]
              ,tCell [Str "1"]
              ,tCell [Str "1"]
              ,tCell [Str "1"]]]]
            (TableFoot ("idf", ["klsf"], [("k1f", "v1f"), ("k2f", "v2f")])
             [tRow
              [tCell [Str "foot"]
              ,tCell [Str "footright"]
              ,tCell [Str "footleft"]
              ,tCell [Str "footcenter"]
              ,tCell [Str "footdefault"]]])
          ,[s|{"t":"Table","c":[["id",["kls"],[["k1","v1"],["k2","v2"]]],[[{"t":"Str","c":"short"}],[{"t":"Para","c":[{"t":"Str","c":"Demonstration"},{"t":"Space"},{"t":"Str","c":"of"},{"t":"Space"},{"t":"Str","c":"simple"},{"t":"Space"},{"t":"Str","c":"table"},{"t":"Space"},{"t":"Str","c":"syntax."}]}]],[[{"t":"AlignDefault"},{"t":"ColWidthDefault"}],[{"t":"AlignRight"},{"t":"ColWidthDefault"}],[{"t":"AlignLeft"},{"t":"ColWidthDefault"}],[{"t":"AlignCenter"},{"t":"ColWidthDefault"}],[{"t":"AlignDefault"},{"t":"ColWidthDefault"}]],[["idh",["klsh"],[["k1h","v1h"],["k2h","v2h"]]],[[["id",["kls"],[["k1","v1"],["k2","v2"]]],[[["a",["b"],[["c","d"],["e","f"]]],{"t":"AlignDefault"},1,1,[{"t":"Plain","c":[{"t":"Str","c":"Head"}]}]],[["a",["b"],[["c","d"],["e","f"]]],{"t":"AlignDefault"},1,1,[{"t":"Plain","c":[{"t":"Str","c":"Right"}]}]],[["a",["b"],[["c","d"],["e","f"]]],{"t":"AlignDefault"},1,1,[{"t":"Plain","c":[{"t":"Str","c":"Left"}]}]],[["a",["b"],[["c","d"],["e","f"]]],{"t":"AlignDefault"},1,1,[{"t":"Plain","c":[{"t":"Str","c":"Center"}]}]],[["a",["b"],[["c","d"],["e","f"]]],{"t":"AlignDefault"},1,1,[{"t":"Plain","c":[{"t":"Str","c":"Default"}]}]]]]]],[[["idb",["klsb"],[["k1b","v1b"],["k2b","v2b"]]],1,[[["id",["kls"],[["k1","v1"],["k2","v2"]]],[[["a",["b"],[["c","d"],["e","f"]]],{"t":"AlignDefault"},1,1,[{"t":"Plain","c":[{"t":"Str","c":"ihead12"}]}]],[["a",["b"],[["c","d"],["e","f"]]],{"t":"AlignDefault"},1,1,[{"t":"Plain","c":[{"t":"Str","c":"i12"}]}]],[["a",["b"],[["c","d"],["e","f"]]],{"t":"AlignDefault"},1,1,[{"t":"Plain","c":[{"t":"Str","c":"i12"}]}]],[["a",["b"],[["c","d"],["e","f"]]],{"t":"AlignDefault"},1,1,[{"t":"Plain","c":[{"t":"Str","c":"i12"}]}]],[["a",["b"],[["c","d"],["e","f"]]],{"t":"AlignDefault"},1,1,[{"t":"Plain","c":[{"t":"Str","c":"i12"}]}]]]]],[[["id",["kls"],[["k1","v1"],["k2","v2"]]],[[["a",["b"],[["c","d"],["e","f"]]],{"t":"AlignDefault"},1,1,[{"t":"Plain","c":[{"t":"Str","c":"head12"}]}]],[["id",["kls"],[["k1","v1"],["k2","v2"]]],{"t":"AlignDefault"},1,1,[{"t":"Plain","c":[{"t":"Str","c":"12"}]}]],[["a",["b"],[["c","d"],["e","f"]]],{"t":"AlignDefault"},1,1,[{"t":"Plain","c":[{"t":"Str","c":"12"}]}]],[["id",["kls"],[["k1","v1"],["k2","v2"]]],{"t":"AlignDefault"},1,1,[{"t":"Plain","c":[{"t":"Str","c":"12"}]}]],[["a",["b"],[["c","d"],["e","f"]]],{"t":"AlignDefault"},1,1,[{"t":"Plain","c":[{"t":"Str","c":"12"}]}]]]],[["id",["kls"],[["k1","v1"],["k2","v2"]]],[[["a",["b"],[["c","d"],["e","f"]]],{"t":"AlignDefault"},1,1,[{"t":"Plain","c":[{"t":"Str","c":"head123"}]}]],[["a",["b"],[["c","d"],["e","f"]]],{"t":"AlignDefault"},1,1,[{"t":"Plain","c":[{"t":"Str","c":"123"}]}]],[["a",["b"],[["c","d"],["e","f"]]],{"t":"AlignDefault"},1,1,[{"t":"Plain","c":[{"t":"Str","c":"123"}]}]],[["a",["b"],[["c","d"],["e","f"]]],{"t":"AlignDefault"},1,1,[{"t":"Plain","c":[{"t":"Str","c":"123"}]}]],[["a",["b"],[["c","d"],["e","f"]]],{"t":"AlignDefault"},1,1,[{"t":"Plain","c":[{"t":"Str","c":"123"}]}]]]],[["id",["kls"],[["k1","v1"],["k2","v2"]]],[[["a",["b"],[["c","d"],["e","f"]]],{"t":"AlignDefault"},1,1,[{"t":"Plain","c":[{"t":"Str","c":"head1"}]}]],[["a",["b"],[["c","d"],["e","f"]]],{"t":"AlignDefault"},1,1,[{"t":"Plain","c":[{"t":"Str","c":"1"}]}]],[["a",["b"],[["c","d"],["e","f"]]],{"t":"AlignDefault"},1,1,[{"t":"Plain","c":[{"t":"Str","c":"1"}]}]],[["a",["b"],[["c","d"],["e","f"]]],{"t":"AlignDefault"},1,1,[{"t":"Plain","c":[{"t":"Str","c":"1"}]}]],[["a",["b"],[["c","d"],["e","f"]]],{"t":"AlignDefault"},1,1,[{"t":"Plain","c":[{"t":"Str","c":"1"}]}]]]]]]],[["idf",["klsf"],[["k1f","v1f"],["k2f","v2f"]]],[[["id",["kls"],[["k1","v1"],["k2","v2"]]],[[["a",["b"],[["c","d"],["e","f"]]],{"t":"AlignDefault"},1,1,[{"t":"Plain","c":[{"t":"Str","c":"foot"}]}]],[["a",["b"],[["c","d"],["e","f"]]],{"t":"AlignDefault"},1,1,[{"t":"Plain","c":[{"t":"Str","c":"footright"}]}]],[["a",["b"],[["c","d"],["e","f"]]],{"t":"AlignDefault"},1,1,[{"t":"Plain","c":[{"t":"Str","c":"footleft"}]}]],[["a",["b"],[["c","d"],["e","f"]]],{"t":"AlignDefault"},1,1,[{"t":"Plain","c":[{"t":"Str","c":"footcenter"}]}]],[["a",["b"],[["c","d"],["e","f"]]],{"t":"AlignDefault"},1,1,[{"t":"Plain","c":[{"t":"Str","c":"footdefault"}]}]]]]]]]}|]
              )
  where
    tCell i = Cell ("a", ["b"], [("c", "d"), ("e", "f")]) AlignDefault 1 1 [Plain i]
    tCell' i = Cell ("id", ["kls"], [("k1", "v1"), ("k2", "v2")]) AlignDefault 1 1 [Plain i]
    tRow = Row ("id", ["kls"], [("k1", "v1"), ("k2", "v2")])

t_figure :: (Block, ByteString)
t_figure = (Figure
            ("id", ["kls"], [("k1", "v1"), ("k2", "v2")])
            (Caption (Just [Str "hello"]) [Para [Str "cap content"]])
            [Para [Str "fig content"]]
           ,[s|{"t":"Figure","c":[["id",["kls"],[["k1","v1"],["k2","v2"]]],[[{"t":"Str","c":"hello"}],[{"t":"Para","c":[{"t":"Str","c":"cap content"}]}]],[{"t":"Para","c":[{"t":"Str","c":"fig content"}]}]]}|]
           )

t_div :: (Block, ByteString)
t_div = ( Div ("id", ["kls"], [("k1", "v1"), ("k2", "v2")]) [Para [Str "Hello"]]
         , [s|{"t":"Div","c":[["id",["kls"],[["k1","v1"],["k2","v2"]]],[{"t":"Para","c":[{"t":"Str","c":"Hello"}]}]]}|]
         )


entry :: (ToJSON a, Show a) => String -> (a, ByteString) -> Int -> Value
entry kind (v, _) p = object ["request" .= toJSON [toJSON ("show"::String), toJSON kind, toJSON p, toJSON v], "expected" .= showsPrec p v ""]
examples :: [Value]
examples = concat [
 [entry "meta" t_meta p | p <- [-1,0,6,10,11,12]],
 [entry "metaValue" t_metamap p | p <- [-1,0,6,10,11,12]],
 [entry "metaValue" t_metalist p | p <- [-1,0,6,10,11,12]],
 [entry "metaValue" t_metabool p | p <- [-1,0,6,10,11,12]],
 [entry "metaValue" t_metastring p | p <- [-1,0,6,10,11,12]],
 [entry "metaValue" t_metainlines p | p <- [-1,0,6,10,11,12]],
 [entry "metaValue" t_metablocks p | p <- [-1,0,6,10,11,12]],
 [entry "quote" t_singlequote p | p <- [-1,0,6,10,11,12]],
 [entry "quote" t_doublequote p | p <- [-1,0,6,10,11,12]],
 [entry "citationMode" t_authorintext p | p <- [-1,0,6,10,11,12]],
 [entry "citationMode" t_suppressauthor p | p <- [-1,0,6,10,11,12]],
 [entry "citationMode" t_normalcitation p | p <- [-1,0,6,10,11,12]],
 [entry "citation" t_citation p | p <- [-1,0,6,10,11,12]],
 [entry "math" t_displaymath p | p <- [-1,0,6,10,11,12]],
 [entry "math" t_inlinemath p | p <- [-1,0,6,10,11,12]],
 [entry "inline" t_str p | p <- [-1,0,6,10,11,12]],
 [entry "inline" t_emph p | p <- [-1,0,6,10,11,12]],
 [entry "inline" t_underline p | p <- [-1,0,6,10,11,12]],
 [entry "inline" t_strong p | p <- [-1,0,6,10,11,12]],
 [entry "inline" t_strikeout p | p <- [-1,0,6,10,11,12]],
 [entry "inline" t_superscript p | p <- [-1,0,6,10,11,12]],
 [entry "inline" t_subscript p | p <- [-1,0,6,10,11,12]],
 [entry "inline" t_smallcaps p | p <- [-1,0,6,10,11,12]],
 [entry "inline" t_quoted p | p <- [-1,0,6,10,11,12]],
 [entry "inline" t_cite p | p <- [-1,0,6,10,11,12]],
 [entry "inline" t_code p | p <- [-1,0,6,10,11,12]],
 [entry "inline" t_space p | p <- [-1,0,6,10,11,12]],
 [entry "inline" t_softbreak p | p <- [-1,0,6,10,11,12]],
 [entry "inline" t_linebreak p | p <- [-1,0,6,10,11,12]],
 [entry "inline" t_rawinline p | p <- [-1,0,6,10,11,12]],
 [entry "inline" t_link p | p <- [-1,0,6,10,11,12]],
 [entry "inline" t_image p | p <- [-1,0,6,10,11,12]],
 [entry "inline" t_note p | p <- [-1,0,6,10,11,12]],
 [entry "inline" t_span p | p <- [-1,0,6,10,11,12]],
 [entry "block" t_plain p | p <- [-1,0,6,10,11,12]],
 [entry "block" t_para p | p <- [-1,0,6,10,11,12]],
 [entry "block" t_lineblock p | p <- [-1,0,6,10,11,12]],
 [entry "block" t_codeblock p | p <- [-1,0,6,10,11,12]],
 [entry "block" t_rawblock p | p <- [-1,0,6,10,11,12]],
 [entry "block" t_blockquote p | p <- [-1,0,6,10,11,12]],
 [entry "block" t_orderedlist p | p <- [-1,0,6,10,11,12]],
 [entry "block" t_bulletlist p | p <- [-1,0,6,10,11,12]],
 [entry "block" t_definitionlist p | p <- [-1,0,6,10,11,12]],
 [entry "block" t_header p | p <- [-1,0,6,10,11,12]],
 [entry "row" t_row p | p <- [-1,0,6,10,11,12]],
 [entry "caption" t_caption p | p <- [-1,0,6,10,11,12]],
 [entry "tableHead" t_tablehead p | p <- [-1,0,6,10,11,12]],
 [entry "tableBody" t_tablebody p | p <- [-1,0,6,10,11,12]],
 [entry "tableFoot" t_tablefoot p | p <- [-1,0,6,10,11,12]],
 [entry "cell" t_cell p | p <- [-1,0,6,10,11,12]],
 [entry "rowHeadColumns" t_rowheadcolumns p | p <- [-1,0,6,10,11,12]],
 [entry "rowSpan" t_rowspan p | p <- [-1,0,6,10,11,12]],
 [entry "colSpan" t_colspan p | p <- [-1,0,6,10,11,12]],
 [entry "block" t_table p | p <- [-1,0,6,10,11,12]],
 [entry "block" t_figure p | p <- [-1,0,6,10,11,12]],
 [entry "block" t_div p | p <- [-1,0,6,10,11,12]]
 ]
