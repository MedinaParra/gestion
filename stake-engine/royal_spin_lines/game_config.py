"""Royal Spin 5x3 / 20-line configuration for Stake Engine Math SDK."""

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
        self.working_name = "Royal Spin Lines"
        self.wincap = 2050.0
        self.win_type = "lines"
        self.rtp = 0.954796451
        self.construct_paths()

        self.num_reels = 5
        self.num_rows = [3] * self.num_reels

        # Multipliers are expressed against the total 20-line stake.
        # Android credit values are these multipliers x 20.
        self.paytable = {
            (5, "W"): 102.50, (4, "W"): 20.50, (3, "W"): 4.10,
            (5, "7"): 64.00, (4, "7"): 12.80, (3, "7"): 3.35,
            (5, "D"): 42.50, (4, "D"): 8.45, (3, "D"): 2.55,
            (5, "BELL"): 25.60, (4, "BELL"): 6.40, (3, "BELL"): 2.05,
            (5, "BAR"): 16.90, (4, "BAR"): 4.10, (3, "BAR"): 1.65,
            (5, "A"): 8.45, (4, "A"): 2.05, (3, "A"): 0.85,
            (5, "K"): 6.40, (4, "K"): 1.65, (3, "K"): 0.60,
            (5, "Q"): 4.10, (4, "Q"): 1.25, (3, "Q"): 0.40,
            (5, "J"): 3.35, (4, "J"): 0.80, (3, "J"): 0.30,
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
        # S is deliberately absent from reels; it keeps the generic draw_board
        # path compatible without introducing a feature round in v1.
        self.special_symbols = {"wild": ["W"], "scatter": ["S"]}
        self.freespin_triggers = {
            self.basegame_type: {6: 0},
            self.freegame_type: {6: 0},
        }
        self.anticipation_triggers = {
            self.basegame_type: 5,
            self.freegame_type: 5,
        }

        self.reels = {
            "BR0": self.read_reels_csv(os.path.join(self.reels_path, "BR0.csv"))
        }
        self.padding_reels[self.basegame_type] = self.reels["BR0"]

        base_conditions = {
            "reel_weights": {self.basegame_type: {"BR0": 1}},
            "force_wincap": False,
            "force_freegame": False,
        }
        zero_conditions = {
            "reel_weights": {self.basegame_type: {"BR0": 1}},
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
                    Distribution(criteria="0", quota=0.60, win_criteria=0.0, conditions=zero_conditions),
                    Distribution(criteria="basegame", quota=0.40, conditions=base_conditions),
                ],
            )
        ]
