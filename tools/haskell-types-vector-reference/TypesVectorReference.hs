{-# LANGUAGE OverloadedStrings #-}
-- Original QuickCheck vectorOf/Gen instances execute unchanged; BSD3 licenses/.
import Test.QuickCheck (Gen,chooseInt,vectorOf)
import Test.QuickCheck.Gen (Gen(..),unGen)
import Test.QuickCheck.Random (mkQCGen)
import Data.Aeson
import qualified Data.ByteString.Lazy.Char8 as B
import Data.IORef
import Control.Exception (SomeException,evaluate,try,throwIO)
import System.IO.Unsafe (unsafePerformIO)
import System.Environment (getArgs)

q :: Gen Int
q = chooseInt (-7,259)
observe seed size count = let xs=unGen (vectorOf count q) (mkQCGen seed) size
                              positions=filter (<max 0 count) [0,1,2,3,31,32,33,1023,1024,99999]
 in object ["seed" .= seed,"size" .= size,"count" .= count,"length" .= length xs,
            "positions" .= positions,"expected" .= map (xs !!) positions,
            "complete" .= (count<=1024),"all" .= (if count<=1024 then xs else [])]
-- A test callback records which original lazy element thunks are evaluated.
-- These functions are NOINLINE to prevent sharing distinct callback invocations.
{-# NOINLINE counted #-}
counted ref r n = unsafePerformIO $ do
 modifyIORef' ref (+1)
 pure (unGen q r n)
{-# NOINLINE failing #-}
failing ref _ _ = unsafePerformIO $ do
 modifyIORef' ref (+1)
 throwIO (userError "original vector element failure")
{-# NOINLINE nullable #-}
nullable ref _ _ = unsafePerformIO $ do
 modifyIORef' ref (+1)
 pure (Nothing :: Maybe Int)
probe :: IORef Int -> String -> Int -> IO ()
probe ref name x = do
 result<-try(evaluate x) :: IO (Either SomeException Int)
 count<-readIORef ref
 B.putStrLn $ encode $ object ["name" .= name,"value" .= either (const Nothing) Just result,"calls" .= count]
demand = do
 ref<-newIORef (0::Int)
 let xs=unGen (vectorOf 100000 (MkGen $ counted ref)) (mkQCGen 611) 30
 probe ref "length-before-elements" (length xs)
 probe ref "last-element" (xs !! 99999)
 probe ref "same-last-element" (xs !! 99999)
 probe ref "first-element" (head xs)
 bad<-newIORef (0::Int)
 let ys=unGen (vectorOf 100000 (MkGen $ failing bad)) (mkQCGen 611) 30 :: [Int]
 probe bad "failure-spine-length" (length ys)
 probe bad "first-failure" (head ys)
 probe bad "same-first-failure" (head ys)
 nulls<-newIORef (0::Int)
 let zs=unGen (vectorOf 100000 (MkGen $ nullable nulls)) (mkQCGen 611) 30
 probe nulls "nullable-length" (length zs)
 probe nulls "nullable-head" (maybe 7 id (head zs))
 probe nulls "same-nullable-head" (maybe 7 id (head zs))
main = do
 args<-getArgs
 if args==["demand"] then demand else mapM_ (B.putStrLn . encode)
   [observe seed size count | seed<-[minBound,maxBound,-1,0,1,611],size<-[0,3,30],count<-[-1,0,1,2,3,31,32,33,1024,100000]]
