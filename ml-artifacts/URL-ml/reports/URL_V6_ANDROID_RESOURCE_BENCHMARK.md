# URL-ML V6 Android Resource Benchmark

The lexical V6 artifact is 2,523,835 bytes (model plus vectorizer), under 10 MB, and repeated serialized inference is deterministic. It is not Android-qualified: Python TF-IDF serialization is not a production Kotlin artifact and no TFLite conversion was performed. The structural HGB V6 bundle is 921,407 bytes and uses the existing 92-feature offline scalar path, but fails the protected benign-FPR gate. Native Android timing and memory qualification remain future work after the data blocker is resolved.
