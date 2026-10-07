## Change log
----------------------

Version 0.3.0 (unreleased)
-------------

ADDED:

- the privacy block's architecture (phase B, ADR 0002): a transfer has three building blocks a
  scheme can replace - authorization, recipient, amount - and every scheme carries a permanent,
  versioned identifier. A consensus rule admits each scheme per chain from a block height on, and
  optionally until a later one, so a broken scheme can be switched off without a new chain. Today's
  rule admits exactly what both chains accepted before: ed25519, ml-dsa-65, direct, stealth-v2 and
  plain amounts from height 0; no behaviour changes. No scheme that hides sender or amount exists
  yet (#60)

CHANGED:

- ADR 0001, three rules for what the privacy block may take in: code under a licence compatible
  with MIT, Apache-2.0 included when a NOTICE file carries its notices; copyleft code only as a
  separate program across a process boundary; code without a licence never; and no scheme with a
  known patent valid in the EU, or an unclear patent position, before the maintainer has clarified
  it (#58, #65)
- ADR 0002, how a scheme written in C is integrated: every scheme in pure Java, the authors' C code
  only in the tests, through the Foreign Function and Memory API, as the reference for difference
  tests, beside forgery tests and PIT on the verification logic; parameters only from the paper or
  the reference code (#65)
- `docs/research/pq-privacy-literature.md`, the phase A literature on hiding sender and amount, now
  also checked against the full texts of six general results on Fiat-Shamir in the quantum random
  oracle model. No candidate's proof-model level changes: none of them reaches a quantum random
  oracle proof through a general result (#54, #70)

Version 0.2.0
-------------

FORMAT:

- Wallets: a wallet file created or restored with 0.2.0 is sealed in the envelope `LETHWF`, and 0.1.0
  cannot open it - it refuses the file as "not a wallet file: it is not sealed with a password the
  way a wallet file is". A wallet file written by 0.1.0 keeps opening and is never rewritten:
  lethenon writes a wallet file only when one is created or restored, and never over an existing
  one (#45)
- Chains: a chain started with `mine --testnet` carries the identifier `lethenon-test-1`, which 0.1.0
  does not know. 0.1.0 refuses such a chain at its genesis block - `balance` and `mine` end with
  "block 0 belongs to chain 'lethenon-test-1'" and exit 1 - and leaves the chain file as it was.
  Main chains (`lethenon-1`) are unchanged in both directions: 0.1.0 replays a main chain 0.2.0
  wrote and mines on it, 0.2.0 replays the result, and a chain written by 0.1.0 is a main chain
  (#50)

ADDED:

- a test chain, `lethenon-test-1`, beside the main chain `lethenon-1`: `mine --testnet` writes its
  genesis block, and from then on the genesis block decides - every block, transfer and sweep
  carries its identifier, the replay refuses a block or a transfer of the other chain and a genesis
  block naming neither, and `--testnet` on a main chain is an error. `Mining.nextBlock` takes the
  identifier for a new chain; without one it follows the genesis block. Chains written by 0.1.0 are
  main chains and replay unchanged (#50)

CHANGED:

- the rule "this project does not write new cryptography" is replaced, not removed, by ADR 0001:
  a new cryptographic construction only after a peer-reviewed publication with its proof, checked
  against the authors' test vectors or a reference implementation, on the test chain first and on
  the main chain only after an external cryptographic review, and no invented primitives. Sender
  ambiguity and amount confidentiality are described as not built rather than ruled out. Nothing
  in the code changes (#49). A fifth rule: every scheme names the model its proof holds in (#55)
- the wallet file is sealed with mystic-crypt's PassphraseEnvelope under the marker LETHWF - the
  envelope of mystic-crypt-ui's vault, byte for byte - instead of PassphraseCryptor's MCRYPT layout,
  which is still read. A wallet file 0.1.0 wrote is a fixed test vector and opens, its bytes on disk
  unchanged afterwards. mystic-crypt 13.3 to 13.5: 13.5's envelope declares
  GeneralSecurityException, so the calls into it are plain calls again (#45, #47)
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
