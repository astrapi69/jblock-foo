## Change log
----------------------

Version 0.3.0 (unreleased)
-------------

CHANGED:

- the main chain, `lethenon-1`, runs the same block limits as the test chain, from height 0 (#109):
  a block's timestamp at most two hours after the verifying node's clock, a block at most 300,000
  bytes. This is a consensus change. The timestamp bound rejects no chain that verified before,
  since the clock only moves on. The size limit rejects a main chain with a block over 300,000
  bytes, which lethenon 0.2.0 could mine: 56 or more ML-DSA-65 transfers, or 1,515 or more
  Ed25519 transfers, in one block paying an Ed25519 miner. Mining on the main chain now carries the
  longest prefix of the waiting transfers that fits, and the rest keeps waiting

FIXED:

- two nodes could hold two connections to each other: a node did not notice that a peer was a
  node it was already connected to, so two nodes that dialled each other kept both connections,
  relayed everything twice and used two of their slots for one neighbour. It showed as a red CI
  run of `PeerExchangeTest` on a slow runner. Now a second connection to a node identity that is
  already a peer is closed, and both ends close the same one: the connection kept is the one
  dialled by the node with the smaller identity, decided from each connection's own direction.
  Behind it was a gap in peer exchange as well: an address left the list of addresses being
  dialled once its socket was open, before its handshake ended, and could be dialled again in
  between. It now counts as busy until the handshake ends (#117)
- a connection that ended because a frame could not be written, or because a peer let its queue of
  1,024 frames fill up, ended without a reason on either side. The node's refusals now name the
  peer and the cause (#101)
- a block the node's own miner built could be too large to fetch: the miner capped blocks at 500
  transfers by count, and 500 ML-DSA-65 transfers make 2,692,122 bytes, so two such blocks did not
  fit in a 4 MiB BLOCKS frame and a node behind never caught up. Blocks are now bounded in bytes,
  and a node answers GET_BLOCKS with as many blocks as fit in one frame (#98)
- admitting a transfer to the pool re-applied every waiting transfer of the same sender, signature
  check included, so filling the pool with 5,000 transfers from one sender did not finish within
  60 s. Admission now keeps per sender what is waiting and checks each signature once (#92)
- a node that was fetching from a peer asked it again for its chain whenever that peer relayed a
  block the node could not place yet, the second answer overtook the first, and the node
  disconnected an honest peer (`BLOCKS with 20 blocks for a request of 4`). Now one
  synchronisation runs per peer, an unknown parent during it asks once more when it ends, and a
  CHAIN nobody asked for disconnects (#85)
- decoding a block, chain or transfer no longer trusts a length prefix: a negative length, or one
  larger than the bytes left, is refused before anything is allocated, and bytes that end early are
  refused as such instead of escaping as BufferUnderflowException. Before, one crafted field asked
  for up to 2 GiB - which matters once the bytes come from a peer (#80)

ADDED:

- Tor, step 2 of ADR 0004 (#116): `node --tx-proxy tor,host:port[,max]` turns on an anonymity zone.
  Onion peers given with `--peer` are reached through Tor and carry transfers only, and a transfer
  that originates on the node - submitted on it, or handed over by a command such as
  `send --node` - goes only to them, or waits for one, and is never sent in the clear. The zone has
  its own node identity, announces no listening port and names only the genesis block, carries no
  chain sync, and passes over blocks a zone peer relays. Transfers from other nodes are relayed as
  before. In the library: `Node.anonymityZone(Outbound, int)` and `Node.anonymousPeers()`. Known
  limits, in the ADR: a node that mines can reveal its own transfers in its own block, and every
  zone peer sees all own transfers
- Tor, step 1 of ADR 0004 (#114): `node --proxy host:port` and `sync --proxy host:port` send every
  outgoing connection through a SOCKS5 proxy such as a Tor daemon, with the peer's host handed over
  unresolved, so nothing about a peer reaches the local DNS. Peers learnt by peer exchange go the
  same way, and so does the genesis bootstrap. Nothing is dialled around a proxy that is given. A
  node with a proxy listens on 127.0.0.1 unless `--bind` says otherwise. v3 onion addresses (56
  base32 characters and `.onion`) are recognised, other names ending in `.onion` are refused, and
  an onion address without a proxy is refused before anything touches the network. In the library:
  `Outbound`, `Node.dialingThrough`, `Node.listen(InetAddress, int)` and overloads of `Sync.once`,
  `Handover.send` and `Bootstrap.genesisFrom` that take the route. Tested against a SOCKS5 server in
  the test sources; a run against a real Tor is ahead (#112)
- a sync that stops (#107): `Sync.once` and `lethenon sync --chain FILE --peer HOST:PORT` bring a
  chain file up to one node's tip, verify every block with the replay a node uses, write the file
  once and only when the chain grew, and stop. A failed sync - an unreachable peer, a refused
  handshake, a peer that breaks off, the time running out - writes nothing and names the peer, the
  height it announced and the height reached. It reads no wallet and asks for nothing but the
  chain. For wallets, scripts and the desktop plugin (astrapi69/mystic-crypt-ui#530)
- a genesis block fixed in the code, the mechanism without the block, the fourth prerequisite for a
  main chain (#104): `ConsensusRules` carries per chain at most one anchor, the block's canonical
  bytes, checked when the table is built; `Replay` rejects a chain that starts elsewhere;
  `Genesis.start` gives the anchor instead of mining one, and `mine` and `node` start chains
  through it; `lethenon genesis` prints a candidate's hash and bytes and writes nothing. No chain
  has an anchor yet: the main chain's block, and the allocation it carries, is the maintainer's
  decision
- peer exchange, the third prerequisite for a main chain (#102): HELLO carries the listening port
  and a random node identity (protocol version 2), after Monero's `my_port` and `peer_id`. A node
  asks listening peers with GET_PEERS, passes on at most 250 addresses of nodes it has itself been
  connected to, keeps Monero's confirmed and heard-of lists (1,000 and 5,000), and dials learnt
  addresses while it has room for outgoing connections. A caller that does not listen is never
  passed on; a connection to the node itself is recognised and that address never dialled again.
  `node --no-discovery` stays with the given peers
- a consensus limit on block size, the second prerequisite for a main chain (#99): on
  `lethenon-test-1` a block larger than 300,000 bytes does not verify, Monero's full reward zone
  taken as a hard limit; `CanonicalEncoding.blockSize` counts it. Mining carries the longest
  prefix of the waiting transfers that fits, and `mine` keeps the rest waiting instead of dropping
  it. The main chain has the same limit since #109
- a consensus bound on timestamps in the future, the first prerequisite for a main chain (#96):
  on `lethenon-test-1` a block whose timestamp lies more than two hours after the verifying
  node's clock does not verify, Monero's `CRYPTONOTE_BLOCK_FUTURE_TIME_LIMIT`. The limit sits in
  `ConsensusRules` as the chain's `BlockLimits`; the main chain has the same bound since #109
- a network for the test chain, seventh building block (#76, #94, ADR 0003): `lethenon node
  --chain <file> --listen <port> [--peer host:port ...] [--mine --wallet <file>] [--for <seconds>]`.
  It serves the chain file, relays and synchronises; with `--mine` it mines on its pool, at most
  500 transfers per block, and starts a test chain on an empty file; without it, an empty file
  takes the genesis block from the first peer that answers, checked against that peer's HELLO, and
  then synchronises block by block. The README shows two nodes, a send through one and a balance
  from the other's copy
- a network for the test chain, sixth building block (#76, #92, ADR 0003): limits. A node opens at
  most 12 connections and accepts at most 16, refusing above that with the reason recorded; a peer
  that does not answer a GET_CHAIN or GET_BLOCKS within 120 s is disconnected, while one with
  nothing asked of it may stay quiet; the pool holds at most 5,000 transfers. ADR 0003 called the
  120 s an idle timeout, which with no keepalive message would have disconnected every honest peer
  on a quiet network; it is the answer timeout Monero's value is
- a network for the test chain, fifth building block (#76, #90, ADR 0003): the shared pool. A node
  serving a chain file writes every chain it adopts and every change of its pool to `<chain>` and
  `<chain>.pending`, and at start offers each waiting transfer again, so a double spend in the file
  is dropped and the file rewritten. `send` and `faucet` take `--node host:port`: the transfer is
  handed to the node over a handshake and one TRANSFER frame, and the command fails unless the
  node's pool file then carries it
- a network for the test chain, fourth building block (#76, #88, ADR 0003): a node follows a fork.
  A peer's chain that leaves the node's below its tip is fetched from the fork point; the node
  switches when the candidate's cumulative work is strictly greater and it verifies whole, and keeps
  its chain on equal work. The transfers of the dropped blocks go back into the pool ahead of the
  waiting ones if they still fit. A fork is followed only if it overtakes within one CHAIN answer,
  500 blocks after the fork point; a deeper one is recorded and not followed, which ADR 0003 now
  states as a reorganisation depth limit
- a network for the test chain, third building block (#76, #84, ADR 0003): a node whose peer
  announces more cumulative work in its HELLO asks for that peer's chain right after the handshake,
  instead of waiting for the next block, and fetches it like any other, each extended chain checked
  whole. A peer with equal or less work is not asked; one that claims more work than it has costs a
  fetch and changes nothing
- a network for the test chain, second building block (#76, #82, ADR 0003): `TransactionPool`
  admits a transfer only if the next block could carry it - chain and schemes admitted, signature,
  nonce exactly the sender's next counting its waiting transfers, balance after them - refuses a
  second transfer with the same sender and nonce as a double spend, keeps the first one seen, and
  re-checks itself on every new tip. Nodes relay blocks and transfers they accept to every peer but
  the sender; a block with an unknown parent makes a node ask for the peer's chain with a locator
  built as Monero's and fetch the missing blocks in batches of at most 20, each extended chain
  checked whole with `Replay.verify`. A peer sending bytes that do not decode, a message out of
  place or a block that does not verify is disconnected. Frames to a peer go through a queue of at
  most 1,024 with a writer thread of its own. A fork below the tip is recorded and not yet followed
- a network for the test chain, first building block (#76, #78, ADR 0003): the package
  `lethenon.transport` speaks TCP in frames of a 4-byte length, a type byte and a payload, at most
  4 MiB, checked before anything is allocated. Nodes connect to a fixed list of peers and shake
  hands with HELLO (magic, protocol version, chain identifier, genesis hash, tip, cumulative work),
  and refuse within 5 seconds anything that is not this protocol version on `lethenon-test-1` with
  the same genesis block. A node does not run on the main chain. `ChainWork` sums 2^difficulty
  over a chain
- the privacy block's architecture (phase B, ADR 0002): a transfer has three building blocks a
  scheme can replace - authorization, recipient, amount - and every scheme carries a permanent,
  versioned identifier. A consensus rule admits each scheme per chain from a block height on, and
  optionally until a later one, so a broken scheme can be switched off without a new chain. Today's
  rule admits exactly what both chains accepted before: ed25519, ml-dsa-65, direct, stealth-v2 and
  plain amounts from height 0; no behaviour changes. No scheme that hides sender or amount exists
  yet (#60)

CHANGED:

- ADR 0001, three rules for what the privacy block may take in: code under a licence compatible
  with MIT, Apache-2.0 included when a NOTICE file carries its notices; copyleft code only as a
  separate program across a process boundary; code without a licence never; and no scheme with a
  known patent valid in the EU, or an unclear patent position, before the maintainer has clarified
  it (#58, #65)
- ADR 0002, how a scheme written in C is integrated: every scheme in pure Java, the authors' C code
  only in the tests, through the Foreign Function and Memory API, as the reference for difference
  tests, beside forgery tests and PIT on the verification logic; parameters only from the paper or
  the reference code (#65)
- `docs/research/pq-privacy-literature.md`, the phase A literature on hiding sender and amount, now
  also checked against the full texts of six general results on Fiat-Shamir in the quantum random
  oracle model. No candidate's proof-model level changes: none of them reaches a quantum random
  oracle proof through a general result (#54, #70)
- ADR 0001 rule 8: ML-KEM and ML-DSA only with the parameters of their standard, from the JDK or
  Bouncy Castle, on the basis of NIST's licence summary; modified parameters and predecessor
  versions such as Kyber round 3 are excluded, and Jintai Ding's 2022 statement on US 9,246,675 is
  recorded as a known, unresolved residual risk (#72)
- a post-quantum hidden recipient is postponed: the one-time destinations stay Ed25519 with an
  X25519 view key. Maram and Xagawa's QROM anonymity result is for Kyber round 3, not ML-KEM, and
  gives no one-time ML-DSA key only the recipient can spend; SPIRIT, which does, is proven in the
  classical random oracle model only, changes Dilithium's parameters and has code without a
  licence. Recorded in the README and in section 15 of the literature (#72)
- hidden amounts are postponed and the account model stays: no published amount scheme meets ADR 0001
  and fits an account model, and a combination of our own over LNP22/LaZer is not built without a
  cryptographer's security argument. README, ADR 0002 and section 16 of the literature now say what
  lethenon is: post-quantum in its authenticity, classical in its hidden recipient, open about
  sender and amount (#74)

Version 0.2.0
-------------

FORMAT:

- Wallets: a wallet file created or restored with 0.2.0 is sealed in the envelope `LETHWF`, and 0.1.0
  cannot open it - it refuses the file as "not a wallet file: it is not sealed with a password the
  way a wallet file is". A wallet file written by 0.1.0 keeps opening and is never rewritten:
  lethenon writes a wallet file only when one is created or restored, and never over an existing
  one (#45)
- Chains: a chain started with `mine --testnet` carries the identifier `lethenon-test-1`, which 0.1.0
  does not know. 0.1.0 refuses such a chain at its genesis block - `balance` and `mine` end with
  "block 0 belongs to chain 'lethenon-test-1'" and exit 1 - and leaves the chain file as it was.
  Main chains (`lethenon-1`) are unchanged in both directions: 0.1.0 replays a main chain 0.2.0
  wrote and mines on it, 0.2.0 replays the result, and a chain written by 0.1.0 is a main chain
  (#50)

ADDED:

- a test chain, `lethenon-test-1`, beside the main chain `lethenon-1`: `mine --testnet` writes its
  genesis block, and from then on the genesis block decides - every block, transfer and sweep
  carries its identifier, the replay refuses a block or a transfer of the other chain and a genesis
  block naming neither, and `--testnet` on a main chain is an error. `Mining.nextBlock` takes the
  identifier for a new chain; without one it follows the genesis block. Chains written by 0.1.0 are
  main chains and replay unchanged (#50)

CHANGED:

- the rule "this project does not write new cryptography" is replaced, not removed, by ADR 0001:
  a new cryptographic construction only after a peer-reviewed publication with its proof, checked
  against the authors' test vectors or a reference implementation, on the test chain first and on
  the main chain only after an external cryptographic review, and no invented primitives. Sender
  ambiguity and amount confidentiality are described as not built rather than ruled out. Nothing
  in the code changes (#49). A fifth rule: every scheme names the model its proof holds in (#55)
- the wallet file is sealed with mystic-crypt's PassphraseEnvelope under the marker LETHWF - the
  envelope of mystic-crypt-ui's vault, byte for byte - instead of PassphraseCryptor's MCRYPT layout,
  which is still read. A wallet file 0.1.0 wrote is a fixed test vector and opens, its bytes on disk
  unchanged afterwards. mystic-crypt 13.3 to 13.5: 13.5's envelope declares
  GeneralSecurityException, so the calls into it are plain calls again (#45, #47)
- the commands that read a password - wallet create, wallet restore, and every command that opens a
  wallet - overwrite it once the file is written or opened, on success and on failure, and that is
  now asserted on the array's content rather than assumed (#45)

Version 0.1.0
-------------

The first release of the library. The CHAIN is not published and never will be: nobody can buy the
coin, there is no pool, no listing, no promotion (#2). What is on Maven Central is the library the
desktop application and the command line are built from.

ADDED:

- the chain: an account model with a nonce per account, transfers with a signed memo, a canonical
  encoding with a version byte in every persisted structure, and proof of pun - a block is mined
  when its hash carries the required leading zero bits. `Replay.verify` checks every signature,
  every state transition, every block hash and the supply invariant from the genesis block on, and
  says what it checked (#2, milestone 1)
- two signature suites in one chain, the identifier inside the signed bytes: Ed25519 by default and
  ML-DSA-65 (FIPS 204) per transaction, with known-answer tests from RFC 8032 and the ML-DSA
  vectors. An unknown suite identifier is refused rather than ignored (milestone 2)
- a fixed supply of 1,984,000,000 LETH in eight decimals, the block reward of 1,984 LETH paid from
  a pre-minted pool of 992,000,000 - exactly 500,000 blocks, then fees only. Integer arithmetic
  throughout, and the sum of all balances equals the supply after every block, as an assertion
- the difficulty as a rule rather than a block's own claim: minimum 8 bits, a 120 second target,
  adjusted every 30 blocks by at most 2 bits, and a timestamp that must lie past the median of the
  last 11 (#24)
- one-time destinations: the sender derives `P = S + H(s)*B` from the recipient's published
  address, so two payments to the same address land on keys with nothing visibly in common, the
  recipient recognises both with its view key alone, and only the holder of the spend key can move
  them. The additive construction comes from mystic-crypt's `Ed25519KeyBlinding`; this project
  writes no curve arithmetic (#21)
- a balance that is computed, never requested: `WalletScan` replays the chain locally, and
  `NoBalanceQueryTest` keeps the chain package unable to name a networking type at all, so the
  property holds for every run rather than for one captured one
- a wallet that can be recovered: BIP-39 words, SLIP-0010 hierarchical derivation through
  mystic-crypt's `SeedDerivation`, Shamir shares of the seed through its `SecretSharing`, and a
  wallet file with its own password sealed the way the desktop application's vault is (#34)
- a picocli command line: `wallet create`/`restore`, `mine`, `faucet`, `send` - to an account key
  or to a published address - `sweep` for what arrived at one-time destinations, and `balance`
  (#32, #37)

Pre-history: the versions this file named before this entry belonged to the 2021 java-library
template this repository started from. That template did not build against a current JDK and was
replaced wholesale; nothing of it is in this release, so its entries are not kept here.

Notable links:
[keep a changelog](http://keepachangelog.com/en/1.0.0/) Don’t let your friends dump git logs into changelogs
