"""Summarises one seed node measurement run (measure.sh): CPU, memory, chain growth and traffic,
over the whole run and over its last hour, and sets them against Oracle's idle thresholds.

Usage: python3 -I analyse.py <run directory> <chain stats csv of the seed chain>
"""
import csv
import math
import re
import sys

run, chain_stats = sys.argv[1], sys.argv[2]
TICKS = 100
samples = [{key: float(value) for key, value in row.items()} for row in csv.DictReader(open(run + "/samples.csv"))]
blocks = list(csv.DictReader(open(chain_stats)))


def percentile(values, share):
    ordered = sorted(values)
    return ordered[min(len(ordered) - 1, math.ceil(share * len(ordered)) - 1)]


def window(rows, label):
    cpu = []
    for before, after in zip(rows, rows[1:]):
        seconds = after["t_s"] - before["t_s"]
        cpu.append(100.0 * (after["seed_cpu_ticks"] - before["seed_cpu_ticks"]) / TICKS / seconds)
    seconds = rows[-1]["t_s"] - rows[0]["t_s"]
    traffic = rows[-1]["lo_rx_bytes"] - rows[0]["lo_rx_bytes"]
    growth = rows[-1]["seed_chain_bytes"] - rows[0]["seed_chain_bytes"]
    print(f"== {label}: {len(rows)} samples over {seconds / 3600:.2f} h (t = {rows[0]['t_s']:.0f} .. {rows[-1]['t_s']:.0f} s)")
    print(f"  cpu, % of one core per 30 s: mean {sum(cpu) / len(cpu):.2f}, p95 {percentile(cpu, 0.95):.2f}, max {max(cpu):.2f}")
    print(f"  rss kB: min {min(r['seed_rss_kb'] for r in rows):.0f}, max {max(r['seed_rss_kb'] for r in rows):.0f}, last {rows[-1]['seed_rss_kb']:.0f}; hwm last {rows[-1]['seed_hwm_kb']:.0f}")
    print(f"  threads: min {min(r['seed_threads'] for r in rows):.0f}, max {max(r['seed_threads'] for r in rows):.0f}")
    print(f"  traffic (loopback, both directions once): {traffic:.0f} bytes, {traffic / seconds:.1f} B/s, {traffic / seconds * 86400 / 1e6:.1f} MB/day, {traffic / seconds * 8 / 1e3:.2f} kbit/s")
    print(f"  chain file growth: {growth:.0f} bytes, {growth / seconds * 86400 / 1e6:.3f} MB/day")
    return cpu, traffic / seconds


print(f"run {run}")
cpu_all, _ = window(samples, "whole run")
last_hour = [r for r in samples if r["t_s"] >= samples[-1]["t_s"] - 3600]
cpu_hour, rate_hour = window(last_hour, "last hour")

heights = [int(b["height"]) for b in blocks]
end_ms = int(blocks[-1]["timestamp_ms"])
recent = [b for b in blocks if int(b["timestamp_ms"]) >= end_ms - 3600 * 1000]
print(f"== chain: {len(blocks)} blocks, height {heights[-1]}, last difficulty {blocks[-1]['difficulty']}")
print(f"  blocks in the last hour of the chain: {len(recent)}, mean interval {sum(float(b['interval_s']) for b in recent[1:]) / max(1, len(recent) - 1):.1f} s")
print(f"  bytes per block: mean {sum(int(b['bytes']) for b in blocks) / len(blocks):.1f}, without transfers {sum(int(b['bytes']) for b in blocks if b['transfers'] == '0') / max(1, sum(1 for b in blocks if b['transfers'] == '0')):.1f}, with transfers {sum(int(b['bytes']) for b in blocks if b['transfers'] != '0') / max(1, sum(1 for b in blocks if b['transfers'] != '0')):.1f}")
print(f"  transfers carried: {sum(int(b['transfers']) for b in blocks)}")
per_block = sum(int(b["bytes"]) for b in blocks) / len(blocks)
print(f"  at 720 blocks a day (two-minute target): {per_block * 720 / 1e6:.3f} MB/day, {per_block * 720 * 365 / 1e6:.1f} MB/year")

print("== against Oracle's idle rule (7 days; each below 20 %)")
print(f"  cpu p95 last hour, % of one core: {percentile(cpu_hour, 0.95):.2f} (one A1 OCPU is one hardware thread)")
print(f"  network, last hour: {rate_hour * 8 / 1e6:.4f} Mbit/s = {100 * rate_hour * 8 / 2e9:.6f} % of 2 Gbit/s (A1, 2 OCPU), {100 * rate_hour * 8 / 1e9:.6f} % of 1 Gbit/s (1 OCPU)")
rss = max(r["seed_rss_kb"] for r in samples) / 1e6
print(f"  memory, max rss {rss:.3f} GB = {100 * rss / 12:.2f} % of 12 GB, {100 * rss / 6:.2f} % of 6 GB")

for line in open(run + "/pids.txt"):
    print("  pids.txt:", line.rstrip())
for name in ("seed.out", "send.out", "payee.balance.out"):
    try:
        text = open(run + "/" + name).read()
    except FileNotFoundError:
        continue
    lines = [l for l in text.splitlines() if "JAVA_TOOL_OPTIONS" not in l]
    print(f"  {name}: {len(lines)} lines; last: {lines[-1] if lines else ''}")
    if name == "send.out":
        print(f"  transfers handed over: {len(re.findall('handed it to the node', text))}")
