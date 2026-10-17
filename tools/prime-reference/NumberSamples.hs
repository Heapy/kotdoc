module Main where
import Utils
import Test.QuickCheck hiding(sample)
import Test.QuickCheck.Gen(unGen)
import Test.QuickCheck.Random(mkQCGen)
main = mapM_ sample [0..99]
sample index = do
 let (TestDRG(a,b,c,d,e),Int0_2901 base)=unGen arbitrary(mkQCGen(613+index))(index `mod` 101)
     suffix = unwords(map show[a,b,c,d,e])
 mapM_ putStrLn ["wordsGenerate "++show(5+base `mod` 800)++" "++suffix,"wordsSafe "++show(6+base `mod` 200)++" "++suffix,"square "++show(5+base `mod` 500)++" "++suffix++" "++show index]
