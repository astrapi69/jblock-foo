# Wallet files written by released versions

## `written-by-0.1.0.lethw`

A wallet file as lethenon 0.1.0 writes it: mystic-crypt's `PassphraseCryptor` layout (`MCRYPT`,
version byte, iterations, salt, key-committing AES-GCM), around lethenon's own content (`LETHW`,
version 1, 32 bytes of entropy). It is what `WalletFileTest` opens after the switch to
`PassphraseEnvelope` (#45), to show that a wallet a released version wrote keeps opening and is not
rewritten by being opened.

It is the one exception to "key material in this project's tests is made at test time": a file a
released version wrote cannot be made by a later one. Nothing in it is a secret:

- **entropy**: synthetic, the bytes `0x40` to `0x5F` in order
- **password**: `wallet written by 0.1.0`

How it was made, on 2026-10-04: a worktree at `RELEASE-0.1.0` (built against mystic-crypt 13.2, as
the release was), a throwaway test in package `io.github.astrapi69.lethenon` calling
`WalletFile.write(file, Wallet.ofEntropy(entropy), "wallet written by 0.1.0".toCharArray())`, run
with `./gradlew test --tests '*WriteAFixtureTest*'`, the worktree removed afterwards.

Measured: 125 bytes, first bytes `MCRYPT` and `0x01`,
sha256 `4ea7825ae6a93343b4d94f8e7190a97ddc80599632ed24f2a7e89fcbc302d8bd`
(`sha256sum written-by-0.1.0.lethw`).
