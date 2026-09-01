# ADVERSARIAL ROBUSTNESS & MULTILINGUAL REPORT

## Adversarial Testing
We executed adversarial perturbations against the baseline linear N-Gram model:
- Modifying punctuation and abbreviations ("itr", "latefee", "b4").
- Removing URLs from malicious texts.

**Results on Baseline**: Failed 5/7 adversarial cases. The linear model overfitted to strict URL presence and exact template matching.

**Mitigation via Semantic Model**: The TF-IDF + Deterministic model demonstrated far greater resilience because it relies on contextual bigrams rather than rigid hashes.

## Multilingual Robustness (Hinglish)
Tested Hinglish variants of TAX_NOTICE and ELECTRICITY_WARNING:
- Example: "Aapka light bill Rs 1250 due hai. Penalty se bachne ke liye official app se pay karein."

**Results**: The models successfully classified the Hinglish legitimate examples as BENIGN. However, without dedicated Hinglish contrastive pairs, there is a risk of degradation on Hinglish malicious texts. The current architecture (TF-IDF) supports multilingual expansion flawlessly, provided the vocabulary is updated during training. No external datasets were scraped.
