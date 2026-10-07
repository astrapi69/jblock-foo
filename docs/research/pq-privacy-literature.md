# Phase A: literature survey for a post-quantum privacy block (sender and amount hiding)

First research: 2026-10-06. Revised: 2026-10-07 (second pass with more hosts reachable, then a third
pass against the full texts of 14 ePrint papers uploaded by the maintainer, see 0.3; fourth pass:
proof-model levels, see 14; fifth pass: the six general QROM results in full text, see 14.1;
sixth pass: the phase C pre-check of a post-quantum hidden recipient, see 15; seventh pass: the
phase C pre-check of hidden amounts, see 16). Scope:
building blocks that could hide SENDER and AMOUNT on lethenon against a quantum adversary, checked
against ADR 0001 rules (1) peer-reviewed publication with assumption and proof, (2) authors' test
vectors or reference implementation, and (5) the proof model at one of three levels: L1 "QROM,
direct", L2 "QROM via a general result", L3 "ROM only"; an L3 scheme is described as "post-quantum
assumptions, classical proof".

## 0. How this survey was produced, and what that limits

Read this section before using any number below.

### 0.1 Access, as measured on 2026-10-07

Reachable and used: **eprint.iacr.org landing pages** (title, authors, abstract, "Publication info",
DOI, version history; the PDFs, the version archive and old-version pages return 403 behind a
Cloudflare bot challenge, not bypassed; ePrint search works); **arxiv.org** abs and pdf;
**iacr.org** (bare host): cryptodb conference listings and paper pages, talk slides under
`iacr.org/submit/files/slides/`, one proceedings PDF under `iacr.org/archive/crypto2021/` (SMILE;
directory listings are 403); **csrc.nist.gov**, **datatracker.ietf.org**, **zips.z.cash**,
**github.com** (`git clone`), **gitlab.com** (`git ls-remote` and `git clone` over HTTPS, checked
2026-10-07).

Not reachable: www.iacr.org and crypto.iacr.org (proxy CONNECT rejected), doi.org, Springer (login
redirect), dl.acm.org (403), ieeexplore (418), semanticscholar, mdpi.com (CONNECT rejected),
ro.uow.edu.au and other university repositories.

Downloads and clones were kept outside this repository and treated as data only; nothing was
built or run.

### 0.2 Source tags

Every fact below carries one of these tags and the link of a page fetched in THIS pass:
- **[E]** ePrint landing page (abstract, "Publication info", authors). Not the paper body.
- **[F]** full text of an ePrint PDF uploaded by the maintainer on 2026-10-07 (see 0.3), cited as
  "ePrint YYYY/NNN full text, p. N, <label>". Page numbers are PDF pages; for all 14 PDFs they
  coincide with the printed page numbers. After the first full [F] citation in a bullet, later ones
  in the same bullet are shortened to "[F: p. N]" (same paper); a bare [F] cites the pages named in
  the same sentence. In section 14 [F] also cites the full texts of six general QROM results
  uploaded by the maintainer on 2026-10-07 (2017/916, 2021/927, 2022/889, 2023/245, 2023/246,
  2025/985), with PDF page numbers. For 2017/916, 2021/927, 2023/245 and 2025/985 they coincide
  with the printed page numbers; for 2023/246 the printed number is one lower (title page
  unnumbered); for 2022/889 the two were not compared.
- **[A]** arXiv full text, read in this pass (section, theorem and table numbers given).
- **[P]** proceedings full text from iacr.org/archive, read in this pass.
- **[C]** IACR cryptodb accepted-paper listing for the named conference and year.
- **[X]** secondary: a reference list entry inside another fetched document (named each time). It
  shows what another author cites, not the cited paper itself.
- **[Q]** full text of a general QROM result read from arXiv (arXiv id given); page numbers are
  arXiv PDF pages, which may differ from the proceedings pagination. Used in section 14 only.
- **[N]** csrc.nist.gov page or a PDF hosted there. **[I]** datatracker.ietf.org. **[Z]**
  zips.z.cash. **[G]** read from a `git clone` made on 2026-10-07.

The first pass's **[S]** tag (a web-search summary of a page that was not itself opened) is no
longer used. Every remaining "not verified" says why, in short form:
- **(PDF)** full text only as an ePrint PDF that was neither fetched nor uploaded.
- **(PUB)** only on a publisher site that is not reachable (Springer, ACM, IEEE, MDPI).
- **(SRCH)** looked for and not found; absence of a hit is not absence.
- **(NIP)** "not in the PDF": the uploaded full text does not print it (most often the venue, the
  ePrint number or a version date). The fact then rests on the other tag given.
Sizes and timings are quoted, never computed here. Nothing below is filled in from memory.

### 0.3 Full-text check (third pass, 2026-10-07)

The maintainer uploaded 14 ePrint PDFs on 2026-10-07, converted with `pdftotext -layout`
(MatRiCT's p. 2 tables and p. 14 figures also rendered as images). The PDFs are not committed.
One findings note per paper, kept outside this repository, marks each survey claim CONFIRMED, WRONG
or STILL NOT IN PAPER; the marks are integrated here, conflicts were re-checked in the full text,
corrections are listed in section 12. The 14 papers: 2016/997 (BDLOP), 2018/857 (Raptor), 2019/569
(LRCT v2.0), 2019/1287 (MatRiCT), 2020/646 (Calamari/Falafl), 2021/545 (MatRiCT+), 2021/564 (SMILE,
full version), 2021/1674 (Gao et al.), 2022/284 (LNP22), 2022/1341 (LaBRADOR), 2022/1696
(Maram-Xagawa), 2023/1148 (SPIRIT), 2024/553 (Xue et al.), 2024/1846 (LaZer). Not uploaded, so still
from landing pages or secondary sources only: 2018/379 (LRCT v1.0), 2018/773 (Esgin et al.),
2019/371, 2018/107, 2020/1121, 2018/046, 2018/828, 2022/1608, 2020/517, 2020/1448, 2021/540,
2024/457, 2025/112 (read via arXiv), the arXiv preprints, SALRS. The PDFs are ePrint versions. Where
a venue is printed in the PDF it is cited as [F]; where the PDF prints none, the venue keeps its
[E], [C] or [X] source and is marked (NIP).

## 1. Lattice-based RingCT / confidential transaction schemes (sender + amount)

### 1.1 Lattice RingCT v1.0 (L2RS)
- Citation: W. Alberto Torres, R. Steinfeld, A. Sakzad, J. K. Liu, V. Kuchta, N. Bhattacharjee, M.
  H. Au, J. Cheng, "Post-Quantum One-Time Linkable Ring Signature and Application to Ring
  Confidential Transactions in Blockchain (Lattice RingCT v1.0)", ePrint 2018/379, Publication info
  "Published elsewhere. Minor revision. ACISP-2018" [E] (https://eprint.iacr.org/2018/379). Pages
  558-576 [X: ePrint 2019/569 full text, p. 18, reference [1]]. DOI: not verified (PUB).
- Hides: sender (one-time linkable ring signature) and amount, per the abstract's RingCT application
  [E].
- Assumption: Ring-SIS; the abstract claims "unconditional anonymity" for L2RS [E]; v2.0 repeats
  that L2RS "also achieves unconditional anonymity" [F: ePrint 2019/569 full text, p. 3, Sec. 2].
- Proofs: the v2.0 paper states "the proposed Lattice RingCT v1.0 showed no security definition or
  proofs, and transactions were restricted to Single Input and Single Output wallets", while L2RS
  itself has Theorems 2-5 in the v1.0 paper [F: ePrint 2019/569 full text, p. 3 and p. 10]. Proof
  model, sizes, timings of v1.0: not verified (PDF, not uploaded).
- Reference implementation: see 1.2 (chainchip code cites this paper as its basis).
- Test vectors: none found [G].

### 1.2 Lattice RingCT v2.0 (LRCT v2.0, MIMO)
- Citation: W. Alberto Torres, V. Kuchta, R. Steinfeld, A. Sakzad, J. K. Liu, J. Cheng, "Lattice
  RingCT v2.0 with Multiple Input and Multiple Output Wallets" (title as printed in the PDF [F:
  ePrint 2019/569 full text, p. 1]), ePrint 2019/569, Publication info "Published elsewhere. Major
  revision. ACISP2019", last revised 2020-09-16 [E] (https://eprint.iacr.org/2019/569); venue and
  date (NIP). One author is from Collinstar Capital [F: p. 1].
- Hides: sender in a ring of w users (linkable ring signature MIMO.L2RS with linking tag) and amount
  in homomorphic commitments; Definition 7 covers user anonymity and amount privacy; refined balance
  model; lattice range proof [F: ePrint 2019/569 full text, p. 1 abstract, p. 7 Def. 7, p. 11 Table
  1, Sec. 5.1 p. 13]. Recipient: no recipient-hiding mechanism; output addresses are public keys [F:
  p. 12, eq. (3)].
- Assumption: Module-SIS only (Definition 1), R_q = Z_q[x]/(x^n + 1), q = 1 mod 2n; commitment
  hiding is information-theoretic via the Leftover Hash Lemma (Theorem 1); parameters n = 1024, m =
  132, log q = 196, lambda = 100, BKZ Hermite factor delta = 1.007 [F: ePrint 2019/569 full text, p.
  3 Def. 1, p. 8 Thm. 1, p. 17 Sec. 7].
- Proof model: ROM, Fiat-Shamir NIZK (Sec. 2.2 p. 4, Proposition 2 p. 10, Theorem 11 p. 32); QROM
  not mentioned [F]. Proofs: Theorems 1-6, 9-12 with Appendices A, B.1, B.2, F, G, H, I (pp. 19-39)
  [F]. Gaps printed in the paper: Proposition 1 (p. 10) inherits unforgeability, anonymity,
  linkability and non-slanderability "from the L2RS' security analysis"; Theorem 7 (p. 17) has no
  separate proof; Lemma 2 (p. 3) has a heading and no body [F].
- Sizes: estimates, not measurements ("Preliminary parameter estimates") [F: p. 1]. Table 2:
  2-in/2-out signature about 5.1 MB at w = 1, about 8 MB at w = 5; 1-in/2-out about 4.8 MB at w = 1;
  public key about 146 KB for 2-in/2-out [F: ePrint 2019/569 full text, p. 18, Table 2]. Timings:
  the PDF gives none (NIP).
- Implementations [G], none mentioned in the paper (NIP), none by the paper's authors:
  https://github.com/HcashOrg/RingCT (README only, ISC per README, 2019-05-13);
  https://github.com/chainchip/Lattice-RingCT-v2.0 (C, 105 files, 2019-02-21; README: "the hcash
  foundation commissioned ChainChip Corporation based on Joseph's paper[1]", [1] = ePrint 2018/379;
  ISC per README, no LICENSE file; `ring_test.c` randomized round trips; `grep -ci range
  Ring2.0/ring.c` returns 0, so range-proof coverage is unchecked);
  https://github.com/Sobieg/Lattice-RingCT (fork, MIT LICENSE, 2022-01-12).
- Test vectors: none [G]; none in the paper [F].

### 1.3 MatRiCT
- Citation: M. F. Esgin, R. K. Zhao, R. Steinfeld, J. K. Liu, D. Liu, "MatRiCT: Efficient, Scalable
  and Post-Quantum Blockchain Confidential Transactions Protocol", ePrint 2019/1287 [E]
  (https://eprint.iacr.org/2019/1287): "the full version of an article published in the proceedings
  of 2019 ACM SIGSAC Conference on Computer and Communications Security (CCS '19)", DOI
  10.1145/3319535.3354200 [F: ePrint 2019/1287 full text, p. 1, footnote *]. Pages 567-584 [X: SMILE
  proceedings ref. 13; SMILE full version ref. [18], ePrint 2021/564 full text, p. 30] (NIP). The
  revised version (2020-05-05) adds "more information about the proved relation" to Lemma 5.5 [E];
  Lemma 5.5 is on p. 15, the PDF prints no revision note [F].
- Hides: sender (ring signature) and amount (commitments, balance proof); NOT the recipient
  ("spender anonymity (not recipient anonymity)", stealth addresses "a future work") [F: ePrint
  2019/1287 full text, p. 3, Sec. 1.1]. Optional auditability [E] without formal definition [F: p.
  18]. An account is "a registered public key and coin pair", spent once under a serial number among
  decoys [F: p. 4, p. 7].
- Assumption: M-SIS and M-LWE (Defs. 2.1, 2.2) over Z_q[X]/(X^d + 1) and Z_q^[X]/(X^d + 1), q about
  31 bits, q^ about 53 bits; for N <= 1000, r = 64, M, S <= 2: d = 64, q = 2^31 - 2^18 + 2^3 + 1,
  (n, m) = (18, 38), (n^, m^) = (32, 65), public key 4.36 KB, serial number 248 B, root Hermite
  factor 1.0045 for "128-bit post-quantum security" [F: ePrint 2019/1287 full text, p. 3, p. 6, p.
  13 Sec. 4.1]. n_s = 1 "actually makes M-SIS easy to solve with respect to H", argued to affect
  availability only [F: pp. 19-20, App. B].
- Proof model: ROM (pp. 7, 9, 11); QROM explicitly not claimed: post-quantum security "does not
  necessarily involve a security proof in the quantum random oracle model" [F: p. 1, fn. 1].
- Proofs: Section 5 (pp. 14-17) and Appendices C-F: Lemma 5.1 (anonymity), Lemma 5.7 (balance:
  forgery, double spend, unbalanced amounts), both "without shuffling"; honest input coins assumed
  (p. 9); no availability property (p. 8) [F].
- Sizes: Table 1, 2-in/2-out 110 KB and 1-in/2-out 93 KB at anonymity level 1/10; 120 / 103 KB at
  1/100 [F: ePrint 2019/1287 full text, p. 2, Table 1]. Figure 1 is a plot (markers at log N = 1, 3,
  6, ..., 30, no printed values) [F: p. 14, rendered]. Ring signature alone: 18 KB at N = 2, 59 KB
  at 4096 [F: p. 3, Table 3]. See 1.3.1 for the 110 KB figure.
- Timings: Table 2 at 3 GHz, 2-in/2-out at 1/10: generation 375 ms, verification 23 ms; at 1/1000:
  3514 / 223 ms [F: p. 2, Table 2]; GCC 8.3.0 -O3 -march=native, AES-NI, SHAKE-256, CPU model not
  printed [F: p. 14].
- Reference implementation: "the first full implementation of a post-quantum RingCT" is claimed
  [E][F: p. 1]; no URL in the paper (NIP); none found (SRCH: GitHub search returned LRCT repos and
  https://github.com/insight-decentralized-consensus-lab/post-quantum-monero [G], writeups only).
  Gao et al.'s "toy" Go repository (13.2) contains MatRiCT's balance and one-out-of-many proofs for
  comparison [G]; that is third-party code.
- Patent / licence to Hcash: not verified (no patent source fetched; nothing in the paper). Test
  vectors: none found (SRCH); none in the paper.

#### 1.3.1 The 110 KB figure (resolved, three values with locations)
- MatRiCT: 110 KB for 2-in/2-out at anonymity level **1/10** (N = 10) [F: ePrint 2019/1287 full
  text, p. 2, Table 1].
- MatRiCT+ quoting MatRiCT [5]: 110 KB for 2-in/2-out under "Anonymity level 1/N = **1/11**" [F:
  ePrint 2021/545 full text, p. 2, Table I; p. 16, Table IX].
- SMILE quoting MatRiCT [18]: 110 KB for (2, 2) at ring size **N = 2^5**, "taken from [18, Figure
  1]" [F: ePrint 2021/564 full text, p. 12, Fig. 3][P Fig. 3].
- MatRiCT's Figure 1 has no marker at log N = 5 and prints no values [F: ePrint 2019/1287 full text,
  p. 14]. The only printed MatRiCT value is 110 KB at N = 10; "1/11" is MatRiCT+'s label, "2^5" is
  SMILE's reading of an unlabelled plot.

### 1.4 MatRiCT+
- Citation: M. F. Esgin, R. Steinfeld, R. K. Zhao, "MatRiCT+: More Efficient Post-Quantum Private
  Blockchain Payments", ePrint 2021/545, "Published elsewhere. Major revision. IEEE Symposium on
  Security and Privacy (S&P) 2022" [E] (https://eprint.iacr.org/2021/545); the authors' repository
  README gives "IEEE S&P 2022. DOI 10.1109/SP46214.2022.9833655" [G:
  gitlab.com/raykzhao/matrict_plus README.adoc]. The PDF prints no venue, date or ePrint number
  (NIP). Latest ePrint version 2021-07-30 [E].
- Hides: sender (aggregate 1-out-of-N proof "a.k.a. a linkable ring signature") and amount [F:
  ePrint 2021/545 full text, p. 1 abstract, p. 10 Sec. V]. Recipient anonymity only as an unproven
  extension in Appendix C-A ("lattice analog of the technique used in Monero") [F: p. 17].
- Assumption: M-SIS and M-LWE (Defs. 1-2), "Extended M-LWE" for the optimized rejection sampling
  (Alg. 2), Asymmetric M-SIS for estimation; full list as Assumption 1 (nine items) [F: ePrint
  2021/545 full text, p. 5 Sec. II-A/II-C, pp. 13-14]. Parameters: d = 256, q = 167770241, q^ =
  3489661057, w = 44, N = 11 "as in Monero", RHF 1.0043-1.00469 [F: pp. 12-13].
- Proof model: ROM: "we analyze the security of our scheme in the ROM while relying on the hardness
  of 'post-quantum' lattice assumptions" [F: p. 1, footnote 1]. QROM not discussed.
- Proofs: Theorem 1 (Balance, pp. 14-15), Theorem 2 (Anonymity, Appendix G p. 20), witness
  extraction Appendix E pp. 18-19 [F]. Both theorems "without shuffling". The anonymity proof covers
  only Alg. 1 rejection sampling, while Spend-II calls RejOp (Alg. 2) and the implementation uses
  both [F: p. 20, p. 12 Alg. 6, p. 13]. Self-reported "denial-of-spending" surface: serial numbers
  do not depend on secret information [F: p. 18, Appendix D].
- Sizes: Table I, 2-in/2-out at 1/N = 1/11: 47 KB (current implementation, no compression), 29 KB
  "w/ compression" (estimate, not implemented); 100-in/2-out 61 / 43 KB; PK 3.42 KB, serial number
  32 B [F: ePrint 2021/545 full text, p. 2, Table I, and p. 1 Sec. I-A].
- Timings: Table II at 3 GHz, 2-in/2-out at 1/10: key generation 0.07 ms, transaction generation 100
  ms, verification 2 ms; 261 ms / 8 ms at 1/50 [F: p. 2, Table II]; C/C++ on i7-7700K [F: p. 13].
  "2-18x shorter", "3-11x faster" [E][F: p. 1].
- Reference implementation: "The implementation source code along with our Asymmetric M-SIS scripts
  are available at https://gitlab.com/raykzhao/matrict plus" [F: ePrint 2021/545 full text, p. 13,
  footnote 10; also p. 7, footnote 5]. Repository, see 13.1 [G]: exists as `matrict_plus`, C, BSD
  Zero Clause LICENSE, last commit 2025-08-25. Test vectors: none in the paper or the repository.

