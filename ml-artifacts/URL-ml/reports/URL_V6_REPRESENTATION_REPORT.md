# URL-ML V6 Representation Report

V6 retained the 92 deterministic Kotlin-portable host/path/query/interaction features for the structural experiment. The explicitly synthetic structural expansion did not improve the hard-source frontier. A second experiment used normalized URL character 2–5-gram TF-IDF with 60,000 features and logistic regression; it improved ordinary TEST metrics but degraded protected benign rejection to 69.832% FPR.

The result supports the V5 conclusion that adding URL-only representation capacity is not sufficient. Any future representation should be tested only alongside new adjudicated source coverage; deployment feasibility also requires replacing the Python TF-IDF artifact with a verified Kotlin-compatible implementation.
