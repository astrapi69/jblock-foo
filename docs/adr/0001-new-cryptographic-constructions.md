# ADR 0001: When this project may use a new cryptographic construction

- Status: accepted
- Date: 2026-10-06
- Decided by the maintainer on 2026-10-06 (#49)
- Replaces: "Both mean writing new cryptography, which this project does not do" (README, privacy
  section) and "This project writes formats and rules, not primitives" (README, planned shape)

## Context

Lethenon hides the recipient of a payment with one-time destinations (#18, #21), and nothing else:
the sender is a public key in the clear and the amount is a plain number in every
`TransactionBody`. The README ruled hiding the other two out with a sentence that was never the
maintainer's decision, and it ruled them out for the wrong reason: the work is not forbidden, it
needs a rule for doing it without inventing cryptography.

The goal it makes room for is the post-quantum privacy block: hide sender and amount as well, with
schemes that hold against quantum computers, behind interfaces that let later schemes join and a
broken one be switched off without starting the chain again.

## Decision

The maintainer set four rules for any construction that is new to this project:

1. **Published, peer reviewed, proven.** A new cryptographic construction is used only after a
   peer-reviewed publication. Its record here cites the paper (authors, title, venue, year), names
   the security assumption, and points to the proof in the paper.
2. **Checked against the authors.** The implementation passes the authors' test vectors, or agrees
   with a reference implementation. Where neither exists, the record says so explicitly; that is
   a known gap, not a silent one.
3. **Test network first.** Every scheme runs on the test chain only (#50). It reaches the main
   chain `lethenon-1` only after an external cryptographic review that the maintainer commissions.
   The consensus rule refuses it on the main chain until then.
4. **No invented primitives.** This project does not design primitives of its own. Combining
   known building blocks is allowed when the security of the combination is argued and
   documented in the record of that scheme.

## Consequences

- The README no longer says that sender ambiguity and amount confidentiality are out. It says they
  are not built, and under which rule they may be.
- Every scheme gets a record of its own under `docs/adr/` before its code: the citation, the
  assumption, the test vectors or their absence, and the review status.
- README and CHANGELOG promise only what is measured. A comparison with another system (Monero, for
  example) is stated only with the number or the property that supports it.
- **Precondition for everything that follows:** the test chain identifier (#50) is built. Before
  it exists there is nowhere for rule 3 to put a scheme.
- **Outside this repository:** mystic-crypt's `.claude/rules/library-first.md`, level 0, keeps
  saying "Never implement cryptographic primitives yourself ... This level has no exceptions." It
  stays without exception. A new construction is built here, in lethenon, and moves into the
  library only after its external review, recorded there as well (#49).
