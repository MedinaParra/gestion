"""Generate a small traceable Stake Engine math build for Royal Spin."""

from game_config import GameConfig
from gamestate import GameState
from src.state.run_sims import create_books
from src.write_data.write_configs import generate_configs


if __name__ == "__main__":
    config = GameConfig()
    gamestate = GameState(config)
    create_books(
        gamestate=gamestate,
        config=config,
        num_sim_args={"base": 5000},
        batching_size=1000,
        num_threads=2,
        compression=True,
        profiling=False,
    )
    generate_configs(gamestate)
    print(f"Royal Spin math generated. Target RTP: {config.rtp:.6%}")
