# Messages-ML Champion V2 Android Deployment Package
**Package**: `com.sentinel.messages`  
**Model Version**: V2 Champion (Frozen Production Model)  
**Readiness Level**: `JVM_VERIFIED_ANDROID_READY`  

---

## 1. Package Architecture

```
android-runtime/messages/
├── assets/
│   ├── champion_v2_word_vocab_idf.json   # 1,500 word vocabulary + IDF + stop words (34 KB)
│   ├── champion_v2_char_vocab_idf.json   # 500 char_wb vocabulary + IDF (13 KB)
│   ├── champion_v2_scaler.json           # 2,070 mean & scale parameters (87 KB)
│   └── champion_v2_trees.json            # 309 decision trees across 103 iterations (2,407 KB)
├── src/main/kotlin/com/sentinel/messages/
│   ├── TextNormalizer.kt                 # NFKD normalizer, homoglyph mapping, script detector
│   ├── SenderParser.kt                   # DLT header parser & Indian entity resolver
│   ├── MessageFeatureExtractor.kt        # 70 deterministic features extraction
│   ├── DualTfidfVectorizer.kt           # Word (1,500) + Char_wb (500) TF-IDF vectorizer
│   ├── FeatureScaler.kt                  # 2,070 StandardScaler
│   ├── MultiClassTreeEvaluator.kt        # 309 HistGradientBoosting decision trees + Softmax
│   ├── MessageAdjudicator.kt             # Threshold tau = 0.704 on non-benign probability
│   ├── MessageAssetLoader.kt             # Fast JSON asset loader
│   └── MessageScanner.kt                 # Unified high-level API
└── src/test/kotlin/com/sentinel/messages/
    └── MessageParityTest.kt              # 116 golden records verification suite
```

---

## 2. API Usage in Android

```kotlin
import com.sentinel.messages.MessageScanner
import android.content.Context

class SmsReceiverHelper(context: Context) {
    private val scanner: MessageScanner

    init {
        val wJson = context.assets.open("champion_v2_word_vocab_idf.json").bufferedReader().use { it.readText() }
        val cJson = context.assets.open("champion_v2_char_vocab_idf.json").bufferedReader().use { it.readText() }
        val sJson = context.assets.open("champion_v2_scaler.json").bufferedReader().use { it.readText() }
        val tJson = context.assets.open("champion_v2_trees.json").bufferedReader().use { it.readText() }

        scanner = MessageScanner.create(wJson, cJson, sJson, tJson, 0.704f)
    }

    fun onSmsReceived(body: String, sender: String?) {
        val result = scanner.scan(body, sender)
        when (result.label) {
            "MALICIOUS" -> {
                // High risk alert: Phishing / Disconnection threat / OTP Theft / Fake APK
                println("MALICIOUS SMS: P(Malicious)=" + result.probabilities[2])
            }
            "SUSPICIOUS_SPAM" -> {
                // Medium risk alert: Lottery / Reward Spam
                println("SUSPICIOUS SMS: P(Suspicious)=" + result.probabilities[1])
            }
            "BENIGN" -> {
                // Legitimate bank alert, OTP, delivery, casual message
                println("BENIGN SMS: P(Benign)=" + result.probabilities[0])
            }
        }
    }
}
```

---

## 3. Key Specifications
- **Input**: Message body text (`String`) and optional sender header (`String?`)
- **Pipeline Order**:
  1. `TextNormalizer` & `SenderParser`
  2. `MessageFeatureExtractor`: 70 deterministic features
  3. `DualTfidfVectorizer`: 1,500 word features (n=1,2, stop-words) + 500 char_wb features (n=3,4,5)
  4. Concatenation: 2,070-dim feature vector
  5. `FeatureScaler`: Standardization $(x - \mu) / \sigma$ in 64-bit IEEE precision
  6. `MultiClassTreeEvaluator`: 103 iterations $\times$ 3 classes (309 trees) + 3-class softmax
  7. `MessageAdjudicator`: Decision threshold $\tau = 0.704$ on $P(\text{SUSPICIOUS\_SPAM}) + P(\text{MALICIOUS})$
- **Runtime Dependencies**: `kotlin-stdlib` only (Zero Python, Zero C++, Zero JNI).
- **Latency**: 0.48 ms mean.