# Demo Guide

## Goal

Demonstrate that Sentinel AI can distinguish a normal link from a deterministic malicious test fixture, explain the result, and retain both scans in local history.

Allow three to five minutes for the complete flow.

## Preparation

- Install and open the debug build.
- Complete the initial permission screen.
- Confirm that **Real-time protection** is enabled.
- Keep the device connected to the presentation display.
- Do not continue from the malicious test result into an external browser.

## Demo Flow

### 1. Open the App

Open Sentinel AI and briefly show the dashboard. Point out the protection status, manual scanner, alert area, and history navigation.

### 2. Scan a Safe Link

1. Open the scanner.
2. Select the link scan mode.
3. Enter:

   ```text
   https://www.google.com/
   ```

4. Start the scan.

Expected result:

- The app shows no strong threat evidence.
- The decision should remain `ALLOW` under the bundled rules and normal provider conditions.
- The screen shows a risk score and a **Continue** action.

Do not present the result as a guarantee that every page on a trusted domain is safe; it is the output for this specific URL and available evidence.

### 3. Scan a Phishing Test Link

1. Return to the scanner.
2. Enter the deterministic development fixture:

   ```text
   https://malicious.com/phish
   ```

3. Start the scan.

Expected result:

- The bundled local heuristics return malicious evidence.
- The final decision is `BLOCK`.
- The screen explains that critical threat evidence was detected.
- No browser continuation action is offered.

This URL is used only as a string fixture. Do not open or visit it outside the Sentinel AI result screen.

### 4. Compare the Results

Highlight the differences between the two scans:

- Risk score and risk level.
- `ALLOW` versus `BLOCK` action.
- Explanation and evidence source.
- Available next action.

### 5. Show History

1. Close the result screen.
2. Open **History** from the app navigation.
3. Show the recent safe and malicious scan entries.
4. Open an entry to show the stored source, time, risk level, and explanation.

The history is backed by the local Room database and remains available after restarting the app.

## Optional Notification Segment

If notification access is enabled, send a controlled test message from a supported app with obvious urgency and credential language. For example:

```text
Urgent: verify your account and share the OTP immediately.
```

Show the generated warning and the corresponding history record. Use only test accounts and synthetic content during the demonstration.

## Presentation Notes

- Model output can vary when the model asset changes, so describe relative risk rather than promising a fixed percentage.
- Local heuristics and TFLite inference run fully offline.
- The malicious fixture remains deterministic because it is recognized by the bundled local heuristics.

