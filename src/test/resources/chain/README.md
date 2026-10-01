# The chain fixtures

`chain.lethenon` is a two-block chain written once by a process that no longer exists: a genesis
block and one mined block carrying one signed transfer of 42 LETH with a fee of 100 lethe. Both
blocks were mined at difficulty 8. `holder.key` and `miner.key` are the two public keys it names,
as raw X.509 bytes - there are no private keys here and none are needed, because verifying needs
none.

`chain-corrupted.lethenon` is the same file with one bit flipped near its end, inside the
signature. It exists so that the verifier has something it must refuse: a verifier that has never
rejected anything is untested.

Written with the code of 2026-10-01; the chain file is 601 bytes. They are regenerated only when
the canonical encoding changes, and then the change of the encoding version is what says so.
