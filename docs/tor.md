# Running lethenon over Tor

A runbook for the first run of the Tor transport against a real Tor (ADR 0004, step 4, #124).
Steps 1 to 3 (#114, #116, #120) are tested against a SOCKS5 server in the test sources. A real Tor
could not be reached from the environment they were built in (ADR 0004, "Measured in this
environment"), so this run is the first time the transport meets the Tor network. What it shows
belongs in #112.

Everything runs on the test network, `lethenon-test-1`. A node does not run on the main chain.

## What you need

- Tor, version 0.4.x, as a daemon: `apt install tor` on Debian or Ubuntu
- a lethenon build that contains #123 (`node --anonymous-inbound`)
- one machine is enough. Its two nodes reach each other through the Tor network, by onion
  addresses, exactly as two machines would

## 1. Tor: one SOCKS port, two onion services

Add to `/etc/tor/torrc`, then restart Tor (`systemctl restart tor`):

```text
SocksPort 127.0.0.1:9050

# the anonymity zone: transfers only, never the chain (ADR 0004, step 3)
HiddenServiceDir /var/lib/tor/lethenon-zone/
HiddenServicePort 18484 127.0.0.1:18484

# the chain: forwards to the node's ordinary port, for sync over Tor
HiddenServiceDir /var/lib/tor/lethenon-chain/
HiddenServicePort 18431 127.0.0.1:18431
```

Why two services: the zone carries no chain. A node disconnects a zone peer that asks for blocks.
So a node that wants to take the chain over Tor needs a second onion service that leads to the
ordinary listener, and `sync --proxy` uses that one. Monero keeps its RPC onion service apart from
its P2P one for the same reason. The two onion addresses of one node can be linked by anyone who
watches both, so a node that wants them unlinkable runs them on different machines.

After the restart, Tor has written the two addresses:

```sh
ZONE=$(sudo cat /var/lib/tor/lethenon-zone/hostname)     # 56 characters and .onion
CHAIN=$(sudo cat /var/lib/tor/lethenon-chain/hostname)
```

Check that Tor is bootstrapped before going on:
`sudo journalctl -u tor | grep "Bootstrapped 100%"`.

## 2. Two wallets

```sh
lethenon wallet create --wallet a.wallet     # the password is the first line of standard input
lethenon wallet create --wallet b.wallet
```

Note B's `account (ed25519): ...` line, written below as `<B account>`.

## 3. Node A: mines, and is an onion service

```sh
lethenon node --chain a.lethenon --listen 18431 --mine --wallet a.wallet \
    --tx-proxy tor,127.0.0.1:9050 \
    --anonymous-inbound $ZONE:18484,127.0.0.1:18484
```

Expect, among other things:

```text
mined the genesis block of lethenon-test-1
node on lethenon-test-1, listening on 0.0.0.0 port 18431, 0 peer(s) configured, connecting direct, anonymity zone through the SOCKS5 proxy at 127.0.0.1:9050, at most 10 peer(s), onion service <ZONE>:18484 on 127.0.0.1 port 18484, at most 16 peer(s), mining for <A account>
```

Let it mine a few blocks, so that A holds money to send.

## 4. B takes the chain over Tor

In a second shell:

```sh
lethenon sync --chain b.lethenon --peer $CHAIN:18431 --proxy 127.0.0.1:9050
```

Expect `chain lethenon-test-1 from the node at <CHAIN>:18431: took N block(s), now at height
N-1`. The first connection to an onion service can take tens of seconds while Tor builds its
circuits. `--within` raises the default of 300 seconds.

What this shows: the chain came through Tor (SOCKS5, host unresolved, #114) from an onion service,
and every block was verified locally.

## 5. Node B joins A's zone

```sh
lethenon node --chain b.lethenon --listen 18432 --no-discovery \
    --tx-proxy tor,127.0.0.1:9050 --peer $ZONE:18484 --for 900
```

B has no clearnet peer and needs none for this run. Its only peer is A, reached through A's onion
service, inside the zone.

## 6. A's own transfer goes only through the zone

In a third shell, hand a transfer to A's node:

```sh
lethenon send --chain a.lethenon --wallet a.wallet --to <B account> --amount 7 \
    --node 127.0.0.1:18431
```

A's node counts it as its own, because a command has no node identity. So the node sends it to
its zone peers only, which here means B, and to no clearnet peer (#116). When B stops after its
900 seconds, or earlier with Ctrl-C, expect:

```text
stopped at height H, mined 0 block(s), 1 transfer(s) waiting, 0 peer(s) connected, 1 anonymity peer(s)
```

The transfer waits in B's pool (`b.lethenon.pending`). B never had a clearnet peer, so it can only
have come through the zone.

A mines it into its next block. That is the limit ADR 0004 names: a node that mines can reveal its
own transfer in its own block. For this run that does not matter. Then:

```sh
lethenon sync --chain b.lethenon --peer $CHAIN:18431 --proxy 127.0.0.1:9050
lethenon balance --chain b.lethenon --wallet b.wallet    # B's account holds 7 LETH
```

## 7. What leaves the machine

While B runs, its Java process should have no connection except to Tor's SOCKS port:

```sh
ss -tnp | grep java
```

B's lines should all go to `127.0.0.1:9050`. A's lines are its listeners, its connections from
Tor's onion services (from 127.0.0.1), and its own SOCKS connections.

## 8. What to record in #112

- the Tor version (`tor --version`) and the lethenon commit
- the start and stop lines of both nodes and the output of steps 4 and 6
- how long the first onion connection took
- the output of step 7
- anything that differed from this runbook

Once that is recorded, the README sentence "not yet run against a real Tor" goes, in the same
change that links the record.
