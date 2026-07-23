# Royal Spin — Stake Engine math

This folder contains the game-specific files for the official MIT-licensed
[`StakeEngine/math-sdk`](https://github.com/StakeEngine/math-sdk).

## Model

- 5 reels × 3 visible rows
- 20 fixed paylines
- left-to-right wins with 3, 4 or 5 matching symbols
- `W` substitutes for every paying symbol
- one stateless `base` mode, cost `1.0`
- theoretical target RTP: `0.954796451` (95.4796451%)
- maximum full-board multiplier: 2050× total stake

The Android demo mirrors the same symbol weights, paylines and paytable. Its
paytable is expressed in integer credits per line, while the Python paytable is
normalized against the total 20-line stake.

## Run with the official SDK

```bash
git clone https://github.com/StakeEngine/math-sdk.git
cp -R stake-engine/royal_spin_lines math-sdk/games/royal_spin_lines
cd math-sdk
make setup
python3 games/royal_spin_lines/run.py
```

This is a development/demo model only. Publishing to a real-money platform
requires Stake Engine review, jurisdictional compliance, independent math
verification and responsible-gaming controls.
