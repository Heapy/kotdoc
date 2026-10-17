{-# LANGUAGE ScopedTypeVariables #-}
-- Executes pinned pandoc-types Data dictionaries and the installed GHC runtime.
import Data.Data
import Data.Proxy
import Data.Aeson (encode)
import qualified Data.ByteString.Lazy.Char8 as B
import Text.Pandoc.Definition
import Text.Pandoc.Builder (Many)
metadata :: forall a. Data a => Proxy a -> (String,[(Int,String,[String],String)])
metadata _ = let dt=dataTypeOf (error "unused metadata root"::a)
             in (dataTypeName dt,[(constrIndex c,showConstr c,constrFields c,show(constrFixity c))|c<-dataTypeConstrs dt])
main :: IO ()
main=mapM_ (B.putStrLn.encode)
 [metadata(Proxy::Proxy Pandoc),metadata(Proxy::Proxy Meta),metadata(Proxy::Proxy MetaValue)
 ,metadata(Proxy::Proxy ListNumberStyle),metadata(Proxy::Proxy ListNumberDelim),metadata(Proxy::Proxy Format)
 ,metadata(Proxy::Proxy RowHeadColumns),metadata(Proxy::Proxy Alignment),metadata(Proxy::Proxy ColWidth)
 ,metadata(Proxy::Proxy Row),metadata(Proxy::Proxy TableHead),metadata(Proxy::Proxy TableBody)
 ,metadata(Proxy::Proxy TableFoot),metadata(Proxy::Proxy Caption),metadata(Proxy::Proxy Cell)
 ,metadata(Proxy::Proxy RowSpan),metadata(Proxy::Proxy ColSpan),metadata(Proxy::Proxy Block)
 ,metadata(Proxy::Proxy QuoteType),metadata(Proxy::Proxy MathType),metadata(Proxy::Proxy Inline)
 ,metadata(Proxy::Proxy Citation),metadata(Proxy::Proxy CitationMode),metadata(Proxy::Proxy(Many Inline))]
