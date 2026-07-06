Sentinel AI
AI Security Agent for Digital Public Safety

Hackathon: ET AI Hackathon 2026
Problem Statement 6: AI for Digital Public Safety: Defeating Counterfeiting, Fraud & Digital Arrest Scams

1. Problem Understanding

India is witnessing an unprecedented rise in cyber-enabled fraud, digital arrest scams, phishing attacks, financial fraud, impersonation scams, fake government notices, malicious links, deepfake-assisted fraud, and social engineering attacks.

The ET AI Hackathon problem statement highlights several critical issues:

Digital arrest scams are causing massive financial losses.
Fraudsters impersonate government officials, law enforcement agencies, banks, and financial institutions.
Victims are often manipulated through phone calls, WhatsApp messages, Telegram messages, SMS, emails, and fake websites.
Existing solutions are largely reactive and investigate incidents after money has already been lost.
Citizens lack real-time protection at the point of attack.
Law enforcement receives complaints only after victimization.

The key challenge is shifting from:

Reactive Fraud Investigation → Proactive Fraud Prevention

Sentinel AI addresses this challenge by becoming an always-on personal AI security guardian that warns users before they become victims.

2. Key Pain Points
Citizens
Communication Overload

Users receive:

SMS
Phone calls
WhatsApp messages
Telegram messages
Emails
Unknown links
Shared files

and cannot reliably determine which interactions are malicious.

Digital Arrest Scams

Victims cannot distinguish:

Real police communication
Fake government notices
Fake ED/CBI/Customs threats
Fraudulent legal claims
Social Engineering

Attackers exploit:

Fear
Urgency
Authority
Financial pressure

instead of technical vulnerabilities.

Link-Based Fraud

Users unknowingly open:

Phishing websites
Credential theft pages
Fake banking portals
Fake government portals
Malware Distribution

Fraudsters distribute:

APK files
PDF malware
Fake invoices
Malicious attachments
Lack of Real-Time Guidance

Current systems detect fraud after damage occurs.

Users need intervention before:

Opening a link
Downloading a file
Making a payment
Sharing sensitive information
3. Hackathon Success Criteria

A successful MVP should demonstrate:

Fraud Detection
Detect scam SMS
Detect scam emails
Detect suspicious WhatsApp messages
Detect suspicious Telegram messages
Digital Arrest Protection
Identify digital arrest scam patterns
Detect authority impersonation attempts
Warn users immediately
Link Intelligence
Analyze URLs before opening
Detect phishing indicators
Generate risk scores
File Intelligence
Analyze attachments
Flag suspicious files
User Safety
Prevent risky actions before execution
Provide simple explanations
Minimize false positives
Demo Success

A live demonstration showing:

Fraudulent message received
Sentinel AI analyzes threat
User receives warning
Dangerous action prevented
4. Product Vision
Mission

Protect every smartphone user from digital fraud using an AI-powered personal security guardian.

Vision Statement

Sentinel AI acts as a real-time AI security agent that continuously monitors digital communication channels, evaluates threats, explains risks, and intervenes before users become victims.

Instead of expecting users to recognize scams, Sentinel AI recognizes scams on their behalf.

Core Principle

Detect → Explain → Prevent

Not:

Detect → Report

5. Project Scope
In Scope
Android Mobile Application

Primary platform for MVP.

Multi-Channel Threat Monitoring
SMS
Incoming calls
WhatsApp
Telegram
Gmail
Threat Analysis
Text analysis
Link analysis
File analysis
Conversation analysis
User Alerts
Real-time warnings
Risk scoring
Safety recommendations
AI Copilot

Interactive security assistant.

Out of Scope (Hackathon MVP)
Full Telecom Integration
Telecom operator integration
Network-level interception
Bank Integration
Live banking transaction monitoring
Government System Integration
NCRP integration
MHA integration
Police systems
Large-Scale Fraud Network Intelligence
Cross-user graph analysis
Criminal network discovery

These can be future roadmap items.

6. Features Included
F1. SMS Fraud Detection

Analyze:

Sender
Content
Intent
URLs

Detect:

OTP theft
KYC scams
Banking scams
Reward scams
Lottery scams

Output:

Safe
Suspicious
Dangerous
F2. Incoming Call Risk Detection

Analyze:

Caller metadata
Reported scam databases
Conversation transcripts

Detect:

Digital arrest scripts
Authority impersonation
Banking fraud

Provide:

Real-time call warning
F3. WhatsApp Fraud Shield

Analyze:

Incoming messages
Shared links
Shared files

Detect:

Scam campaigns
Investment fraud
Loan fraud
Government impersonation
F4. Telegram Fraud Shield

Analyze:

Messages
Groups
Shared URLs
Shared files

Detect:

Crypto scams
Investment scams
Phishing campaigns
F5. Gmail Threat Scanner

Analyze:

Sender reputation
Subject
Email body
Attachments
Links

Detect:

Phishing
Fake invoices
Credential theft
F6. Link Intelligence Engine

Analyze:

Domain reputation
URL structure
Brand impersonation
Suspicious patterns

Output:

Risk score
Threat explanation
F7. File Intelligence Engine

Analyze:

PDFs
Images
APKs
Documents

Detect:

Malware indicators
Fake notices
Suspicious attachments
F8. Digital Arrest Scam Detector

Specialized AI agent focused on:

CBI impersonation
ED impersonation
Customs impersonation
Police impersonation

Recognizes:

Threat language
Arrest language
Urgency signals
Financial coercion
F9. AI Security Copilot

User can ask:

Is this message safe?
Is this link malicious?
Is this caller genuine?
Should I make this payment?

AI provides:

Verdict
Reasoning
Recommended action
F10. Real-Time Risk Alert Engine

