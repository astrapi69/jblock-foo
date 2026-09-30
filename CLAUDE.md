# Lethenon

A protest chain against the politics of surveillance: a joke currency whose point is a working
demonstration. Java 25, Gradle, JUnit 6. Nothing is published yet.

## The first rule

**Use this family's libraries - crypt-api, crypt-data, mystic-crypt, checksum-up, gen-tree - and
search them before writing anything.** The chain is their acceptance test. See
`.claude/rules/use-the-family-libraries.md`; it also says what stays this project's own, and what
to do when a library does not fit.

## What is decided

Issues [#1](https://github.com/astrapi69/lethenon/issues/1) (the reasoning) and
[#2](https://github.com/astrapi69/lethenon/issues/2) (the cut, the numbers, the milestones) carry
every decision. In short: account model, proof of pun, Ed25519 by default with ML-DSA-65 selectable
per transaction and the suite named inside the signed bytes, one canonical encoder, one-time
addresses in the format from the start, 1,984,000,000 LETH fixed with the block reward paid out of
a pre-minted pool.

**It stays private.** Nobody can buy it: no pool, no listing, no promotion. A tradeable joke is a
financial product with real losers, and in the EU it falls under MiCA.

## How the work is done here

- tests first, and a counter-run that proves the test bites: break the thing on purpose, watch the
  right test fail, revert
- integers only for money, `Math.addExact`, and the supply invariant after every block
- every number in a commit message or an issue carries the command that measured it
- `./gradlew build` prints what ran, including "no test classes" while that is true
