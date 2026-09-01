import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
from feature_config import ALL_FEATURE_GROUPS
from autonomous_optimization import run_experiment

if __name__ == "__main__":
    exp_id = "EXP_002_WITH_NGRAMS"
    cfg = {"active_groups": ALL_FEATURE_GROUPS, "ngram_hash_bins": 128}
    model_kwargs = {"max_iter": 1000, "random_state": 42, "class_weight": "balanced"}
    run_experiment(exp_id, cfg, model_kwargs)
