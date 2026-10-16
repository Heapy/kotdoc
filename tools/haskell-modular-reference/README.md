# Modular arithmetic and fixed-width Montgomery reference

This development reference links pinned crypton 2.0.1, GHC 9.12.2 and
Hspec 2.11.18. Production contains Kotlin implementations and does not call
this executable, a C cryptography library, Pandoc or calibre.

`--original --seed 1729 --qc-max-success 200 --match PATTERN` executes the
original vendor `NumberSpec.hs`, `Imports.hs` and `Utils.hs` unchanged.
Patterns `exponentiation`, `inverse` and `squareRoot` select all nine examples
for the modular arithmetic module. The other NumberSpec tests for basic
numbers, serialization, generators and primality remain outside this port.

`--samples` reuses the original QAInteger, TestDRG and Int0_2901 generators,
fixed seeds and the original prime generator to capture concrete arguments.
It uses every original exponentiation/inversion corner and all nine example
names. OriginalNumberTests has only its module/export declaration changed,
plus an appended export of the exact original inverse-corner literal. Both
transformations are checked mechanically against the pinned source.
Each distinct generated call is also observed individually, so large modular
results and the precise selected square root are checked independently of
agreement/round-trip properties.

The raw oracle calls all helpers in crypton_bignum.h, with 64-bit limbs, and
records outputs, carry/borrow return values, destroyed scratch buffers,
aliasing and byte import/export failure state. OriginalPowm.c is the pinned
crypton_powm.c with observation calls at multiplication, squaring, windows,
table reads and workspace wiping. Instrumentation is checked mechanically;
arithmetic and selection expressions are preserved. It records both the
number of table reads and an ordered hash of every relative address, so
the Kotlin result is not held only to its own algebraic invariants.

Build from this folder with GHC/cabal:

```
cabal build --builddir=/path/to/build modular-reference
cabal list-bin --builddir=/path/to/build modular-reference
```

Use the resulting executable as ORACLE from the repository root:

```
python3 tools/extract-modular-reference.py
python3 tools/import-modular-fixtures.py --oracle "$ORACLE"
python3 tools/import-modular-fixtures.py --oracle "$ORACLE" --verify-oracle
python3 tools/import-modular-fixtures.py --check-source
```

The raw exponent input is borrowed and remains unchanged, as in C. The public
expSafe wrapper allocates a word-rounded temporary exponent and wipes it after
the call; both layers clear their workspaces. No variable-width BigInteger
operation is used inside the secret-exponent Montgomery loop. BigInteger is
used for public base reduction, serialization/result conversion and the
ordinary expFast path. Loop bounds depend on public modulus/exponent widths;
table selection visits all sixteen entries and uses masks. These observations
test the algorithm and its access schedule, not machine-code timing under
every JVM/JIT/GC or hardware configuration; that runtime audit remains pending.

Non-prime Tonelli-Shanks searches can fail to terminate upstream. Finite
constructor observations and explicit modulus failures are included without
inventing finite results for the undefined searches. A controlled calloc
failure records the original status and untouched output. Kotlin maps failure
of the matching workspace allocation to that status; the wrapper's fallback
and exponent wiping are tested without actually exhausting process memory.
Real host memory pressure and buffers beyond JVM array capacity remain unverified.
