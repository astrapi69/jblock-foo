# Lethenon

<div align="center">

[![Java CI with Gradle](https://github.com/astrapi69/lethenon/actions/workflows/gradle.yml/badge.svg)](https://github.com/astrapi69/lethenon/actions/workflows/gradle.yml)
[![Open Issues](https://img.shields.io/github/issues/astrapi69/lethenon.svg?style=flat)](https://github.com/astrapi69/lethenon/issues)
[![MIT license](http://img.shields.io/badge/license-MIT-brightgreen.svg?style=flat)](http://opensource.org/licenses/MIT)

</div>

A joke currency with a serious point: **a protest against the politics of surveillance**, made of
the only argument software can actually make - a working demonstration.

## Status

Milestones 1 to 3 of [#2](https://github.com/astrapi69/lethenon/issues/2) are built - canonical
encoding, Ed25519 and ML-DSA-65 signatures, proof of pun, a replay that verifies a chain from its
bytes alone, one-time destinations and a balance computed from the chain - and milestone 4, a
wallet that can be recovered, comes with a command line (below). Money paid to a one-time
destination can now be spent again as well
([#21](https://github.com/astrapi69/lethenon/issues/21)): the destination is the one-time public
key `P = S + H(s)*B`, so a transfer out of it is verified by the same rule as any other, and only
the holder of the spend key can sign it. Remaining gaps are open issues. The plan and the decisions behind it are
in the issues: [#1](https://github.com/astrapi69/lethenon/issues/1) is the brainstorm with the
reasoning, [#2](https://github.com/astrapi69/lethenon/issues/2) the cut into five milestones.

The **library** is on Maven Central; the **chain** is not published and will not be - nobody can
buy the coin, there is no pool and no listing, and a tradeable joke is a financial product with
real losers.

```groovy
implementation "io.github.astrapi69:lethenon:0.2.0"
```

Milestone 5, the desktop plugin, lives in
[mystic-crypt-ui#450](https://github.com/astrapi69/mystic-crypt-ui/issues/450) and consumes exactly
this library.

### Command line

`./gradlew installDist` builds `build/install/lethenon/bin/lethenon`. Two kinds of file: the chain
file, which anybody can replay, and a wallet file, which only its password opens. A password is
never an argument; it is the first line of standard input.

```
lethenon wallet create  --wallet holder.wallet          # prints the account and the 24 words
lethenon wallet restore --wallet again.wallet           # stdin: the 24 words, then a new password
lethenon mine    --chain chain.lethenon --wallet holder.wallet   # first run: the genesis block
lethenon mine    --testnet --chain test.lethenon --wallet holder.wallet   # genesis of a test chain
lethenon faucet  --chain chain.lethenon --wallet holder.wallet --to <account>
lethenon send    --chain chain.lethenon --wallet w.wallet --to <account> --amount 12.5 --memo "..."
lethenon send    --chain chain.lethenon --wallet w.wallet --to-address <view:spend> --amount 12.5
lethenon sweep   --chain chain.lethenon --wallet w.wallet       # one-time payments onto the account
lethenon balance --chain chain.lethenon --wallet w.wallet       # replays the chain, asks nobody
```

`--to` names an account and the chain shows who was paid; `--to-address` names the published
address that `wallet create` and `balance` print, and the transfer then goes to a one-time
destination derived from it, so two payments to the same address have nothing visibly in common.
`sweep` is the other end of that: it moves what was paid to those destinations onto the account,
one transfer per destination, and says out loud what that costs - the chain then shows those
destinations and the account together. Receiving is unlinkable; spending is the moment that ends.

There are two chains, `lethenon-1` and the test chain `lethenon-test-1`, and the identifier is
inside every signed transfer and every block, so nothing signed for one is accepted on the other.
`--testnet` chooses the test chain when `mine` writes the genesis block; from then on the genesis
block decides, every command follows it, and `--testnet` on a main chain is refused. A new
cryptographic scheme runs on the test chain first ([ADR 0001](docs/adr/0001-new-cryptographic-constructions.md)).

`send`, `faucet` and `sweep` sign transfers that wait in `chain.lethenon.pending`; the next `mine` puts
every waiting transfer into a block, replays the result and only then writes it. Exit codes: 0
done, 1 refused with the reason on standard error, 2 a command line that was not understood.

The chain is called Lethenon, from
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

## The coin

| | |
|---|---|
| Supply | **1,984,000,000 LETH**, fixed. 1984 is the protest in the amount itself |
| Base unit | the **lethe**; 10^8 lethe = 1 LETH |
| Why that magnitude | the supply is 1.984 x 10^17 base units against a 64-bit signed integer's 9.22 x 10^18 - a factor of 46 of headroom, so integer arithmetic suffices and no `BigInteger` is needed |
| Mining reward | **1,984 LETH per block, from a pre-minted pool** of 992,000,000 - exactly 500,000 blocks, about 1.9 years at two-minute blocks, then fees only |
| Inflation | none. The reward is distributed, never minted: the sum of all balances equals the supply after every block, and that is an assertion, not a promise |

## Privacy, and its honest label

Privacy is four different things here, and what is in and what is out is stated rather than implied.

**In:** one-time addresses derived by the sender from the published address, so two payments to the
same address land on unrelated destinations; a wallet that never asks a server for the balance of an
address, because that query is how most light wallets expose their users; transport over Tor; and no
account, no identity check, no telemetry.

**Built of that so far:** the one-time destinations, spendable as well as receivable, and a balance
that is computed instead of requested. `WalletScan` replays the chain and recognises its own
payments with the view private key alone; `Wallet.oneTimeKey` blinds the spend key into the key
that moves such a payment, which is the additive construction Monero uses, built in mystic-crypt
rather than here (#21); and `NoBalanceQueryTest` keeps the whole chain package unable to name a
networking type, so the property holds for every run rather than for one captured one. The Tor
transport is still ahead.

**Not built:** amount confidentiality and sender ambiguity. The sender is a public key in the clear
and the amount a plain number in every transaction. Both need cryptographic constructions this
project does not have yet, and those come in only under [ADR 0001](docs/adr/0001-new-cryptographic-constructions.md):
a peer-reviewed publication with its proof, the authors' test vectors, the test chain first, and
the main chain only after an external cryptographic review.

**The label, because somebody would otherwise have to find it out:** Monero's privacy rests entirely
on Ed25519 mathematics - ring signatures, stealth addresses and RingCT are all discrete-logarithm
constructions. ML-DSA gives none of that; it is a signature scheme, not a toolkit for rings or
commitments. So Lethenon is **post-quantum in its authenticity and pre-quantum in its
unlinkability**. The direction that would join the two is hash-based proofs, and that is a research
programme rather than a milestone.

## The planned shape

Decided in [#1](https://github.com/astrapi69/lethenon/issues/1), with the reasoning there:

| Decision | Choice |
|---|---|
| Model | accounts with a nonce, not UTXO - one signature per transaction instead of one per input, which is what makes post-quantum signatures affordable |
| Consensus | **proof of pun**: a block is valid when its memo hash carries the required prefix, the difficulty starts at 8 bits and steps by at most 2 bits every 30 blocks towards two-minute blocks, timestamps later than the median of the 11 before ([#24](https://github.com/astrapi69/lethenon/issues/24), after Bitcoin's rules). Fork choice by most work comes with a network. Mining with wordplay instead of nonces, CPU-friendly on purpose - Monero gives the same reasoning for RandomX, "designed to make the use of mining-specific hardware unfeasible", and a protest whose mining needs bought hardware is a poor protest |
| Signatures | Ed25519 by default, ML-DSA-65 selectable per transaction, the suite identifier inside the signed bytes |
| Encoding | one canonical encoder, a round-trip property test, a format version in every persisted structure |
| Arithmetic | integers only, `Math.addExact` and `Math.subtractExact`, and the supply invariant checked after every block |
| Proof it works | a replay verifier: a fresh process checks a chain from genesis and prints what it verified, and a deliberately corrupted fixture it must reject |

Built on the libraries this family already publishes - [mystic-crypt](https://github.com/astrapi69/mystic-crypt),
crypt-data and crypt-api over Bouncy Castle. This project writes formats and rules, and invents no
primitives; when it uses a new construction, [ADR 0001](docs/adr/0001-new-cryptographic-constructions.md) says on what terms.

## Milestones

1. one signed transaction, one block, and a replay that proves it - with one-time addresses in the
   format from the start, because an address format is a hard fork to change later
2. the same chain, post-quantum: both suites in one chain, with their measured sizes
3. unlinkability: one-time addresses end to end, no balance lookups, Tor transport
4. a wallet that can be recovered from its Shamir shares alone
5. an internal plugin for [mystic-crypt-ui](https://github.com/astrapi69/mystic-crypt-ui), once
   milestone 4 is green

## License

MIT.

## Want to help?

Issues and pull requests are welcome at the
[issues page](https://github.com/astrapi69/lethenon/issues). Pull requests go against `develop`,
with tests.

## Donations

If you like this project, a donation goes through
[PayPal](https://www.paypal.com/cgi-bin/webscr?cmd=_s-xclick&hosted_button_id=B37J9DZF6G9ZC).

The cryptocurrency donation addresses that used to stand here are deliberately gone from this one
repository: on a project that is itself a coin, an address in the README reads like a way to buy
in, and there is nothing to buy.
