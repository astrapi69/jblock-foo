# Lethenon

<div align="center">

[![Open Issues](https://img.shields.io/github/issues/astrapi69/jblock-foo.svg?style=flat)](https://github.com/astrapi69/jblock-foo/issues)
[![MIT license](http://img.shields.io/badge/license-MIT-brightgreen.svg?style=flat)](http://opensource.org/licenses/MIT)

</div>

A joke currency with a serious point: **a protest against the politics of surveillance**, made of
the only argument software can actually make - a working demonstration.

## Status

**Nothing is implemented yet.** This repository was created in 2021 for "tests with blockchain
technology" and has carried a template class ever since. The plan, the decisions behind it and the
milestones are in the issues: [#1](https://github.com/astrapi69/jblock-foo/issues/1) is the
brainstorm with the reasoning, [#2](https://github.com/astrapi69/jblock-foo/issues/2) is the cut
into four milestones.

The repository still carries its 2021 name. The chain is called Lethenon, from
[Lethe](https://en.wikipedia.org/wiki/Lethe), the river of forgetting - the opposite of a permanent
record about people. The name was checked against GitHub, Maven Central, npm, PyPI, crates.io and
the coin listings before it was picked, because a name collision is cheaper to avoid than to fix.

## What it is not

**Lethenon stays private. Nobody can buy it, there is no pool, no listing, no promotion, no sale.**
The moment a joke currency becomes tradeable it is a financial product with real losers, and in the
European Union it falls under MiCA. Nothing about the point of this project depends on that, so it
does not happen. If you found your way here expecting an investment, there is nothing here for you,
and that is deliberate.

## What it demonstrates

- **Verification without an observer.** Anybody can replay the chain and check every signature and
  every state transition themselves. Nothing has to be taken on trust from a central party, and the
  check itself requires no identity.
- **Post-quantum from the first block.** "Harvest now, decrypt later" - record today's traffic and
  break it when the machine exists - is a surveillance practice, not a theory. ML-DSA-65 signatures
  (FIPS 204) answer it directly. Measured with OpenSSL 3.5.5: an Ed25519 signature is 64 bytes, an
  ML-DSA-65 signature 3309 - the cost of the answer, stated rather than hidden.
- **Recovery without custody.** A seed with hierarchical derivation, and Shamir splitting of that
  seed, so a backup can live across people or places without a company holding the keys.
- **The protest is inside the chain.** Every transaction carries a memo that is signed with it, so
  the text is part of what gets verified rather than metadata beside it.

## The planned shape

Decided in [#1](https://github.com/astrapi69/jblock-foo/issues/1), with the reasoning there:

| Decision | Choice |
|---|---|
| Model | accounts with a nonce, not UTXO - one signature per transaction instead of one per input, which is what makes post-quantum signatures affordable |
| Consensus | proof of authority with a named signer, behind an interface. Not proof of work: on a chain nobody mines, proof of work is theatre |
| Signatures | Ed25519 by default, ML-DSA-65 selectable per transaction, the suite identifier inside the signed bytes |
| Encoding | one canonical encoder, a round-trip property test, a format version in every persisted structure |
| Proof it works | a replay verifier: a fresh process checks a chain from genesis and prints what it verified, and a deliberately corrupted fixture it must reject |

Built on the libraries this family already publishes - [mystic-crypt](https://github.com/astrapi69/mystic-crypt),
crypt-data and crypt-api over Bouncy Castle. This project writes formats and rules, not primitives.

## Open decisions

- the supply: a fixed, deliberately silly number, still to be decided in
  [#2](https://github.com/astrapi69/jblock-foo/issues/2)
- whether the repository is renamed from `jblock-foo` to the chain's name

## Milestones

1. one signed transaction, one block, and a replay that proves it
2. the same chain, post-quantum: both suites in one chain, with their measured sizes
3. a wallet that can be recovered from its Shamir shares alone
4. an internal plugin for [mystic-crypt-ui](https://github.com/astrapi69/mystic-crypt-ui), once
   milestone 3 is green

## License

MIT.

## Want to help?

Issues and pull requests are welcome at the
[issues page](https://github.com/astrapi69/jblock-foo/issues). Pull requests go against `develop`,
with tests.

## Donations

If you like this project, a donation goes through
[PayPal](https://www.paypal.com/cgi-bin/webscr?cmd=_s-xclick&hosted_button_id=B37J9DZF6G9ZC).

The cryptocurrency donation addresses that used to stand here are deliberately gone from this one
repository: on a project that is itself a coin, an address in the README reads like a way to buy
in, and there is nothing to buy.
