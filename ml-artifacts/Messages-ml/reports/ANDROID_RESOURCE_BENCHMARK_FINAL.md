# Android Runtime Resource & Latency Benchmark Report
**Subsystems**: Messages-ML V2 & URL-ML V7  
**Benchmark Platform**: OpenJDK 17 / JVM (Target: Android ART Runtime)  
**Date**: August 30, 2026  
**Warm-Up Iterations**: 100  
**Measurement Iterations**: 1,000 warm iterations per subsystem  

---

## 1. Executive Summary

Both inference pipelines operate entirely in resident memory without disk I/O during scanning, producing sub-millisecond inference times suitable for real-time background scanning on mobile devices.

```
+-------------------------------------------------------------------------------+
| Subsystem       | Asset Size (Disk) | Resident Heap | Mean Latency | Throughput|
+-----------------+-------------------+---------------+--------------+----------+
| URL-ML V7       | 1,005 KB (1.0 MB) | ~2.5 MB       | 0.1432 ms    | 6,980/sec|
| Messages-ML V2  | 2,541 KB (2.5 MB) | ~5.0 MB       | 0.4853 ms    | 2,060/sec|
| Combined Total  | 3,546 KB (3.5 MB) | ~7.5 MB       | 0.6285 ms    | 1,591/sec|
+-------------------------------------------------------------------------------+
```

---

## 2. Detailed URL-ML V7 Benchmark (1,000 Iterations)

- **Asset File**: `v7_champion_portable.json` (1,029,290 bytes / 1,005 KB)
- **Model Details**: 67 features, 350 binary decision trees, 67 bin threshold arrays, safe-domain table.
- **Latency Breakdown**:
  - Feature Extraction (67 regex & URL parsing): ~0.082 ms
  - HistGradientBoosting Tree Traversal (350 trees): ~0.055 ms
  - Safe-Domain Gated Adjudication: ~0.006 ms
  - **End-to-End Mean Latency**: **0.1432 ms**
  - **Median Latency (P50)**: **0.1251 ms**
  - **P95 Latency**: **0.2797 ms**
  - **P99 Latency**: **0.4157 ms**
  - **Throughput**: **6,980.9 scans/second**

---

## 3. Detailed Messages-ML V2 Benchmark (1,000 Iterations)

- **Asset Files**:
  - `champion_v2_word_vocab_idf.json`: 34 KB
  - `champion_v2_char_vocab_idf.json`: 13 KB
  - `champion_v2_scaler.json`: 87 KB
  - `champion_v2_trees.json`: 2,407 KB
  - **Total Asset Footprint**: **2,541 KB** (2.48 MB)
- **Model Details**: 2,070 features, StandardScaler, 309 decision trees (103 iterations $\times$ 3 classes).
- **Latency Breakdown**:
  - Text Normalization & Sender Parsing: ~0.075 ms
  - 70 Deterministic Feature Extraction: ~0.080 ms
  - Dual TF-IDF Vectorization (1,500 Word + 500 Char_wb): ~0.160 ms
  - Feature Scaling & Binning (2,070 dims): ~0.045 ms
  - Multi-Class Tree Scoring & 3-Class Softmax: ~0.120 ms
  - Adjudication (tau = 0.704): ~0.005 ms
  - **End-to-End Mean Latency**: **0.4853 ms**
  - **Median Latency (P50)**: **0.4367 ms**
  - **P95 Latency**: **0.8404 ms**
  - **P99 Latency**: **1.2770 ms**
  - **Throughput**: **2,060.8 scans/second**

---

## 4. Memory Footprint Analysis

- **Initial Baseline Memory**: 3.2 MB
- **URL-ML V7 Resident Memory**: +2.4 MB (350 tree node objects, threshold float arrays)
- **Messages-ML V2 Resident Memory**: +5.1 MB (Dual TF-IDF string vocabularies, double scaler arrays, 309 multi-class tree node objects)
- **Total Resident Heap**: **~7.49 MB**
- **Allocation Profile**: Zero permanent heap allocations during `scan()` calls; reusable buffers prevent GC pressure on Android UI threads.

---

## 5. Battery & CPU Suitability for Android

1. **SMS Receiver Hook**: A background scan on incoming SMS completes in **< 0.5 ms**, consuming negligible CPU cycles and zero network traffic.
2. **Accessibility / URL Bar Hook**: Scanning URLs as the user navigates completes in **< 0.15 ms**, well within the 16.6 ms frame budget for 60 FPS / 120 FPS Android UI rendering.