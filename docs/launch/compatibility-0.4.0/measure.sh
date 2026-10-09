#!/usr/bin/env bash
# Compatibility of lethenon 0.4.0 with 0.1.0, 0.2.0 and 0.3.0, both directions, on chains carrying
# real transfers: an Ed25519 transfer, a payment to a published address and its sweep, a transfer
# from an ML-DSA-65 account, the faucet. Run build.sh first. Writes results.tsv and measure.log next
# to this script: one line per command, version, chain, exit code, first line it printed.
set -uo pipefail
C=$(cd "$(dirname "$0")" && pwd)
export JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64
declare -A CLI=( [0.1.0]=$C/wt-0.1.0/build/install/lethenon/bin/lethenon [0.2.0]=$C/wt-0.2.0/build/install/lethenon/bin/lethenon
  [0.3.0]=$C/wt-0.3.0/build/install/lethenon/bin/lethenon [0.4.0]=$C/wt-0.4.0/build/install/lethenon/bin/lethenon )
W=$C/run; rm -rf $W; mkdir -p $W
PW=correct-horse-battery-staple
OUT=$C/results.tsv; : > $OUT
LOG=$C/measure.log; : > $LOG
lc() { local v=$1; shift; printf '%s\n' "$PW" | "${CLI[$v]}" "$@" 2> $W/err.txt > $W/out.txt; local rc=$?; grep -v JAVA_TOOL_OPTIONS $W/err.txt > $W/err2.txt; mv $W/err2.txt $W/err.txt
  { echo "### $v $*  -> exit $rc"; cat $W/out.txt $W/err.txt; } >> $LOG; return $rc; }
rec() { local v=$1 action=$2 chain=$3 rc=$4; local msg; msg=$(head -1 $W/err.txt); [ -z "$msg" ] && msg=$(grep -m1 -E "holds|mined|signed|replayed" $W/out.txt); printf '%s\t%s\t%s\t%s\t%s\n' "$v" "$action" "$chain" "$rc" "$msg" >> $OUT; }
step() { local v=$1 action=$2 chain=$3; shift 3; lc $v "$@"; local rc=$?; rec $v "$action" "$chain" $rc; return $rc; }
acct() { grep -m1 -oE "account \(ed25519\): [0-9a-f]+" $W/out.txt | awk '{print $3}'; }
pq() { grep -m1 -oE "account \(ml-dsa-65\): [0-9a-f]+" $W/out.txt | awk '{print $3}'; }
addr() { grep -m1 -oE "address \(publish this\): [0-9a-f]+:[0-9a-f]+" $W/out.txt | awk '{print $4}'; }

# wallets per version: holder H, recipient A, address payee P
for v in 0.1.0 0.2.0 0.3.0 0.4.0; do
  for w in H A P; do lc $v wallet create --wallet $W/$v-$w.wallet; done
done
# what does 0.4.0 print for a wallet (account lines)
lc 0.4.0 balance --chain /nonexistent --wallet $W/0.4.0-H.wallet || true

