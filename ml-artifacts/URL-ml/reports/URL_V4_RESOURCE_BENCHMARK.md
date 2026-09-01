# URL-ML V4 Resource Benchmark

Benchmarked the compact deterministic 92-feature ExtraTrees challenger `v4_weight40_extra40` on 1,000 hard-source URLs. The serialized artifact is 4,762,912 bytes (4.54 MiB). Measured model-only prediction latency was approximately 0.015 ms/URL on the development host; deterministic extraction is sub-millisecond per URL in the same harness. This is within the <10 MB and <10 ms engineering targets, although Android-device confirmation remains required before packaging.

The 1,592-feature hashed logistic artifact is only 16,418 bytes, but its hard-source behavior is unacceptable. Resource compactness therefore does not compensate for the generalization failure.
