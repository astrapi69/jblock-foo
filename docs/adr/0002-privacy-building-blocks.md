# ADR 0002: Building blocks, scheme identifiers and their activation by consensus

- Status: accepted; the way C code is integrated decided by the maintainer on 2026-10-07 (#65)
- Date: 2026-10-07
- Phase B of the privacy block (#60), after the literature of phase A (#54,
  `docs/research/pq-privacy-literature.md`) and under ADR 0001

## Context

Phase A found no scheme that is ready to hide sender and amount on lethenon: none is proven in the
quantum random oracle model; of the schemes that hide both, only MatRiCT+ has code under a licence
file, and it has an anonymity proof that does not cover the rejection sampling its Spend step
uses (ePrint 2021/545, p. 20, App. G). Phase B therefore builds what every later scheme needs and
none of them decides: a place for each part of a transfer that a scheme can replace, an identifier
for each scheme that never changes, and a consensus rule that says which scheme a chain admits from
which height.

## Decision

### Three building blocks

A transfer has three parts a privacy scheme can replace, each independently of the others
(`BuildingBlock`):

| Block | What it decides | Schemes today | Wire |
|---|---|---|---|
| authorization | who may spend, and the proof of it | `ed25519`, `ml-dsa-65` (`SignatureSuite`) | named in every signed transfer and in the signed bytes |
| recipient | where the funds go | `direct`, `stealth-v1`, `stealth-v2` (`AddressScheme`) | named in every destination |
| amount | how much moves | `plain` (`AmountScheme`) | implied by transaction encoding version 1, not named |

A scheme that hides the sender replaces the authorization; one that hides the amount replaces the
amount. Every scheme implements `Scheme`: an identifier and the block it fills.

### Identifiers are permanent

An identifier is this project's own, not an upstream enum's name, and it is permanent once a chain
carries it. A scheme that changes in any way a verifier could notice gets a new identifier rather
than a new meaning for the old one; `stealth-v1` and `stealth-v2` are the precedent (#21). The
existing identifiers keep their names, so nothing on the wire changes; a new scheme carries its
version in its identifier from the start.

The amount block has no identifier on the wire today. A scheme that hides the amount therefore needs
a transaction encoding version that names it; that is a format decision of its own and belongs to
phase C, with the maintainer.

### Activation by consensus, per chain and height

`ConsensusRules` is a table of `SchemeActivation` lines: a chain admits a scheme from one height on,
either for good or until a later height. The end is how a scheme that turns out to be broken is
switched off without starting the chain again: blocks before it stay valid, blocks from it on may no
longer use it. One line per chain and scheme; a second line for the same pair is refused, because a
consensus rule must not depend on the order of its lines.

The replay checks every transfer against the table: its authorization scheme, its recipient scheme
and its amount scheme, at the height of the block that carries it. A transfer the table does not
admit is refused with the block, the scheme and the range in the message, for example "block 1
carries a transfer whose authorization scheme 'ed25519' chain 'lethenon-test-1' admits from height
2".

The table is code. Every verifier runs the same one, so changing it is a fork at the height the
change names. ADR 0001 decides what may be added: a new scheme gets a line for `lethenon-test-1`
first, and a line for `lethenon-1` only after the external review its rules 3 and 5 require.

### The schemes of today, hooked in without a change in behaviour

`ConsensusRules.LETHENON` admits `ed25519`, `ml-dsa-65`, `direct`, `stealth-v2` and `plain` on both
chains from height 0, for good. It has no line for `stealth-v1`, so that scheme is admitted nowhere.
That is what both chains already did: the existing refusal of a `stealth-v1` destination, with its
own reason (#21), runs before the table and is unchanged.

Measured on this change (`./gradlew build`):

- all 345 tests that existed before pass unchanged; `git diff --stat origin/develop -- src/test`
  lists only the new `ConsensusRulesTest`
- 54 new tests, 399 in all, 0 failures
- each rule bites, shown by breaking it on purpose and reverting
  (`./gradlew test --tests '*ConsensusRulesTest'` after each edit):

| broken on purpose | failures |
|---|---|
| authorization scheme not checked | 2 |
| recipient scheme not checked | 1 |
| amount scheme not checked | 1 |
| end height admitted (`<=` instead of `<`) | 1 |
| test chain table without `stealth-v2` | 4 |
| a duplicate line accepted | 1 |

The rule is shown to bite with tables that admit less than `LETHENON` (a scheme admitted only from
height 2, one switched off at height 2, one missing), through a package-private
`Replay.verify(chain, rules)`. The public `Replay.verify(chain)` runs `LETHENON`.

### What a new scheme brings

1. A record under `docs/adr/` meeting ADR 0001 rules 1 to 7: publication, assumption, proof and its
   level, test vectors or their absence, licence of every piece of code, patent position.
2. A `Scheme` with a new, versioned identifier, in the block it replaces.
3. For the amount block, and for any block whose scheme changes what a transfer carries: a new
   transaction encoding version, decided with the maintainer.
4. One activation line for `lethenon-test-1`. A line for `lethenon-1` only after the review.

## How a scheme written in C is integrated

The only scheme of phase A with licensed code that hides sender and amount is MatRiCT+, in C. The
other implementations phase A found are in C or Go, or have no licence. Three ways are possible.
Measured facts first, then the ways.

### What the code needs

MatRiCT+ (`gitlab.com/raykzhao/matrict_plus`, HEAD b24f317, 2025-08-25):

- three copies of the code, `n10`, `n20` and `n50`, one per anonymity level, 3954 lines each
  (`cat n10/*.c n10/*.h | wc -l`); the parameters are compile-time constants (`N_SPENT` 10 or 50 in
  `param.h`), so each anonymity level is its own build
- AVX2 intrinsics in the Gaussian sampler (`_mm256_*` in `gaussian_avx.c`), which `spend.c` and
  `keygen.c` use; `CFLAGS=-O3 -march=native`; `rdtsc` in `cpucycles.c`, used only by `test.c`
- randomness from `/dev/urandom` (`randombytes.c`), which is POSIX
- the XKCP Keccak library (`-lXKCP`; README: "you need the XKCP i.e., Keccak library")
- the Makefile builds one program, `ringct`, from the test driver; there is no library target

LaZer (`github.com/lazer-crypto/lazer`, HEAD 3330e48, 2026-09-28), README "Dependencies": "Linux
amd64 / x86-64 system", "avx512 and aes instruction set extensions", "gcc compiler >= 13.2",
"sagemath >= 10.2", and "during compilation, the cpu_features package will be cloned from GitHub".

The Java side, measured with JDK 25.0.4.1 on x86_64 (a 14-line program calling `strlen` through
`Linker.nativeLinker().downcallHandle`): it works, and without a flag the JVM prints "WARNING: A
restricted method in java.lang.foreign.Linker has been called" and "Restricted methods will be
blocked in a future release unless native access is enabled"; with
`--enable-native-access=ALL-UNNAMED` it prints nothing.

### Way 1: Foreign Function and Memory API, in the same process

- Platforms: one native build per operating system and CPU, shipped with the jar or installed
  beside it. As written, MatRiCT+ builds for x86-64 with AVX2 and a POSIX `/dev/urandom`; LaZer for
  Linux x86-64 with AVX-512. ARM (Apple silicon) and Windows need a port of the C code first.
- Risks: a memory error in the C code takes the whole JVM down, wallet and chain with it. Every
  application, the desktop plugin included, needs `--enable-native-access`, and the JDK announces
  blocking without it. MatRiCT+ has no library target, so a C API would have to be written around
  it. A native library inside a Maven Central artifact is a release process of its own. By ADR 0001
  rule 6 the code must be MIT-compatible, which MatRiCT+ (0BSD) and LaZer (MIT) are.

### Way 2: port to Java

- Platforms: every platform the JDK runs on, nothing native to ship, no flag; the desktop plugin and
  the command line stay plain Java.
- Risks: a reimplementation can be wrong where the original is right, and the authors publish no
  known-answer test vectors (phase A, section 13), so there is nothing fixed to compare against.
  About 4000 lines per anonymity level, and the AVX2 sampler has to be rewritten as plain Java,
  whose speed is not measured. Constant-time behaviour has to be argued again for the Java code.

### Way 3: helper process

- Platforms: as in way 1, one native build per platform, but outside the JVM.
- Risks: a crash or memory error ends the helper, not the chain. A wire format between the two
  processes, and its version, has to be defined and kept. Starting a process per proof costs time,
  not measured. It is the only way ADR 0001 rule 6 leaves open for copyleft code: a separate program
  in its own repository, called across the process boundary. Code without a licence and schemes
  with an unclear patent stay excluded on this way too.

### Recommendation

Port to Java (way 2) for everything the chain and the wallets run, and use the authors' C code as a
test oracle across a process boundary (way 3), in CI only, on Linux x86-64: the same inputs and
randomness go into both, and the outputs have to agree byte for byte. That gives lethenon a reference
to check against where the authors give no test vectors, keeps every platform, and keeps native code
out of the process that holds keys. Way 1 is not recommended: it ties the chain to x86-64 builds and
to a JVM flag, and puts C memory safety into the wallet's process.

### Decision

Decided by the maintainer on 2026-10-07 (#65):

- **Every scheme is implemented in pure Java.** The chain, the wallets, the command line and the
  desktop plugin run no native code.
- **The authors' C code is used in the tests only, through the Foreign Function and Memory API,
  as the reference for difference tests:** the same inputs, including the randomness, go into the
  Java implementation and into the C code, and the outputs have to be equal. Any difference is a
  failure, not a tolerance.
- **Targeted forgery tests** beside them: proofs, signatures and transactions altered on purpose -
  a changed byte, a swapped response, an amount that does not balance, a reused key - each of
  which the Java verifier has to refuse.
- **PIT on the verification logic**, so that a verifier that accepts too much is caught by a
  surviving mutant and not only by luck.
- **Parameters only from the paper or the authors' reference code**, each with its location (the
  table or the file and line); none chosen or tuned here.

This differs from the recommendation above in one point: the reference runs in the test JVM
through FFM, not across a process boundary. The test JVM then needs native access enabled
(`--enable-native-access`, measured above), and the C code has to be MIT-compatible under
ADR 0001 rule 6, because it runs in the same process: MatRiCT+ (0BSD) is; copyleft code could not
be used this way. Building the reference needs what the C code needs (for MatRiCT+: a C compiler,
XKCP, an x86-64 CPU with AVX2, one build per anonymity level), on the machine that runs the tests.

### Licences of the code phase B considered (ADR 0001 rule 6)

Read on 2026-10-07 from a `git clone --depth 1` of each repository:

| Code | Commit | Licence, as read | File |
|---|---|---|---|
| MatRiCT+ | b24f317 (2025-08-25) | BSD Zero Clause License, "Copyright (c) 2025 Raymond K. Zhao" | `LICENSE` |
| XKCP (needed by MatRiCT+) | 4affab4 (2026-09-25) | a summary of per-file terms, beginning "The redistribution and use of this software (with or without changes) is allowed without the payment of fees"; individual files carry their own terms, for example Brian Gladman's for `lib/common/brg_endian.h` | `LICENSE` |
| LaZer | 3330e48 (2026-09-28) | MIT, "Copyright (c) 2022-2026 IBM" | `LICENSE` |
| LaZer, bundled Falcon | `third_party/Falcon-impl-20211101.zip` | "This code is provided under the MIT license", "Copyright (c) 2017-2020 Falcon Project" | `README.txt` inside the archive |
| LaZer, bundled Intel HEXL | `third_party/hexl-development.zip` | Apache License, Version 2.0 | `hexl-development/LICENSE` inside the archive |
| LaZer, `third_party/estimator.py` | 3330e48 | no licence stated in the file's header | `third_party/estimator.py` |
| LaBRADOR | 8b6626b (2024-09-04) | Apache License, Version 2.0, "Copyright (C) 2024, IBM Corp." | `LICENSE` |
| Gao et al., `GoldSaintEagle/RingCT_Implementation` | 4975ff4 (2023-01-10) | no licence file | - |
| SPIRIT, `sihangpu/SPIRIT` | f99f1e1 (2023-08-02) | no licence file | - |

Not checked here: the licences of what LaZer fetches or needs at build time (cpu_features,
sagemath), and the per-file terms of XKCP beyond its summary. By rule 6, Gao et al. and SPIRIT are
excluded in every repository: they carry no licence. Apache-2.0 code (LaBRADOR, HEXL) is allowed,
with a NOTICE file, as decided by the maintainer on 2026-10-07 and written into rule 6 (#65).

### Patents (ADR 0001 rule 7)

For MatRiCT and MatRiCT+ no patent document could be opened from this environment: the patent
registers (Google Patents, Espacenet, the EPO register, USPTO, WIPO PATENTSCOPE, AusPat) and the
CSIRO and Monash pages were refused by the network policy on 2026-10-07. Search snippets speak of a
patent held by CSIRO for MatRiCT; that is not a fact this record can state. Neither paper mentions a
patent (`grep -ci patent` on both full texts: 0), and the MatRiCT+ repository has no file that does.
Under rule 7 the patent position of MatRiCT and MatRiCT+ is unclear, so both stay excluded until the
maintainer clarifies it.

## Consequences

- No scheme that hides sender or amount exists yet; this record only makes room for one. As of
  2026-10-07 none will be built for now: the post-quantum recipient is postponed (#72), hidden
  amounts are postponed without a cryptographer's security argument for a combination of our own
  (#74), and sender ambiguity waits as well. The recipient stays `stealth-v2`, sender and amount
  stay visible, and the account model stays (#74).
- A new scheme is a new identifier and a new activation line, not a change to an existing scheme.
  Switching a broken one off is an end height, not a new chain.
- The amount block's wire format is open until phase C.
- The integration way is decided: pure Java, the authors' C code as a reference in the tests only.
- The choice of scheme waits for the maintainer, and the patent clarification of rule 7 comes
  before phase C. On the facts above,
  MatRiCT+, the only scheme hiding sender and amount with code under a licence file, is excluded
  by rule 7 until its patent position is clarified. Of the others that hide both, Gao et al. has
  code without a licence, LRCT v2.0 only third-party code with no licence file (its README names
  ISC, phase A section 1.2), and SMILE no code at all; for none of them was a patent search
  possible from this environment.
