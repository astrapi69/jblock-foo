# ADR 0004: Tor as a transport

- Status: accepted
- Date: 2026-10-07
- Released by the maintainer as the block after the main-chain prerequisites; every choice below
  was made within that release, by best practice and the recommendations recorded here, without a
  further question to the maintainer

## Context

ADR 0003 gave the test chain a network and said what it costs: a node that listens publishes its
address to every node it meets, and a transfer leaves from the IP address of whoever sends it. The
README promises "transport over Tor" as part of unlinkability (milestone 3), and ADR 0003 names Tor
as the step that changes what a listening node gives away.

### What the code does today

Measured on develop 8c2d5a0 with
`grep -rn "new Socket\|new ServerSocket\|InetSocketAddress\|InetAddress" src/main/java`:

- Outgoing connections are opened in two places: `Node.connect` and `Client.open`. `Client` serves
  `Handover` (`send --node`) and `Bootstrap`, and `Sync` goes through both. Both resolve the host
  name locally (`new InetSocketAddress(host, port)`).
- `Node.listen` binds every interface (`new InetSocketAddress(port)`).
- An accepted peer is passed on as `socket.getInetAddress()` and the port it announced
  (`Node.listeningAddress`). A peer that reaches the node through a Tor onion service arrives from
  127.0.0.1, and would be passed on as `127.0.0.1:<port>`.
- `HELLO` carries a node identity drawn once per node. Used on two networks, it would link them.
- `PeerAddress` takes any host. `PeerList` carries hosts up to 253 characters, so a v3 onion
  address (56 characters plus `.onion`) fits the wire as it is.

### Monero as the blueprint

`docs/ANONYMITY_NETWORKS.md` in monero-project/monero (read at f6a591c):

- `--proxy`: all external traffic goes through a SOCKS proxy, treated as the clearnet zone. Hidden
  services are not dialled over it.
- `--tx-proxy tor,127.0.0.1:9050[,max]`: an anonymity zone. A transfer that originates on the node
  ("lacks a valid context") is sent only to peers in that zone. Without such a peer it is held, not
  sent in the clear.
- Over anonymity networks, only handshakes, timed syncs and transfer broadcasts. The chain is not
  synchronised over hidden services, "to make Sybil attacks more difficult".
- `--anonymous-inbound <onion>:<port>,127.0.0.1:<port>[,max]`: a separate listener for the onion
  service. Its address is announced only to peers in the same zone.
- Its "Privacy Limitations" section is the list of what this design does not solve: timestamps that
  link zones, a node that runs only to send, bandwidth shaping, and one Tor stream used for two
  transfers.

### Measured in this environment

Tor 0.4.9.11 installs here, but it cannot bootstrap. With direct connections it stays at "5%
(conn): Connecting to a relay". Through the environment's HTTPS proxy, every relay is refused:
`The https proxy refused to allow connection to 205.185.125.239 (status code 403, "Forbidden")`
(tor log, 2026-10-07). So no test here can use the Tor network.

## Decision

### No Tor library, a Tor daemon

lethenon talks SOCKS5 to a Tor daemon the operator runs, as Monero does. The JDK's SOCKS client
(`java.net.Proxy.Type.SOCKS`) sends an unresolved host as a domain name, so the proxy resolves it
and the node never asks DNS for a peer's name. This adds no dependency. A Java Tor implementation
would put a large, little-reviewed codebase inside the process that holds the chain, for nothing
the daemon does not already do.

### Steps, one pull request each

1. **A proxy for every outgoing connection (`--proxy host:port`).** Node, `sync`, `send --node` and
   the genesis bootstrap dial through SOCKS5 with the host unresolved. A proxy given means no
   connection is ever made around it. A node with a proxy listens on 127.0.0.1 unless `--bind`
   says otherwise. `PeerAddress` recognises v3 onion addresses: 56 base32 characters and
   `.onion`, case-insensitive, other lengths refused. An onion address without a proxy is refused
   before anything touches the network.
2. **An anonymity zone (`--tx-proxy tor,host:port[,max]`).** Onion peers are dialled through that
   proxy and belong to a zone of their own:
   - their own peer lists;
   - their own node identity in `HELLO`, drawn apart from the clearnet one;
   - no chain synchronisation: `GET_CHAIN`, `GET_BLOCKS` and `BLOCK` from such a peer disconnect,
     and none are sent to it;
   - only `HELLO`, `TRANSFER` and the exchange of onion addresses.

   With the zone on, a transfer that originates on this node goes only to anonymity peers: the
   node's own `submitTransfer` and a transfer handed over with `send --node`. Without one, the
   transfer waits in the pool and the refusals say so. It is not sent in the clear. A transfer that
   arrives from a peer is relayed as before.
3. **Anonymous inbound (`--anonymous-inbound <onion>:<port>,127.0.0.1:<port>[,max]`).** A second
   listener, on loopback only, whose peers belong to the anonymity zone. Its onion address travels
   only inside that zone: `HELLO` gains a field for it (protocol version 3), filled only on
   connections in the anonymity zone and empty everywhere else. An accepted anonymity peer is passed
   on by the onion address it announced, never as 127.0.0.1. A clearnet `HELLO` that carries one is
   refused, because a node that did that would link its IP address to its onion address.
4. **The runbook against a real Tor.** `docs/tor.md`: the torrc lines (`SocksPort`,
   `HiddenServiceDir`, `HiddenServicePort`), the three node configurations, and a manual
   end-to-end run between two machines. It cannot be executed here (see "Measured in this
   environment"). Until the maintainer has run it once, the Tor transport is tested only against
   the SOCKS5 server in the tests, and the README says exactly that.

### How it is tested

Each step is tested against a SOCKS5 server that lives in the test sources. It records every
`CONNECT`: the address type (IPv4 or domain name), the host and the port. It forwards an onion
name to a local port the test chose. That makes the properties checkable that matter more than the
connection itself:
- with a proxy, nothing is dialled directly: the target port is closed to direct connections and
  reachable only through the proxy;
- an onion host reaches the proxy as a domain name, never resolved;
- a transfer that originates locally never reaches a clearnet peer while the zone is on;
- an onion address never appears in a clearnet `HELLO` or `PEERS`.

## What this does not solve

These are the limits Monero lists, and they apply here as well:
- **Running only to send.** A node started to hand over one transfer and stopped again tells an
  observer of the connection when it sent. `send --node` through Tor hides the address, not the
  moment.
- **One circuit, two transfers.** Two transfers over the same connection let the onion service's
  operator link them. Rotating connections is a later step.
- **Bandwidth shaping** against a Tor connection is not mitigated.
- **Timestamps.** `HELLO` carries no clock time, so this linkage of the zones does not apply. A
  block's timestamp is the miner's, and blocks do not travel in the anonymity zone.

And one that is lethenon's own: the chain is public, and every transfer names its sender's account
(ADR 0002, sender ambiguity postponed). Tor hides where a transfer was sent from, not who sent it.

## Consequences

- No new dependency. The operator runs Tor.
- The chain package stays unable to name a networking type (`NoBalanceQueryTest`). Everything here
  lives in `transport` and `cli`.
- Protocol version 3 with step 3. Version-2 nodes are refused, as version 1 was at #102.
- The chain is synchronised in the clear, or through `--proxy` over Tor exit nodes to clearnet
  peers, never over onion services. That follows Monero's reasoning on Sybil attacks.
