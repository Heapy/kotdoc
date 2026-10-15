-- Minimal assertion runner for the unchanged upstream RequestSpec/RequestBodySpec.
-- It executes every original action and predicate; no expected value is replaced.
module Test.Hspec (Spec,describe,context,it,shouldBe,shouldSatisfy,shouldReturn,passed) where
import Data.IORef
import System.IO.Unsafe (unsafePerformIO)
import Control.Exception (bracket_)
type Spec = IO ()
path :: IORef [String]
path = unsafePerformIO (newIORef [])
{-# NOINLINE path #-}
passed :: IORef [String]
passed = unsafePerformIO (newIORef [])
{-# NOINLINE passed #-}
describe :: String -> Spec -> Spec
describe name action = bracket_ (modifyIORef path (++[name])) (modifyIORef path init) action
context = describe
it :: String -> IO () -> Spec
it name action = describe name $ do
 action
 readIORef path >>= \p -> modifyIORef passed (++[concatMap ('/':) p])
shouldBe :: (Eq a, Show a) => a -> a -> IO ()
shouldBe actual expected = if actual == expected then pure () else error ("expected "++show expected++", got "++show actual)
shouldSatisfy :: Show a => a -> (a -> Bool) -> IO ()
shouldSatisfy actual predicate = if predicate actual then pure () else error ("predicate failed: "++show actual)
shouldReturn :: (Eq a, Show a) => IO a -> a -> IO ()
shouldReturn action expected = action >>= (`shouldBe` expected)
