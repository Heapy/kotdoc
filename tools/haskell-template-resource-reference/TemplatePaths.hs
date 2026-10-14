{-# LANGUAGE OverloadedStrings #-}
module Main where
import Data.Aeson
import qualified Data.ByteString.Lazy.Char8 as B
import qualified System.FilePath.Posix as P
import qualified System.FilePath.Windows as W
import qualified CanonicalPosix as CP
import qualified CanonicalWindows as CW
import qualified Data.Vector as V
import qualified Data.Text as T
main = B.getContents >>= mapM_ (B.putStrLn . encode . run . either error id . eitherDecode) . B.lines
run (Array a) = case V.toList a of
 [String style,String path,String dir] ->
   let s=T.unpack path
       d=T.unpack dir
       (file,rel,combined,canonical) = if style == "posix"
         then (P.takeFileName s,P.isRelative s,P.combine d s,CP.makeCanonical s)
         else (W.takeFileName s,W.isRelative s,W.combine d s,CW.makeCanonical s)
       extended = if style == "posix" then P.addExtension s d else W.addExtension s d
   in object ["file" .= file,"relative" .= rel,"combined" .= combined,"canonical" .= canonical,"extended" .= extended]
 _ -> error "request"
run _ = error "array"
