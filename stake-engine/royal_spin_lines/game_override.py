from game_executables import GameExecutables
from src.events.events import fs_trigger_event


class GameStateOverride(GameExecutables):
    """Royal Spin hooks: feature triggers are consecutive WILDs on an active line."""

    MAX_FEATURE_SPINS = 90

    def assign_special_sym_function(self):
        # Wild substitution itself is handled by the shared lines evaluator.
        self.special_symbol_functions = {}

    def wild_line_count(self) -> int:
        """Return the longest left-to-right WILD prefix among all active paylines."""
        best = 0
        for rows in self.config.paylines.values():
            count = 0
            for reel, row in enumerate(rows):
                if self.board[reel][row].name == "W":
                    count += 1
                else:
                    break
            best = max(best, count)
        return best

    def check_fs_condition(self, scatter_key: str = "scatter") -> bool:
        return self.wild_line_count() >= 3 and not self.repeat

    def run_freespin_from_base(self, scatter_key: str = "scatter") -> None:
        count = min(5, self.wild_line_count())
        self.record({"kind": count, "symbol": "wild_line", "gametype": self.gametype})
        self.tot_fs = self.config.freespin_triggers[self.gametype][count]
        fs_trigger_event(self, basegame_trigger=True, freegame_trigger=False)
        self.run_freespin()

    def update_fs_retrigger_amt(self, scatter_key: str = "scatter") -> None:
        count = min(5, self.wild_line_count())
        requested = self.config.freespin_triggers[self.gametype][count]
        add = max(0, min(requested, self.MAX_FEATURE_SPINS - self.tot_fs))
        if add > 0:
            self.tot_fs += add
            fs_trigger_event(self, freegame_trigger=True, basegame_trigger=False)

    def check_repeat(self):
        super().check_repeat()
        if self.repeat is False:
            win_criteria = self.get_current_betmode_distributions().get_win_criteria()
            if win_criteria is not None and self.final_win != win_criteria:
                self.repeat = True
                return
            if win_criteria is None and self.final_win == 0:
                self.repeat = True
