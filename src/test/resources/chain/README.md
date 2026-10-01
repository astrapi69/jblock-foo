# The chain fixtures

`chain.lethenon` is a two-block chain: a genesis block that names the holder of the non-pool half
of the supply, and one mined block that names its miner and carries one signed transfer of 42 LETH
with a fee of 100 lethe. Both blocks were mined at difficulty 8. The chain replays from this file
alone - who was paid is inside the blocks (lethenon#23). `holder.key` and `miner.key` are the two
public keys the blocks name, as raw X.509 bytes, so that a test can say whose balance it expects;
there are no private keys here and none are needed, because verifying needs none.

`chain-corrupted.lethenon` is the same file with one bit of its last byte flipped, which is inside
the signature of the transfer. It exists so that the verifier has something it must refuse: a
verifier that has never rejected anything is untested.

Written by `./gradlew writeChainFixtures` (`ChainFixtureWriter`, fresh keys every run) with block
encoding version 2; the chain file is 698 bytes. They are regenerated only when the encoding
changes, and then the change of the encoding version is what says so.
