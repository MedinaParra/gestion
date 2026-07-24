"""Royal Spin 2.0: 5x3 / 20 lines with line-WILD free spins."""

import os

from src.config.betmode import BetMode
from src.config.config import Config
from src.config.distributions import Distribution


class GameConfig(Config):
    _instance = None

    def __new__(cls):
        if cls._instance is None:
            cls._instance = super().__new__(cls)
        return cls._instance

    def __init__(self):
        super().__init__()
        self.game_id = "royal_spin_lines"
        self.provider_number = 0
        self.working_name = "Royal Spin 2.0 Free Spins"
        self.wincap = 5000.0
        self.win_type = "lines"
        self.rtp = 0.9548
        self.construct_paths()

        self.num_reels = 5
        self.num_rows = [3] * self.num_reels

        # Multipliers are expressed against the total 20-line stake.
        self.paytable = {
            (5, "W"): 98.35, (4, "W"): 19.70, (3, "W"): 3.95,
            (5, "7"): 61.45, (4, "7"): 12.30, (3, "7"): 3.20,
            (5, "D"): 40.80, (4, "D"): 8.10, (3, "D"): 2.45,
            (5, "BELL"): 24.60, (4, "BELL"): 6.10, (3, "BELL"): 1.95,
            (5, "BAR"): 16.25, (4, "BAR"): 3.95, (3, "BAR"): 1.60,
            (5, "A"): 8.10, (4, "A"): 1.95, (3, "A"): 0.85,
            (5, "K"): 6.10, (4, "K"): 1.60, (3, "K"): 0.60,
            (5, "Q"): 3.95, (4, "Q"): 1.20, (3, "Q"): 0.40,
            (5, "J"): 3.20, (4, "J"): 0.80, (3, "J"): 0.30,
        }

        self.paylines = {
            1: [0, 0, 0, 0, 0], 2: [1, 1, 1, 1, 1], 3: [2, 2, 2, 2, 2],
            4: [0, 1, 2, 1, 0], 5: [2, 1, 0, 1, 2], 6: [0, 0, 1, 2, 2],
            7: [2, 2, 1, 0, 0], 8: [1, 0, 1, 2, 1], 9: [1, 2, 1, 0, 1],
            10: [0, 1, 1, 1, 2], 11: [2, 1, 1, 1, 0], 12: [0, 1, 0, 1, 2],
            13: [2, 1, 2, 1, 0], 14: [1, 1, 0, 1, 1], 15: [1, 1, 2, 1, 1],
            16: [0, 2, 1, 0, 2], 17: [2, 0, 1, 2, 0], 18: [0, 0, 2, 0, 0],
            19: [2, 2, 0, 2, 2], 20: [1, 0, 0, 0, 1],
        }

        self.include_padding = True
        # S remains absent. WILD line-prefix detection is implemented in game_override.py.
        self.special_symbols = {"wild": ["W"], "scatter": ["S"]}
        self.freespin_triggers = {
            self.basegame_type: {3: 30, 4: 30, 5: 30},
            self.freegame_type: {3: 10, 4: 10, 5: 10},
        }
        self.anticipation_triggers = {
            self.basegame_type: 2,
            self.freegame_type: 2,
        }

        br0 = self.read_reels_csv(os.path.join(self.reels_path, "BR0.csv"))
        self.reels = {"BR0": br0, "FR0": br0}
        self.padding_reels[self.basegame_type] = self.reels["BR0"]
        self.padding_reels[self.freegame_type] = self.reels["FR0"]

        regular = {
            "reel_weights": {
                self.basegame_type: {"BR0": 1},
                self.freegame_type: {"FR0": 1},
            },
            "force_wincap": False,
            "force_freegame": False,
        }
        zero = {
            "reel_weights": {
                self.basegame_type: {"BR0": 1},
                self.freegame_type: {"FR0": 1},
            },
            "force_wincap": False,
            "force_freegame": False,
        }

        self.bet_modes = [
            BetMode(
                name="base",
                cost=1.0,
                rtp=self.rtp,
                max_win=self.wincap,
                auto_close_disabled=False,
                is_feature=True,
                is_buybonus=False,
                distributions=[
                    Distribution(criteria="0", quota=0.55, win_criteria=0.0, conditions=zero),
                    Distribution(criteria="basegame", quota=0.45, conditions=regular),
                ],
            )
        ]
