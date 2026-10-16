-- Development-only registration parser. No test executable is built or run.
module Main where
import Distribution.PackageDescription
import Distribution.Simple.PackageDescription (readGenericPackageDescription)
import Distribution.Verbosity (silent)
import Distribution.Pretty (prettyShow)
import Distribution.Utils.Path (getSymbolicPath, makeSymbolicPath)
import System.Environment (getArgs)
import Data.Char (ord)
import Numeric (showHex)
import Data.List (intercalate)

str :: String -> String
str s = "\"" ++ concatMap escape s ++ "\"" where
  escape '"' = "\\\""
  escape '\\' = "\\\\"
  escape c | ord c < 32 = let h=showHex (ord c) "" in "\\u" ++ replicate (4-length h) '0' ++ h
           | otherwise = [c]
obj xs = "{" ++ intercalate "," [str k ++ ":" ++ v | (k,v)<-xs] ++ "}"
arr xs = "[" ++ intercalate "," xs ++ "]"
strings = arr . map str
boolean x = if x then "true" else "false"
bi b = [("source_dirs",strings $ map getSymbolicPath $ hsSourceDirs b),
        ("other_modules",strings $ map prettyShow $ otherModules b),
        ("autogen_modules",strings $ map prettyShow $ autogenModules b),
        ("buildable",boolean $ buildable b),
        ("dependencies",strings $ map prettyShow $ targetBuildDepends b),
        ("build_tools",strings $ map prettyShow $ buildToolDepends b),
        ("cpp_options",strings $ cppOptions b)]
suite t = obj $ [("interface",str $ show $ testInterface t),
                 ("entry",case testInterface t of
                   TestSuiteExeV10 _ p -> obj [("kind",str "main-is"),("value",str $ getSymbolicPath p)]
                   TestSuiteLibV09 _ m -> obj [("kind",str "test-module"),("value",str $ prettyShow m)]
                   _ -> "null")] ++ bi (testBuildInfo t)
exe e = obj $ [("main_is",str $ getSymbolicPath $ modulePath e)] ++ bi (buildInfo e)
tree f (CondNode value constraints branches) = obj [("data",f value),("constraints",strings $ map prettyShow constraints),
  ("branches",arr [obj [("condition",str $ show condition),("then",tree f yes),("else",maybe "null" (tree f) no)] | CondBranch condition yes no<-branches])]
main = do
 paths <- getArgs
 rows <- mapM readOne paths
 putStrLn $ arr rows
 where
 readOne path = do
  g <- readGenericPackageDescription silent Nothing (makeSymbolicPath path)
  let p=packageDescription g
  pure $ obj [("path",str path),("package",str $ prettyShow $ package p),
    ("flags",strings $ map show $ genPackageFlags g),
    ("test_suites",arr [obj [("name",str $ prettyShow name),("tree",tree suite t)] | (name,t)<-condTestSuites g]),
    ("executables",arr [obj [("name",str $ prettyShow name),("tree",tree exe t)] | (name,t)<-condExecutables g])]
