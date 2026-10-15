{-# LANGUAGE OverloadedStrings #-}
module Hooks where
import Data.IORef
import Data.Aeson
import qualified Data.Vector as V
import Data.Char(chr,ord)
import System.IO.Unsafe(unsafePerformIO)
callback :: IORef (String->[String]->IO Value)
callback=unsafePerformIO(newIORef(\_ _->error "directory hook uninitialized"))
{-# NOINLINE callback #-}
call name args=readIORef callback >>= \f->f name args
as :: FromJSON a=>Value->a
as v=case fromJSON v of Success a->a;Error e->error e
chars (Array v)=map(chr.as)(V.toList v)
chars _=error "chars"
