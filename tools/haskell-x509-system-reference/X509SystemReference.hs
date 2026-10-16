{-# LANGUAGE OverloadedStrings,ScopedTypeVariables,PackageImports #-}
module Main where
import Data.Aeson
import qualified Data.Aeson.KeyMap as K
import qualified Data.ByteString as B
import qualified Data.ByteString.Lazy as L
import qualified Data.ByteString.Lazy.Char8 as LC
import Control.Exception
import System.IO.Error
import Data.X509
import Data.X509.CertificateStore
import qualified System.X509.Unix as U
import qualified System.X509.MacOS as M
import qualified System.X509.Win32 as W
import SystemEffects (configure,cleanup,trace,field,convert)
snapshot v s=case convert(field v "part")::String of
 "ignore"->Null
 "empty"->toJSON $ isEmptyCertificateStore s
 "count"->toJSON $ length $ listCertificates s
 _->toJSON $ map (toJSON . B.unpack . encodeSignedObject) $ listCertificates s
run v=bracket_ (configure v) cleanup $ do
 result<-try $ do
  store<-case convert(field v "platform")::String of "unix"->U.getSystemCertificateStore;"mac"->M.getSystemCertificateStore;_->W.getSystemCertificateStore
  let value=snapshot v store
  evaluate $ L.length $ encode value
  pure value
 let value=case result of
      Right x->object["right" .= x]
      Left(e::SomeException)->case fromException e::Maybe IOException of
       Just io->object["io" .= ioeGetErrorString io]
       Nothing->object["fatal" .= takeWhile (/='\n') (displayException e)]
 log<-trace
 pure $ object["result" .= value,"trace" .= log]
main=LC.getContents >>= mapM_ (\line->case eitherDecode line of Left e->error e;Right v->run v >>= LC.putStrLn . encode) . LC.lines
