# ADR 0005: The genesis block of the main chain

- Status: accepted
- Date: 2026-10-08
- The allocation (#111), the emission with its tail (#133) and the new identifiers (#137) were
  decided by the maintainer; the content of the block below was chosen within that release, by the
  reasons recorded here, without a further question to the maintainer. It can be replaced without
  cost until a release carries it, and never after

## Context

#104 built the mechanism: a `GenesisAnchor` in `ConsensusRules`, checked when the table is built,
`Replay` refusing a chain whose genesis block is not the anchor of its chain, and `Genesis.start`
returning the anchor instead of mining a new block. It filed no anchor: the block, and above all
the distribution it carries, was the maintainer's decision.

Three decisions have been taken since, and they are what the block has to carry:

- **#111, no pre-allocation.** The whole supply goes into the mining pool, and the genesis block is
  paid the ordinary block reward out of it, like every other block, on both chains.
- **#133, the emission.** A block pays a millionth of the pool, so the genesis block's reward is
  exactly 1,984 LETH; the reward declines and never falls below a tail of 66 LETH.
- **#137, the identifiers.** The main chain under these rules is `lethenon-2`, the test chain
  `lethenon-test-2`, built together with the anchor so there is one consensus break instead of
  three.

What is left for the block itself: whom its reward goes to, its timestamp, and its pun.

## Decision

**The beneficiary is nobody.** The block names as its beneficiary the UTF-8 bytes of
"nobody holds the genesis reward of lethenon-2" (`Genesis.NOBODY`). Those bytes are words, not a
public key: they do not decode as an X.509 key of either suite, so `TransactionSigner.verify` returns
false for any signature from that sender, and a transfer spending the reward never verifies
(`MainChainGenesisTest`, for Ed25519 and ML-DSA-65 alike, and on a chain).

The alternatives, and why not:

- **A key of the maintainer.** The maintainer would hold the first 1,984 LETH before anybody else
  could mine a block. That is a pre-allocation of one block, which is what #111 decided against,
  and it would make the anchor wait for a key to be handed over.
- **The pool itself as the beneficiary.** The reward would go from the pool back into the pool, so
  the genesis block would be paid in name only. #111 decided that the genesis block is paid like
  every other block; paying nobody does that, paying the pool does not.
- **Leaving the reward out for the genesis block.** That is the rule #111 replaced.

The cost is that 1,984 LETH, a millionth of the genesis supply, can never be spent. They stay in
every balance sum, so the supply invariant is unchanged.

**The timestamp is the moment it was mined,** 1,791,468,338,241 ms (2026-10-08 14:05:38 UTC).
Every later block of the main chain has to be later than the median of the ones before it, so a
main chain cannot be backdated before this change.

**The pun is the default of `lethenon genesis`,** "in the beginning was the pun", with the counter
mining appended: "in the beginning was the pun #315". The difficulty is the minimum, 8 bits.

**Hash:** `009cffa549366d12a7f223be0e90821b7155f215a57ee3b6eed9d1e98cd04e1f`, 218 bytes of canonical
encoding, filed as `LETHENON_2_GENESIS` in `ConsensusRules`. It was mined by the code of this change,
`Mining.nextBlock(Chain.IDENTIFIER, List.of(), Genesis.NOBODY, List.of(), "in the beginning was
the pun", now)` and `Blocks.mine(..., 10_000_000L)`, run once from a throwaway test that was not
committed. A rerun mines another block, because the timestamp differs; what anybody can check is
the filed one, and `ConsensusRules` checks it on every start: it decodes to one block, at height 0,
of `lethenon-2`, mined, and verifying on its own.

**The test chain has no anchor.** A test chain is started by whoever runs `mine --testnet`, and the
faucet pays from the genesis holder's account, so a test chain's genesis block has to pay a wallet
that can sign. A node on the test network still trusts the first peer that answers for a test
chain's first block (#94).

## Consequences

- `mine` on a new main chain writes the anchored block and says so: it pays nobody, and the next
  `mine` mines block 1 for the wallet. `lethenon genesis` refuses the main chain, as since #104.
- Every main chain is the same chain from block 0: two nodes on `lethenon-2` never start from two
  genesis blocks.
- Tests that need a main chain of their own replay it under a rule table without the anchor, or
  run on the test chain.
- Replacing the anchor before a release is a change of one constant and of this record. After a
  release it is a new chain identifier, by the reasoning of #137.
