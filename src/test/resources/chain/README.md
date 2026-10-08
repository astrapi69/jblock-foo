# The chain fixtures

`chain.lethenon` is a two-block chain of the test chain, `lethenon-test-2`: a genesis block that pays its
holder the block reward, and one mined block that names its miner and carries one signed transfer of
42 LETH with a fee of 100 lethe. Both blocks were mined at difficulty 8. The chain replays from this
file alone - who was paid is inside the blocks (lethenon#23). `holder.key` and `miner.key` are the two
public keys the blocks name, as raw X.509 bytes, so that a test can say whose balance it expects;
there are no private keys here and none are needed, because verifying needs none.

`chain-corrupted.lethenon` is the same file with one bit of its last byte flipped, which is inside
the signature of the transfer. It exists so that the verifier has something it must refuse: a
verifier that has never rejected anything is untested.

Written by `./gradlew writeChainFixtures` (`ChainFixtureWriter`, fresh keys every run) with block
encoding version 2; the chain file is 713 bytes. They are regenerated only when the encoding or the
chain identifier changes; the last time was when the main chain got its anchored genesis block
(#137, ADR 0005): a main chain of its own no longer replays, so the fixtures moved to the test chain.

`chain-lethenon-1.lethenon` is the chain file as an earlier build wrote it, 698 bytes, under
`lethenon-1` (commit 2e1076c, 2026-10-01). It is not regenerated: it is the evidence that a chain
started under the rules before 0.4.0 is refused, and with that reason, by this build (#137).
