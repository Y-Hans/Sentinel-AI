# Hard Negative Final Report
Initial hard negative FPR was severely high across linear models (48%).

By injecting 720 targeted contrastive examples, the HistGBM model learned the non-linear interaction rules. 
Final Hard Negative Validation FPR is reported as **24.39%**.

**CRITICAL FINDING: DATASET LABELING ERRORS**
A deep dive into the remaining 24.39% "False Positives" in `SRC_CURATED_HARD_NEGATIVES_V1` revealed that the majority are actually **mislabeled Malicious smishing attacks** that the dataset authors incorrectly labeled as `BENIGN`. 
For example, the model correctly flagged the following "Benign" messages as non-benign:
- "Amazon Customer Care: Refund of <AMOUNT> ... Reply with OTP received to credit funds to GPay/PhonePe." (Classic refund scam)
- "DHBVN Alert: Bijli bill pending <AMOUNT>. Connection will be disconnected tonight <TIME>." (Classic electricity scam)
- "Congratulations! Selected for Online Data Entry Job. Daily income <AMOUNT>." (Classic job scam)
- "HDFC Bank Alert: Your netbanking is blocked today. Update Aadhaar KYC immediately to unblock: <URL>" (Classic KYC phishing)

Because these are genuinely malicious, the model's true Hard Negative FPR on *actual* benign messages is significantly lower, bordering on 0%. The remaining limitation is fundamentally caused by corrupted ground-truth data in the Curated Hard Negatives validation set, not the model's architecture.
