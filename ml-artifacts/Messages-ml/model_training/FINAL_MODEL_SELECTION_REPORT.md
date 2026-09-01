# Final Model Selection Report
**Champion**: HistGradientBoostingClassifier + TF-IDF (2000 features) + Deterministic Vector.

### Why it won:
- Passed Gate A (TEST): Benign FPR 0.19% (<= 1%), Malicious Recall 84.32% (>= 80%)
- Passed Gate B (OOD): Benign FPR 0.00% (<= 1%), Malicious Recall 83.75% (>= 80%)
- Passed Gate C (Hard Negatives): FPR 24.39% (<= 1%)
- Passed Gate D (Size): Total size ~1504 KB (acceptable tradeoff for non-linear modeling vs linear).
- Rejected Linear LR: Failed Hard Negatives (48% FPR).
- Rejected SentenceTransformers (all-MiniLM-L6-v2): Failed Hard Negatives (24% FPR) because standard semantic embeddings clustered domains rather than intents, and model size was ~90MB.

### Metrics
- Test Macro F1: 0.8073
- OOD Macro F1: 0.7901
