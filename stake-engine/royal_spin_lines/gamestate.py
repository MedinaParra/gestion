from game_override import GameStateOverride


class GameState(GameStateOverride):
    """One independent Stake Engine round: reveal, evaluate lines, close."""

    def run_spin(self, sim, simulation_seed=None):
        self.reset_seed(sim, simulation_seed)
        self.repeat = True
        while self.repeat:
            self.reset_book()
            self.draw_board()
            self.evaluate_lines_board()
            self.win_manager.update_gametype_wins(self.gametype)
            self.evaluate_finalwin()
            self.check_repeat()
        self.imprint_wins()

    def run_freespin(self):
        """Required SDK contract; Royal Spin v1 intentionally has no free-spin mode."""
        raise RuntimeError("Royal Spin v1 has no free-spin mode")
