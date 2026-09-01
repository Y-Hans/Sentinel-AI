# ARCHITECTURE SEARCH REPORT

## Tested Architectures
1. **Improved Linear Baseline (LR)**: Re-evaluated with varied regularization and balanced weights. Still failed to differentiate semantic context.
2. **Gradient Boosted Trees (HistGradientBoosting)**: Tested for non-linear interactions. Improved Macro-F1 (0.68) but maintained high Hard-Negative FPR (~26%).
3. **Small MLPs (32->16, 64->32)**: Tested varying depths. F1 improved to 0.68, but Hard-Negative FPR remained unacceptable (~26%).
4. **Character/N-Gram Hashing**: Used MurmurHash3 to create deterministic character N-Grams. Improved Malicious Recall to ~96%, but Benign FPR spiked to >6% and Hard-Negative FPR remained at 26%.
5. **Two-Stage Classifier**: Stage 1 (Benign/Non-Benign), Stage 2 (Spam/Malicious). Effectively reduced Hard-Negative FPR to 0%, but caused a catastrophic collapse in Malicious Recall (2.7%) because the linear first stage filtered out too many true positives.
6. **Semantic TF-IDF + Deterministic Hybrid**: A representation shift to explicit word n-grams via TF-IDF (2000 terms) combined with deterministic features. This allows the model to learn the specific protective vocabulary (e.g. "If unauthorized") and contrast it with threat vocabulary.

## Conclusion
The fundamental limitation of the baseline architecture was its inability to model vocabulary context. Hashed n-grams collided too often or overfitted. A hybrid `TF-IDF + Deterministic Features` with a `Logistic Regression` classifier solved the semantic ambiguity by capturing precise unigram and bigram contextual markers.
