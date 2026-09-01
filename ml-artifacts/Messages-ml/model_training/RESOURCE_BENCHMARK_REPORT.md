# RESOURCE BENCHMARK REPORT

## Objective
To ensure the final Champion architecture (TF-IDF + Det Logistic Regression) is suitable for deployment within strict CPU and memory bounds, particularly targeting eventual on-device Android deployment.

## Metrics
- **Parameter Count**: 6,150 (2000 TF-IDF features + 50 Deterministic features * 3 classes)
- **Serialized Model Size**: 222.19 KB total (125.80 KB TF-IDF Vocab + 48.34 KB Model weights + 48.05 KB Scaler)
- **Inference Latency (p99 effectively, single batch)**: 0.81 ms on dev hardware CPU.

## Evaluation
The model is extremely lightweight. It occupies less than 250 KB on disk, uses negligible RAM during inference, and evaluates in less than a millisecond. This easily satisfies strict engineering budgets for mobile deployment and runs orders of magnitude faster/smaller than transformer-based semantic embeddings.
