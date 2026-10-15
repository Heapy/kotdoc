{-# LANGUAGE ForeignFunctionInterface, OverloadedStrings #-}
module Main where
import Data.Aeson hiding (withArray)
import qualified Data.Vector as V
import qualified Data.ByteString as BS
import qualified Data.ByteString.Lazy as BL
import qualified Data.ByteString.Lazy.Char8 as C
import Foreign
import Foreign.C
import Data.Word (Word64)
import System.IO
import Control.Monad (unless)
foreign import ccall "reader_observations" observe :: Ptr Word8 -> CSize -> CInt -> Word64 -> Ptr CInt -> CSize -> Ptr CInt -> CSize -> IO CString
foreign import ccall "reader_free" release :: CString -> IO ()
foreign import ccall "reader_codepoint_problem" pointProblem :: Word32 -> Ptr CInt -> IO CString
foreign import ccall "reader_utf16_problem" unitProblem :: Word32 -> CInt -> Ptr CInt -> IO CString
as :: FromJSON a => Value -> a
as v=case fromJSON v of Success x->x;Error e->error e
arr (Array a)=V.toList a
arr _=error "array"
response :: Value -> IO Value
response v=case arr v of
 [String "units",start,end,encoding] -> toJSON <$> mapM check [as start .. as end-1] where
  check cp=alloca $ \vp->do
   p<-unitProblem cp (fromIntegral(as encoding::Int)) vp;value<-peek vp
   problem<-if p==nullPtr then pure Null else toJSON <$> peekCString p
   pure(toJSON [problem,toJSON (fromIntegral value::Int)])
 [String "domain",start,end] -> toJSON <$> mapM check [as start .. as end-1] where
  check cp=alloca $ \vp->do
   p<-pointProblem cp vp;value<-peek vp
   problem<-if p==nullPtr then pure Null else toJSON <$> peekCString p
   pure(toJSON [problem,toJSON (fromIntegral value::Int)])
 [String "reader",bs,chunks,encoding,offset,steps] -> do
  let number v=fromIntegral(as v::Int)::CInt
      b=BS.pack(map as(arr bs));cs=map number(arr chunks);ss=concatMap(map number.arr)(arr steps)
  BS.useAsCStringLen b $ \(p,n)->withArray cs $ \cp->withArray ss $ \sp->do
   result<-observe(castPtr p)(fromIntegral n)(number encoding)(read(as offset))(cp)(fromIntegral(length cs))(sp)(fromIntegral(length ss `div` 2))
   text<-peekCString result;release result
   pure(either error id(eitherDecode(C.pack text)))
 _->error "request"
main=loop where
 loop=do
  end<-isEOF
  unless end $ do
   line<-BS.getLine
   result<-response(either error id(eitherDecode(BL.fromStrict line)))
   C.putStrLn(encode result);hFlush stdout;loop
