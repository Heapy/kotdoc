{-# LANGUAGE ForeignFunctionInterface, OverloadedStrings #-}
module Main where
import Data.Aeson hiding(withArray)
import qualified Data.Vector as V
import qualified Data.ByteString as BS
import qualified Data.ByteString.Lazy as BL
import qualified Data.ByteString.Lazy.Char8 as C
import qualified Data.Text as T
import qualified Data.Text.Encoding as TE
import Foreign
import Foreign.C
import System.IO
import Control.Monad(unless)
foreign import ccall "parse_observations" observe :: Ptr Word8->CSize->Ptr CInt->CSize->CInt->CInt->IO CString
foreign import ccall "parse_free" release :: CString->IO()
as :: FromJSON a=>Value->a
as v=case fromJSON v of Success x->x;Error e->error e
arr(Array a)=V.toList a
arr _=error "array"
response :: Value->IO Value
response v=case arr v of
 [String "literal",source]->pure(case reads(as source)::[(String,String)] of
  [(s,"")]->toJSON(BS.unpack(TE.encodeUtf8(T.pack s)));_->Null)
 [String "parse",bytes,chunks,encoding,limit]->do
  let b=BS.pack(map as(arr bytes));number v=fromIntegral(as v::Int);cs=map number(arr chunks)
  BS.useAsCStringLen b $ \(p,n)->withArray cs $ \cp->do
   r<-observe(castPtr p)(fromIntegral n)cp(fromIntegral(length cs))(number encoding)(number limit)
   result<-peekCString r;release r;pure(either error id(eitherDecode(C.pack result)))
 _->error "request"
main=loop where
 loop=do
  end<-isEOF;unless end $ do
   line<-BS.getLine;result<-response(either error id(eitherDecode(BL.fromStrict line)))
   C.putStrLn(encode result);hFlush stdout;loop
