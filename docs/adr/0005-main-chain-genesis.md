# ADR 0005: The genesis block of the main chain

- Status: accepted
- Date: 2026-10-09
- Decided by the maintainer in #148, on 2026-10-09: the genesis reward goes to `Genesis.NOBODY`, a
  burn account no transfer may spend, on both chains; the block's words are a headline of the day
  the main chain starts; and the rules ship before the start, separately from it. The proposal of
  2026-10-08, a block already mined with the default pun, is withdrawn and not shipped

## Context

#104 built the mechanism: a `GenesisAnchor` in `ConsensusRules`, checked when the table is built,
`Replay` refusing a chain whose genesis block is not the anchor of its chain, and `Genesis.start`
returning the anchor instead of mining a new block. It filed no anchor: the block, and above all
the distribution it carries, was the maintainer's decision.

Three decisions had been taken before this one, and the block has to carry them:

- **#111, no pre-allocation.** The whole supply goes into the mining pool, and the genesis block is
  paid the ordinary block reward out of it, like every other block, on both chains.
- **#133, the emission.** A block pays a millionth of the pool, so the genesis block's reward is
  exactly 1,984 LETH; the reward declines and never falls below a tail of 66 LETH.
- **#137, the identifiers.** The main chain under these rules is `lethenon-2`, the test chain
  `lethenon-test-2`, in one consensus break with the emission and this record.

What was left: whom the genesis reward goes to, the block's words, and when the block is made.

## Decision

### The genesis reward is burned, by a rule of the chain

Block 0 pays its reward to `Genesis.NOBODY`, the UTF-8 bytes of "nobody holds the genesis reward of
lethenon-2". This holds on the main chain and on every test chain, so the test chain tests the
main chain's rule, as #111 asks. Two locks keep the reward where it is, each enough alone:

1. **A consensus rule.** `Replay` refuses a chain whose block 0 pays anybody but `Genesis.NOBODY`,
   and `ChainState` refuses every transfer whose sender is `Genesis.NOBODY`
   (`BurnedGenesisTest`, `ReplayTest`). The rule is written down, so it does not depend on what a
   signature suite happens to accept.
2. **No key.** The bytes are words, not a public key: they decode as the X.509 key of no registered
   signature suite, and no signature verifies for them (`NobodyIsNoKeyTest`, for every value of
   `SignatureSuite`).

**A signature suite added later has to pass `NobodyIsNoKeyTest` before it is admitted.** The test
runs over `SignatureSuite.values()`, so a new suite is tested without anybody remembering to. A
suite for which these bytes decode as a key would weaken the second lock to the first alone, and
changing `Genesis.NOBODY` to fit it would change every genesis block, which is a new chain
identifier by the reasoning of #137.

The burned 1,984 LETH stay in every balance sum: the sum of all balances, `Genesis.NOBODY`
included, is the genesis supply plus what the tail has minted, after every block.

Why: a start anybody can check to be fair. Nobody, the maintainer included, holds a single LETH
before somebody mines block 1, and the chain itself says so, which is what #111 decided in words.
The alternatives, and why not:

- **A key of the maintainer.** The maintainer would hold the first 1,984 LETH before anybody else
  could mine a block: a pre-allocation of one block, which is what #111 decided against.
- **The pool itself as the beneficiary.** The reward would go from the pool back into the pool, so
  the genesis block would be paid in name only, where #111 decided it is paid like every block.
- **Leaving the reward out for the genesis block.** That is the rule #111 replaced.
- **Only the second lock, words that are no key.** That was the proposal of 2026-10-08. It holds
  only as long as every suite refuses the bytes, which nothing in the rules said; the first lock
  says it.

### The words are a headline of the day the main chain starts

The genesis block of `lethenon-2` carries a headline of its start day, chosen by the maintainer on
that day and given to `lethenon genesis --headline`. A headline proves the block was not mined
before that day. The block mined on 2026-10-08 with the default pun "in the beginning was the pun"
(hash `009cffa5...4e1f`) is therefore not shipped as the anchor.

### The rules ship before the start, the start ships on its own

- **0.4.0 carries the rules:** the emission (#133), the identifiers (#137), the burn account and
  the anchor mechanism (#104), and **no anchor for the main chain**. Without an anchor,
  `Genesis.start` refuses to start `lethenon-2` with a message naming 1.0.0 and `--testnet`, so
  `mine` on a new main chain exits 1 and writes nothing (`TestnetOnTheCommandLineTest`). `node`
  runs test chains only and refuses a main chain file, as since ADR 0003 (`NodeCommandTest`). The
  test network runs as before.
- **1.0.0 is the start of the main chain:** on the start day the block is mined from the
  maintainer's headline with the release build, filed as the anchor of `lethenon-2` in its own
  pull request, and released as 1.0.0. The steps are in `docs/launch/launch-checklist.md`, "The
  start day".

### The test chain

A test chain gets no anchor, ever: it is started by whoever runs `mine --testnet`, and its genesis
block pays `Genesis.NOBODY` like the main chain's. Block 1 pays the first wallet that mines on it,
and the faucet pays from that wallet. Since block 1 pays 1,983.998016 LETH under the declining
reward, the faucet hands out 1,000 LETH instead of 1,984, so that it works once block 1 is mined.
A node on the test network still trusts the first peer that answers for a test chain's first block
(#94).

## Consequences

- Every genesis block, on every chain, pays `Genesis.NOBODY`; a chain whose block 0 pays a wallet,
  every chain an earlier version wrote among them, is refused, already by its retired identifier.
- `mine --testnet` mines block 0 for nobody, and the next `mine` mines block 1 for the wallet.
- Before 1.0.0 there is no main chain to join: nothing in 0.4.0 can start one by accident, and
  `lethenon genesis` mines the main chain's candidate only to print it.
- After 1.0.0 every main chain is the same chain from block 0: two nodes on `lethenon-2` never start
  from two genesis blocks.
- Replacing the anchor after a release is a new chain identifier, by the reasoning of #137.
