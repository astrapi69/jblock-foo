# Use this family's libraries. Search before writing.

Scope: every line of this project.

## The rule

Before writing a class, a helper, a constant or an enum, look for it in the astrapi69 family
first. If it exists there, use it. If it almost exists, use it and report the gap in that
repository. Writing it here again is the last option, and it needs a reason in the commit
message.

This is not tidiness. The chain exists to be this family's acceptance test (lethenon#1):
primitives nobody consumes are never found to be wrong, and every line written here instead of
consumed there is a line of evidence that does not get produced.

## Where to look, in this order

| What | Where |
|---|---|
| algorithm names, key formats, key sizes, hash and cipher enums | `crypt-api` - e.g. `KeyPairGeneratorAlgorithm` (Ed25519, ML-DSA, SLH-DSA), `HashAlgorithm`, `KeyFormat`, `KeyFileFormat` |
| key readers and writers, PEM/DER, hex, certificates | `crypt-data` - e.g. `PrivateKeyReader`, `PublicKeyExtensions`, `HexExtensions` |
| signing, verifying, encryption, KEM, Shamir sharing, password hashing | `mystic-crypt` - e.g. `Ed25519Signer`, `Ed25519Verifier`, `SecretSharing` |
| checksums of files and bytes | `checksum-up` |
| tree structures | `gen-tree`, `tree-api` |
| random test data | `randomizer`, `test-object` |
| files and IO | `file-worker`, `silly-io` |

Nothing from this list is imported "just in case": a dependency is declared when it is used, and
used dependencies are declared rather than inherited quietly through another one.

## What stays this project's own

Only what the chain itself defines and what must not move when a library moves:

- the **wire identifiers** that go into signed bytes and into blocks. `SignatureSuite.identifier()`
  is `"ed25519"` and deliberately not the enum's name - a persisted format must not change when an
  upstream enum is renamed or reordered. The wire is forever, the library is a dependency
- the chain's own rules: amounts, emission, encoding order, block validity, consensus

## When the library does not fit

Report it there, do not work around it quietly. Two found on the first day of using them:

- `mystic-crypt#149` - no suite-dispatching signer, so every caller writes the same switch and each
  decides for itself whether an unknown suite is refused or ignored
- `crypt-data#51` - `HexExtensions` throws `DecoderException` from commons-codec while the
  dependency is `implementation`, so a consumer cannot compile without declaring commons-codec

A workaround is allowed while the gap is open, and it carries a comment naming the issue.
