{-# LANGUAGE OverloadedStrings #-}
-- Pure observations of original private collector functions (dev export only).
import qualified SourceOrgReader.Meta as O
import Text.Pandoc
import qualified Text.Pandoc.Builder as B
import Data.Aeson
import Data.Aeson.Types(parseEither)
import qualified Data.Map as M
import qualified Data.Text as T
import qualified Data.ByteString.Lazy.Char8 as BS
value kind n=case kind of
 "absent" -> Nothing
 "bool" -> Just(MetaBool True)
 "map" -> Just(MetaMap(M.singleton "inner"(MetaString "hidden")))
 "string" -> Just(MetaString "old value")
 "empty-string" -> Just(MetaString "")
 "inlines" -> Just(MetaInlines[Str "old",Space,Str "value"])
 "empty-inlines" -> Just(MetaInlines[])
 "list" -> Just(MetaList[MetaInlines[],MetaString "x",MetaBool False,MetaInlines[Str "y"]])
 "empty-list" -> Just(MetaList[])
 "blocks" -> Just(MetaBlocks[Para[Str "one"],Div nullAttr[Plain[Str "two"]],CodeBlock nullAttr "code",BlockQuote[Plain[Str "quote"]],BulletList[[Plain[Str "item"]]],DefinitionList[([Str "term"],[[Para[Str "definition"]]])],HorizontalRule])
 "line-block" -> Just(MetaBlocks[LineBlock[[Str "a"],[],[Str "b"]]])
 "raw-block" -> Just(MetaBlocks[RawBlock(Format "html")"raw"])
 "header" -> Just(MetaBlocks[Header 3 nullAttr[Str "header"]])
 "ordered-list" -> Just(MetaBlocks[OrderedList(3,Decimal,Period)[[Plain[Str "one"]],[Para[Str "two"]]]])
 "figure" -> Just(MetaBlocks[Figure nullAttr(Caption Nothing[Plain[Str "caption"]])[Plain[Str "body"]]])
 "table" -> Just(MetaBlocks[Table nullAttr(Caption Nothing[Plain[Str "caption"]])[(AlignLeft,ColWidth 0.5)](TableHead nullAttr[Row nullAttr[B.simpleCell(B.plain(B.str "head"))]])[TableBody nullAttr 0 [Row nullAttr[B.simpleCell(B.plain(B.str "body-head"))]][Row nullAttr[B.simpleCell(B.plain(B.str "body"))]]](TableFoot nullAttr[Row nullAttr[B.simpleCell(B.plain(B.str "foot"))]])])
 "deep-list" -> Just(iterate (MetaList . (:[]))(MetaString "bottom")!!n)
 "deep-div" -> Just(MetaBlocks[iterate (Div nullAttr . (:[]))(Para[Str "bottom"])!!n])
 _ -> error"unknown kind"
observe q=case parseEither(withObject"request"$ \o -> (,,,,,) <$>o.:"kind" <*>o.:"operation" <*>(o.:?"depth".!=0) <*>(o.:?"repeat".!=1) <*>(o.:?"empty".!=False)<*>(o.:?"observeShow".!=False))q of
 Left e -> error e
 Right(kind,op,depth,n,empty,observeShow) -> do
  let initial=Meta$maybe M.empty(M.singleton "x")(value kind depth)
  let step=if op==("lines"::T.Text)then O.collectLines "x" (B.fromList(if empty then[]else[Str "tail"]))else O.collectAsList "x" (MetaInlines(if empty then[]else[Str "tail"]))
  let result=iterate step initial!!n
  BS.putStrLn$encode$object["request".=q,"expected".=(if observeShow then object["showDocument".=show(Pandoc result [])]else toJSON(Pandoc result []))]
main=BS.getContents>>=mapM_(observe.either error id.eitherDecode).BS.lines