### 1.5 SMILE (and its confidential-transaction application)
- Citation: V. Lyubashevsky, N. K. Nguyen, G. Seiler, "SMILE: Set Membership from Ideal Lattices
  with Applications to Ring Signatures and Confidential Transactions", CRYPTO 2021 [E:
  https://eprint.iacr.org/2021/564] [C:
  https://iacr.org/cryptodb/data/conf.php?year=2021&venue=crypto] [F: ePrint 2021/564 full text, p.
  1, footnote "This is the full version of [27] presented at CRYPTO 2021"; ref. [27] p. 30 gives
  pages 611-640]. DOI 10.1007/978-3-030-84245-1_21 [E] (NIP). Proceedings version read in full [P]
  (https://iacr.org/archive/crypto2021/12826384/12826384.pdf); full version read in full [F].
- Hides: set membership proof -> ring signature (sender); a Monero-like transaction system "based on
  the MatRiCT framework" (sender + amount) [E][P 1.6][F: p. 10 Sec. 1.6, p. 51 App. E]. Recipient
  unlinkability only as a model statement in Appendix E, no construction [F: p. 51].
- Ring: R_q = Z_q[X]/(X^128 + 1), 32 factors X^4 - r_i [P 1.1][F: p. 3]. Commitments: BDLOP.
- Assumption: M-SIS (binding) and M-LWE (hiding) [P 2.4][F: pp. 19-20], and Extended-MLWE for
  zero-knowledge and anonymity (Def. A.4 p. 32, Theorem B.1 p. 34, Theorem C.4 p. 43), with the
  parameter assumption "Extended-MLWE is almost as hard as M-LWE", delta about 1.0042 [F: ePrint
  2021/564 full text, p. 48, App. C.5].
- Proof model and location: the proceedings defer the analysis to the full version [P Sec. 3]. The
  full version gives Theorem B.1 (one-out-of-many proof: HVZK under Extended-MLWE, special soundness
  under M-SIS; proof pp. 35-41) and Theorem C.4 (ring signature: anonymity, unforgeability "in the
  random oracle model"; proof pp. 43-46) [F: ePrint 2021/564 full text, p. 34, Theorem B.1; p. 43,
  Theorem C.4]. QROM not stated; Don-Fehr-Majenz is cited only for the definition of the multi-round
  Fiat-Shamir transform [F: p. 42, App. C.2]. **The confidential-transaction system has no security
  theorem or proof** in either version: Appendix E "follow[s] the model and construction from [18]"
  (MatRiCT) and gives the protocol only [F: pp. 51-57].
- Sizes (all calculated, not measured [F: p. 54]): ring signature 16 KB at 2^5, 22 KB at 2^25
  [E][P][F: p. 1]; exact 15.96 KB at 2^5, 21.53 KB at 2^25 [F: p. 48, Fig. 11]. Transactions (Fig.
  3): 2-in/2-out 24 KB at N = 2^5, 27 KB at 2^10, 30 KB at 2^15, 36 KB at 2^25; 1-in/2-out 22 KB at
  2^5, 28 KB at 2^25 [P][F: p. 12, Fig. 3]. The text gives 26.49 KB for 2-in/2-out at N = 1024 [F:
  p. 55], Fig. 3 prints 27 KB for the same case. Fig. 4: M = 25, N = 1024: 100 KB; M = 100: 345 KB
  (text: 344.26 KB, p. 56) [P][F: p. 12]. Public key 3.28 KB, coin 3.78 KB [F: p. 54].
- Timings: none in either version [P][F]. Code, test vectors: none named [F]; none found (SRCH).

### 1.6 Gao et al., lattice ZK proofs for RingCT
- Citation: S. Gao, T. Zheng, Y. Guo, Z. Peng, B. Xiao, "Lattice-based Zero-knowledge Proofs for
  Blockchain Confidential Transactions", PKC 2025 [E: "A minor revision of an IACR publication in
  PKC 2025", https://eprint.iacr.org/2021/1674] [C:
  https://iacr.org/cryptodb/data/conf.php?year=2025&venue=pkc], DOI 10.1007/978-3-031-91832-2_5 [C
  paper page https://iacr.org/cryptodb/data/paper.php?pubkey=35153]. Venue and DOI (NIP).
- Hides: sender (ring signature via linear-sum relation, linkable by serial number) and amount
  (inner-product linear-equation balance proof, 64-bit, no corrector values) [E][F: ePrint 2021/1674
  full text, p. 1 abstract, p. 3, p. 22 Sec. 6.1]. Recipient: not addressed [F: p. 24].
- Assumption: M-SIS and M-LWE (Defs. 1-2) over R_q = Z_q[X]/(X^d + 1), d a power of 2; HMC
  commitment from MatRiCT; parameters for the MatRiCT- and MatRiCT+-based variants; 128-bit
  post-quantum target via delta about 1.0045 [F: ePrint 2021/1674 full text, pp. 4-6, p. 26, p. 27].
- Proof model: ROM for the ring signature (Theorem 3, p. 18); Fiat-Shamir heuristic for the
  non-interactive RingCT (p. 21); QROM not discussed [F].
- Proofs: Theorem 1 (App. B p. 34), Theorem 2 (App. C p. 38, whose heading is printed "Proof of
  Theorem 4"), Theorem 4 (App. D p. 40), Theorem 3 (App. E pp. 43-44) [F]. No theorem for the
  composed RingCT (balance, anonymity, linkability); the unbalancing fix is argued in prose [F: p.
  23]. Partial amortization requires S <= 2 [F: p. 14 fn. 5, p. 28].
- Sizes/timings: the paper has no tables and prints no absolute values; Figs. 1-4 are plots [F:
  ePrint 2021/1674 full text, p. 25]. The measured ring signature is the linear-size variant (k = 1,
  beta = N, N up to 10) [F: p. 26]. Abstract: up to 50% / 20% smaller proofs, 30% / 20% faster
  proving, 20% / 20% faster verification than MatRiCT / MatRiCT+ [E][F: p. 1]; body: 15% smaller
  balance proof than MatRiCT at M = 1, about 15% smaller ring signature than MatRiCT+ [F: pp.
  26-27]. Talk slides give other percentages (90%/30%, 70%/20%, 60%/20%, 60%/15%) [I-slides:
  https://iacr.org/submit/files/slides/2025/pkc/pkc2025/46/46_slides.pdf] (NIP). Platform: Go, LaGo
  ring library, i7-8750H [F: pp. 25-26].
- Reference implementation: "a reference implementation of our approaches in Golang",
  https://github.com/GoldSaintEagle/RingCT_Implementation [F: ePrint 2021/1674 full text, p. 25,
  footnote 7]. Repository, see 13.2 [G]: exists, Go, no LICENSE file, README "toy", last commit
  2023-01-10.

### 1.7 LACT+ (aggregable confidential transactions, amount only)
- Citation: J. Alupotha, X. Boyen, M. McKague, "LACT+: Practical Post-Quantum Scalable Confidential
  Transactions", Cryptography (MDPI) 7(2), 2023: not verified (PUB: mdpi.com CONNECT rejected). The
  repository's CITATION.cff exists [G]; its content was not used as a venue source.
- Hides: **amount only** ("hide coin amounts but verify the validity of hidden coins"); not the
  sender [G README, https://github.com/jaymine/lactv2].
- Assumption: Approximate Short Integer Solution (Approx-SIS) [G README].
- Proof model / location, sizes, timings: not verified (PUB).
- Reference implementation [G]: https://github.com/jaymine/lactv2. C, CMake, GPL-3.0 (LICENSE), last
  commit 2023-07-20. README: "the implementation mainly targets educational purposes". `src/tests.c`
  self-checking unit tests, no published known-answer vectors.
- Maturity note: whether an MDPI journal satisfies ADR rule (1) is a project decision.

### 1.8 Newer items, preprints

#### Obscura-PQ (arXiv 2608.22645) [A]
- N. Azimi (Emory University), "Obscura-PQ: Post-Quantum Privacy-Preserving Protocol for the
  Algorand Blockchain Using Lattice-Based Linkable Ring Signatures", arXiv 2608.22645v1, 2026-08-23
  (https://arxiv.org/abs/2608.22645). **Preprint only** (no journal reference; the README's IACR
  badge reads "xxxx/xxx" [G]).
- Fixed-denomination deposit/withdraw pool (20.101 ALGO [G]); hides the deposit-withdrawal link
  (sender) in a ring of at most r_max = 10 [A Table 1]; **does not hide amounts** (future work "in
  the style of MatRiCT", [A section 10]).
- Linkable ring signature over Z_q[X]/(X^512+1), q = 12289; Ring-SIS (soundness, linkability, theft
  resistance), Ring-LWE plus "an explicit decisional linking assumption" (anonymity); Theorems 1-4,
  proofs in Appendix A [A abstract, Table 1, section 8].
- Classical ROM only, "A dedicated QROM analysis ... has not been carried out"; relaxed extraction;
  the implemented mask sampler is not the full Fiat-Shamir-with-aborts sampler [A 8.3]. Signature 32
  + 3072r bytes, "over 30 KB at the maximum ring size" [A section 9].
- Code [G]: https://github.com/n-azimi/Obscura-PQ, GPL-3.0, HEAD dc707c4 (2026-08-26). **The
  `core/`, `contract/` and `frontend/` directories that the README and the paper describe are not in
  the repository** (37 files: launchers, tools, README, images). No test vectors.

#### ChipmunkRing (arXiv 2510.09617) [A]
- D. A. Gerasimov (Cellframe), "ChipmunkRing: A Practical Post-Quantum Ring Signature Scheme for
  Blockchain Applications", arXiv 2510.09617v1, 2025-09-17 (https://arxiv.org/abs/2510.09617).
  **Preprint only**, single author, part of Cellframe development [A].
- Sender only; optional linkability tag; Ring-LWE, 112-bit PQ target [A 3.1, 4.2.3, 7.1]. Theorems
  1-2 are half-page sketches (anonymity in the ROM) [A 6.2]. Quoted, not evaluated: "Acorn
  Verification" replaces Fiat-Shamir and recomputes SHAKE256^iter over the input including
  per-participant randomness [A 4, Algorithm 4], while the anonymity proof argues via "the
  Fiat-Shamir challenge" [A Theorem 2]; Tables 5 and 6 label the same values as verification and
  signing times.
- Ring 2: 20.5 KB, sign 1.114 ms, verify 0.706 ms; ring 64: 279.7 KB [A Table 5].
- Code [G]: https://github.com/demlabs-cellframe/dap-sdk, branch `feature/chipmunk-ring`
  (2026-07-22); the paper's test directory does not exist there; `CHIPMUNK_RING_REDESIGN.md`
  describes a different, logarithmic design; no KAT files; no top-level LICENSE file.

#### Other
- Attacks/errata on MatRiCT, MatRiCT+, SMILE: none found (SRCH). None of the uploaded PDFs has an
  errata section; MatRiCT's n_s = 1 note (1.3) and MatRiCT+'s denial-of-spending note (1.4) are
  self-reported.
- ePrint 2020/1121, Torres, Steinfeld, Sakzad, Kuchta (L2RS-CS): Preprint; ROM, Module-SIS [E]
  (https://eprint.iacr.org/2020/1121).

## 2. Lattice / PQ (linkable) ring signatures and one-out-of-many proofs (sender)

### 2.1 Esgin et al., short lattice one-out-of-many proofs
- Citation: M. F. Esgin, R. Steinfeld, A. Sakzad, J. K. Liu, D. Liu, "Short Lattice-based
  One-out-of-Many Proofs and Applications to Ring Signatures", ePrint 2018/773, "Published
  elsewhere. Major revision. ACNS 2019", note "The full version of the paper accepted to ACNS 2019"
  [E] (https://eprint.iacr.org/2018/773). LNCS 11464, pp. 67-88 [X: SMILE proceedings PDF reference
  list; SMILE full version ref. [17], ePrint 2021/564 full text, p. 30].
- Hides: sender. Logarithmic communication, no trusted setup [E].
- Assumption: "(module) lattices", "post-quantum lattice assumptions" [E]. Precise: not verified
  (PDF, not uploaded). MatRiCT names its CRYPTO 2019 successor [7] as the state of the art "from
  standard lattice assumptions (in the random oracle model)", one "which uses the same blueprint in
  [8]", [8] being this paper [F: ePrint 2019/1287 full text, p. 2].
- Sizes: ring of 1 billion at 128-bit security: 3 MB, versus 216 MB for Libert et al. [E]. The
  "Esgin et al." row of SMILE's Fig. 2 is **not** this paper: it cites MatRiCT ([13] in the
  proceedings [P], [18] in the full version [F: ePrint 2021/564 full text, p. 9, Fig. 2]).
- Linkability, proof model / location: not verified (PDF, not uploaded).
- Reference implementation, test vectors: not found (SRCH).

### 2.2 Raptor
- Citation: X. Lu, M. H. Au, Z. Zhang, "Raptor: A Practical Lattice-Based (Linkable) Ring
  Signature", ePrint 2018/857 (https://eprint.iacr.org/2018/857). The ePrint landing page says
  "Preprint. MINOR revision." [E]; venue ACNS 2019, LNCS 11464, pp. 110-130 [X: SMILE proceedings
  PDF reference 15; SMILE full version ref. [22], ePrint 2021/564 full text, p. 30]. The Raptor PDF
  prints no venue or date (NIP).
- Hides: sender; the linkable variant is ONE-TIME linkable: "the public key for a signer is only
  supposed to use once" [F: ePrint 2018/857 full text, p. 20, Sec. 6.2].
- Assumption: standard-lattice version SIS/ISIS; NTRU version ("Raptor") decisional NTRU, R-SIS and
  R-ISIS, ring Z_q[x]/(x^n + 1), n in {512, 1024}, q = 12289, preimage sampler from Falcon [F:
  ePrint 2018/857 full text, pp. 8-9 Defs. 2-6, pp. 13-14 Sec. 4, p. 23]. Raptor-512: "114 bits
  security against classical attackers, and 103 bits security against quantum attackers" [F: p. 14,
  Sec. 5]. Caveat printed: Falcon's parameters do "not support GPV's security proof" [F: p. 9].
- Proof model: ROM ("provably secure in random oracle model", Theorems 3-5); QROM not claimed for
  Raptor [F: p. 1, p. 4, pp. 26-29]. Proofs: Theorem 3 (anonymity), 4 (linkability, forking lemma),
  5 (non-slanderability), unforgeability via Theorem 2, Appendix 6.7 pp. 23-30; the plain ring
  signature's proof is omitted "due to page limitation" [F: p. 12].
- Sizes, paper: Table 1(b) linkable Raptor-512: 7.8 / 14.2 / 64.8 KB for 5 / 10 / 50 users, PK 0.9
  KB, SK 9.1 KB; Table 1(a) Raptor-512: 6.3 / 12.7 / 63.3 KB [F: ePrint 2018/857 full text, p. 4,
  Table 1]. "roughly 1.3 KB per user" [E][F: p. 1, p. 14]; linear in ring size [F: p. 5, Table 2].
  **The README's signature sizes differ**: 8.8 / 16 / 73.8 KB [G].
- Timings, paper Table 1(b): sign 10.7 / 17.4 / 61 ms, verify 5.2 / 11 / 50 ms, keygen 57 ms, "a
  typical laptop with an Intel 6600U processor" [F: p. 4, p. 14]; the README gives the same timings
  without hardware [G].
- Reference implementation [G]: https://github.com/zhenfeizhang/raptor (owner name matches the third
  author). The paper does not link it: "Our source code is available at [6]", with [6] "Anonymous.
  Raptor source code. online. available from TBD" [F: p. 14, p. 15]. C, GPLv3 (GPLv3.txt), last
  commit 2020-07-17, README "Prototype", "non-audited (use at your own risk!!!)". README "Is Raptor
  Patented?": NTRUSIGN "(expiring 2021(?))"; three further patent lines and the commercial-terms
  paragraph are struck through.
- Test vectors: none; `test.c` uses `randombytes` [G]; none in the paper.

### 2.3 Calamari and Falafl
- Citation: W. Beullens, S. Katsumata, F. Pintore, "Calamari and Falafl: Logarithmic (Linkable) Ring
  Signatures from Isogenies and Lattices", ePrint 2020/646 (landing page: "Preprint. MINOR
  revision.") [E] (https://eprint.iacr.org/2020/646); ASIACRYPT 2020 [C:
  https://iacr.org/cryptodb/data/conf.php?year=2020&venue=asiacrypt]; LNCS 12492, pp. 464-492 [X:
  SMILE reference 4]; DOI 10.1007/978-3-030-64834-3_16 [C paper page
  https://iacr.org/cryptodb/data/paper.php?pubkey=30695]. The PDF prints only "29th May 2020", no
  venue (NIP) [F: ePrint 2020/646 full text, p. 1].
- Hides: sender, linkable variants "almost as efficient as the non-linkable variant" [E][F: p. 1].
- Assumption: Falafl: MSIS and MLWE (Theorems 5.7, 5.8), ring Z_q[X]/(X^256 + 1), q = 8380417,
  Dilithium "medium" parameters, NIST level I; Calamari: GAIP_p and sdCSIDH_p (Theorem 5.1) on
  CSIDH-512, 128 classical and about 60 quantum bits; both linkable schemes also need a
  collision-resistant HColl (Theorem 4.7) [F: ePrint 2020/646 full text, p. 26, p. 27, p. 30, p.
  32].
- Proof model: ROM (p. 5; Theorem 4.7 p. 26); QROM not discussed. Linkable ring signature proof in
  Appendix A.4 (pp. 37-40); plain ring signature only a proof sketch (Appendix A.1, pp. 35-36) [F].
- Sizes/timings: ring of 8: Calamari 5.5 KB, 79 s signing; Falafl 30 KB, 90 ms signing [E][F: p. 1].
  Table 1: Falafl 29 KB at N = 2, Calamari 3.5 KB at N = 2 and 5.4 KB at N = 2^3 [F: ePrint 2020/646
  full text, p. 3, Table 1]. Falafl is smaller than "state-of-the-art schemes" only for N above
  about 1024 [E][F: p. 1].
- Implementation [G]: https://github.com/WardBeullens/Calamari-and-Falafl, URL printed in the paper
  [F: p. 32, Sec. 6.1]. C with Makefile targets `test_rs_lat`, `test_lrs_lat` etc.; last commit
  2020-05-28; **no top-level LICENSE file** (only XKCP's). `LatticeAction/test/test_vectors.c` is a
  Dilithium-style self-test with a fixed seed, not ring-signature KATs.

### 2.4 SALRS: lattice linkable ring signature with stealth addresses
- ePrint 2019/371 is not SALRS but X. Wang, Y. Chen, X. Ma, "Adding Linkability to Ring Signatures
  with One-Time Signatures", ISC 2019 [E] (https://eprint.iacr.org/2019/371); see 2.5.
- Citation: Z. Liu, K. Nguyen, G. Yang, H. Wang, D. S. Wong, "A lattice-based linkable ring
  signature supporting stealth addresses", ESORICS 2019, pp. 726-746 [X: reference [8] of BaseSAP,
  arXiv 2306.14272, https://arxiv.org/pdf/2306.14272]. MatRiCT+ cites it as [26], the formal lattice
  analysis of its recipient-anonymity method [X: ePrint 2021/545 full text, p. 17]. No ePrint
  version found (SRCH). The UOW repository page was not reachable.
- Hides: payer (sender) and payee (stealth addresses) in one primitive: not verified from the paper
  itself (PUB); the claim rests on the title and on MatRiCT+'s citation.
- Proof model, sizes, implementation: not verified (PUB).

### 2.5 Other ring-signature items
- Wang, Chen, Ma, ePrint 2019/371, ISC 2019 [E]: generic linkability transform; instantiated with
  Esgin et al. (ACNS 2019) and a TCHES 2018 one-time signature: lattice linkable ring signature,
  logarithmic size, "for 2^30 ring members and 100 bit security, our signature size is only 4 MB";
  linkability proved with "a new proof technique in the random oracle model" [E].
- Baum, Lin, Oechsner, "Towards Practical Lattice-Based One-Time Linkable Ring Signatures", ePrint
  2018/107, ICICS 2018, note "bugfix linkability definition" [E] (https://eprint.iacr.org/2018/107).
- Xue, Lu, Au, Zhang, "Efficient Linkable Ring Signatures: New Framework and Post-Quantum
  Instantiations", ePrint 2024/553, "Published elsewhere. Minor revision. ESORICS 2024" [E]
  (https://eprint.iacr.org/2024/553); the PDF prints no venue (NIP). Instantiated with ethSTARK
  adapted to a signature of knowledge, hash Rescue-Prime; signature O(polylog(log n)); public keys
  32 bytes; at 128-bit security: 29 KB at ring 1024 (25 KB at 2^3, 38 KB at 2^14) [E][F: ePrint
  2024/553 full text, p. 1, p. 6 Table 2, p. 16]. Verification: amortized (online) 0.3 ms; single
  verification "128 ms" in the abstract, while Table 4 prints "Total verifying time" 112 ms at 2^10
  [F: p. 1 abstract; p. 20, Table 4]. Proof model ROM, Theorems 1-4 with proofs (pp. 14-16); the
  ZK-ethSTARK signature-of-knowledge properties the theorems assume are described informally (App.
  A, pp. 23-26) and deferred to "the full version [?]" (p. 17) [F]. No code link in the paper.
- Hu, Liu, "Lattice-Based Linkable Ring Signature in the Standard Model", ePrint 2022/101, Preprint
  [E] (https://eprint.iacr.org/2022/101): states that all earlier lattice LRS relied on random
  oracles.
- Lattice Isomorphism Problem (LIP) line: the first pass's SPACE LIP linkable ring signature
  (Springer 10.1007/978-3-031-51583-5_13) is not reachable (PUB). Budroni, Chi-Dominguez, Franch,
  CIC 2025 [E] (https://eprint.iacr.org/2025/516) "reveal[s] a vulnerability in the linkable ring
  signature scheme proposed by Khuc et al. (SPACE 2024)"; Diaz Iribarnegaray, Gregor, L'ecu Leal,
  ePrint 2026/671, Preprint [E]: the LRS of [KTS+24] has "neither the property of correctness nor
  linkability". That Khuc et al. is the Springer chapter: not verified (PUB), topic and year match.
  London, Gardham, Dragan, "RingSLIP", SECRYPT 2026 [E] (https://eprint.iacr.org/2026/889):
  linkable, logarithmic, 46 KB for 4096 members, on HAWK.

## 3. Commitments, range proofs and general lattice ZK (amount)

### 3.1 BDLOP commitments
- Citation: C. Baum, I. Damgard, V. Lyubashevsky, S. Oechsner, C. Peikert, "More Efficient
  Commitments from Structured Lattice Assumptions", ePrint 2016/997, "Published elsewhere. 11th
  Conference on Security and Cryptography for Networks (SCN 2018)" [E]
  (https://eprint.iacr.org/2016/997); pages 368-385 [X: SMILE reference 3; LaZer ref. [5], ePrint
  2024/1846 full text, p. 13]. The PDF prints no venue, date or pages (NIP).
- Role: additively homomorphic commitment with ZK proofs of opening (Fig. 4), of a linear relation
  (Fig. 5) and of a sum [E][F: ePrint 2016/997 full text, p. 1, pp. 15-17]. No range proof; no
  amount, transaction or ring-signature notion in the paper [F].
- Assumption: Module-SIS in l2 (binding, Lemma 7) and Module-LWE in l_inf (hiding, Lemma 6) over R_q
  = Z_q[X]/(X^N + 1), N a power of 2 [F: ePrint 2016/997 full text, p. 3, pp. 6-7, p. 11, p. 12].
  Note: Section 1.2 of the same PDF says the reverse ("binding ... relies on the Module-LWE
  assumption, while the hiding is based on ... Module-SIS"); the lemmas and Section 4.3 contradict
  that sentence [F: p. 2 against pp. 11-13]. Statistically hiding or binding variants possible, most
  efficient when both are computational; about 4x smaller proof and 6x smaller commitment than
  Benhamouda et al. [E][F: p. 1, p. 13].
- Proof model: only the interactive Sigma-protocols are proven (Lemma 8: completeness, special
  soundness, honest-verifier ZK for non-aborting transcripts, pp. 15-16); Fiat-Shamir is named as
  the standard conversion, no ROM or QROM proof [F: pp. 14-15]. Relaxed opening: the extractor "does
  not guarantee that it will extract f = 1" [F: p. 11].
- Sizes: Table 2, optimal parameters (N = 1024, q about 2^32, k = 3): commitment 8.1 KB, proof 6.6
  KB; statistically hiding: 9 KB / 29 KB; Hermite factor 1.0035, no bit-security level printed [F:
  ePrint 2016/997 full text, p. 22, Table 2; p. 20].
- Reference implementation, test vectors: none in the paper [F]; none standalone found (SRCH);
  "abdlop" code and parameter files exist inside LaZer (`tests/abdlop-test.c`) [G], and LaZer uses
  the ABDLOP commitment of LNP22 [F: ePrint 2024/1846 full text, p. 2, p. 6].

### 3.2 Attema-Lyubashevsky-Seiler, product proofs
- "Practical Product Proofs for Lattice Commitments", ePrint 2020/517, "Published by the IACR in
  CRYPTO 2020" [E] (https://eprint.iacr.org/2020/517) [C]. Multiplicative proof 9 KB versus 7 KB for
  proving knowledge of the committed values; works over rings where X^d+1 splits into low-degree
  factors (useful for range proofs) [E].

### 3.3 LNS21, one-time commitments
- V. Lyubashevsky, N. K. Nguyen, G. Seiler, "Shorter Lattice-Based Zero-Knowledge Proofs via
  One-Time Commitments", PKC 2021 [E] (https://eprint.iacr.org/2020/1448) [C]. About 30% smaller
  than prior proofs of "a bit under 50KB" for knowledge of short s with As = t [E]. Range-proof
  sizes: not verified (PDF).

### 3.4 LNP22
- Citation: V. Lyubashevsky, N. K. Nguyen, M. Plancon, "Lattice-Based Zero-Knowledge Proofs and
  Applications: Shorter, Simpler, and More General", CRYPTO 2022 [E]
  (https://eprint.iacr.org/2022/284) [C] [F: ePrint 2022/284 full text, p. 1, "full version of the
  paper presented at CRYPTO 2022"]; LNCS 13508, pp. 71-101 [X: LaZer NIST preview writeup reference
  [LNP22], and LaZer ref. [28], ePrint 2024/1846 full text, p. 13]; DOI 10.1007/978-3-031-15979-4_3
  [X: LaZer NIST writeup] (NIP). The name "Lantern" does not occur in the PDF; it comes from
  2024/457.
- Assumption: Module-SIS and Module-LWE [E][F: p. 14, Sec. 2.5], plus Extended-MLWE for
  zero-knowledge (Def. 2.13 p. 15; Theorems 4.2 p. 24, 4.5 p. 33), assumed "almost as hard as plain
  MLWE" [F: p. 48].
- Proof model: ROM, knowledge soundness Theorem B.7 (Appendix B, pp. 66-77); QROM not discussed [F:
  ePrint 2022/284 full text, pp. 66-67, p. 77].
- Contribution: direct l2-norm proofs without l_inf equivocation or CRT conversion; a "cheap,
  approximate range proof" lifts the proof from Z_q to Z [E][F: p. 1]. That approximate range proof
  is a norm-bound tool (Proposition 5.1, pp. 38-39); the paper gives **no range proof for 64-bit
  amounts** and no transaction construction [F].
- Sizes: MLWE-secret proof 14.4 KB (Fig. 12, p. 51; Table 2 p. 10: 14 KB vs 33 KB before);
  verifiable encryption 19.0 KB (Fig. 14, p. 54); group signature 92 KB vs 203 KB (Fig. 16 p. 58,
  Table 1 p. 9) [F: ePrint 2022/284 full text]. No timings.
- Code: the paper points only to SAGE parameter scripts, https://github.com/khalvador/LBZKP [F: p.
  50] (not cloned). Implementation paper: L. Heimberger, F. Lugstein, C. Rechberger, "Studying
  Lattice-Based Zero-Knowlege Proofs: A Tutorial and an Implementation of Lantern", ePrint 2024/457,
  Preprint [E] (https://eprint.iacr.org/2024/457): SageMath notebook, "not optimized for
  performance", 35 s for a Module-LWE secret proof [E]; notebook repository not located (SRCH). Also
  implemented in LaZer: "The ZK protocol of [28], which we implemented" [F: ePrint 2024/1846 full
  text, p. 3, Sec. 1.2].

### 3.5 LaBRADOR
- Citation: W. Beullens, G. Seiler, "LaBRADOR: Compact Proofs for R1CS from Module-SIS", ePrint
  2022/1341 (landing page: "Preprint.") [E] (https://eprint.iacr.org/2022/1341); CRYPTO 2023 [C:
  https://iacr.org/cryptodb/data/conf.php?year=2023&venue=crypto]; Part V, LNCS 14085, pp. 518-548
  [X: LaZer NIST writeup [BS23]; LaZer ref. [9], ePrint 2024/1846 full text, p. 13]; DOI
  10.1007/978-3-031-38554-4_17 [X]. The PDF prints no venue (NIP).
- Assumption: Module-SIS over Z_q[X]/(X^64 + 1), q about 2^32, 128-bit Core-SVP [E][F: ePrint
  2022/1341 full text, p. 6, p. 19 Thm. 5.1, p. 27].
- **Not zero-knowledge**: "we disregard the zero-knowledge property in this work"; ZK is said to be
  achievable by "a simple linear-sized shim protocol", which the paper does not give [F: ePrint
  2022/1341 full text, p. 3]. LaZer: its LaBRADOR path "only supports succinctness, but not yet
  zero-knowledge", ZK "was already described in [9]" [F: ePrint 2024/1846 full text, p. 5, Sec.
  1.4], where [9] is the LaBRADOR paper, whose only ZK statement is the shim sentence above. Both
  say no zero-knowledge LaBRADOR is given or implemented.
- Proof model: interactive public-coin knowledge soundness (Def. 3.3 p. 7, Thm. 5.1 p. 19, Thms.
  6.2/6.3 pp. 23-26); Fiat-Shamir mentioned only for proof size (p. 21); ROM / QROM not stated [F].
- Sizes: R1CS mod 2^64+1: 47 KB at 2^10 to 58 KB at 2^20 constraints; binary R1CS 49.02-53.84 KB for
  2^20-2^25 [E][F: ePrint 2022/1341 full text, p. 28, Tables 1-2].
- Reference implementation [G]: https://github.com/lattice-dogs/labrador (not named in the paper,
  NIP). C with AVX-512, Apache-2.0, Copyright 2024 IBM Corp., last commit 2024-09-04; Chihuahua,
  Dachshund, Greyhound front ends. LaZer pulls a fork as submodule
  `https://github.com/lazer-crypto/labrador.git` [G].
- Test vectors: none; `test_*.c` programs check random instances [G].

### 3.6 LaZer library
- Citation: V. Lyubashevsky, G. Seiler, P. Steuer, "The LaZer Library: Lattice-Based Zero Knowledge
  and Succinct Proofs for Quantum-Safe Privacy", ePrint 2024/1846, CCS '24, DOI
  10.1145/3658644.3690330 [E][F: ePrint 2024/1846 full text, p. 1, ACM Reference Format]; pp.
  3125-3137 [X: LaZer NIST writeup reference [LSS24]] (NIP: the PDF prints "13 pages").
- Content: C core with Python interface; builds proof systems from user-specified lattice relations
  and norm bounds, using LaBRADOR (succinct, not ZK, see 3.5) or LNP22 (linear-size, ZK); examples:
  anonymous credentials, blind signatures, Kyber1024 key proof, Swoosh key well-formedness, FALCON
  aggregate signature [E][F: p. 1, p. 3, p. 5, p. 12]. Paper sizes: credential 29 KB, aggregate of
  1024 FALCON signatures 73.5 KB [F: p. 4, Tables 1-2].
- Assumption: "The underlying MLWE and MSIS problems are hard" [F: p. 7, Sec. 3.1]. The paper
  contains **no theorem or security proof**; security rests on LNP22 and LaBRADOR [F]. No ROM/QROM
  statement [F].
- Build: Linux AMD64, SageMath 10.2 or newer, gcc or clang; "LaBRADOR requires" AVX-512 while "it is
  possible to build the rest of LaZer separately ... on systems lacking those prerequisites" [F: p.
  5, Sec. 2]. The current README requires AVX-512 and AES-NI and gcc >= 13.2 [G]; the two sources
  differ (paper 2024, repository 2026).
- NIST [N]: Category S6 submission to the First Call for Multi-Party Threshold Schemes, presented
  2026-07-08 at TCPT2 (https://csrc.nist.gov/presentations/2026/tcpt2-2b1). Preview writeup 0.1
  (https://csrc.nist.gov/csrc/media/Projects/threshold-cryptography/documents/TCall-1/LaZer-PW02.pdf):
  M-SIS/M-LWE, Fiat-Shamir, 128-bit target, "We will provide test vectors", MIT license, "no
  patents" known; ML-KEM-1024 secret-key proof 19.01 KB, prover 95.87 ms, verifier 51.23 ms
  (i7-11850H). None of this is in the CCS paper (NIP).
- Repository [G]: https://github.com/lazer-crypto/lazer [F: p. 5]. LICENSE: MIT, "Copyright (c)
  2022-2026 IBM". Last commit 2026-09-28 "Improve build time" (3330e48). README pins commit 10eafeca
  for the CCS 2024 results. Demos: `blindsig`, `kyber1024`, `kyber512-secrets`. Tests are unit tests
  and Sage-generated parameter headers; no statement-proof KAT files [G].

### 3.7 Couteau-Klooss-Lin-Reichle range proofs
- "Efficient Range Proofs with Transparent Setup from Bounded Integer Commitments", EUROCRYPT 2021
  [E] (https://eprint.iacr.org/2021/540) [C]. Under LWE: "improve over the state of the art in a
  batch setting when at least a few dozen range proofs are required" [E]. Single-proof size: not
  verified (PDF).

## 4. Hash-based / symmetric-only options

### 4.1 STARK
- Citation: E. Ben-Sasson, I. Bentov, Y. Horesh, M. Riabzev, "Scalable, transparent, and
  post-quantum secure computational integrity" (the paper's title, its wording), ePrint 2018/046,
  "Preprint. MINOR revision." [E] (https://eprint.iacr.org/2018/046). Peer-reviewed venue: none on
  the landing page; none found (SRCH).
- Assumption: transparent ZK-STARK built on interactive oracle proofs [E]. Precise model: not
  verified (PDF).
- Implementation [G]: https://github.com/elibensasson/libSTARK, LICENSE.md names the four authors
  (2017-2018), last commit 2018-12-11.
- Use for payments: no peer-reviewed STARK-based confidential payment scheme found (SRCH). The
  nearest item is ePrint 2024/553 (2.5), a linkable ring signature, not a payment scheme.

### 4.2 Aurora
- Citation: E. Ben-Sasson, A. Chiesa, M. Riabzev, N. Spooner, M. Virza, N. P. Ward, "Aurora:
  Transparent Succinct Arguments for R1CS", EUROCRYPT 2019 [E] (https://eprint.iacr.org/2018/828)
  [C]. Pages: not verified (PUB).
- Assumption: transparent setup, "plausibly post-quantum secure" (the abstract's wording, not a
  level under rule 5, see 14), lightweight cryptography [E].
- Sizes: O(log^2 n); "less than 130kB even for several million constraints" at 128 bits [E].
- Implementation [G]: https://github.com/scipr-lab/libiop, MIT LICENSE, last commit 2021-05-12.

### 4.3 Ligero
- S. Ames, C. Hazay, Y. Ishai, M. Venkitasubramaniam, "Ligero: Lightweight Sublinear Arguments
  Without a Trusted Setup", ePrint 2022/1608, "Published elsewhere. CCS 2017", DOI
  https://doi.org/10.1145/3133956 [E] (https://eprint.iacr.org/2022/1608). The ePrint text is an
  extended version with "a tighter analysis ... along with formal proofs" [E].
- Assumption: any collision-resistant hash; non-interactive in the random oracle model [E].
- Sizes: communication proportional to the square root of the circuit size; SHA-256 preimage at
  2^-40 soundness error "roughly 35KB" [E].
- Implementation: not located (SRCH).

### 4.4 Zcash-style (Sapling/Orchard)
- ZIP 2005 "Ironwood Quantum Recoverability", Proposed, Consensus, created 2025-03-31, owners D.-E.
  Hopwood and J. Grigg [Z] (https://zips.z.cash/zip-2005): changes note construction so funds can be
  recovered after the discrete-log protocols are disabled; it "does not by itself make the protocol
  secure against quantum adversaries"; Sapling and Orchard note commitments "are not post-quantum
  binding" [Z]. ZIPs 2006 and 2007 are Reserved [Z] (https://zips.z.cash/). The first pass's "NU6.3"
  label is not verified (not in the ZIP text read).
- Relevance: commitment + nullifier + general ZK proof is PQ only if the commitment is PQ binding,
  the proof system is PQ, and note encryption uses a KEM.
- Qnero [G] (https://github.com/DigitalGuards/qnero, MIT, last commit 2026-10-01): pre-alpha testnet
  ("no independent firm has audited"), Plonky2/FRI proofs, Poseidon2 note commitments, ML-KEM view
  keys, 62-bit range checks in circuit; "security levels remain unassessed". Design reference only,
  not a publication.

## 5. Post-quantum stealth / one-time addresses (recipient, view key)

### 5.1 Mikic-Srbakoski-Praska PQ stealth address protocols [A]
- M. Mikic, M. Srbakoski, S. Praska, "Post-Quantum Stealth Address Protocols", ePrint 2025/112
  "Preprint." [E] (https://eprint.iacr.org/2025/112); arXiv 2501.13733v1, 2025-01-23
  (https://arxiv.org/abs/2501.13733). **Preprint only**.
- LWE (FrodoKEM), Ring-LWE (NewHope) and Module-LWE (Kyber) SAPs; MLWE SAP: view key = Kyber
  decapsulation key, stealth key P = XOF(rho_K) * XOF(S) + Decompress(t_K) [A 5, 5.3].
- **No security definition and no theorem for unlinkability or key privacy**; the only theorems are
  Kyber correctness facts [A Theorems 1-2]; no spending signature scheme is specified.
- Scan of 5000 announcements, 1-byte view tag: Kyber512 87 ms, Kyber768 141 ms, Kyber1024 205 ms,
  Frodo640 6444 ms, MacBook Air M2 [A Table 1, 6].
- Code [G]: https://github.com/0x3327/pq-sap, Rust, no LICENSE file, last commit 2025-12-07; forks
  of `pqc_kyber` and a Dilithium crate; unit tests, no KAT files. FIPS 203 conformance of the Kyber
  fork: not verified (not examined).

### 5.2 SPIRIT: post-quantum stealth signatures
- S. Pu, S. A. Thyagarajan, N. Doettling, L. Hanzlik, "Post Quantum Fuzzy Stealth Signatures and
  Applications", ePrint 2023/1148, "Published elsewhere. Minor revision. CCS 2023" [E]
  (https://eprint.iacr.org/2023/1148). The PDF is dated "July 25, 2023" and prints no venue or
  ePrint number (NIP) [F: ePrint 2023/1148 full text, p. 1].
- Hides: the recipient (unlinkable one-time keys, Definitions 4.4 / 4.6); not sender or amount.
  Master tracking key mtk works as a view key and is given to the adversary in the unforgeability
  games [F: p. 1, pp. 12-15, Fig. 8 p. 19].
- Built from: Kyber with the FO transform replaced per [GMP22] ("anonymized" Kyber, ANO-CCA) and
  Dilithium with beta, gamma1, gamma2 doubled; Spirit2/3/5 on Dilithium2/3/5 and Kyber512/768/1024;
  optional Falcon or Dilithium in the key-exposure compiler [F: ePrint 2023/1148 full text, p. 12,
  p. 20, p. 29 App. B, p. 30 Table 3 fn. 1]. So neither component is stock Kyber or Dilithium; FIPS
  203/204 are not mentioned [F].
- Assumptions: MLWE and SelfTargetMSIS (unforgeability, Lemma 6.1 / F.1), ANO-CCA of the KEM plus
  MLWE (unlinkability, Theorem 6.2 / F.3); post-quantum FMD: LWE with super-polynomial
  modulus-to-noise ratio, "a somewhat stronger assumption" [F: pp. 20-22, p. 35, p. 38].
- Proof model: classical ROM for Spirit (Sec. 6.1 p. 20; Lemma F.1 p. 35); QROM not proven, "our
  protocols are highly likely to be secure even in the QROM setting ... We will add this discussion
  to the paper" [F: ePrint 2023/1148 full text, p. 29, App. A]. Standard model for the FMD extension
  (p. 21). Proofs: Appendices E-H, pp. 34-42 [F]. Basic Spirit only in a weak model; leaked one-time
  key reveals msk without the Section 5 compiler [F: p. 1, p. 8].
- Sizes/timings [F: ePrint 2023/1148 full text, p. 30, Table 3], Apple M1, C reference code: Spirit2
  (114 classical / 104 quantum Core-SVP bits): one-time public key 2.08 KB, signature 2.54 KB, sign
  0.208 ms, verify 0.053 ms; with key-exposure security: Dilithium2+Spirit2 6.40 KB,
  Falcon512+Spirit2 4.09 KB. Scalable fuzzy tracking 3.424 ms per message at N = 2^20 clients,
  108.77 ms at 2^30 [F: p. 30, Table 4]. "about 800x" smaller signatures than the prior lattice
  scheme, whose sizes the authors estimated themselves [E][F: p. 1, p. 30].
- Implementation: "https://github.com/sihangpu/SPIRIT", "proof-of-concept", C [F: p. 26 ref. [imp],
  p. 29]. Repository, see 13.3 [G]: exists, no LICENSE file, README "Toy implementation", last
  commit 2023-08-02.

### 5.3 Anonymity of Kyber (needed for a KEM-based view key)
- V. Maram, K. Xagawa, "Post-Quantum Anonymity of Kyber", PKC 2023 [E]
  (https://eprint.iacr.org/2022/1696) [C, marked "Best paper award"]. The PDF prints no venue
  header; it thanks "the anonymous reviewers of PKC 2023" [F: ePrint 2022/1696 full text, p. 30,
  Acknowledgements]. The award is not in the PDF (NIP).
- Result: anonymity of Kyber and derived (hybrid) PKE in the QROM under MLWE (Corollary 1 p. 27,
  Corollary 2 p. 29), conditional on Kyber.PKE being IND-CPA and strongly disjoint-simulatable
  (Lemmas 5, 6, both "informal", p. 15) [F]. IND-CCA: a concrete-bound proof in the QROM (Theorem 1,
  p. 15) that "is non-tight ... square-root advantage loss"; a tight proof would need Kyber's
  injectivity, "out of the scope of our work" [F: p. 6, Sec. 1.2]. Proofs pp. 16-29 [F].
- Object: Kyber as in the NIST round 3 submission v3.02 (Fig. 5) [F: p. 14; ref. 4, p. 30]. FIPS 203
  / ML-KEM is not mentioned; transfer to ML-KEM is the project's own argument (NIP).
- Lists anonymous cryptocurrencies such as Zcash among applications [E][F: p. 2].

### 5.4 SALRS
- See 2.4 (ESORICS 2019, secondary citation only).

### 5.5 Deployed-code item, not a publication
- https://github.com/specter-privacy/specter-sdk [G]: Apache-2.0, last commit 2026-07-05, "pre-1.0".
  Hybrid: ML-KEM-768 viewing key plus **secp256k1** spending key ("ERC-5564-style additive tweak"),
  so spending is not post-quantum. Wraps `pranshurastogi/SPECTER` (not cloned).

## 6. Standardisation status

- FIPS 203 (ML-KEM), FIPS 204 (ML-DSA), FIPS 205 (SLH-DSA): each "Date Published: August 13, 2024"
  [N] (https://csrc.nist.gov/pubs/fips/203/final, /204/final, /205/final); FIPS 203 has a planning
  note (11/17/2025) about an issue to be corrected, FIPS 204 one (07/31/2026) listing "several minor
  issues" [N]. HQC selected, announced March 11, 2025 [N]
  (https://csrc.nist.gov/news/2025/hqc-announced-as-a-4th-round-selection). The NIST threshold call
  has a ZK category (S6); LaZer is a submission [N] (3.6).
- IETF [I], datatracker name search (ring-sig, ringsig, ring+signature, stealth, linkable, lattice,
  zero-knowledge, zkp, sigma, fiat-shamir, range-proof, confidential+transaction, ligero): no draft
  or RFC on ring signatures, RingCT, stealth addresses or lattice ZK / range proofs. Generic layer:
  draft-irtf-cfrg-fiat-shamir-03 and draft-irtf-cfrg-sigma-protocols-03 (IRTF, last updated
  2026-08-16; the latter only "in prime-order elliptic curve groups")
  (https://datatracker.ietf.org/doc/draft-irtf-cfrg-fiat-shamir/,
  https://datatracker.ietf.org/doc/draft-irtf-cfrg-sigma-protocols/). Name search misses drafts with
  unrelated file names.
- ISO: not searched.

## 7. Cross-cutting note on proof models (QROM)

Superseded by section 14 (fourth pass): the general QROM results with their conditions, the level
per candidate under ADR 0001 rule 5, and what is open for L2. One item outside the candidate set
stays here:
- ePrint 2020/1121 (L2RS-CS): ROM, Module-SIS [E] (https://eprint.iacr.org/2020/1121); full text not
  read, so no level is assigned.

## 8. Comparison table

"Size" = a published figure for the smallest set found in a fetched source; for a building block
without anonymity set, its own headline figure. "Impl" / "TV" = implementation / test vectors
located and read [G]. "Venue" states the source tag of the venue claim; (NIP) = the uploaded PDF
does not print it. "Proof model (rule 5)" = the level from section 14 (n.d. = not determinable;
L3 = post-quantum assumptions, classical proof).

| Scheme | Hides | Venue (tag) | Assumption, model (tag) | Size, smallest set found | Impl [G] | Proof model (rule 5) | TV |
|---|---|---|---|---|---|---|---|
| LRCT v1.0 (2018/379) | sender, amount | ACISP 2018 [E] | Ring-SIS [E] | n.v. (PDF) | via chainchip (1.2) | n.d. (no full text) | no |
| LRCT v2.0 (2019/569) | sender, amount | ACISP 2019 [E] (NIP) | M-SIS, ROM [F] | 2-in/2-out ~5.1 MB at w=1, estimate [F p. 18] | chainchip, ISC per README, 2019-02-21, third-party | L3 ROM only | no |
| MatRiCT (2019/1287) | sender, amount | CCS 2019 [E][F p. 1] | M-SIS, M-LWE, ROM, no QROM [F] | 2-in/2-out 110 KB at N=10 [F p. 2 Table 1] | not found (no URL in paper) | L3 ROM only | no |
| MatRiCT+ (2021/545) | sender, amount | S&P 2022 [E][G] (NIP) | M-SIS, M-LWE, Ext. M-LWE, ROM [F] | 2-in/2-out 47 KB impl., 29 KB est., N=11 [F p. 2 Table I] | C, 0BSD, 2025-08-25, authors (13.1) | L3 ROM only | no |
| SMILE (2021/564) | sender (+ amount via MatRiCT framework, unproven) | CRYPTO 2021 [E][C][F] | M-SIS, M-LWE, Ext-MLWE, ROM [F] | ring sig 15.96 KB at 2^5 [F p. 48]; tx 2-in/2-out 24 KB at 2^5 [F p. 12] | not found | L3 ROM only (ring sig.); tx: no proof | no |
| Gao et al. (2021/1674) | sender, amount | PKC 2025 [E][C] (NIP) | M-SIS, M-LWE, ROM [F] | relative only, plots without values [F p. 25] | Go, no license file, "toy", 2023-01-10, authors (13.2) | L3 ROM only (ring sig.); RingCT heuristic | no |
| LACT+ | amount only | MDPI 2023, n.v. (PUB) | Approx-SIS [G] | n.v. (PUB) | C, GPL-3.0, 2023-07-20 | n.d. (no full text) | no |
| Obscura-PQ (arXiv) | sender (fixed denomination) | preprint [A] | Ring-SIS, Ring-LWE, ROM [A] | 32+3072r bytes, r <= 10 [A] | repo lacks prover/contract code | L3 ROM only | no |
| Esgin et al. 1-of-many (2018/773) | sender | ACNS 2019 [E] | module lattice [E] | 3 MB at 10^9 [E] | not found | n.d. (no full text) | no |
| Raptor (2018/857) | sender (one-time linkable) | ACNS 2019 [X] (ePrint: preprint; NIP) | NTRU, R-SIS/R-ISIS, ROM [F] | 7.8 KB linkable at 5 users [F p. 4 Table 1] (README 8.8 KB [G]) | C, GPLv3, 2020-07-17, prototype, not linked by paper | L3 ROM only | no |
| Falafl (2020/646) | sender (linkable) | ASIACRYPT 2020 [C] (NIP) | MSIS, MLWE, ROM [F] | 29 KB at N=2 [F p. 3 Table 1] | C, no license file, 2020-05-28, linked by paper | L3 ROM only | no |
| Calamari (2020/646) | sender (linkable) | ASIACRYPT 2020 [C] (NIP) | GAIP, sdCSIDH (CSIDH-512, ~60 quantum bits), ROM [F] | 3.5 KB at N=2 [F p. 3 Table 1]; 79 s sign at 8 [E] | same repo | L3 ROM only | no |
| Wang-Chen-Ma (2019/371) | sender (linkable) | ISC 2019 [E] | lattice, ROM [E] | 4 MB at 2^30, 100-bit [E] | not searched | n.d. (no full text) | no |
| SALRS | sender + recipient | ESORICS 2019 [X] | n.v. (PUB) | n.v. (PUB) | not found | n.d. (no full text) | no |
| Xue et al. (2024/553) | sender (linkable) | ESORICS 2024 [E] (NIP) | CRHF, Rescue-Prime + ethSTARK SoK, ROM [F] | 25 KB at 2^3; 29 KB at 1024 [F p. 6 Table 2] | no link in paper; not searched | L3 ROM only | no |
| RingSLIP (2026/889) | sender (linkable) | SECRYPT 2026 [E] | LIP / HAWK [E] | 46 KB at 4096 [E] | not searched | n.d. (no full text) | no |
| ChipmunkRing (arXiv) | sender | preprint [A] | Ring-LWE [A] | 20.5 KB at ring 2 [A Table 5] | dap-sdk branch, no license file | L3 ROM only | no |
| BDLOP (2016/997) | amount (commitment) | SCN 2018 [E] (NIP) | M-SIS binding, M-LWE hiding, interactive [F] | commitment 8.1 KB, proof 6.6 KB [F p. 22 Table 2] | inside LaZer only | no oracle-model proof | no |
| LNP22 (2022/284) | ZK framework (no amount proof in paper) | CRYPTO 2022 [E][C][F] | M-SIS, M-LWE, Ext-MLWE, ROM [F] | MLWE-secret proof 14.4 KB [F p. 51] | SAGE scripts (paper); LaZer | L3 ROM only | no |
| LaBRADOR (2022/1341) | succinct PoK for R1CS, **not ZK** | CRYPTO 2023 [C][X] (NIP) | M-SIS, interactive [F] | 47 KB at 2^10 R1CS [F p. 28 Table 1] | C/AVX-512, Apache-2.0, 2024-09-04 | no oracle-model proof | no |
| LaZer (2024/1846) | ZK library | CCS 2024 [E][F] | M-SIS, M-LWE [F][N]; no proof in paper | ML-KEM-1024 key proof 19.01 KB [N]; credential 29 KB [F p. 4] | C/Python, MIT (IBM), 2026-09-28 | no oracle-model proof | promised [N], none in repo |
| STARK (2018/046) | general ZK | preprint [E] | hash / IOP [E] | n/a | libSTARK, 2018-12-11 | n.d. (no full text) | no |
| Aurora (2018/828) | general ZK | EUROCRYPT 2019 [E][C] | hash, plausibly PQ [E] | < 130 kB, millions of constraints [E] | libiop, MIT, 2021-05-12 | n.d. (no full text) | no |
| Ligero (2022/1608) | general ZK | CCS 2017 [E] | CRHF, ROM [E] | ~35 KB SHA-256 preimage [E] | not found | n.d. (no full text) | no |
| PQ SAP (2025/112) | recipient | preprint [E][A] | M-LWE / Kyber, no proof [A] | n/a | Rust, no license, 2025-12-07 | no oracle-model proof | no |
| SPIRIT (2023/1148) | recipient (stealth signature) | CCS 2023 [E] (NIP) | MLWE, SelfTargetMSIS, ANO-CCA (modified Kyber), ROM [F] | signature 2.54 KB, opk 2.08 KB [F p. 30 Table 3] | C, no license file, "toy", 2023-08-02, authors (13.3) | L3 ROM only | no (Kyber KAT generator only) |
| Maram-Xagawa (2022/1696) | recipient (KEM anonymity) | PKC 2023 [E][C] | MLWE, QROM [F] | n/a | none (proof paper) | L1 QROM, direct | no |

(n.v. = not verified; reason in parentheses or in the section.)

## 9. ADR 0001 rules (1), (2) and (5), re-derived from the corrected facts

Rule (1): peer-reviewed venue + security assumption + proof in the paper. Rule (2): authors' test
vectors or reference implementation, absence noted. Rule (5): proof model at L1 "QROM, direct", L2
"QROM via a general result" or L3 "ROM only"; levels and their basis are in section 14. "Venue NIP" =
venue rests on [E]/[C]/[X] because the PDF does not print it (a caveat, not a failure). **No
candidate has test vectors**; every rule (2) "met" rests on an implementation. Format: (1) verdict;
(2) verdict; then one line for rule 5.

Sender and amount:
- LRCT v1.0: (1) not met (no proofs for the RingCT part per ePrint 2019/569 p. 3; v1.0 PDF not
  read); (2) not met (only third-party chainchip code).
  rule 5: not determinable (no full text).
- LRCT v2.0: (1) met with caveat (venue NIP; Prop. 1 inherits from v1.0, Theorem 7 unproven, Lemma 2
  empty; MB-size estimates); (2) not met (paper names no code; third-party code only).
  rule 5: L3 ROM only; described as post-quantum assumptions, classical proof.
- MatRiCT: (1) met with caveat (ROM, QROM disclaimed; "without shuffling"; auditability undefined);
  (2) not met (implementation claimed, no URL, none found).
  rule 5: L3 ROM only; described as post-quantum assumptions, classical proof.
- MatRiCT+: (1) met with caveat (venue NIP, [E] and authors' README; ROM; "without shuffling";
  anonymity proven for Alg. 1 only while Spend uses Alg. 2); (2) met with caveat (authors' C code,
  0BSD; n10/n20/n50 while Table I uses N = 11; random benchmark with pass/fail bit; XKCP,
  `-march=native`).
  rule 5: L3 ROM only; described as post-quantum assumptions, classical proof.
- SMILE: (1) met with caveat (sender part only; the transaction system has no theorem or proof); (2)
  not met (no code named or found).
  rule 5: L3 ROM only for the ring signature, described as post-quantum assumptions, classical
  proof; the transaction system has no proof at all.
- Gao et al.: (1) met with caveat (venue NIP; component proofs only, no theorem for the composed
  RingCT; inconsistent theorem labels); (2) met with caveat (authors' Go code linked from the paper;
  no LICENSE file; README "toy", "do NOT use this in production"; unit tests only).
  rule 5: L3 ROM only for the ring signature, described as post-quantum assumptions, classical
  proof; the composed RingCT is only "heuristic".
- LACT+ (amount only): (1) not met (venue PUB, proof not read); (2) met with caveat (GPL-3.0,
  "educational purposes").
  rule 5: not determinable (no full text).
- Obscura-PQ: (1) not met (preprint); (2) not met (prover and contract code absent).
  rule 5: L3 ROM only; described as post-quantum assumptions, classical proof.

Sender only:
- Esgin et al. 2018/773: (1) not established (PDF not read); (2) not met (no code found).
  rule 5: not determinable (no full text).
- Raptor: (1) met with caveat (venue only [X], ePrint "Preprint"; ROM; plain ring signature proof
  omitted; Falcon parameters outside the GPV proof); (2) met with caveat (repository under the third
  author's name, not linked by the paper ("available from TBD"); GPLv3 prototype; README sizes
  differ from the paper).
  rule 5: L3 ROM only; described as post-quantum assumptions, classical proof.
- Falafl / Calamari: (1) met with caveat (venue NIP; ROM; plain ring signature only sketched;
  Calamari about 60 quantum bits); (2) met with caveat (linked by the paper; no license file).
  rule 5: L3 ROM only for both; described as post-quantum assumptions, classical proof.
- Xue et al. 2024/553: (1) met with caveat (venue NIP; SoK properties deferred to an unreferenced
  full version); (2) not met (no code link in the paper; not searched).
  rule 5: L3 ROM only; described as post-quantum assumptions, classical proof.
- Wang-Chen-Ma 2019/371, SALRS, RingSLIP: (1) not established (no full text); (2) not searched or
  not found.
  rule 5: not determinable (no full text) for all three.
- ChipmunkRing: (1) not met (preprint); (2) not met (named tests absent).
  rule 5: L3 ROM only; described as post-quantum assumptions, classical proof.

Amount and general ZK:
- BDLOP: (1) met with caveat (venue NIP; interactive protocols only; intro p. 2 states the
  assumptions reversed); (2) not met (no code in the paper; LaZer's ABDLOP is a later variant).
  rule 5: no oracle-model proof (interactive only).
- LNP22: (1) met with caveat (ROM; no 64-bit range proof); (2) met with caveat via LaZer (shares
  author Lyubashevsky, implements LNP22 per ePrint 2024/1846 p. 3); the paper itself gives only SAGE
  parameter scripts.
  rule 5: L3 ROM only; described as post-quantum assumptions, classical proof.
- LaBRADOR: (1) met with caveat (venue NIP; knowledge soundness only, not zero-knowledge); (2) met
  with caveat (Apache-2.0, AVX-512 only, not named in the paper).
  rule 5: no oracle-model proof (interactive only).
- LaZer: (1) not met as worded (no theorem or proof in the paper; relies on LNP22, LaBRADOR); (2)
  met with caveat (MIT; AVX-512 per README; test vectors only promised to NIST).
  rule 5: no oracle-model proof (no theorem; inherits LNP22 and LaBRADOR).
- STARK: (1) not met (preprint); (2) met (libSTARK).
  rule 5: not determinable (no full text).
- Aurora, Ligero: (1) venue met, proof not verified (no full text); (2) Aurora met (libiop), Ligero
  not met.
  rule 5: not determinable (no full text) for both.

Recipient side:
- SPIRIT: (1) met with caveat (venue NIP, [E] only; ROM, QROM argued not proven; modified Kyber and
  Dilithium, not FIPS 203/204); (2) met with caveat (authors' C code linked from the paper; no
  LICENSE file; "Toy implementation").
  rule 5: L3 ROM only; described as post-quantum assumptions, classical proof.
- Maram-Xagawa: (1) met with caveat (venue via [E][C] and the PDF's thanks to PKC 2023 reviewers;
  QROM; two "informal" lemmas; round-3 Kyber, not ML-KEM); (2) not applicable (proof paper).
  rule 5: L1 QROM, direct (for round-3 Kyber; ML-KEM transfer is open, section 11 item 10).
- PQ SAP 2025/112: (1) not met (preprint, no security theorem); (2) met with caveat (no license).
  rule 5: no oracle-model proof (no security theorem).

Summary as stated: among sender-and-amount schemes, **MatRiCT+** and **Gao et al.** meet both rules
with caveats, and MatRiCT+ is the only one whose authors' code carries a license. MatRiCT and LRCT
v2.0 meet rule (1) only; SMILE meets rule (1) for its sender part only. Under rule 5 no
sender-and-amount scheme is above L3, and no candidate reaches L2; the only L1 is Maram-Xagawa on
the recipient side (section 14).

## 10. Trade-offs (no recommendation)

- Integrated RingCT (MatRiCT / MatRiCT+ / SMILE / Gao et al.): sender and amount in one design.
  Printed 2-in/2-out sizes: MatRiCT 110 KB at N = 10, MatRiCT+ 47 KB implemented (29 KB estimated)
  at N = 11, SMILE 24-36 KB for N = 2^5 to 2^25 (calculated); Gao et al. relative only. LRCT v2.0 is
  in megabytes (estimated ~5.1 MB at w = 1). Proofs: ROM only everywhere (rule 5: L3, post-quantum
  assumptions, classical proof; section 14), all "without shuffling" where stated; SMILE proves no
  transaction-level property, Gao et al. no property of the composed RingCT. Code: MatRiCT+ (C,
  0BSD) and Gao et al. (Go, no license, "toy") from the authors; none for MatRiCT or SMILE. All use
  one-time accounts with decoys and serial numbers (Monero model); lethenon is account-model, so the
  anonymity-set notion must be mapped (an argued combination under ADR rule 4). None of the four
  proves recipient hiding (MatRiCT p. 3, MatRiCT+ App. C-A, SMILE App. E, Gao et al. p. 24). The
  MatRiCT patent report remains unverified.
- Composition (ring signature + lattice commitment + range/balance proof): e.g. Raptor or Falafl for
  sender, BDLOP + LNP22 for amount. Peer-reviewed parts, but the composition's security argument is
  the project's to write, and linkability must be bound to the commitment. Raptor's size grows
  linearly with ring size and its linkability is one-time. LNP22 gives no 64-bit range proof;
  LaBRADOR is not zero-knowledge, so it cannot hide an amount without a ZK shim that neither paper
  gives.
- General-purpose proof (LaZer): one proof over a custom relation; only its LNP22 path is ZK; most
  actively maintained code found (commit 2026-09-28, MIT, in a NIST call). The paper says only
  LaBRADOR needs AVX-512, the current README requires it. The payment relation would be lethenon's
  own design.
- Hash-only path: the most conservative assumption; building blocks (Aurora, Ligero, 2024/553 for
  linkable ring signatures) but no peer-reviewed payment scheme found.
- Recipient side: the X25519 view key must be replaced with a KEM. Kyber anonymity is proven in the
  QROM for round-3 Kyber (PKC 2023), not for ML-KEM. SPIRIT gives a peer-reviewed PQ stealth
  signature with 2.54 KB signatures, but on modified Kyber and modified Dilithium parameters and
  with a ROM proof. The Mikic et al. SAP is a preprint without security proofs.
- Java: none of the implementations found is in Java (C, C with AVX-512, C/C++, Go, Rust, Python). A
  JVM port, JNI, or a re-implementation validated against the original would be needed; the absence
  of test vectors everywhere means validation would be by differential testing against code that is
  itself unvalidated.

## 11. Open questions

1. Proof model: moved to section 14 (the "not determinable" rows of 14.3, the open L2 conditions
   per candidate, and the general results not read in full, 14.5). Aborts in multi-round FS in the
   QROM: still open after the fifth pass. No general result read in full covers them: 2022/889
   excludes prover aborts and is interactive only, 2017/916, 2023/245, 2023/246 and 2025/985 are
   3-move only, 2021/927 is concrete for 5 rounds only and needs its LinHC; 2023/245 p. 3 says most
   of its results "carry over" to constant-round protocols but states no theorem (14.6) [F].
2. Sizes/timings tables for LRCT v1.0, Esgin et al. (PDF); Gao et al. prints plots only.
3. SMILE and MatRiCT: public code; test vectors for every candidate (SRCH).
4. MatRiCT patent: number, jurisdictions, status (no patent source fetched).
5. Precise hardness problems and parameters for Esgin et al. 2018/773 (PDF).
6. LACT+ venue (PUB: mdpi.com unreachable).
7. ePrint 2024/457 (Lantern) notebook repository (SRCH); LNP22's SAGE scripts
   (github.com/khalvador/LBZKP) not cloned.
8. SALRS: everything except title, authors, venue, pages (PUB; no ePrint version found).
9. Whether Khuc et al. (SPACE 2024), shown broken by ePrint 2025/516 and 2026/671, is the Springer
   chapter the first pass listed (PUB).
10. Whether Maram-Xagawa's Kyber anonymity carries over to FIPS 203 ML-KEM: the PDF does not address
    it; a project argument (ADR rule 4).
11. Whether SPIRIT's doubled Dilithium parameters and [GMP22]-modified Kyber can be mapped onto FIPS
    204 / FIPS 203 implementations (not addressed in the paper).
12. Whether `0x3327/kyber` implements FIPS 203 ML-KEM or round-3 Kyber (not examined).
13. Exact MatRiCT+ factors in its first version (first pass: "2-17x / 3-8x" for 2021-04-27)
    (old-version page 403; the uploaded PDF has no version line).
14. Known attacks or errata on the lattice RingCT schemes: none found (SRCH).
15. ISO drafts (not searched); IETF titles and abstracts (only file names searched).
16. Obscura-PQ code: whether the missing directories exist elsewhere.
17. Whether chainchip's LRCT code implements the v2.0 range proof (code not audited).
18. Whether the MatRiCT+ code (parameter constants checked) and the Gao et al. code (not checked)
    implement the papers (code read, not audited, not built).

## 12. Corrections from the full-text check

One line each: survey said / paper says / location (all locations are in the uploaded full
texts, tag [F]).
1. MatRiCT 110 KB "at N = 2^5 [SMILE Fig. 3]" as MatRiCT's figure / MatRiCT prints 110 KB at anonymity level 1/10; 2^5 is SMILE's plot reading / ePrint 2019/1287 p. 2 Table 1, p. 14 Fig. 1; ePrint 2021/564 p. 12 Fig. 3.
2. First pass "110 KB at 1/11" as unverifiable / it is what MatRiCT+ prints for MatRiCT; MatRiCT itself says 1/10 / ePrint 2021/545 p. 2 Table I, p. 16 Table IX.
3. MatRiCT "Table: not verified" / Table 2 at 3 GHz, 2-in/2-out 1/10: 375 ms generation, 23 ms verification / ePrint 2019/1287 p. 2.
4. MatRiCT hides sender and amount, recipient unstated / recipient anonymity explicitly not covered / ePrint 2019/1287 p. 3 Sec. 1.1.
5. MatRiCT+ sizes "relative only", 47/29 KB not verified / 47 KB (implementation) and 29 KB (compression estimate) at 2-in/2-out, 1/11 / ePrint 2021/545 p. 2 Table I, p. 1.
6. MatRiCT+ "Reference implementation: not found (SRCH)" / code URL printed: gitlab.com/raykzhao/matrict plus / ePrint 2021/545 p. 13 fn. 10.
7. Gao et al. "no public repository found" / Golang reference implementation at github.com/GoldSaintEagle/RingCT_Implementation / ePrint 2021/1674 p. 25 fn. 7.
8. Section 10 "no public code from the authors" for the integrated RingCT group / false for MatRiCT+ and Gao et al. / ePrint 2021/545 p. 13; ePrint 2021/1674 p. 25.
9. LRCT v2.0 title "... Multiple Input and Output Wallets" / "... Multiple Input and Multiple Output Wallets" / ePrint 2019/569 p. 1.
10. LRCT v2.0 assumption "lattice, precise not verified" / Module-SIS only, n = 1024, log q = 196, lambda = 100, ROM / ePrint 2019/569 p. 3 Def. 1, p. 17.
11. LRCT v2.0 sizes "not verified" / 2-in/2-out ~5.1 MB at w = 1, ~8 MB at w = 5 (estimates) / ePrint 2019/569 p. 18 Table 2.
12. Section 9: proof in the paper "for two items only" / proofs also in LRCT v2.0, MatRiCT, MatRiCT+, SMILE (full version), Gao et al., Raptor, Calamari/Falafl, BDLOP, LNP22, LaBRADOR, Maram-Xagawa, SPIRIT, Xue et al. / see 9 for locations.
13. SMILE proof "not in the proceedings, not verified" / Theorems B.1 and C.4 in the full version, ROM; the transaction system has no theorem / ePrint 2021/564 pp. 34-46, pp. 51-57.
14. SMILE assumption "M-SIS, M-LWE" / also Extended-MLWE, assumed almost as hard as M-LWE / ePrint 2021/564 p. 32 Def. A.4, p. 34, p. 43, p. 48.
15. Esgin et al. 2018/773 "SMILE's Fig. 2 also lists Esgin et al." / that row cites MatRiCT ([13] proceedings, [18] full version) / ePrint 2021/564 p. 9 Fig. 2, p. 30; SMILE proceedings Fig. 2.
16. Raptor size "8.8 KB at 5 users [G]" as the published figure / paper: 7.8 KB linkable, 6.3 KB plain, at 5 users / ePrint 2018/857 p. 4 Table 1.
17. Raptor timings "Hardware not stated" / "a typical laptop with an Intel 6600U processor" / ePrint 2018/857 p. 14 Sec. 5.
18. Raptor linkability (unqualified) / one-time linkable / ePrint 2018/857 p. 20 Sec. 6.2.
19. Falafl "30 KB at ring 8" as smallest set / 29 KB at N = 2 / ePrint 2020/646 p. 3 Table 1.
20. Calamari "5.5 KB at ring 8" as smallest set / 3.5 KB at N = 2 (5.4 KB at N = 8 in Table 1) / ePrint 2020/646 p. 3 Table 1.
21. Falafl assumption "MLWE group action" / MSIS and MLWE; Calamari GAIP and sdCSIDH, about 60 quantum bits / ePrint 2020/646 pp. 27, 30, 32.
22. BDLOP size "n/a" / commitment 8.1 KB, proof 6.6 KB (optimal parameters) / ePrint 2016/997 p. 22 Table 2.
23. BDLOP assumptions only "as used in SMILE" / M-SIS binding (Lemma 7), M-LWE hiding (Lemma 6) in the paper itself; its intro states the reverse / ePrint 2016/997 pp. 11-12 against p. 2.
24. LNP22 size "n/a" / MLWE-secret proof 14.4 KB, verifiable encryption 19.0 KB, group signature 92 KB / ePrint 2022/284 pp. 51, 54, 58.
25. LNP22 role "amount (ZK framework)", range-proof sizes pending / no 64-bit amount range proof in the paper; approximate range proof is a norm tool / ePrint 2022/284 pp. 38-39.
26. LaBRADOR "general ZK" / not zero-knowledge, ZK disregarded / ePrint 2022/1341 p. 3.
27. LaZer "needs AVX-512" / only the LaBRADOR part requires it (README now requires it overall) / ePrint 2024/1846 p. 5 Sec. 2.
28. LaZer under rule (1) as passing / the paper has no theorem or proof / ePrint 2024/1846 (whole text).
29. SPIRIT assumption "Dilithium + Kyber" / MLWE, SelfTargetMSIS, ANO-CCA of a modified Kyber, Dilithium with doubled parameters, ROM / ePrint 2023/1148 pp. 20-21, 29.
30. SPIRIT size and implementation "not verified" / "not searched" / signature 2.54 KB, opk 2.08 KB; code at github.com/sihangpu/SPIRIT / ePrint 2023/1148 p. 30 Table 3, p. 26, p. 29.
31. SPIRIT "signing and verification as efficient as 0.2 ms" (abstract) / signing 0.208-0.226 ms in every row; verification 0.053-0.114 ms / ePrint 2023/1148 p. 30 Table 3.
32. Maram-Xagawa "plus tight IND-CCA proofs" / the IND-CCA proof given is non-tight (square-root loss); tight only if injectivity holds / ePrint 2022/1696 p. 6 Sec. 1.2, p. 15 Thm. 1.
33. Xue et al. "single verification 128 ms" / abstract 128 ms, Table 4 112 ms at 2^10 (in-paper discrepancy) / ePrint 2024/553 p. 1, p. 20 Table 4.
34. SMILE 2-in/2-out at N = 1024 (Fig. 3: 27 KB) / text gives 26.49 KB (in-paper discrepancy) / ePrint 2021/564 p. 12 against p. 55.

## 13. Code named by the full texts, checked 2026-10-07 [G]

Cloned outside this repository. Contents treated as data; nothing built or run.

### 13.1 MatRiCT+ (ePrint 2021/545, p. 13 fn. 10)
- `https://gitlab.com/raykzhao/matrict_plus` **exists** (`git ls-remote` HEAD b24f3176): the PDF's
  "matrict plus" is the underscore form; `matrict-plus` and `matrictplus` ask for credentials.
  License: file `LICENSE`, BSD Zero Clause, "Copyright (c) 2025 Raymond K. Zhao" (commit "Add
  LICENSE" 2023-10-27, "Fix year" 2025-07-30). Last commit 2025-08-25 "clang-format"; first
  2021-04-21; 17 commits. Language: C (gcc `-O3 -march=native`, links XKCP), plus SageMath/Python
  estimation scripts.
- README: "implementation source code for the paper", IEEE S&P 2022, DOI
  10.1109/SP46214.2022.9833655; folders `n10`, `n20`, `n50` = anonymity levels 1/10, 1/20, 1/50;
  `./ringct` prints cycles for SamMat, Spend, Verify and a correctness bit. `n10` constants Q
  167770241, QHAT 3489661057, D 256, W 44, M = S = 2, R = 64, N_SPENT 10 match the paper (ePrint
  2021/545 pp. 12-13) except that its size table uses N = 11.
- Test vectors: none; `test.c` runs 1000 randomized rounds; no KAT, `.rsp` or stored outputs.

### 13.2 Gao et al. (ePrint 2021/1674, p. 25 fn. 7)
- `https://github.com/GoldSaintEagle/RingCT_Implementation` **exists**. **No license file** (15
  tracked files). Last commit 2023-01-10 "add test case"; first 2021-03-29; 34 commits. Go (no
  `go.mod`), ring arithmetic from `github.com/dedis/lago`.
- README: "a **toy** implementation of our work under MatRiCT (Matric+) and MatRiCT+ (Matric+_plus).
  Please do **NOT** use this in production environment."; balance proof limited to small
  input/output counts; documents a `MulPoly` bug in LaGo. A tracked `README.md.orig` contains
  unresolved merge-conflict markers.
- Test vectors: none; `*_test.go` unit tests, of which `RingMatrix_test.go` checks fixed expected
  ring-matrix arithmetic only.

### 13.3 SPIRIT (ePrint 2023/1148, p. 26 ref. [imp])
- `https://github.com/sihangpu/SPIRIT` **exists**. **No license file** (148 tracked files); some
  bundled third-party files carry their own notices (e.g. `src/fips202.c` "Based on the public
  domain implementation ...", files under `src/falcon/`), nothing covers the SPIRIT code. Last
  commit 2023-08-02 "Update README.md"; first 2022-06-28; 26 commits; a 409 KB `spirit.zip` (228
  entries incl. `__MACOSX`) is tracked beside `src/`. C99, CMake/Ninja, plus Python
  security-estimate scripts.
- README: "Toy implementation of SPIRIT"; runs SPIRIT with and without KEY_EXPOSURE_SECURITY at
  128/192/256-bit, a Falcon variant, post-quantum FMD and scalable fuzzy tracking.
- Test vectors: none for SPIRIT. `src/kyber/ref/` holds the upstream Kyber KAT generator programs
  `PQCgenKAT_kem.c` and `test_vectors.c`, no stored outputs; `src/test/test_spirit.c` uses
  `randombytes`.

## 14. Proof model per candidate (ADR 0001 rule 5)

Fourth pass, 2026-10-07. Levels as named in ADR 0001 rule 5: **L1** "QROM, direct" (proven in the
scheme's own paper); **L2** "QROM via a general result" (a published general result, cited with
location, and every one of its conditions argued for the scheme); **L3** "ROM only", described as
"post-quantum assumptions, classical proof". Two further entries are used where L3 would overstate:
"no oracle-model proof" (the paper has no oracle-model security statement: interactive only, or no
proof at all) and "not determinable: no full text". A level is assigned per paper as written; an L2
argument the project could build itself is listed as open, not credited.

Sources: general results via their ePrint landing pages [E] (https://eprint.iacr.org/YYYY/NNN),
IACR cryptodb paper pages, written [C N] for https://iacr.org/cryptodb/data/paper.php?pubkey=N, and
for the results read in full the arXiv text [Q] (https://arxiv.org/abs/ID) or, for the six uploaded
on 2026-10-07 (2017/916, 2021/927, 2022/889, 2023/245, 2023/246, 2025/985), the ePrint full text
[F]. Candidates are cited [F] or [A] as in 0.2. Quotes transliterate non-ASCII symbols (Sigma, >=,
q^2, x for the product). "(abstract only)" = the statement rests only on the ePrint abstract. In
14.1, the six rows marked "ePrint full text [F]" end with three verdicts from the full text:
aborts, multi-round, extraction.

### 14.1 General results

| ePrint | arXiv (read) | Title (authors) | Venue (tag) | What it gives | Conditions (location) |
|---|---|---|---|---|---|
| 2019/190 | 1902.07556v4, full text [Q] | Security of the Fiat-Shamir Transformation in the QROM (Don, Fehr, Majenz, Schaffner) | CRYPTO 2019 [E][C 29892] | FS of a Sigma-protocol preserves soundness (Cor. 13, p. 11) and proof of knowledge (Cor. 16, p. 12) against quantum provers; EUF-NMA (Thm. 21, p. 13) and sEUF-CMA (Thm. 22, p. 14) signatures; loss O(q^2) multiplicative plus an additive term bounded by 1/(2q abs(C)) (Thm. 8, p. 10) | 3-round Sigma-protocol (Def. 4, p. 8); superpolynomial challenge space (Cor. 13, 16); soundness / PoK of the INTERACTIVE protocol against quantum provers (Def. 9, p. 10; Def. 14, p. 11); Sigma security formalised in the standard model (fn. 17, p. 17); quantum PoK from special soundness: t-soundness plus quantum computationally unique responses (Thm. 25, p. 16; Def. 24, p. 15); sEUF-CMA additionally naHVZK, min-entropy and computationally unique responses as in KLS18 (Thm. 22, p. 14) |
| 2020/282 | 2003.05207v3, full text [Q] | The Measure-and-Reprogram Technique 2.0: Multi-Round Fiat-Shamir and More (Don, Fehr, Majenz) | CRYPTO 2020 [E][C 30375] | multi-round FS preserves soundness and quantum PoK (Cor. 15, p. 12); (s)UF-CMA of multi-round FS signatures, proof sketch (Thm. 23, pp. 17-18); loss factor n!/(2q+n+1)^(2n) plus additive n!/abs(C) (Cor. 13, p. 12), tight up to a factor depending on the number of rounds only (Cor. 19, p. 15) | (2n+1)-round public-coin interactive proof (Def. 9, p. 11); FS as in Def. 11 (p. 11), more of the transcript may be hashed (Remark 12, p. 11); constant-round; soundness / quantum PoK of the interactive protocol (Cor. 15, p. 12); signatures: quantum PoK, completeness, HVZK, unpredictable commitments, superpolynomial challenge space (Thm. 23, p. 17) |
| 2020/1361 | 2010.15103v2, Sec. 1 and 4.1 [Q] | Tight adaptive reprogramming in the QROM (Grilo, Hoevelmanns, Huelsing, Majenz) | ASIACRYPT 2021 [C 31480]; ePrint "Preprint" [E] | UF-CMA0 (= UF-NMA) plus HVZK implies UF-CMA of FS signatures in the QROM; additive loss (3 qs / 2) sqrt((qH + qs + 1) gamma(Commit)) (Thm. 3, p. 13) | HVZK of the identification scheme; UF-CMA0 proven separately, for arbitrary ID schemes via 2019/190 (Sec. 4.1, p. 13); FS with aborts explicitly not covered (p. 5) |
| 2022/270 | 2202.13730, Sec. 3.2 only [Q] | Efficient NIZKs and Signatures from Commit-and-Open Protocols in the QROM (Don, Fehr, Majenz, Schaffner) | ePrint "Preprint" [E]; venue not checked | tight online extractability of FS of commit-and-open Sigma-protocols in the QROM (abstract only) | the response consists of openings of hash commitments (Sec. 3.2, p. 11, Fig. 2); Sigma-protocols "analyzed in the standard model" are "not the scope here" (fn. 7) |
| 2021/280 | 2103.03085, abstract only | Online-Extractability in the QROM (Don, Fehr, Majenz, Schaffner) | EUROCRYPT 2022 [C 31882]; ePrint "Preprint" [E] | tight online extractability of commit-and-open Sigma-protocols in the QROM (abstract only) | commit-and-open structure (abstract only) |
| 2019/699 | 1906.05415, not read | Tight quantum security of the FS transform for commit-and-open identification schemes (Chailloux) | ePrint "Preprint" [E]; venue not checked | tight QROM reduction for C&O identification schemes (abstract only) | commit-and-open structure, "special soundness notions" (abstract only) |
| 2021/334 | 2103.08140, not read | Post-Quantum Succinct Arguments: Breaking the Quantum Rewinding Barrier (Chiesa, Ma, Spooner, Zhandry) | ePrint "Preprint" [E]; venue not checked | Kilian's 4-message argument in the STANDARD model (abstract only); not about FS | collapsing hash (abstract only) |
| 2019/262 | no | Revisiting Post-Quantum Fiat-Shamir (Liu, Zhandry) | CRYPTO 2019 [C 29891]; ePrint "Preprint" [E] | "mild conditions under which Fiat-Shamir is secure in the quantum setting" (abstract only) | not quotable from the abstract |
| 2017/398 | no | Post-Quantum Security of Fiat-Shamir (Unruh) | ASIACRYPT 2017 [E][C 28284] | FS a zero-knowledge simulation-sound proof system "(but not a proof of knowledge!)" (abstract only) | computational zero-knowledge and statistical soundness; signatures need a "dual-mode hard instance generator" (abstract only) |
| 2017/916 | no; ePrint full text [F] | A Concrete Treatment of Fiat-Shamir Signatures in the QROM (Kiltz, Lyubashevsky, Schaffner) | EUROCRYPT 2018 [E][C 28552] | UF-CMA1, UF-CMA and sUF-CMA of FS-with-aborts signatures FS[ID, H, kappa_m] in the QROM, Thm. 3.1 (p. 11); CMA-to-NMA Thm. 3.2 (p. 12) and Thm. 3.3 (p. 14); lossy identification to UF-NMA, Thm. 3.4 (p. 14); Dilithium UF-NMA from MLWE and SelfTargetMSIS, Lemma 4.10 (p. 28) [F]. Thm. 3.2 and 3.3 reported flawed (2023/245 pp. 5-6; 2023/246 p. 4), see 14.2. Aborts: yes (Sec. 3.1, p. 10). Multi-round: no (3-move, Def. 2.2, p. 8). Extraction: no (signature unforgeability only; forking lemma avoided, pp. 2-3, p. 28) | canonical 3-move identification scheme, Def. 2.2 (p. 8); statistical naHVZK, Def. 2.5 (p. 9); alpha bits of min-entropy, Def. 2.6 (p. 9); for NMA lossy keys and eps_ls-lossy soundness, Def. 2.8 (p. 9); for strong unforgeability computational unique responses, Def. 2.7 (p. 9) [F] |
| 2023/245 | no; ePrint full text [F] | A Detailed Analysis of Fiat-Shamir with Aborts (Devevey, Fallahpour, Passelegue, Stehle, Xagawa) | CRYPTO 2023 [E][C 33185] | CMA-to-NMA for FS with bounded aborts in the QROM: Thm. 7 (pp. 33-34, UF-CMA1), Thm. 8 (p. 40, sUF-CMA1), Thm. 9 (p. 42), Thm. 10 (pp. 45-46), Thm. 11 (pp. 51-52); unbounded aborts, Thm. 12 (pp. 53-54); Renyi-divergence versions, Thm. 13, 14 (pp. 56-57) [F]. Reports flaws in 2017/916 Thm. 3.2/3.3 and 2021/927 Lemma 4.6 (pp. 5-6), see 14.2. Aborts: yes (the subject). Multi-round: no (constant-round only as a remark without theorem, p. 3). Extraction: no | 3-round public-coin Sigma-protocol with aborts, Def. 1 (p. 14), p. 15; HVZK including aborting transcripts, Def. 9 (p. 20), or sc-HVZK, Def. 10 (p. 23); commitment min-entropy, Def. 5 (p. 15); (gamma, beta)-correctness, Def. 2 (p. 14), for strong and unbounded; computational unique responses, Def. 6 (p. 15), for strong; "How to obtain NMA security is beyond the scope of this work" (p. 4) [F] |
| 2023/246 | no; ePrint full text [F] | Fixing and Mechanizing the Security Proof of Fiat-Shamir with Aborts and Dilithium (Barbosa et al.) | CRYPTO 2023 [C 33203]; ePrint "Preprint" [E] | EF-CMA from EF-NMA for FS with aborts in the QROM, Thm. 2 (p. 22); ROM, Thm. 3 (p. 22); Dilithium end-to-end only in the classical ROM, Thm. 4 (p. 30) [F]. States that the gap in 2017/916 Thm. 3.2 "potentially affects all FS-based schemes involving rejection sampling", listing LNP22 and BKP20 (Calamari/Falafl) among works that "need to be re-examined carefully" (p. 4). Aborts: yes. Multi-round: no ("three flows", p. 6). Extraction: no | 3-flow commit-challenge-response identification scheme with aborts (pp. 6-7); statistical acHVZK, Def. 1 (p. 7); an event Gamma with abort probability <= p < 1 and E[eps] <= eps over KeyGen, eps the commitment guessing probability of eq. (1) (p. 7), Thm. 2 (p. 22); ordinary unforgeability on a fresh message (p. 11; strong only in fn. 2, p. 4) [F] |
| 2025/985 | no; ePrint full text [F] | Tighter Quantum Security for Fiat-Shamir-with-Aborts and Hash-and-Sign-with-Retry Signatures (Fallahpour, Fehr, Huang) | ePrint "Preprint" [E] | UF-CMA from UF-NMA in the QROM for the generalized FS-with-aborts scheme, Thm. 1 (pp. 9-10), average-key Cor. 1 (p. 10); strong unforgeability only in Remark 2 (p. 11) [F]. Aborts: yes (the subject). Multi-round: no (single r, y, z; p. 8). Extraction: no | the scheme is an instance of Fig. 1 (p. 7): sign repeats r <- D; y := H(r, m); z <- f(r, y) until z != bot, verify z in supp f(r, H(r, m)); statistical acHVZK, Def. 1 (p. 8); p_sk < 1 and guessing probability eps (eq. 1, 2, p. 7); the adversary "cannot influence the number of loop repetitions" (Lemma 3 proof, p. 13); NMA left to the instantiation (p. 1) [F] |
| 2021/927 | no; ePrint full text [F] | A New Simple Technique to Bootstrap Various Lattice Zero-Knowledge Proofs to QROM Secure NIZKs (Katsumata) | CRYPTO 2021 [E][C 31108] | a NEW transform, not plain FS: the Sigma-protocol is augmented with an extractable linear homomorphic commitment (LinHC, Def. 3.1, p. 15) and then FS-compiled; straight-line PoK of the interactive protocol, Lemma 4.3 (p. 28); eu-cma of the FS signature, Lemma 4.6 (p. 30); straight-line PoK of the 5-round [BLS19] protocol, Lemma 5.3 (p. 36); 5-round NIZK sketched only (p. 39) [F]. Lemma 4.6 reported flawed (2023/245 pp. 5-6), see 14.2. Aborts: yes (responses may be bot, Def. 2.1, p. 10). Multi-round: partly (5-round concrete, Sec. 5; general multi-round only informal, p. 7 and Remark 4.5, p. 29; nothing for a growing number of rounds). Extraction: yes, straight-line; for Lyubashevsky-type protocols in a relaxed relation (Lemma 4.3, pp. 27-28) | Sigma-protocol in the CRS model with responses z = beta*e + r (p. 4; Def. 3.1, p. 16); relaxed k-special soundness, Def. 2.4 (p. 11); computational naHVZK, Def. 2.3 (p. 11); zeta-min-entropy, Def. 2.6 (p. 12); LinHC QAnaHVZK and F-almost straight-line extractable, Def. 3.3, 3.4 (pp. 16-17); MSIS, qaMLWE, qaPRF (Lemma 4.6, p. 30) [F]. Applicability to other protocols (Sec. 5.4, pp. 40-42) asserted ("it can be checked", "it is clear"), not proved; "assessment of the concrete security ... as future work" (p. 40) |
| 2022/889 | no; ePrint full text [F] | Quantum Rewinding for Many-Round Protocols (Lai, Malavolta, Spooner) | TCC 2022 [C 32627]; ePrint "Preprint" [E] | post-quantum proof of knowledge of the INTERACTIVE (2t+1)-message protocol, Thm. 2 (p. 12); lattice Bulletproofs, Thm. 4 (p. 23) [F]. No QROM and no FS result: "We stress that all of our results concern the protocol in the interactive setting" (Remark 1, p. 7). Aborts: no (no prover abort in the paper). Multi-round: yes, interactive only. Extraction: yes, interactive only (additive inverse-polynomial loss, Def. 4, p. 11) | recursively k-special sound family, Def. 5 (p. 11); last-round collapsing at every level, Def. 6 (pp. 11-12); extractor polynomial-size only for k = O(1), t = O(log n), Lemma 4 (p. 15) [F] |
| 2019/834 | no | Succinct Arguments in the QROM (Chiesa, Manohar, Spooner) | TCC 2019 [E] | IOP-based SNARGs "with round-by-round soundness are unconditionally secure in the quantum random oracle model" (abstract only) | round-by-round soundness of the IOP (abstract only) |
| 2020/1270 | no | Classical vs Quantum Random Oracles (Yamakawa, Zhandry) | EUROCRYPT 2021 [E] | lifting theorems ROM to QROM for certain schemes and notions, incl. "Fiat-Shamir signatures" (abstract only) | not quotable from the abstract |
| 2024/884 | no | Security of Fixed-Weight Repetitions of Special-Sound Multi-Round Interactive Proofs | Designs, Codes and Cryptography 2025 [E] | fixed-weight repetition of special-sound protocols is knowledge sound; the abstract does not say quantum (abstract only) | special soundness (abstract only) |
| 2024/1724 | no | Straight-Line Knowledge Extraction for Multi-Round Protocols (Rotem, Tessaro) | CRYPTO 2025 [C 35673]; ePrint "Preprint" [E] | a NEW transform (not FS) for multi-round protocols with a QROM proof (abstract only) | not quotable from the abstract |
| 2014/296 | no | Quantum Attacks on Classical Proof Systems (Ambainis, Rosmanis, Unruh) | FOCS 2014 [E] | negative: relative to an oracle, Sigma-protocols, FS and Fischlin's system "are quantum insecure under assumptions that are sufficient for classical security" (abstract only) | n/a |

Fetched and not used: 2021/1543 (FOCS 2022 [E], post-quantum zero knowledge of non-FS protocols)
and 2023/334 (PKC 2023 [E], a different transform in the NPROM).

### 14.2 Key finding: the general results need a premise no candidate proves

- **The premise.** Both results read in full transfer soundness or proof of knowledge from the
  interactive protocol to its FS version only when the interactive protocol ALREADY has it against
  quantum provers. 2019/190, Cor. 13 (p. 11) and Cor. 16 (p. 12), with Def. 9 (p. 10) and Def. 14
  (p. 11) quantifying over "any (quantum polynomial-time/unbounded) adversary A" and asking for "a
  quantum polynomial-time black-box 'knowledge extractor'"; Sigma security is formalised in the
  standard model (fn. 17, p. 17) [Q: arXiv 1902.07556v4]. 2020/282, Cor. 15 (p. 12): "Let Pi be a
  constant-round PCIP that has (statistical/computational) soundness, and/or the
  (statistical/computational) quantum proof-of-knowledge-property ... Then, in the QROM, FS[Pi] has
  ... too" [Q: arXiv 2003.05207v3].
- **The candidates prove the interactive part classically.** Every candidate with a proof proves
  interactive soundness or extraction by classical rewinding or the forking lemma (14.3, Basis
  column). That does not supply the premise; 2014/296 (abstract only) shows that conditions
  sufficient classically are not sufficient against quantum provers in general. Where the scheme's
  own proof extracts a witness (unforgeability, balance), a soundness-only transfer (2019/190 Cor.
  13; 2017/398, abstract only) is not enough: the PoK transfer plus a quantum PoK of the interactive
  protocol is needed.
- **Published routes to the premise.** 2019/190 Thm. 25 (p. 16): "Let Pi be a Sigma-protocol with
  t-soundness for some constant t and with quantum computationally unique responses. Then Pi is a
  computational proof of knowledge as in Definition 14"; Def. 24 (p. 15): the verification predicate
  as a relation "is collapsing from Z to Y x C". For the Lyubashevsky-type lattice protocol with
  aborts this property is reduced to Assumption 27 (the family fA "is collapsing"), an ASSUMPTION,
  giving Cor. 28 (p. 18) "Under Assumption 27, Sig[LatticeSigma] is strongly existentially
  unforgeable in the QROM"; the same page reports weak-collapsingness under LWE from [LZ19] with a
  worse extractor [Q]. 2020/282 gives quantum PoK for multi-round protocols only in a limited form:
  "q2 identification schemes" (5-round, "the second challenge is a single bit", Def. 26, p. 21) via
  q2-extractability plus collapsingness (Def. 27, Thm. 28, Cor. 29, p. 22); no general
  k-special-soundness-to-quantum-PoK theorem is in the paper [Q]. 2022/889 needs a recursively
  k-special sound family and last-round collapsing at every level (ePrint 2022/889 full text,
  p. 11, Def. 5 and 6 [F]), and gives the interactive premise only (see "Interactive only" below).
  **None of the candidate papers states a collapsing-type property.**
- **Aborts.** 2019/190 Remark 6 (p. 8) does not require a Sigma-protocol to be statistically
  correct, which "allows us to include protocols that use rejection sampling"; its FS prover repeats
  until verification passes "(or some bound is reached)" (Sec. 3.2, p. 9) [Q]. 2020/282 does not
  address aborts: the word occurs once (p. 3, about a measurement), Def. 9 states no correctness
  requirement, and whether Remark 6 carries over is not stated [Q]. 2020/1361 excludes FS with
  aborts: "we decided not to further complicate our proof with the required modifications" (p. 5)
  [Q]. The CMA-to-NMA step for FS with aborts is the subject of 2023/245, 2023/246 and 2025/985,
  all three 3-move only (14.1).
- **Flaws in the CMA-to-NMA step of FS with aborts.** 2023/245 locates flaw F1 in "[Lyu12, Lemma
  5.3], [Lyu16, Lemma 4.1], [KLS18, Theorem 3.2], and [Kat21, Lemma 4.6]" and states "Flaws F2 and
  F3 both appear in the QROM analyses of [Kat21, Lemma4.6] and [KLS18, Theorems 3.2 and 3.3]"
  (ePrint 2023/245 full text, pp. 5-6, Sec. 2.1 [F]); KLS18 = 2017/916, Kat21 = 2021/927.
  2023/246 reports the same gap in the proof of KLS18 Thm. 3.2 and says it "potentially affects
  all FS-based schemes involving rejection sampling", naming LNP22 and BKP20 (Calamari/Falafl)
  among works that "need to be re-examined carefully" (ePrint 2023/246 full text, p. 4, Sec. 1
  [F]). The corrected QROM results are 2023/245 Thm. 7-12 (pp. 33-54), 2023/246 Thm. 2 (p. 22) and
  2025/985 Thm. 1 (pp. 9-10) [F]. They concern the signing-oracle simulation of a single-key 3-move
  FS-with-aborts signature, not extraction: none of them gives NMA security or a proof of
  knowledge ("How to obtain NMA security is beyond the scope of this work", 2023/245 p. 4 [F]). A
  candidate whose unforgeability or balance proof extracts a witness gets nothing from them for that
  step.
- **Interactive only.** 2022/889 proves a post-quantum proof of knowledge for interactive
  many-round protocols (Thm. 2, p. 12; lattice Bulletproofs Thm. 4, p. 23) and leaves the FS step
  open: "We stress that all of our results concern the protocol in the interactive setting", and
  extending parallel repetition to the quantum setting is left open, "Note that this required to
  establish that existing lattice-based Bulletproofs protocols can be made non-interactive in the
  QROM via Fiat-Shamir" (ePrint 2022/889 full text, p. 7, Remark 1 [F]). No prover abort occurs in
  the paper (14.1).
- **Straight-line extraction by a modified protocol.** 2021/927 gives straight-line extraction
  for 3-round (Lemma 4.3, p. 28) and 5-round (Lemma 5.3, p. 36) protocols, but only after the
  protocol is augmented with an extractable LinHC (Def. 3.1, p. 15); plain FS of a candidate as
  written does not meet that condition (ePrint 2021/927 full text, p. 15, Def. 3.1 [F]). It claims
  that the range proof of [ESLL19, Theorem 1] and the one-out-of-many proofs of [ESLL19, Theorems 2
  and 3] are compatible with extractable LinHC ("It can be checked", p. 41) and that the
  commitment opening proof of [BDL+18] can be turned into a QROM secure NIZK ("it is clear",
  p. 40), without proof; it leaves "assessment of the concrete security of these other protocols
  as future work" (p. 40) and says the 5-round protocol of [ALS20, Figure 4] is "not clear if it is
  compatible with our current formalization" (p. 42) [F: pp. 40-42].
- **Conditions used in 14.3.** C1 structure fits (Sigma-protocol, 2019/190 Def. 4; constant-round
  public-coin proof with FS as in 2020/282 Def. 9/11); C2 superpolynomial challenge space; C3
  interactive soundness / quantum PoK against quantum provers; C4 interactive security in the
  standard model (2019/190 fn. 17); C5 for CMA-type properties HVZK and the further conditions of
  2019/190 Thm. 22 or 2020/282 Thm. 23, with aborts as above.

### 14.3 Level per candidate

Order as in the section 8 table. No level changed by the six full texts of the fifth pass (14.1);
the open conditions for L2 take them into account.

| Candidate | Level | Basis (location) | Open condition for L2 |
|---|---|---|---|
| LRCT v1.0 (2018/379) | not determinable: no full text | - | full text (PDF) |
| LRCT v2.0 (2019/569) | L3 ROM only | NIZK defined in the ROM, Def. 4 pp. 4-5; "a Fiat-Shamir Non-Interactive Proof of Knowledge in the Random Oracle Model", Prop. 2 p. 10; forking lemma pp. 35, 38 [F] | C1: no interactive protocol whose FS is the scheme (a ring loop of hashes, p. 10), and the loop is not a 3-move ID signature of the kind 2017/916 Def. 2.2 (p. 8) or 2025/985 Fig. 1 (p. 7) require [F]; C3: quantum PoK, which none of the six results of 14.1 gives for plain FS |
| MatRiCT (2019/1287) | L3 ROM only | QROM disclaimed, p. 1 fn. 1; 3-move FS proof, Alg. 8 and 10, pp. 12-13; k'-special soundness p. 15; classical rewinding, Lemma 5.7 p. 16 [F] | C3: quantum PoK of the 3-move protocol (2019/190 Cor. 16 premise; Thm. 25 needs quantum computationally unique responses, Def. 24), for the relaxed relation; 2021/927 (p. 41) covers only a LinHC-augmented [ESLL19], MatRiCT proves "a slightly different relation" (p. 11), the claim is unproved and 2021/927 Lemma 4.6 is reported flawed (2023/245 pp. 5-6) [F]; the 3-move FS-with-aborts results do not help, balance and unforgeability are proven by extraction (Lemma 5.7 p. 16); C2 stated as a number only for the ring-signature parameters (p. 19) |
| MatRiCT+ (2021/545) | L3 ROM only | ROM, p. 1 fn. 1; 5-move (H0, H), pp. 12-13; "standard rewinding argument", Thm. 1 p. 14; 3-transcript extractor, App. E p. 18 [F] | C3: QROM extraction for a 5-move protocol with aborts (2020/282 Cor. 15 premise; Cor. 29 needs a single-bit second challenge, which MatRiCT+ does not have); 2022/889 excludes prover aborts and is interactive only, the four FS-with-aborts results of 14.1 are 3-move only [F]; none of the six covers this |
| SMILE (2021/564) | L3 ROM only (ring signature); transaction system: no proof | multi-round FS per 2020/282 Def. 11, App. C.2 pp. 42-43; "unforgeable in the random oracle model", Thm. C.4 p. 43; rewinding p. 46; QROM not in the text [F] | C3: QROM extraction for m+3 challenge rounds, growing with the ring size (p. 42), with aborts (restart on rejection, p. 43); against "constant-round", loss (2q+1)^(2(m+3)) by Cor. 15; 2022/889 excludes aborts and leaves FS open (p. 7), 2021/927 does not cover a growing number of rounds (p. 7, Remark 4.5 p. 29) [F]; not covered by any of the six |
| Gao et al. (2021/1674) | L3 ROM only (ring signature); RingCT "heuristic" | ROM, Thm. 3 p. 18 (forking lemma); (S, 3)-special soundness, Thm. 1 p. 12; "Fiat-Shamir heuristic", p. 21 [F] | C3: QROM extraction for the 5-move (S, 3)-special-sound proofs with aborts (rejection sampling, Alg. 1 p. 7); 2022/889 excludes aborts, 2021/927 needs a LinHC the scheme does not use [F]; a composed RingCT theorem, even in the ROM |
| LACT+ | not determinable: no full text | - | full text (PUB) |
| Obscura-PQ (arXiv 2608.22645) | L3 ROM only | ROM only, QROM "future work", pp. 7-8 (B3); "In the ROM", Thm. 2 p. 36 [A] | C1: an AOS/Borromean-style challenge chain, not of the form 2019/190 Def. 4 or 2020/282 Def. 11, nor a 3-move FS-with-aborts signature (2017/916 Def. 2.2, 2025/985 Fig. 1) [F]; C3 |
| Esgin et al. (2018/773) | not determinable: no full text | - | full text (PDF); whether it is the [ESLL19] (CRYPTO 2019) of 2021/927's reference list (p. 44), and a proof of 2021/927's compatibility claim (p. 41), which concerns a LinHC-augmented version [F] |
| Raptor (2018/857) | L3 ROM only | ROM, ring-signature proof omitted, p. 12; "General Forking Lemma", p. 28; QROM named only for Falcon's GPV origin, p. 9 [F] | C1: no interactive proof stated (chameleon-hash construction, p. 11); the ring form "m1 xor ... xor m_l = H(...)" (p. 11) is not an instance of 2025/985 Fig. 1 (p. 7), the hash-and-sign-with-retry result [F]; C3; an NMA proof without forking (General Forking Lemma, p. 28) |
| Falafl (2020/646) | L3 ROM only | Sigma-protocol "in the random oracle model", Def. 2.1 p. 5; extractor may output an O-collision, p. 16; FS properties "folklore", sketch App. A.1 (p. 21) [F] | C3: quantum PoK; C4: the Sigma-protocol has an internal random oracle (2019/190 fn. 17), while the FS-with-aborts results are stated for an identification scheme without one (2017/916 Def. 2.2 p. 8; 2023/246 pp. 6-7) [F]; 2022/270's commit-and-open form does not hold (c = 0 response is not an opening, p. 15); 2023/246 (p. 4) lists BKP20 among works to "be re-examined carefully" [F], so the ROM claim is also under question |
| Calamari (2020/646) | L3 ROM only | as Falafl | as Falafl; plus about 60 quantum bits (section 2.3) |
| Wang-Chen-Ma (2019/371) | not determinable: no full text | [E]: ROM | full text (PDF) |
| SALRS | not determinable: no full text | - | full text (PUB) |
| Xue et al. (2024/553) | L3 ROM only | FS "heuristic", p. 9, p. 17; knowledge soundness deferred to [42], p. 26 [F] | round-by-round soundness of the modified ethSTARK IOP for 2019/834 (abstract only); the unreferenced full version; 2019/190 and 2020/282 do not fit an IOP compiled with Merkle commitments, nor do the six results of 14.1 [F] |
| RingSLIP (2026/889) | not determinable: no full text | - | full text (PDF) |
| ChipmunkRing (arXiv 2510.09617) | L3 ROM only | FS replaced, p. 7; QROM only as a bullet without theorem, p. 12; "the random oracle assumption", Thms. 3-5 pp. 11-12 [A] | C1: no FS to transfer |
| BDLOP (2016/997) | no oracle-model proof | interactive 3-move Sigma-protocol with aborts, p. 14; relaxed special soundness, Lemma 8 p. 15; challenge set 2^256, p. 20 [F] | for an FS version: quantum computationally unique responses (2019/190 Thm. 25, Def. 24), a collapsing assumption of the Assumption 27 type that the paper does not make; or a proof of 2021/927's "[BDL+18] ... it is clear" (p. 40) for a LinHC-augmented opening proof, which 2021/927 asserts without proof [F]; the response form z = y + d*r (p. 17) fits 2021/927's z = beta*e + r; the closest candidate to 2019/190 Cor. 28 |
| LNP22 (2022/284) | L3 ROM only | App. B "Security in the Random Oracle Model", pp. 66-67; FS extractor via the classical framework of [AFK21], p. 67; nine-round, "do not appear to be special-sound", p. 66 [F] | C3: a quantum knowledge extractor for a nine-round protocol that is not special-sound (the Thm. 25 and 2022/889 routes start from special soundness; 2022/889 also excludes aborts) [F]; aborts in multi-round FS; loss (2q+1)^8 by Cor. 15; 2023/246 (p. 4) lists LNP22 among works to "be re-examined carefully" [F] |
| LaBRADOR (2022/1341) | no oracle-model proof | interactive knowledge soundness, Def. 3.3 p. 7; rewinding, Lemma 3.7 proof p. 8; FS only as a size, p. 21 [F] | C3: quantum knowledge extractor; recursive k-special soundness and last-round collapsing (2022/889 Def. 5, 6, pp. 11-12) not shown [F]; FS for a growing number of rounds open (2022/889 p. 7), constant-round fails in general (O(log log n) iterations, p. 5) |
| LaZer (2024/1846) | no oracle-model proof | no theorem in the paper [F] | inherits LNP22 and LaBRADOR |
| STARK (2018/046) | not determinable: no full text | - | full text (PDF); 2019/834 full text |
| Aurora (2018/828) | not determinable: no full text | - | full text (PDF); 2019/834 full text |
| Ligero (2022/1608) | not determinable: no full text | [E]: ROM | full text (PDF); 2019/834 full text |
| PQ SAP (2025/112) | no oracle-model proof | no security theorem; Thm. 1 p. 12 is a correctness bound [A] | a security theorem |
| SPIRIT (2023/1148) | L3 ROM only | ROM, Sec. 6.1 p. 20; QROM "highly likely", argued not proven, App. A p. 29; forking lemma avoided, p. 20 [F] | the named route, 2017/916 Sec. 4.5, rests on Thm. 3.2/3.3, reported flawed (2023/245 pp. 5-6); for the corrected 2023/246 Thm. 2 (p. 22) / 2025/985 Thm. 1 (pp. 9-10) [F]: SPIRIT's multi-key EUFCMA w/o-ke game (Fig. 8 p. 19, p. 36) against their single key; acHVZK and commitment guessing probability for the doubled beta, gamma1, gamma2 (p. 20, p. 37); Lemma F.1 restated for a quantum adversary (p. 35); 2020/1270 (Thms. 1.1, 1.2) not read in full |
| Maram-Xagawa (2022/1696) | L1 QROM, direct | IND-CCA, Thm. 1 p. 15, with quantum random-oracle queries; "Kyber.KEM is ANO-CCA secure in the QROM", Cor. 1 p. 27; hybrid PKE, Cor. 2 p. 29 [F] | none for the QROM claim; the object is round-3 Kyber, not FIPS 203 (section 11 item 10) |

### 14.4 Counts

27 rows: **L1 = 1** (Maram-Xagawa); **L2 = 0**; **L3 = 13** (LRCT v2.0, MatRiCT, MatRiCT+, SMILE,
Gao et al., Obscura-PQ, Raptor, Falafl, Calamari, Xue et al., ChipmunkRing, LNP22, SPIRIT); **no
oracle-model proof = 4** (BDLOP, LaBRADOR, LaZer, PQ SAP); **not determinable = 9** (LRCT v1.0,
LACT+, Esgin et al., Wang-Chen-Ma, SALRS, RingSLIP, STARK, Aurora, Ligero). 1 + 0 + 13 + 4 + 9 = 27.
Every L3 scheme is described as "post-quantum assumptions, classical proof". The counts are
unchanged by the fifth pass.

### 14.5 General results not read in full

Their theorem numbers and conditions cannot be quoted until the full text is read (abstract only,
[E]). The six read in full in the fifth pass (2017/916, 2021/927, 2022/889, 2023/245, 2023/246,
2025/985) are no longer in this list:
- 2019/262, Liu, Zhandry, "Revisiting Post-Quantum Fiat-Shamir"
- 2017/398, Unruh, "Post-Quantum Security of Fiat-Shamir"
- 2019/834, Chiesa, Manohar, Spooner, "Succinct Arguments in the Quantum Random Oracle Model"
- 2020/1270, Yamakawa, Zhandry, "Classical vs Quantum Random Oracles"
- 2024/884, "Security of Fixed-Weight Repetitions of Special-Sound Multi-Round Interactive Proofs"
- 2024/1724, Rotem, Tessaro, "Straight-Line Knowledge Extraction for Multi-Round Protocols"
- 2014/296, Ambainis, Rosmanis, Unruh, "Quantum Attacks on Classical Proof Systems - The Hardness of
  Quantum Rewinding"

Downloaded from arXiv, only the abstract or one definition used: 2021/280 (arXiv 2103.03085),
2022/270 (arXiv 2202.13730, Sec. 3.2), 2019/699 (arXiv 1906.05415), 2021/334 (arXiv 2103.08140).

### 14.6 Assumptions and open points

- Assumed: 2019/190 Def. 14 is stated for an arbitrary relation, so a relaxed relation can play the
  role of R; whether the scheme's reduction then still works is the scheme's own ROM argument. This
  is an argument, not a citation.
- Open: whether 2019/190 Remark 6 (aborts allowed) carries over to 2020/282 Cor. 15; 2020/282 does
  not say. 2023/245 says its techniques "also allow to transform a constant-round public-coin
  interactive proof system into a non-interactive one, and most of our results carry over to this
  setup", but states no theorem for it (ePrint 2023/245 full text, p. 3, Sec. 2 [F]); its theorems
  are for 3-round protocols (Def. 1, p. 14) and concern CMA to NMA, not extraction.
- Assumed (fifth pass): a general result applies only to the scheme as written; a version rebuilt
  on 2021/927's LinHC is a different scheme and does not lift the candidate. The results reported
  flawed (2017/916 Thm. 3.2/3.3, 2021/927 Lemma 4.6) are not used as cited for L2; 2017/916 Thm. 3.4
  (lossy identification to NMA) is not in the flaw list of 2023/245 (pp. 5-6), but no candidate
  states lossy keys.
- Open: whether a multi-key version of 2023/246 Thm. 2 / 2025/985 Thm. 1 exists; it is not in the
  six full texts. That is SPIRIT's main L2 gap.
- Open: venues of 2022/270, 2019/699 and 2021/334 (ePrint says "Preprint").

## 15. Phase C pre-check: a post-quantum hidden recipient (2026-10-07), postponed

The maintainer asked for a hidden recipient for ML-DSA-65 accounts based on Maram and Xagawa, as a
new recipient scheme on the test chain. The pre-check found that it cannot be built under ADR 0001
from that paper, and the maintainer postponed the recipient building block (#72). The recipient
stays as it is: `stealth-v2`, Ed25519 one-time keys found with an X25519 view key.

What the pre-check established:

- **What Maram and Xagawa prove.** The anonymity of the KEM in the QROM under MLWE: Theorem 2
  (SPR-CCA, p. 23) and Corollary 1 (ANO-CCA of the KEM, p. 27) [F: ePrint 2022/1696 full text].
  Lemma 6, which the anonymity rests on, has a proof sketch only (p. 15). The scheme analysed is
  Kyber round 3 as in Fig. 5 (p. 14), reference [4] = round-3 submission v3.02 (p. 30); its
  encapsulation hashes m first and derives the key with H(c). The paper does not mention ML-KEM.
- **ML-KEM is not that Kyber.** FIPS 203, Appendix C.1 [N: https://nvlpubs.nist.gov/nistpubs/FIPS/NIST.FIPS.203.pdf]:
  "ML-KEM.Encaps no longer includes a hash of the ciphertext in the derivation of the shared
  secret", and the step m <- H(m) "is not performed in ML-KEM"; the shared key is fixed at 256 bits
  and input checks are added. Whether the anonymity result carries over needs an argument nobody
  has published in the sources read. The JDK 25.0.4.1 lists ML-KEM, ML-KEM-512, -768 and -1024;
  "Kyber" in any spelling throws NoSuchAlgorithmException (measured with a probe on 2026-10-07).
- **No spendable one-time key.** None of the sources read derives, from KEM anonymity, a one-time
  ML-DSA key that only the recipient can spend. A KEM shared secret is known to both sides, so a
  key derived from it alone is spendable by the sender too.
- **SPIRIT, the published construction with that function, is excluded.** Fig. 8 (p. 19) [F: ePrint
  2023/1148 full text]: the recipient decapsulates with the view key and shifts its Dilithium
  master secret, osk = (s1 + s1', s2 + s2'). Its proof is in the classical ROM (Lemma 6.1,
  Theorem 6.2, p. 21; the QROM case only "highly likely", p. 29), so rule 5 level L3; it doubles
  Dilithium's beta, gamma1 and gamma2 (p. 20, p. 29), which rule 8 excludes; its KEM is Kyber with
  a different Fujisaki-Okamoto variant (p. 12, p. 29); and its repository `sihangpu/SPIRIT` has no
  licence file (section 13, f99f1e1), which rule 6 excludes even as a test reference.
- **Licence of the Kyber reference code.** `pq-crystals/kyber`, commit 3edd5af on `main` (FIPS 203
  structure) and 6449083 on branch `round3`: `LICENSE` reads "Public Domain
  (https://creativecommons.org/share-your-work/public-domain/cc0/); or Apache 2.0 License" [G].
  Code of Maram and Xagawa: none found (no link in the paper or on its ePrint page; one web search).
- **Patents.** No patent office record could be opened (patents.google.com HTTP 503,
  worldwide.espacenet.com HTTP 403, 2026-10-07). NIST's licence summary and the Kyber IP statements
  are recorded in ADR 0001 rule 8, which now limits ML-KEM and ML-DSA to their standard parameters
  and records Jintai Ding's 2022 statement on US 9,246,675 as a known, unresolved residual risk.

Not verified: the round-3 specification v3.02 (blocked; v3.0 read, its Algorithms 8 and 9 on p. 10
match Fig. 5); whether the uploaded PDF of 2022/1696 is the 2023-02-13 revision; the validity of
EP 2537284; the exact text of the scanned IP statements; patents on ML-DSA; the JDK's conformance
to FIPS 203 (no test vectors run).

## 16. Phase C pre-check: hidden amounts (2026-10-07), postponed

The maintainer asked for hidden amounts as the next building block, with a candidate that meets
rules 1 and 2. The pre-check found none, and the maintainer postponed the block (#74). The account
model stays; sender and amount stay visible.

What the pre-check established:

- **Account model.** No paper read describes an account balance kept as a commitment and updated in
  place. MatRiCT+ (p. 8), SMILE (p. 10) and Gao et al. (pp. 1-3) spend one-time "accounts" (a key
  and a coin) once under a serial number, the RingCT shape [F]; LACT+ keeps unspent coin outputs
  (section 1.7). How a recipient learns the opening of what it received is outside the schemes:
  MatRiCT+ p. 8, fn. 6, says the openings are "delivered to the recipient(s) privately" [F].
- **Range.** The supply, 1,984,000,000 LETH in eight decimals, is 1.984e17 base units, log2 57.46,
  so it needs 58 bits (`Emission.java:39`, computed); the papers that state a range use 64 bits
  (SMILE p. 10, MatRiCT+ p. 4, Gao et al. p. 1) [F].
- **Candidates against ADR 0001:**
  - LNP22 through LaZer (MIT; bundled HEXL Apache-2.0, needs a NOTICE file): the proof system meets
    rule 1 (CRYPTO 2022, Thm. B.7 p. 77) [F], but there is no published amount scheme on it, the
    LaZer repository (HEAD 3330e48) has no range-proof or transaction demo [G], the proof-model
    level is L3 at best and 2023/246 (p. 4) lists LNP22 for re-examination [F]. Usable only as a
    combination of our own under rule 4: not built without a cryptographer's security argument.
  - LaBRADOR: not zero-knowledge as published (p. 3) [F]; hides nothing on its own.
  - LACT+: GPL-3.0 code (rule 6), venue not verifiable (mdpi.com unreachable).
  - MatRiCT+ amount part: excluded by rule 7 until its patent position is clarified.
  - Gao et al.: no licence (rule 6); no theorem for the composed scheme.
  - SMILE: no proof for the transaction part, no code.
  - STARK, Aurora, Ligero: no payment scheme in the sources read.
- **Open, not blocking:** Couteau, Klooss, Lin, Reichle, ePrint 2021/540 (EUROCRYPT 2021, range
  proofs) and Esgin, Steinfeld, Liu, Liu, ePrint 2019/445 (CRYPTO 2019, lattice range proof, which
  2021/927 p. 41 claims without proof to fit a QROM route) are known from their abstracts only.

Not verified: LACT+'s venue, proof and sizes; every patent register (patents.google.com HTTP 503,
worldwide.espacenet.com HTTP 403); the licence of cpu_features at the commit HEXL pins; whether
LaZer builds without its LaBRADOR submodule; any printed size or timing for a standalone range or
balance proof.
