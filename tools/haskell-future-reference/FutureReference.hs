{-# LANGUAGE OverloadedStrings #-}
module Main where
import Text.Pandoc.Parsing.Future
import Control.Monad.Reader (reader,runReader)
import Control.Applicative (liftA2)
import Data.Aeson
import qualified Data.ByteString.Lazy.Char8 as B
import qualified Data.Vector as V
import qualified Data.Text as T
import Data.Functor.Identity
import Data.Proxy
import Data.Monoid (Sum(..),Product(..))
import Data.Semigroup (First(..),Last(..))
import Data.Maybe (fromJust)
import Data.IORef
import System.IO.Unsafe (unsafePerformIO)
import Control.Exception
import Control.DeepSeq
import Control.Monad (unless)
import System.IO

data D = N Int | S T.Text | A Value | L [D] | Pair D D | O [(T.Text,D)]
arr (Array x)=V.toList x
arr x=error(show x)
as :: FromJSON a=>Value->a
as v=case fromJSON v of Success x->x;Error e->error e
datum v=case arr v of
 [String "n",n]->N(as n)
 [String "s",s]->S(as s)
 [String "a",x]->A x
 [String "l",xs]->L(map datum(arr xs))
 [String "p",a,b]->Pair(datum a)(datum b)
 [String "o",xs]->O [(as k,datum x)|[k,x]<-map arr(arr xs)]
 [String "cons",h,t]->L(datum h:ls(datum t))
 [String "err",m]->error(as m)
 [String "self"]->let x=x in x
 [String "repeat",x]->L(repeat(datum x))
 _->error "datum"
enc (N n)=toJSON [toJSON("n"::T.Text),toJSON n]
enc (S s)=toJSON [toJSON("s"::T.Text),toJSON s]
enc (A v)=toJSON [toJSON("a"::T.Text),v]
enc (L xs)=toJSON [toJSON("l"::T.Text),toJSON(map enc xs)]
enc (Pair a b)=toJSON [toJSON("p"::T.Text),enc a,enc b]
enc (O xs)=toJSON [toJSON("o"::T.Text),toJSON [toJSON [toJSON k,enc v]|(k,v)<-xs]]
num (N n)=n
num _=error "not number"
ls (L xs)=xs
ls _=error "not list"
fn f x=case arr f of
 [String "id"]->x
 [String "const",v]->datum v
 [String "err",m]->error(as m)
 [String "add",v]->N(num x+as v)
 [String "times",v]->N(num x*as v)
 [String "negate"]->N(negate(num x))
 [String "first"]->case x of Pair a _->a;_->error "not pair"
 [String "second"]->case x of Pair _ b->b;_->error "not pair"
 [String "field",k]->case x of O xs->maybe(error "missing field")id(lookup(as k)xs);_->error "not object"
 [String "head"]->case ls x of y:_->y;_->error "empty list"
 [String "tail"]->case ls x of _:ys->L ys;_->error "empty list"
 [String "length"]->N(length(ls x))
 [String "nth",i]->ls x !! as i
 [String "pair",a,b]->Pair(fn a x)(fn b x)
 [String "list",fs]->L [fn g x|g<-arr fs]
 [String "compose",a,b]->fn a(fn b x)
 _->error "function"
fun v=case arr v of
 [String "pure",f]->pure(fn f)
 [String "env",f]->asksF(\s a->fn f(Pair s a))
 [String "mapped",f,p]->(\v a->fn f(Pair v a)) <$> core p
 _->error "function future"
core :: Value->Future D D
core v=case arr v of
 [String "pure",d]->pure(datum d)
 [String "ask"]->askF
 [String "asks",f]->asksF(fn f)
 [String "constructor",f]->Future(reader(fn f))
 [String "fail",m]->error(as m)
 [String "map",f,p]->fn f <$> core p
 [String "replace",d,p]->datum d <$ core p
 [String "bind",p,f]->core p >>= (pure . fn f)
 [String "bind-env",p,f]->core p >>= \x->asksF(\s->fn f(Pair x s))
 [String "ap",f,p]->fun f <*> core p
 [String "lift",f,p,q]->liftA2(\a b->fn f(Pair a b))(core p)(core q)
 [String "then",p,q]->core p *> core q
 [String "before",p,q]->core p <* core q
 [String "append",p,q]->L <$> ((ls <$> core p) <> (ls <$> core q))
 [String "sum",p,q]->N . getSum <$> ((Sum . num <$> core p) <> (Sum . num <$> core q))
 [String "product",p,q]->N . getProduct <$> ((Product . num <$> core p) <> (Product . num <$> core q))
 [String "append-left",p,q]->getFirst <$> ((First <$> core p) <> (First <$> core q))
 [String "append-right",p,q]->getLast <$> ((Last <$> core p) <> (Last <$> core q))
 [String "empty-sum"]->N . getSum <$> (mempty::Future D (Sum Int))
 [String "empty-product"]->N . getProduct <$> (mempty::Future D (Product Int))
 [String "empty"]->L <$> (mempty::Future D [D])
 _->error "future"
tick r x=unsafePerformIO $ modifyIORef' r (+1) >> pure x
{-# NOINLINE tick #-}
operation v=case arr v of
 [String "nullable",String kind,n]->
   let base=asksF(\s->if s<0 then Nothing else Just s)::Future Int (Maybe Int)
       action=case kind of
         "nothing"->pure Nothing
         "ask"->base
         "map"->fmap(fmap(+1))base
         "drop"->const Nothing <$> base
         "bind"->base >>= \v->case v of Nothing->pure Nothing;Just x->asksF(\s->Just(x+s))
         "empty"->fmap(fmap getSum)(mempty::Future Int (Maybe (Sum Int)))
         "sum"->fmap(fmap getSum)((fmap(fmap Sum)base) <> pure(Just(Sum 100)))
         _->error "nullable kind"
   in pure(toJSON(runF action(as n)))
 [String "memo",String kind]->do
   ref<-newIORef (0::Int)
   let d=tick ref(if kind=="failure" then error "shared failure" else N 42)
       p=pure d::Future D D
   first<-try(evaluate(num(runF p(error "state"))))::IO(Either SomeException Int)
   second<-try(evaluate(num(runF p(error "other state"))))::IO(Either SomeException Int)
   count<-readIORef ref
   let output r=case r of Right n->toJSON n;Left e->object ["error" .= takeWhile(/='\n')(displayException e)]
   pure(object ["first" .= output first,"second" .= output second,"calls" .= count])
 [String "return-shape",String kind,d]->case kind of
   "maybe"->pure $ case (returnF(datum d)::Maybe(Future D D)) of Just _->String "just";Nothing->String "nothing"
   "list"->pure(toJSON(length(returnF(datum d)::[Future D D])))
   "identity"->runIdentity(returnF(datum d)::Identity(Future D D)) `seq` pure(String "identity")
   "io"->do p<-returnF(datum d)::IO(Future D D);p `seq` pure(String "io")
   "proxy"->(returnF(datum d)::Proxy(Future D D)) `seq` pure(String "proxy")
   _->error "shape dictionary"
 [String "run",p,s]->pure(enc(runF(core p)(datum s)))
 [String "multi",p,ss]->let action=core p in pure(toJSON [enc(runF action(datum s))|s<-arr ss])
 [String "runDelayed",p,s]->pure(enc(runReader(runDelayed(core p))(datum s)))
 [String "return",String kind,d,s]->case kind of
   "maybe"->pure(toJSON [toJSON("just"::T.Text),enc(runF(fromJust(returnF(datum d)))(datum s))])
   "list"->pure(toJSON [enc(runF p(datum s))|p<-(returnF(datum d)::[Future D D])])
   "identity"->pure(enc(runF(runIdentity(returnF(datum d)))(datum s)))
   "io"->do p<-returnF(datum d);pure(enc(runF p(datum s)))
   "proxy"->let p=returnF(datum d)::Proxy(Future D D) in p `seq` pure(String "proxy")
   _->error "outer monad"
 [String "deep",String kind,n,s]->
   let depth=as n
       base=asksF id::Future D D
       step x=case kind of
         "map"->fmap(fn(toJSON [String "add",toJSON(1::Int)]))x
         "map-lazy"->fmap(fn(toJSON [String "add",toJSON(1::Int)]))x
         "bind"->x >>= pure . fn(toJSON [String "add",toJSON(1::Int)])
         "bind-lazy"->x >>= pure . fn(toJSON [String "add",toJSON(1::Int)])
         "then"->pure(error "ignored") *> x
         "before"->x <* pure(error "ignored")
         _->error "deep kind"
   in pure(enc(runF(iterate step base !! depth)(datum s)))
 _->error "operation"
main=do
 hSetBuffering stdout LineBuffering
 let loop=do
       done<-isEOF
       unless done $ do
         line<-B.pack <$> getLine
         let request=case eitherDecode line of Right x->x;Left e->error e
         result<-try(operation request >>= evaluate . force . encode)::IO(Either SomeException B.ByteString)
         B.putStrLn $ case result of
           Right bytes->bytes
           Left e->encode(object ["error" .= takeWhile(/='\n')(displayException e)])
         loop
 loop
