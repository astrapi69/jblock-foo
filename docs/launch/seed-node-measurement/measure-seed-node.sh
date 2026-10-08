#!/usr/bin/env bash
# What a seed node of lethenon needs, measured rather than guessed (docs/launch/infrastructure.md).
#
# Runs inside a network namespace of its own (unshare -rn), so that the loopback counters in
# /proc/net/dev count nothing but the traffic of this test network. Two nodes of one lethenon
# command line: a miner, which stands in for the network's miners, and a seed node, which mines
# nothing - the role a launch server has. Every two minutes a transfer is handed to the miner's node
# and gossiped on. Every 30 seconds the seed node's CPU time, memory, chain file and the namespace's
# traffic are written to samples.csv. With a heap given, the seed node runs with that maximum heap
# and logs its collections, so the resident size shows what the node needs rather than what the
# JVM was offered.
#
# Usage: unshare -rn bash measure-seed-node.sh <lethenon launcher> <output directory> <seconds> [seed heap, e.g. 64m]
set -uo pipefail
L="${1:?the lethenon launcher, build/install/lethenon/bin/lethenon}"
R="${2:?an output directory, emptied first}"
SECONDS_TO_RUN="${3:?seconds}"
SEED_HEAP="${4:-}"
rm -rf "${R}" && mkdir -p "${R}" && cd "${R}" || exit 1
export TMPDIR="${R}/tmp" JAVA_TOOL_OPTIONS="-Djava.io.tmpdir=${R}/tmp"
mkdir -p "${TMPDIR}"
ip link set lo up || { echo "loopback could not be brought up"; exit 1; }
PASS="a throwaway wallet for the seed node measurement"
printf '%s\n' "${PASS}" | "${L}" wallet create --wallet miner.wallet > miner.create.out 2>&1
printf '%s\n' "${PASS}" | "${L}" wallet create --wallet payee.wallet > payee.create.out 2>&1
PAYEE=$(sed -n 's/^account (ed25519): //p' payee.create.out)

printf '%s\n' "${PASS}" | "${L}" node --chain miner.lethenon --listen 18480 --mine \
	--wallet miner.wallet --for "$((SECONDS_TO_RUN + 60))" > miner.out 2> miner.err &
MINER=$!
sleep 15
SEED_OPTIONS=""
[ -n "${SEED_HEAP}" ] && SEED_OPTIONS="-Xmx${SEED_HEAP} -XX:+UseSerialGC -Xlog:gc:file=${R}/gc.log"
LETHENON_OPTS="${SEED_OPTIONS}" "${L}" node --chain seed.lethenon --listen 18481 --peer 127.0.0.1:18480 \
	--for "$((SECONDS_TO_RUN + 30))" > seed.out 2> seed.err &
SEED=$!
sleep 5
# the start script ends in exec, so the background job is the JVM itself
grep -q "^Name:.*java" "/proc/${SEED}/status" || { echo "the seed process is not the JVM"; exit 1; }
echo "miner ${MINER}, seed ${SEED}, seed heap ${SEED_HEAP:-the default of the JVM}" > pids.txt
echo "t_s,seed_cpu_ticks,seed_rss_kb,seed_hwm_kb,seed_threads,seed_chain_bytes,miner_chain_bytes,lo_rx_bytes,lo_tx_bytes,transfers_sent" > samples.csv
start=$(date +%s)
sent=0
next_send=$((start + 180))
while :; do
	now=$(date +%s)
	elapsed=$((now - start))
	[ "${elapsed}" -ge "${SECONDS_TO_RUN}" ] && break
	if [ "${now}" -ge "${next_send}" ]; then
		if printf '%s\n' "${PASS}" | "${L}" send --chain miner.lethenon --wallet miner.wallet \
			--to "${PAYEE}" --amount 1 --memo "measurement ${sent}" --node 127.0.0.1:18480 >> send.out 2>&1; then
			sent=$((sent + 1))
		fi
		next_send=$((now + 120))
	fi
	stat=( $(cat "/proc/${SEED}/stat" 2> /dev/null | sed 's/.*) //') )
	cpu=$(( ${stat[11]:-0} + ${stat[12]:-0} ))
	rss=$(awk '/^VmRSS:/{print $2}' "/proc/${SEED}/status" 2> /dev/null)
	hwm=$(awk '/^VmHWM:/{print $2}' "/proc/${SEED}/status" 2> /dev/null)
	threads=$(awk '/^Threads:/{print $2}' "/proc/${SEED}/status" 2> /dev/null)
	seed_bytes=$(stat -c %s seed.lethenon 2> /dev/null || echo 0)
	miner_bytes=$(stat -c %s miner.lethenon 2> /dev/null || echo 0)
	read -r rx tx < <(awk '$1 == "lo:" {print $2, $10}' /proc/net/dev)
	echo "${elapsed},${cpu},${rss:-0},${hwm:-0},${threads:-0},${seed_bytes},${miner_bytes},${rx},${tx},${sent}" >> samples.csv
	sleep 30
done
echo "ticks per second $(getconf CLK_TCK)" >> pids.txt
wait "${SEED}"; echo "seed exit $?" >> pids.txt
wait "${MINER}"; echo "miner exit $?" >> pids.txt
printf '%s\n' "${PASS}" | "${L}" balance --chain seed.lethenon --wallet payee.wallet > payee.balance.out 2>&1
echo "measurement ended" >> pids.txt
