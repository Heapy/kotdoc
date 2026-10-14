module Main where
import Test.Tasty
import qualified Tests.Writers.AnnotatedTable as A
main=defaultMain(testGroup "original annotated table tests" A.tests)
