# URL-ML Final Baseline Report

Baseline is the existing 15-feature deterministic pipeline and historical TFLite artifact; this campaign independently evaluates the same merged dataset with domain-disjoint splits. The original training script uses one group holdout and does not provide a clean validation/test separation.

Dataset: `cbabd318016cbe8f047c511431a44dbb6b74ab7796780ff73a359b8012a98380`

- Rows: 238168; labels: `{0: 136807, 1: 101361}`; sources: `{'original': 235368, 'hard': 2800}`
- Exact duplicates: 0; normalized duplicates: 1173; registrable-domain groups: 169636
- Historical artifact: `model/model.tflite` (preserved)
