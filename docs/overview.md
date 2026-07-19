# Project Overview

## Problem Statement

Phishing and social-engineering attacks often reach users through ordinary links and message notifications. Attackers imitate trusted organizations, create a sense of urgency, and direct victims to pages that request passwords, one-time codes, payment details, or personal information.

Most users have to judge these signals at the moment they are about to click or respond. That decision is difficult on a small screen, especially when the sender appears familiar or the destination has been deliberately obscured.

## Why Phishing Is Dangerous

Phishing can lead to:

- Credential and account theft.
- Unauthorized payments and financial loss.
- Exposure of personal or identity information.
- Installation of unwanted or malicious files.
- Continued impersonation of banks, public agencies, employers, or known contacts.

The attack does not always depend on a software vulnerability. It often succeeds by pressuring the user into taking an unsafe action before they have time to verify the request.

## What Sentinel AI Solves

Sentinel AI adds a decision point before a user opens a link and an additional check when supported message notifications arrive. It is designed to answer three practical questions:

- Does this content contain known risk signals?
- How serious is the combined evidence?
- What should the user do next?

The app returns a risk score, a clear decision, and an explanation instead of presenting raw technical indicators.

## High-Level Solution

Sentinel AI uses a local-first Android pipeline:

1. **Capture:** Receive a link, shared file reference, selected text, manual scan request, or supported notification.
2. **Normalize:** Convert the input into a stable form and extract relevant fields.
3. **Analyze:** Apply URL or message heuristics and, for URLs, run an on-device TensorFlow Lite model.
4. **Combine:** Incorporate available reputation evidence without allowing inconclusive lookups to imply safety.
5. **Decide:** Produce an `ALLOW`, `WARN`, or `BLOCK` result with supporting reasons.
6. **Record:** Save the result in the device's local history and display a warning when required.

The current repository focuses on the Android client and its on-device protection paths. Broader platform and model improvements are listed in [Future Scope](future-scope.md).

