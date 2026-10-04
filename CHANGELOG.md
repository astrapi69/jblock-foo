## Change log
----------------------

Version 0.2.0 (unreleased)
-------------

FORMAT:

- A wallet file written by 0.1.0 keeps opening and is never rewritten: lethenon writes a wallet file
  only when one is created or restored, and never over an existing one. Wallets created or restored
  with 0.2.0 are sealed in the new format, which 0.1.0 cannot open (#45)

CHANGED:

- the wallet file is sealed with mystic-crypt's PassphraseEnvelope under the marker LETHWF - the
  envelope of mystic-crypt-ui's vault, byte for byte - instead of PassphraseCryptor's MCRYPT layout,
  which is still read. A wallet file 0.1.0 wrote is a fixed test vector and opens, its bytes on disk
  unchanged afterwards. mystic-crypt 13.3 to 13.4 (#45)
- the commands that read a password - wallet create, wallet restore, and every command that opens a
  wallet - overwrite it once the file is written or opened, on success and on failure, and that is
  now asserted on the array's content rather than assumed (#45)

Version 0.1.0
-------------

The first release of the library. The CHAIN is not published and never will be: nobody can buy the
coin, there is no pool, no listing, no promotion (#2). What is on Maven Central is the library the
desktop application and the command line are built from.

ADDED:

- the chain: an account model with a nonce per account, transfers with a signed memo, a canonical
  encoding with a version byte in every persisted structure, and proof of pun - a block is mined
  when its hash carries the required leading zero bits. `Replay.verify` checks every signature,
  every state transition, every block hash and the supply invariant from the genesis block on, and
  says what it checked (#2, milestone 1)
- two signature suites in one chain, the identifier inside the signed bytes: Ed25519 by default and
  ML-DSA-65 (FIPS 204) per transaction, with known-answer tests from RFC 8032 and the ML-DSA
  vectors. An unknown suite identifier is refused rather than ignored (milestone 2)
- a fixed supply of 1,984,000,000 LETH in eight decimals, the block reward of 1,984 LETH paid from
  a pre-minted pool of 992,000,000 - exactly 500,000 blocks, then fees only. Integer arithmetic
  throughout, and the sum of all balances equals the supply after every block, as an assertion
- the difficulty as a rule rather than a block's own claim: minimum 8 bits, a 120 second target,
  adjusted every 30 blocks by at most 2 bits, and a timestamp that must lie past the median of the
  last 11 (#24)
- one-time destinations: the sender derives `P = S + H(s)*B` from the recipient's published
  address, so two payments to the same address land on keys with nothing visibly in common, the
  recipient recognises both with its view key alone, and only the holder of the spend key can move
  them. The additive construction comes from mystic-crypt's `Ed25519KeyBlinding`; this project
  writes no curve arithmetic (#21)
- a balance that is computed, never requested: `WalletScan` replays the chain locally, and
  `NoBalanceQueryTest` keeps the chain package unable to name a networking type at all, so the
  property holds for every run rather than for one captured one
- a wallet that can be recovered: BIP-39 words, SLIP-0010 hierarchical derivation through
  mystic-crypt's `SeedDerivation`, Shamir shares of the seed through its `SecretSharing`, and a
  wallet file with its own password sealed the way the desktop application's vault is (#34)
- a picocli command line: `wallet create`/`restore`, `mine`, `faucet`, `send` - to an account key
  or to a published address - `sweep` for what arrived at one-time destinations, and `balance`
  (#32, #37)

Pre-history: the versions this file named before this entry belonged to the 2021 java-library
template this repository started from. That template did not build against a current JDK and was
replaced wholesale; nothing of it is in this release, so its entries are not kept here.

Notable links:
[keep a changelog](http://keepachangelog.com/en/1.0.0/) Don’t let your friends dump git logs into changelogs