Severity levels:

Green

Safe

Yellow

Suspicious

Red

High Risk

Critical

Immediate Threat

7. Features Excluded

To maintain MVP feasibility:

Excluded
Counterfeit Currency Detection

Separate computer vision challenge.

Deepfake Video Detection

High complexity for hackathon scope.

Law Enforcement Dashboard

Can be future enterprise version.

Telecom Operator Integration

Requires external partnerships.

Bank Transaction Blocking

Requires regulatory approval.

Multi-Agency Intelligence Sharing

Not needed for MVP.

Device-Level Antivirus

Outside scope.

8. User Personas
Persona 1: Everyday Citizen

Age: 18–60

Needs:

Scam protection
Simple warnings
Easy explanations
Persona 2: Senior Citizen

Age: 60+

Needs:

Protection from digital arrest scams
Voice-based guidance
High-confidence alerts
Persona 3: Students

Needs:

Protection from scholarship scams
Job scams
Internship fraud
Persona 4: Professionals

Needs:

Email security
Financial fraud prevention
Identity theft protection
Persona 5: Small Business Owners

Needs:

Invoice fraud detection
Vendor scam detection
Payment protection
9. Technical Vision

Sentinel AI should function as a local-first AI security layer with optional cloud intelligence.

Design Principles
Privacy First

Only necessary information leaves device.

Explainable AI

Every warning includes:

What happened
Why it is risky
What user should do
Real-Time Response

Latency target:

< 3 seconds

Modular Architecture

Independent AI agents for:

SMS
Calls
Email
Messaging apps
Links
Files
Agentic Workflow

Specialized agents collaborate to determine final risk.

10. High-Level Architecture
+--------------------------------------+
|          Sentinel AI App             |
+--------------------------------------+
                |
                v

+--------------------------------------+
|       Event Collection Layer         |
+--------------------------------------+
| SMS Listener                         |
| Call Listener                        |
| WhatsApp Connector                   |
| Telegram Connector                   |
| Gmail Connector                      |
+--------------------------------------+

                |
                v

+--------------------------------------+
|      Threat Intelligence Layer       |
+--------------------------------------+
| Scam Classifier                      |
| Digital Arrest Detector              |
| Phishing Detector                    |
| URL Analyzer                         |
| File Analyzer                        |
+--------------------------------------+

                |
                v

+--------------------------------------+
|      Multi-Agent Decision Engine     |
+--------------------------------------+
| Risk Scoring Agent                   |
| Context Agent                        |
| Explanation Agent                    |
+--------------------------------------+

                |
                v

+--------------------------------------+
|      User Protection Layer           |
+--------------------------------------+
| Alerts                               |
| Recommendations                      |
| AI Security Copilot                  |
+--------------------------------------+
11. Development Phases
Phase 1 — Core Protection
Android app
SMS analysis
Link analysis
Risk alerts

Deliverable:

Basic fraud shield

Phase 2 — Communication Intelligence
Gmail integration
WhatsApp analysis
Telegram analysis

Deliverable:

Multi-channel protection

Phase 3 — Digital Arrest Defense
Specialized fraud classifier
Scam conversation detection
Real-time warning engine

Deliverable:

Digital arrest prevention

Phase 4 — Agentic Security Copilot
Conversational assistant
Explainability layer
Action recommendations

Deliverable:

Personal AI security guardian

12. Risks
Privacy Concerns

Users may hesitate to grant access.

Mitigation:

On-device processing
Explicit permissions
Android Restrictions

Monitoring third-party apps is limited.

Mitigation:

Accessibility APIs
Notification analysis
User-assisted scanning
False Positives

Excessive warnings reduce trust.

Mitigation:

Confidence scoring
Multi-signal validation
Data Availability

Fraud datasets may be limited.

Mitigation:

Synthetic scam generation
Public phishing datasets
Open fraud repositories
Performance

Continuous monitoring can drain battery.

Mitigation:

Event-driven architecture
Lightweight models
13. Judging Alignment
Innovation (25%)
Personal AI security guardian
Agentic fraud prevention
Multi-channel protection
Business Impact (25%)
Addresses nationwide cybercrime problem
Applicable to millions of smartphone users
Strong public safety impact
Technical Excellence (20%)
Multi-agent architecture
NLP classification
Threat intelligence pipeline
Explainable AI
Scalability (15%)
Android-first
Cloud extensible
Multi-language support
User Experience (15%)
Real-time alerts
Simple risk indicators
AI explanations
Low-friction interactions
Direct Alignment With Problem Statement

Sentinel AI strongly aligns with:

Digital Arrest Scam Detection & Alerting
Citizen Fraud Shield (Multi-channel)
NLP/LLM Scam Classification
Speech AI Integration
Agentic AI Intelligence Fusion

as described in Problem Statement 6.

14. Future Roadmap
V2
Voice Scam Analysis
Real-time speech transcription
Voice risk detection
Deepfake Voice Detection
Synthetic voice detection
Speaker authenticity scoring
Multilingual Fraud Detection
Hindi
Tamil
Telugu
Bengali
Marathi
Kannada
V3
Fraud Network Intelligence
Cross-user threat aggregation
Scam campaign detection
Geospatial Fraud Mapping
Fraud hotspot visualization
Public safety insights
Community Threat Intelligence
Shared scam reporting
Crowdsourced fraud alerts
V4
Counterfeit Currency Detection

Computer vision system for:

Currency note verification
Fake note identification
Law Enforcement Dashboard
Intelligence summaries
Threat heatmaps
Investigation support
Financial Institution Integration
Bank risk signals
Fraud prevention APIs
Sentinel AI Tagline

"Think Before You Click. Sentinel Thinks Before You Do."