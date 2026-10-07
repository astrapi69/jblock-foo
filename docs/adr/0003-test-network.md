# ADR 0003: A network for the test chain

- Status: accepted
- Date: 2026-10-07
- Released by the maintainer (#76); every choice below was made within that release, by best
  practice and the recommendations recorded here, without a further question to the maintainer

## Context

Until now a chain lives in one file on one machine: `mine` appends a block, `send` and `faucet`
append to `<chain>.pending`, and nobody else learns of either. For a chain to be a chain, nodes
have to relay blocks and transfers, a new node has to catch up with every block checked, two nodes
that mined at the same height have to agree on one chain, and a transfer must not be spendable
twice while it waits.

### What the repository already has

Inventory of develop at 6f637a3:

- **Encoding.** `CanonicalEncoding` encodes a signed transfer (`encode(SignedTransaction)`,
  `readSignedTransaction`) and a chain (`encodeChain`, `readChain`), each with its version byte. A
  single block travels as a chain of one, so the network needs no new block format.
- **Verification.** `Replay.verify(chain)` checks every hash, signature, nonce, balance, difficulty,
  timestamp rule, chain identifier and the supply after every block. It is the only verifier, and
  the network uses nothing else.
- **Difficulty.** `DifficultyRule` gives a block's difficulty in leading zero bits, from 8 to 256,
  so a block's work is 2^difficulty.
- **Persistence.** `ChainFile` writes the chain and `<chain>.pending` atomically (write and rename).
- **Isolation.** `NoBalanceQueryTest` keeps the chain package unable to name a networking type, and
  already names `lethenon.transport` as the place a transport belongs.

### Monero as the blueprint

Read from the Monero sources, commit f6a591c (2026-10-04), `git clone --depth 1 --sparse`:

- **Handshake.** It carries a `network_id` (`src/p2p/p2p_protocol_defs.h:151`) and the peer's
  `current_height`, `cumulative_difficulty` and `top_id`
  (`src/cryptonote_protocol/cryptonote_protocol_defs.h:254-257`).
- **Messages.**
  - Relaying: `NOTIFY_NEW_BLOCK`, `NOTIFY_NEW_TRANSACTIONS`.
  - Chain sync: `NOTIFY_REQUEST_CHAIN`, `NOTIFY_RESPONSE_CHAIN_ENTRY`.
  - Fetching blocks: `NOTIFY_REQUEST_GET_OBJECTS`, `NOTIFY_RESPONSE_GET_OBJECTS`.
  - All in `cryptonote_protocol_defs.h:174-291`.
- **Locator for the chain request.** "IDs of the first 10 blocks are sequential, next goes with
  pow(2,n) offset ... and the last one is always genesis block" (`cryptonote_protocol_defs.h:280`).
  The answer carries `start_height`, `total_height` and the block ids (`:297-301`).
- **Limits.**
  - `P2P_DEFAULT_CONNECTIONS_COUNT 12` (`src/cryptonote_config.h:141`).
  - `P2P_DEFAULT_PACKET_MAX_SIZE 50000000` (`:143`).
  - `P2P_DEFAULT_HANDSHAKE_INVOKE_TIMEOUT 5000` ms (`:150`).
  - `P2P_DEFAULT_INVOKE_TIMEOUT` two minutes (`:149`).
  - `BLOCKS_SYNCHRONIZING_DEFAULT_COUNT 20` blocks per request (`:98`).

## Decision

### Where the code lives

- **Package.** The network is the package `io.github.astrapi69.lethenon.transport`. The chain package
  does not name it, and `NoBalanceQueryTest` keeps it that way.
- **What stays in the chain package.** The logic that decides about blocks and transfers - the
  pool of waiting transfers, fork choice, cumulative work - stays there, with no networking types,
  so it is testable without a socket.
- **Dependencies.** None new. Sockets from `java.net`, one virtual thread per connection, and a
  single lock around a node's chain and pool.

### Only the test chain

A node runs only on `lethenon-test-1`.
- It refuses a chain file whose genesis block names `lethenon-1`.
- It creates no main chain.
- Its handshake refuses any peer that names another chain or another genesis block.

The main chain gets a network only by a later decision. This ADR binds nothing on the main chain
to an external review, because it puts nothing there.

### Frames and messages

Each message is one frame on the stream: a 4-byte length, a 1-byte message type, the payload. The
length counts the type and the payload and is checked before anything is allocated.

| Message | Payload | Monero counterpart |
|---|---|---|
| `HELLO` | magic "LETHENON", protocol version, chain identifier, genesis hash, best height, best hash, cumulative work | handshake with `network_id` and core sync data |
| `BLOCK` | one block, encoded as a chain of one | `NOTIFY_NEW_BLOCK` |
| `TRANSFER` | one signed transfer | `NOTIFY_NEW_TRANSACTIONS` |
| `GET_CHAIN` | a locator of block hashes, built as Monero's | `NOTIFY_REQUEST_CHAIN` |
| `CHAIN` | the height of the first hash the peer shares, then the hashes after it | `NOTIFY_RESPONSE_CHAIN_ENTRY` |
| `GET_BLOCKS` | first height, count | `NOTIFY_REQUEST_GET_OBJECTS` |
| `BLOCKS` | the blocks, encoded as a chain | `NOTIFY_RESPONSE_GET_OBJECTS` |

There is deliberately no message that asks for a balance, an account or an address. A node serves
blocks and transfers; a wallet computes its balance from the chain, as before.

### Handshake

Both sides send `HELLO` first and read the other's within the handshake timeout. The connection is
closed, with the reason logged, when any of these hold:
- the magic is wrong;
- the protocol version is not this build's;
- the chain identifier is not `lethenon-test-1`;
- the genesis hash differs.

A peer with more cumulative work is asked for its chain at once.

### Relaying and missing blocks

- **A new block.** A node that mines or accepts a block sends it as `BLOCK` to every peer except the
  one it came from.
- **A new transfer.** A node that admits a transfer to its pool sends it as `TRANSFER` the same way.
- **An unknown parent.** A received block whose parent the node does not know is not a fault: the
  node sends `GET_CHAIN` to that peer and synchronises.

Frames to a peer are written by a thread of that peer's own, from a bounded queue. A node handles a
frame on the thread that read it, and that thread may have to send to other peers; writing to their
sockets directly could leave two nodes that relay to each other waiting for each other to read
(#82).

Full blocks are relayed, not Monero's compact "fluffy" blocks. Blocks here are small, and that
optimisation can come later.

### Synchronisation

1. `GET_CHAIN` carries the locator.
2. The peer answers `CHAIN` with the height of the newest locator hash it shares and up to 500
   following hashes.
3. The node then fetches the blocks in `GET_BLOCKS` batches of at most 20 (Monero's default).
4. It builds the candidate chain: its own blocks up to the shared height, then the fetched ones.
5. It checks the whole candidate with `Replay.verify`.

A candidate that does not verify is dropped and the peer disconnected. Replaying the whole chain
for each batch is linear in its length, which is fine for a test chain; an incremental replay is
a later optimisation.

### Forks: the most work wins

- **Work.** The cumulative work of a chain is the sum of 2^difficulty over its blocks, as a
  BigInteger.
- **Switching.** A node switches to a verified candidate only if its work is strictly greater than
  that of its own chain. On equal work it keeps the chain it had, the first seen.
- **Rolling back.** Switching drops the node's blocks above the fork point. Their transfers go back
  into the pool if they are still valid on the new chain; the rest are dropped.
- **Depth.** The blocks of a fork are held until the fork carries more work. A fork is followed
  only if it overtakes within one `CHAIN` answer, 500 blocks after the fork point; a deeper one is
  recorded and not followed. That bounds what a peer can make a node hold, and it is in effect a
  limit on how deep a reorganisation can be. Lifting it needs a fetch across several `CHAIN`
  answers that keeps the held blocks bounded some other way (#88).

### The pool of waiting transfers

`TransactionPool`, in the chain package, admits a transfer only if:
- its chain identifier and schemes are admitted (`ConsensusRules`);
- its signature verifies;
- its nonce is exactly the sender's next one, counting the transfers of that sender already in the
  pool;
- the sender's balance covers it after them.

A second transfer from the same sender with the same nonce is a double spend. It is refused, and
the first one seen stays: there is no replacement by fee. After each new tip the pool drops what
the block carried and re-checks the rest.

Admission keeps, per sender, how many transfers wait and what they spend, and checks a new one
against that; each signature is checked once, when its transfer is offered. Re-applying a sender's
waiting transfers on every offer took about 12.5 million signature checks to fill the pool from one
sender (#92).

Transfers to a sender that are still waiting do not count towards its balance, the same as
`Transfers.prepare`: then a sender's waiting transfers are valid in any block that carries them in
their order, whatever else the block carries (#82).

A node keeps its chain in `<chain>` and its pool in `<chain>.pending`, in the existing formats, so
`balance` and the other readers keep working on a node's files. At start it offers every waiting
transfer to the pool again, so a double spend or a transfer that no longer fits is dropped there
and the file rewritten (#90).

`send` and `faucet` hand a transfer to a running node with `--node host:port` instead of writing
the file (#90):
- The transfer is prepared against the chain and pool in the file named by `--chain`, which has to
  be the file the node serves.
- The handover is a handshake, one `TRANSFER` frame, then the command closes its direction and reads
  until the node closes the other. A node handles frames in order, so it has then admitted or
  refused the transfer.
- The command then reads the node's pool file. If the transfer is not there, it fails with a
  message. A node on another machine or another file keeps a pool the command cannot see, and that
  case fails closed rather than reporting a success it could not check.

A file a node serves belongs to the node while it runs. `mine` and `sweep` on it would be
overwritten by the node's next write; mining on a node is `node --mine`.

### Limits

| Limit | Value | Basis |
|---|---|---|
| frame size | 4 MiB | below Monero's 50,000,000 bytes; the largest message here is `BLOCKS`, at most 20 blocks |
| outgoing connections | the configured peers, at most 12 | Monero's default of 12 |
| incoming connections | at most 16 | own choice; refused above it |
| handshake timeout | 5 s | Monero's 5000 ms |
| answer timeout | 120 s | Monero's two-minute invoke timeout (`P2P_DEFAULT_INVOKE_TIMEOUT`, `src/cryptonote_config.h:149`): a peer that does not answer a `GET_CHAIN` or `GET_BLOCKS` in time is disconnected; one with nothing asked of it may stay quiet (#92) |
| blocks per `GET_BLOCKS` | at most 20 | Monero's default |
| hashes per `CHAIN` | at most 500 | own choice |
| pool size | at most 5,000 transfers | own choice; refused above it |
| frames waiting for one peer | at most 1,024 | own choice; a peer that does not read them is disconnected (#82) |
| transfers per mined block | at most 500 | keeps the largest block well inside a frame: an ML-DSA-65 transfer carries its signature and public key, a few kilobytes |

A peer that sends a frame above the limit, an unknown message type, bytes that do not decode, or a
block that does not verify is disconnected.

### The node command

`lethenon node --chain <file> --listen <port> [--peer host:port ...] [--mine --wallet <file>]`
- It serves the chain and relays.
- With `--mine` it mines on its pool and pays the wallet, and if the chain file is empty it starts a
  test chain, which is the same as `mine --testnet`.
- Without `--mine`, a node with no chain synchronises from its peers.

## Consequences

- The chain package stays unable to reach a network. Everything that talks lives in `transport`.
- A wallet asks no node for its balance. There is no message to do so.
- **No consensus limit on block size.** The node's miner caps the transfers per block, but a block
  mined elsewhere that is larger than a frame cannot be received. A consensus limit would be a change
  of the chain's rules and is left open.
- **No limit on future timestamps.** The replay checks timestamps only against the median of the
  blocks before, so a miner can put a block's time in the future and influence the difficulty.
  Bounding that is a consensus change as well and is left open.
- **A reorganisation is at most one `CHAIN` answer deep.** A heavier fork whose advantage only
  shows after more than 500 blocks is not followed, and two nodes on such forks stay apart. On a
  test chain that is a choice; it is not one for a chain with value (#88).
- **No peer exchange.** Peers come from the command line; Monero shares up to 250 peers in its
  handshake (`P2P_DEFAULT_PEERS_IN_HANDSHAKE 250`, `src/cryptonote_config.h:144`), and lethenon
  does not, for now.
- **No Tor.** Tor comes later as a transport.
