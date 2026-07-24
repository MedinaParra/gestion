"""Vectorized RTP audit for Royal Spin base rounds plus nested free spins."""

import argparse
import csv
import json
import os
import time

import numpy as np

SYMBOLS = ["W", "7", "D", "BELL", "BAR", "A", "K", "Q", "J"]
SYMBOL_TO_ID = {symbol: index for index, symbol in enumerate(SYMBOLS)}
WILD = SYMBOL_TO_ID["W"]
PAYTABLE = {
    "W": {3: 80, 4: 399, 5: 1993},
    "7": {3: 65, 4: 249, 5: 1245},
    "D": {3: 50, 4: 164, 5: 827},
    "BELL": {3: 40, 4: 124, 5: 498},
    "BAR": {3: 32, 4: 80, 5: 329},
    "A": {3: 17, 4: 40, 5: 164},
    "K": {3: 12, 4: 32, 5: 124},
    "Q": {3: 8, 4: 24, 5: 80},
    "J": {3: 6, 4: 16, 5: 65},
}
PAYLINES = np.asarray([
    [0, 0, 0, 0, 0], [1, 1, 1, 1, 1], [2, 2, 2, 2, 2],
    [0, 1, 2, 1, 0], [2, 1, 0, 1, 2], [0, 0, 1, 2, 2],
    [2, 2, 1, 0, 0], [1, 0, 1, 2, 1], [1, 2, 1, 0, 1],
    [0, 1, 1, 1, 2], [2, 1, 1, 1, 0], [0, 1, 0, 1, 2],
    [2, 1, 2, 1, 0], [1, 1, 0, 1, 1], [1, 1, 2, 1, 1],
    [0, 2, 1, 0, 2], [2, 0, 1, 2, 0], [0, 0, 2, 0, 0],
    [2, 2, 0, 2, 2], [1, 0, 0, 0, 1],
], dtype=np.int8)
POWERS = np.asarray([9 ** 4, 9 ** 3, 9 ** 2, 9, 1], dtype=np.int64)


def evaluate_tuple(values):
    base = WILD
    for value in values:
        if value != WILD:
            base = value
            break
    matched = 0
    for value in values:
        if value == base or value == WILD:
            matched += 1
        else:
            break
    payout = 0
    if matched >= 3:
        payout = PAYTABLE[SYMBOLS[base]][matched]
    wild_prefix = 0
    for value in values:
        if value == WILD:
            wild_prefix += 1
        else:
            break
    if wild_prefix >= 3:
        payout = max(payout, PAYTABLE["W"][wild_prefix])
    return payout, wild_prefix


def create_luts():
    size = 9 ** 5
    payout = np.zeros(size, dtype=np.int32)
    trigger = np.zeros(size, dtype=np.int8)
    for index in range(size):
        value = index
        sequence = [0] * 5
        for position in range(4, -1, -1):
            sequence[position] = value % 9
            value //= 9
        payout[index], trigger[index] = evaluate_tuple(sequence)
    return payout, trigger


def load_visible_reels():
    path = os.path.join(os.path.dirname(__file__), "reels", "BR0.csv")
    columns = [[] for _ in range(5)]
    with open(path, newline="", encoding="utf-8") as handle:
        for row in csv.reader(handle):
            if len(row) != 5:
                raise RuntimeError("Every reel CSV row must contain five symbols")
            for reel, symbol in enumerate(row):
                columns[reel].append(SYMBOL_TO_ID[symbol.strip()])
    visible = []
    for reel in columns:
        size = len(reel)
        visible.append([[reel[(stop + row) % size] for row in range(3)]
                        for stop in range(size)])
    return np.asarray(visible, dtype=np.int8)


def draw_batch(rng, visible, count, payout_lut, trigger_lut):
    reels, stops_per_reel, _ = visible.shape
    stops = rng.integers(0, stops_per_reel, size=(count, reels), dtype=np.int32)
    board = np.empty((count, reels, 3), dtype=np.int8)
    for reel in range(reels):
        board[:, reel, :] = visible[reel, stops[:, reel], :]
    total = np.zeros(count, dtype=np.int64)
    best_trigger = np.zeros(count, dtype=np.int8)
    reel_index = np.arange(5)
    for rows in PAYLINES:
        sequence = board[:, reel_index, rows]
        encoded = sequence.astype(np.int64) @ POWERS
        total += payout_lut[encoded]
        best_trigger = np.maximum(best_trigger, trigger_lut[encoded])
    return total, best_trigger


def simulate(rounds, seed, batch_size=200_000):
    rng = np.random.default_rng(seed)
    visible = load_visible_reels()
    payout_lut, trigger_lut = create_luts()
    base_payout = 0
    feature_payout = 0
    trigger_count = 0
    sessions = []

    remaining = rounds
    while remaining > 0:
        count = min(batch_size, remaining)
        payouts, triggers = draw_batch(rng, visible, count, payout_lut, trigger_lut)
        base_payout += int(payouts.sum())
        activated = int(np.count_nonzero(triggers >= 3))
        trigger_count += activated
        if activated:
            sessions.extend([30] * activated)
        remaining -= count

    retrigger_count = 0
    feature_spin_count = 0
    maximum_feature_length = 0
    if sessions:
        spins_remaining = np.asarray(sessions, dtype=np.int16)
        total_awarded = np.full(len(sessions), 30, dtype=np.int16)
        session_length = np.zeros(len(sessions), dtype=np.int16)
        while np.any(spins_remaining > 0):
            active = np.flatnonzero(spins_remaining > 0)
            spins_remaining[active] -= 1
            session_length[active] += 1
            payouts, triggers = draw_batch(rng, visible, len(active), payout_lut, trigger_lut)
            feature_payout += int(payouts.sum())
            feature_spin_count += len(active)
            candidates = active[(triggers >= 3) & (total_awarded[active] < 90)]
            if len(candidates):
                add = np.minimum(10, 90 - total_awarded[candidates]).astype(np.int16)
                total_awarded[candidates] += add
                spins_remaining[candidates] += add
                retrigger_count += int(np.count_nonzero(add))
        maximum_feature_length = int(session_length.max())

    stake = rounds * 20
    report = {
        "paidRounds": rounds,
        "stakeCredits": stake,
        "basePayoutCredits": base_payout,
        "featurePayoutCredits": feature_payout,
        "baseRtp": base_payout / stake,
        "featureRtp": feature_payout / stake,
        "combinedRtp": (base_payout + feature_payout) / stake,
        "triggerCount": trigger_count,
        "triggerFrequency": trigger_count / rounds,
        "featureSpinCount": feature_spin_count,
        "averageFeatureLength": feature_spin_count / max(1, trigger_count),
        "retriggerCount": retrigger_count,
        "maximumFeatureLength": maximum_feature_length,
        "seed": seed,
    }
    return report


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--rounds", type=int, default=10_000_000)
    parser.add_argument("--seed", type=int, default=20260724)
    parser.add_argument("--output", default="feature-rtp-report.json")
    args = parser.parse_args()
    started = time.time()
    report = simulate(args.rounds, args.seed)
    report["elapsedSeconds"] = round(time.time() - started, 3)
    with open(args.output, "w", encoding="utf-8") as handle:
        json.dump(report, handle, indent=2, sort_keys=True)
    print(json.dumps(report, indent=2, sort_keys=True))
    if not 0.945 <= report["combinedRtp"] <= 0.965:
        raise SystemExit("Combined RTP is outside the approved 94.5%-96.5% band")
    if report["maximumFeatureLength"] > 90:
        raise SystemExit("Feature length exceeded the configured cap")


if __name__ == "__main__":
    main()
