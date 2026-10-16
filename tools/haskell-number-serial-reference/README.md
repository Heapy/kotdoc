# Basic-number and integer-serialization reference

Build the development-only `number-serial-reference` with GHC 9.12.2 from this
folder using Cabal and its pinned freeze file. Production uses Kotlin and does
not invoke the reference or native GMP. The crypton notice is retained in the
core JAR. Pinned GHC/bignum implementation files are included in the corpus
source manifest alongside the complete crypton 2.0.1 tree.

`OriginalNumberSerialTests.hs` is the complete original NumberSpec with only
its module/export declaration changed. The original tests can be run in four
groups, leaving generator/primality/modular arithmetic tests separate:

```
number-serial-reference --original --match num- --seed 1729 --qc-max-success 200
number-serial-reference --original --match as-power --seed 1729 --qc-max-success 200
number-serial-reference --original --match marshalling --seed 1729 --qc-max-success 200
number-serial-reference --original --match rev- --seed 1729 --qc-max-success 200
```

`--vectors` emits all five exact original decimal/128-byte known answers.
`--samples` uses the original QAInteger/Int1_2901 generators and Positive
wrappers to exercise all five basic-number and six serialization properties.
The JSON observer additionally records exact primitive results, public Maybe
constructor/payload/length demand, each extended-GCD component, list
short-circuiting and prefilled guarded raw pointer writes/reads in both byte
orders. It preserves the pinned integer-gmp behavior: zero needs zero bits and
bytes, logarithms of nonpositive integers are zero, negative sizes count the
magnitude, and the old extended-GCD wrapper's second coefficient divides by
zero at b=0 even though its first coefficient/gcd remain available.

From the repository root:

```
python3 tools/import-number-serial-fixtures.py --oracle /path/to/number-serial-reference
python3 tools/import-number-serial-fixtures.py --oracle /path/to/number-serial-reference --verify-oracle
python3 tools/import-number-serial-fixtures.py --check-source
```

Public unpadded i2osp documents a nonnegative integer domain. For negative
inputs its callback writes no bytes to the allocated result. Only its
deterministic result length is captured; no expected garbage byte contents are
invented. Kotlin arrays are initially zeroed, so equality of unspecified
negative-result bytes or allocator/memory-disclosure behavior is not claimed.
The raw pointer API is observed on negative inputs with a controlled prefilled
buffer and preserves every byte. Bounded spans and size guards are exercised;
actual invalid pointers, huge allocation sizes and resource pressure are not.

The original sqrti algorithm's selected two bounds and exact GMP-selected
Bezout coefficients are captured, rather than accepting any bounds or any
coefficients satisfying an identity. Retained GMP source delegates tie choices
to mpz_gcdext; dense half-period cases therefore have independent native
observations. Other GHC/integer backends, timing and native memory parity remain
unverified. Number generation/primality, ECC wrappers, P-256, signatures and
trust validation remain subsequent work.
