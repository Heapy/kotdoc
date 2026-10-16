{-# LANGUAGE ForeignFunctionInterface,OverloadedStrings #-}
module Model(configure,trace) where
import Data.Aeson
import qualified Data.Aeson.KeyMap as K
import Foreign.C.Types
import Control.Monad
field v k=case fromJSON (maybe (error "missing") id (K.lookup k v)) of Success a->a;Error e->error e
foreign import ccall "hg_config" config :: CLLong->CLLong->CLLong->CInt->CInt->CInt->CInt->CInt->CInt->CInt->CInt->IO ()
foreign import ccall "hg_trace_count" count :: IO CInt
foreign import ccall "hg_trace_kind" kind :: CInt->IO CInt
foreign import ccall "hg_trace_arg" arg :: CInt->IO CLLong
configure v=let c=field v "config";[a,b,d,e,f,g]=field c "tm"::[Int] in config (fromIntegral (field c "clockSeconds"::Integer)) (fromIntegral (field c "clockNanos"::Integer)) (fromIntegral (field c "zoneSeconds"::Integer)) (if field c "gmOk" then 1 else 0) (if field c "localOk" then 1 else 0) (fromIntegral a) (fromIntegral b) (fromIntegral d) (fromIntegral e) (fromIntegral f) (fromIntegral g)
trace=do
 n<-count
 toJSON <$> forM [0..n-1] (\i->do
  k<-kind i;a<-arg i
  pure $ case k of 1->toJSON [String "gm",toJSON (fromIntegral a::Integer)];2->toJSON [String "local",toJSON (fromIntegral a::Integer)];_->toJSON [String "clock"])
