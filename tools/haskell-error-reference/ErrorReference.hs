{-# LANGUAGE OverloadedStrings, ScopedTypeVariables #-}
module Main where
import Data.Aeson
import qualified Data.Text as T
import qualified Data.Vector as V
import qualified Data.ByteString as BS
import qualified Data.ByteString.Char8 as B
import qualified Data.ByteString.Lazy as BL
import qualified Data.ByteString.Lazy.Char8 as C
import Data.Char(chr,ord)
import Data.Int(Int64)
import Control.Exception(try,SomeException,evaluate,displayException,fromException,bracket)
import Control.Concurrent.MVar(newEmptyMVar,MVar)
import System.IO
import System.Exit(ExitCode(..))
import System.IO.Temp(withSystemTempFile)
import GHC.IO.Handle(hDuplicate,hDuplicateTo)
import qualified GHC.IO.Exception as E
import qualified GHC.IO.Handle.Types as H
import Text.Pandoc.Error
import Citeproc
as :: FromJSON a => Value -> a
as v=case fromJSON v of Success a->a;Error e->error e
arr(Array v)=V.toList v
arr _=error "array"
chars v=map(chr.as)(arr v)
txt=T.pack.chars
optional f Null=Nothing
optional f v=Just(f v)
types=[E.AlreadyExists,E.NoSuchThing,E.ResourceBusy,E.ResourceExhausted,E.EOF,E.IllegalOperation,E.PermissionDenied,E.UserError,E.UnsatisfiedConstraints,E.SystemError,E.ProtocolError,E.OtherError,E.InvalidArgument,E.InappropriateType,E.HardwareFault,E.UnsupportedOperation,E.TimeExpired,E.ResourceVanished,E.Interrupted]
handle cells v=case arr v of
 [String kind,n,r,w]->if kind=="file" then H.FileHandle(chars n)(cells!!as r) else H.DuplexHandle(chars n)(cells!!as r)(cells!!as w)
 _->error "handle"
io cells v=case arr v of
 [t,l,d,en,f,h]->E.IOError(optional(handle cells)h)(types!!as t)(chars l)(chars d)(optional(fromIntegral.(as::Value->Int64))en)(optional chars f)
 _->error "io"
cite v=case arr v of
 [String "CiteprocXMLError",s]->CiteprocXMLError(txt s)
 [String "CiteprocParseError",s]->CiteprocParseError(txt s)
 [String "CiteprocLocaleNotFound",s]->CiteprocLocaleNotFound(txt s)
 _->error "cite"
hex = T.pack . concatMap (\w -> ["0123456789abcdef"!!fromIntegral(w `div` 16),"0123456789abcdef"!!fromIntegral(w `mod` 16)]) . BS.unpack
fields p e=object["rendered" .= renderError e,"displayed" .= map ord(displayException e),
 "shown" .= map ord(showsPrec p e ""),"shownText" .= T.pack(showsPrec p e "")]
capture e=withSystemTempFile "kotdoc-error" $ \fp h->do
 hClose h
 result<-withFile fp WriteMode $ \out->bracket(hDuplicate stderr)hClose $ \saved->do
   hFlush stderr;hDuplicateTo out stderr;hSetEncoding stderr utf8
   result<-try(handleError(Left e)::IO())::IO(Either SomeException ())
   hFlush stderr;hDuplicateTo saved stderr
   pure result
 bytes<-BS.readFile fp
 pure(case result of
  Left ex->case fromException ex of
   Just(ExitFailure n)->object["exit" .= n,"stderr" .= hex bytes]
   Just ExitSuccess->object["exit" .= (0::Int),"stderr" .= hex bytes]
   Nothing->object["exception" .= displayException ex,"stderr" .= hex bytes]
  Right _->object["returned" .= True,"stderr" .= hex bytes])
response cells req=case arr req of
 [String "error",v,p]->pure(fields(as p)(pandoc cells v))
 [String "handle",v]->capture(pandoc cells v)
 [String "io",v,p]->let e=io cells v in pure(object["shown" .= map ord(showsPrec(as p)e ""),"text" .= T.pack(show e),"displayed" .= map ord(displayException e)])
 [String "ioEq",a,b]->pure(toJSON(io cells a==io cells b))
 [String "cite",v,p]->let e=cite v in pure(object["shown" .= T.pack(showsPrec(as p)e ""),"pretty" .= prettyCiteprocError e])
 [String "citeEq",a,b]->pure(toJSON(cite a==cite b))
 _->error "operation"
main=do
 cells<-sequence(replicate 8(newEmptyMVar::IO(MVar H.Handle__)))
 let loop=do
      end<-isEOF
      if end then pure() else do
       line<-B.getLine
       let req=case eitherDecode(BL.fromStrict line) of Right v->v;Left e->error e
       r<-response cells req;_<-evaluate(BL.length(encode r));C.putStrLn(encode r);hFlush stdout;loop
 loop

-- Generated constructor transport only; every operation above is upstream.
pandoc cells v=case arr v of
 [String "PandocIOError",a0,a1]->PandocIOError (txt a0) (io cells a1)
 [String "PandocHttpError",a0,a1]->PandocHttpError (txt a0) (txt a1)
 [String "PandocShouldNeverHappenError",a0]->PandocShouldNeverHappenError (txt a0)
 [String "PandocSomeError",a0]->PandocSomeError (txt a0)
 [String "PandocParseError",a0]->PandocParseError (txt a0)
 [String "PandocMakePDFError",a0]->PandocMakePDFError (txt a0)
 [String "PandocOptionError",a0]->PandocOptionError (txt a0)
 [String "PandocSyntaxMapError",a0]->PandocSyntaxMapError (txt a0)
 [String "PandocFailOnWarningError"]->PandocFailOnWarningError
 [String "PandocPDFProgramNotFoundError",a0]->PandocPDFProgramNotFoundError (txt a0)
 [String "PandocPDFError",a0]->PandocPDFError (txt a0)
 [String "PandocXMLError",a0,a1]->PandocXMLError (txt a0) (txt a1)
 [String "PandocFilterError",a0,a1]->PandocFilterError (txt a0) (txt a1)
 [String "PandocLuaError",a0]->PandocLuaError (txt a0)
 [String "PandocNoScriptingEngine"]->PandocNoScriptingEngine
 [String "PandocCouldNotFindDataFileError",a0]->PandocCouldNotFindDataFileError (txt a0)
 [String "PandocCouldNotFindMetadataFileError",a0]->PandocCouldNotFindMetadataFileError (txt a0)
 [String "PandocResourceNotFound",a0]->PandocResourceNotFound (txt a0)
 [String "PandocTemplateError",a0]->PandocTemplateError (txt a0)
 [String "PandocNoTemplateError",a0]->PandocNoTemplateError (txt a0)
 [String "PandocAppError",a0]->PandocAppError (txt a0)
 [String "PandocEpubSubdirectoryError",a0]->PandocEpubSubdirectoryError (txt a0)
 [String "PandocMacroLoop",a0]->PandocMacroLoop (txt a0)
 [String "PandocUTF8DecodingError",a0,a1,a2]->PandocUTF8DecodingError (txt a0) (as a1) (as a2)
 [String "PandocIpynbDecodingError",a0]->PandocIpynbDecodingError (txt a0)
 [String "PandocUnsupportedCharsetError",a0]->PandocUnsupportedCharsetError (txt a0)
 [String "PandocFormatError",a0,a1]->PandocFormatError (txt a0) (txt a1)
 [String "PandocUnknownReaderError",a0]->PandocUnknownReaderError (txt a0)
 [String "PandocUnknownWriterError",a0]->PandocUnknownWriterError (txt a0)
 [String "PandocUnsupportedExtensionError",a0,a1]->PandocUnsupportedExtensionError (txt a0) (txt a1)
 [String "PandocCiteprocError",a0]->PandocCiteprocError (cite a0)
 [String "PandocBibliographyError",a0,a1]->PandocBibliographyError (txt a0) (txt a1)
 [String "PandocInputNotTextError",a0]->PandocInputNotTextError (txt a0)
 _->error "PandocError"
