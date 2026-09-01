# URL-ML Champion V7 Android Deployment Package
**Package**: `com.sentinel.url`  
**Model Version**: V7 Champion (Frozen Production Model)  
**Readiness Level**: `JVM_VERIFIED_ANDROID_READY`  

---

## 1. Package Architecture

```
android-runtime/url/
├── assets/
│   └── v7_champion_portable.json        # 1,005 KB self-contained JSON bundle
├── src/main/kotlin/com/sentinel/url/
│   ├── UrlFeatureExtractor.kt          # 67 deterministic features extraction
│   ├── HistGbmTreeEvaluator.kt         # 350 HistGradientBoosting decision trees
│   ├── SafeDomainAdjudicator.kt        # Safe-domain gated rule + thresholding
│   ├── SimpleJsonParser.kt             # Zero-dependency JSON parser
│   └── UrlScanner.kt                   # Unified high-level API
└── src/test/kotlin/com/sentinel/url/
    └── UrlParityTest.kt                # 151 golden records verification suite
```

---

## 2. API Usage in Android

```kotlin
import com.sentinel.url.UrlScanner
import android.content.Context

class SecurityService(context: Context) {
    private val scanner: UrlScanner

    init {
        // Load portable bundle from assets
        val json = context.assets.open("v7_champion_portable.json")
            .bufferedReader().use { it.readText() }
        scanner = UrlScanner.fromJson(json)
    }

    fun inspectUrl(url: String) {
        val result = scanner.scan(url)
        if (result.isMalicious) {
            // Block or alert user
            println("MALICIOUS URL DETECTED: " + result.url)
            println("Risk Probability: " + result.probability)
            println("Raw Probability: " + result.rawProbability)
            println("Triggered Safe Gate: " + result.gatedBySafeDomain)
        } else {
            // Allow
            println("URL is BENIGN: " + result.url)
        }
    }
}
```

---

## 3. Key Specifications
- **Input**: Raw URL string (`String`)
- **Features**: 67 deterministic features (Entropy, TLD, Punycode, IP, Brand keywords, Subdomain counts)
- **Model**: HistGradientBoosting (350 trees, loss='log_loss')
- **Decision Threshold**: $\tau = 0.225887$
- **Safe-Domain Gate**: Clamps risk to $0.001$ if `Safe_Brand_Domain == 1.0` and `raw_p < 0.80`.
- **Runtime Dependencies**: `kotlin-stdlib` only (Zero external dependencies).
- **Latency**: 0.14 ms mean.