# A chain written by a version with transfers: Ed25519 transfer, a payment to a published address, a
# sweep of it, an ML-DSA-65 transfer where the version can, and blocks after them
write_chain() { # version chainfile testnet(yes|no) [genesis-already-there]
  local v=$1 f=$2 t=$3 pre=${4:-}
  local tn=""; [ $t = yes ] && tn="--testnet"
  if [ -z "$pre" ]; then step $v "mine (genesis)" $(basename $f) mine $tn --chain $f --wallet $W/$v-H.wallet; fi
  step $v "mine" $(basename $f) mine --chain $f --wallet $W/$v-H.wallet
  lc $v balance --chain $f --wallet $W/$v-A.wallet; local a; a=$(acct)
  lc $v balance --chain $f --wallet $W/$v-P.wallet; local p; p=$(addr)
  lc $v balance --chain $f --wallet $W/$v-H.wallet; local hpq; hpq=$(pq)
  step $v "send ed25519 12.5" $(basename $f) send --chain $f --wallet $W/$v-H.wallet --to $a --amount 12.5 --memo "no permanent record"
  [ -n "$p" ] && step $v "send to address 20" $(basename $f) send --chain $f --wallet $W/$v-H.wallet --to-address $p --amount 20
  [ -n "$hpq" ] && step $v "send to own ml-dsa-65 100" $(basename $f) send --chain $f --wallet $W/$v-H.wallet --to $hpq --amount 100
  step $v "mine" $(basename $f) mine --chain $f --wallet $W/$v-H.wallet
  [ -n "$p" ] && step $v "sweep" $(basename $f) sweep --chain $f --wallet $W/$v-P.wallet
  [ -n "$hpq" ] && step $v "send ml-dsa-65 5" $(basename $f) send --chain $f --wallet $W/$v-H.wallet --suite ml-dsa-65 --to $a --amount 5
  step $v "mine" $(basename $f) mine --chain $f --wallet $W/$v-H.wallet
  step $v "balance A" $(basename $f) balance --chain $f --wallet $W/$v-A.wallet
  grep -m1 "holds" $W/out.txt >> $OUT
  step $v "balance P" $(basename $f) balance --chain $f --wallet $W/$v-P.wallet
  grep -m1 "holds" $W/out.txt >> $OUT
}

# someone else runs a version on a chain: balance, mine, send; the file must stay as it was
probe() { # version chainfile
  local v=$1 f=$2 before after
  before=$(sha256sum $f | cut -c1-16)
  step $v "balance" $(basename $f) balance --chain $f --wallet $W/$v-A.wallet
  step $v "mine" $(basename $f) mine --chain $f --wallet $W/$v-A.wallet
  step $v "send 1" $(basename $f) send --chain $f --wallet $W/$v-A.wallet --to 00 --amount 1
  after=$(sha256sum $f | cut -c1-16)
  printf '%s\t%s\t%s\t%s\t%s\n' "$v" "file unchanged" "$(basename $f)" "-" "$([ $before = $after ] && echo yes || echo "NO $before -> $after")" >> $OUT
  rm -f $f.pending
}

echo "## 0.4.0 writes" >> $OUT
write_chain 0.4.0 $W/t4.lethenon yes
"$JAVA_HOME/bin/java" -cp "$C/wt-0.4.0/build/install/lethenon/lib/*" $C/GenesisFile.java $W/m4.lethenon "a headline of the day" >> $OUT 2>&1
write_chain 0.4.0 $W/m4.lethenon no pre
step 0.4.0 "faucet on test chain" t4.lethenon faucet --chain $W/t4.lethenon --wallet $W/0.4.0-H.wallet --to $(lc 0.4.0 balance --chain $W/t4.lethenon --wallet $W/0.4.0-A.wallet; acct)
step 0.4.0 "mine" t4.lethenon mine --chain $W/t4.lethenon --wallet $W/0.4.0-H.wallet
step 0.4.0 "mine without --testnet on a new file" new.lethenon mine --chain $W/new.lethenon --wallet $W/0.4.0-H.wallet
printf '%s\t%s\t%s\t%s\t%s\n' 0.4.0 "new file written" new.lethenon - "$([ -e $W/new.lethenon ] && echo yes || echo no)" >> $OUT
cp $W/t4.lethenon $W/t4.keep; cp $W/m4.lethenon $W/m4.keep

echo "## earlier versions on the chains of 0.4.0" >> $OUT
for v in 0.1.0 0.2.0 0.3.0; do probe $v $W/t4.lethenon; probe $v $W/m4.lethenon; done

echo "## earlier versions write, 0.4.0 runs on them" >> $OUT
for v in 0.1.0 0.2.0 0.3.0; do
  write_chain $v $W/m-$v.lethenon no
  [ $v != 0.1.0 ] && write_chain $v $W/t-$v.lethenon yes
done
for f in $W/m-0.1.0.lethenon $W/m-0.2.0.lethenon $W/t-0.2.0.lethenon $W/m-0.3.0.lethenon $W/t-0.3.0.lethenon; do probe 0.4.0 $f; done
wc -c $W/t4.keep $W/m4.keep >> $OUT
echo done
