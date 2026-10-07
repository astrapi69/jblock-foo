# ADR 0001: When this project may use a new cryptographic construction

- Status: accepted
- Date: 2026-10-06
- Decided by the maintainer on 2026-10-06 (#49); rule 5 added by the maintainer on 2026-10-07 (#55)
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

The maintainer set five rules for any construction that is new to this project:

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
5. **The proof model is named, and only a quantum proof is called post-quantum.** The record of
   every scheme states its proof model at one of three levels:
   - **QROM, direct:** the paper proves the scheme in the quantum random oracle model.
   - **QROM via a general result:** a published general result, for example on the Fiat-Shamir
     transform in the QROM, is cited with its location, and the record argues why each of its
     conditions holds for this scheme.
   - **ROM only:** the proof is in the classical random oracle model.

   A ROM-only scheme is allowed, but it is never described as "post-quantum secure", not in its
   record, not in the README, not in the CHANGELOG. It is described as "post-quantum assumptions,
   classical proof". On the main chain, a scheme needs, in addition to the review of rule 3, a
   QROM proof at one of the first two levels, or an explicit verdict of that external review on
   exactly this question.

## Consequences

- The README no longer says that sender ambiguity and amount confidentiality are out. It says they
  are not built, and under which rule they may be.
- Every scheme gets a record of its own under `docs/adr/` before its code: the citation, the
  assumption, the proof model at its level (rule 5), the test vectors or their absence, and the
  review status.
- README and CHANGELOG promise only what is measured. A comparison with another system (Monero, for
  example) is stated only with the number or the property that supports it.
- **Precondition for everything that follows:** the test chain identifier (#50) is built. Before
  it exists there is nowhere for rule 3 to put a scheme.
- **Outside this repository:** mystic-crypt's `.claude/rules/library-first.md`, level 0, keeps
  saying "Never implement cryptographic primitives yourself ... This level has no exceptions." It
  stays without exception. A new construction is built here, in lethenon, and moves into the
  library only after its external review, recorded there as well (#49).
