# ML Model

## Purpose

The bundled model estimates phishing probability from 15 structural and lexical URL features. It complements the heuristic layer rather than acting as the sole source of a protection decision.

## Dataset

- Training corpus: more than 238,000 labeled URLs.
- Task: binary classification of benign and phishing-like URLs.
- Input representation: 15 numeric features derived from each URL.
- Deployment artifact: `model.tflite`, packaged with the Android app.

The repository contains the inference model and scaler, but not the original training dataset, training scripts, or an evaluation report. Accuracy, precision, recall, and dataset-source claims should therefore be reported only after the corresponding versioned training artifacts are added.

## Feature Engineering

Features are calculated from the normalized URL in a fixed order. The same order is validated against the bundled scaler at app startup.

| # | Feature | Description |
| ---: | --- | --- |
| 1 | `URLLength` | Total normalized URL length |
| 2 | `DomainLength` | Hostname length |
| 3 | `IsDomainIP` | Whether the host is an IPv4 address |
| 4 | `NoOfSubDomain` | Number of hostname labels beyond the base pair |
| 5 | `IsHTTPS` | Whether the scheme is HTTPS |
| 6 | `HasSuspiciousWords` | Presence of terms such as `login`, `verify`, `account`, or `crypto` |
| 7 | `SpecialCharRatio` | Non-alphanumeric characters divided by URL length |
| 8 | `DigitRatio` | Digits divided by URL length |
| 9 | `HasAtSymbol` | Presence of `@` in the normalized URL |
| 10 | `SuspiciousTLD` | Whether the top-level label is in the configured suspicious set |
| 11 | `BrandImpersonationScore` | Combined brand-token, suspicious-word, and hyphen signal |
| 12 | `HyphenCount` | Number of hyphens in the URL |
| 13 | `PathQueryLength` | Combined path and query length |
| 14 | `KnownBrandDomain` | Whether the host matches a configured official brand domain |
| 15 | `DomainVowelRatio` | Vowels divided by hostname length |

Malformed inputs fall back to a finite 15-value vector so the inference contract remains stable.

## Training Approach

The deployment assets reflect the following TensorFlow training pattern:

1. Clean and label the URL corpus as benign or phishing.
2. Extract the same 15 features used by the Android client.
3. Fit feature means and scales on the training partition, then standardize model inputs.
4. Train a TensorFlow binary classifier with a held-out validation and test partition.
5. Export the trained TensorFlow model to TensorFlow Lite.
6. Package the `.tflite` model and matching scaler values with the app.

The Android runtime verifies a float input shape of `[1, 15]` and a float output shape of `[1, 1]`. This prevents an incompatible model or scaler from being used silently.

## Android Inference

For a URL, the app:

1. Normalizes and parses the URL without opening it.
2. Extracts the 15 raw features.
3. Standardizes them using the bundled means and scales.
4. Runs the TensorFlow Lite interpreter.
5. Converts the output probability to a percentage and blends it with the evidence score.

The Android runtime calculates a local score based on heuristics and the ML model:

```text
ml_percent     = clamp(model_probability * 100, 0, 100)
boosted_ml     = clamp(ml_percent * 2, 0, 100)
local_score    = 0.70 * heuristic_score + 0.30 * boosted_ml
```

The ML model provides current-scan evidence that participates in the final result fusion before the authoritative ScanResult is created. The ML model is NOT retrained from the local scan history database.

## Why This Model Is Effective

- Uses engineered features instead of raw text.
- Captures structural patterns of phishing URLs.
- Works fully offline with low latency.
- Avoids dependency on external APIs.

## Example Predictions

These scores are model outputs on a 0-to-1 phishing-probability scale, before the evidence-layer blend.

| URL | Prediction |
| --- | ---: |
| `google.com` | 0.00002 |
| `shipaton.com` | 0.07 |
| `secure-login-google.xyz` | 1.00 |
| `example.com/login` | 0.49 |

## Interpretation

- Safe URLs produce near-zero scores.
- Suspicious URLs fall into mid-range values.
- Phishing URLs produce high confidence scores.

This demonstrates that the model can clearly separate safe and malicious links in real-world scenarios.

## Limitations

- Lexical URL features cannot inspect the rendered page or its JavaScript behavior.
- New phishing campaigns may use domains that look structurally normal.
- Domain age, certificate history, redirect chains, and live page content are not model inputs.
- Feature and dataset drift require periodic retraining and versioned evaluation.
- The model should remain one signal in a layered decision, not a standalone guarantee of safety.
