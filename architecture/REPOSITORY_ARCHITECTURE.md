# REPOSITORY_ARCHITECTURE.md
# Sentinel AI — Complete Repository Architecture
**Version:** 1.0.0  
**Architect Role:** Principal Software Architect  
**Hackathon:** ET AI Hackathon 2026 — Problem Statement 6  
**Platform:** Android-First | FastAPI Backend | Neo4j | Multi-Agent AI

---

## Table of Contents

1. [Repository Structure](#1-repository-structure)
2. [Folder Explanations](#2-folder-explanations)
3. [Module Ownership](#3-module-ownership)
4. [Data Flow Between Modules](#4-data-flow-between-modules)
5. [Future Scalability Considerations](#5-future-scalability-considerations)

---

## 1. Repository Structure

```
sentinel-ai/
│
├── README.md
├── REPOSITORY_ARCHITECTURE.md
├── .gitignore
├── .env.example
├── docker-compose.yml
├── docker-compose.prod.yml
├── Makefile
│
├── android/                                    # Android Mobile Application
│   ├── app/
│   │   ├── src/
│   │   │   ├── main/
│   │   │   │   ├── AndroidManifest.xml
│   │   │   │   ├── java/com/sentinel/ai/
│   │   │   │   │   ├── SentinelApp.kt
│   │   │   │   │   │
│   │   │   │   │   ├── core/                   # Core Android infrastructure
│   │   │   │   │   │   ├── di/                 # Dependency injection (Hilt)
│   │   │   │   │   │   │   ├── AppModule.kt
│   │   │   │   │   │   │   ├── NetworkModule.kt
│   │   │   │   │   │   │   └── AgentModule.kt
│   │   │   │   │   │   ├── network/
│   │   │   │   │   │   │   ├── ApiClient.kt
│   │   │   │   │   │   │   ├── RetrofitBuilder.kt
│   │   │   │   │   │   │   └── interceptors/
│   │   │   │   │   │   │       ├── AuthInterceptor.kt
│   │   │   │   │   │   │       └── LoggingInterceptor.kt
│   │   │   │   │   │   ├── permissions/
│   │   │   │   │   │   │   ├── PermissionManager.kt
│   │   │   │   │   │   │   └── PermissionRationale.kt
│   │   │   │   │   │   ├── security/
│   │   │   │   │   │   │   ├── EncryptedPrefs.kt
│   │   │   │   │   │   │   └── KeystoreManager.kt
│   │   │   │   │   │   └── utils/
│   │   │   │   │   │       ├── Extensions.kt
│   │   │   │   │   │       └── Logger.kt
│   │   │   │   │   │
│   │   │   │   │   ├── data/                   # Data layer
│   │   │   │   │   │   ├── local/
│   │   │   │   │   │   │   ├── db/
│   │   │   │   │   │   │   │   ├── SentinelDatabase.kt
│   │   │   │   │   │   │   │   ├── dao/
│   │   │   │   │   │   │   │   │   ├── ThreatDao.kt
│   │   │   │   │   │   │   │   │   ├── AlertDao.kt
│   │   │   │   │   │   │   │   │   └── ScanHistoryDao.kt
│   │   │   │   │   │   │   │   └── entities/
│   │   │   │   │   │   │   │       ├── ThreatEntity.kt
│   │   │   │   │   │   │   │       ├── AlertEntity.kt
│   │   │   │   │   │   │   │       └── ScanHistoryEntity.kt
│   │   │   │   │   │   │   └── prefs/
│   │   │   │   │   │   │       └── UserPreferences.kt
│   │   │   │   │   │   ├── remote/
│   │   │   │   │   │   │   ├── api/
│   │   │   │   │   │   │   │   ├── ThreatApiService.kt
│   │   │   │   │   │   │   │   ├── LinkApiService.kt
│   │   │   │   │   │   │   │   └── FileApiService.kt
│   │   │   │   │   │   │   └── dto/
│   │   │   │   │   │   │       ├── ThreatRequest.kt
│   │   │   │   │   │   │       ├── ThreatResponse.kt
│   │   │   │   │   │   │       └── RiskScoreDto.kt
│   │   │   │   │   │   └── repository/
│   │   │   │   │   │       ├── ThreatRepository.kt
│   │   │   │   │   │       ├── AlertRepository.kt
│   │   │   │   │   │       └── ScanHistoryRepository.kt
│   │   │   │   │   │
│   │   │   │   │   ├── domain/                 # Business logic & models
│   │   │   │   │   │   ├── model/
│   │   │   │   │   │   │   ├── Threat.kt
│   │   │   │   │   │   │   ├── RiskLevel.kt
│   │   │   │   │   │   │   ├── Alert.kt
│   │   │   │   │   │   │   └── ScanResult.kt
│   │   │   │   │   │   └── usecase/
│   │   │   │   │   │       ├── AnalyzeSmsUseCase.kt
│   │   │   │   │   │       ├── AnalyzeLinkUseCase.kt
│   │   │   │   │   │       ├── AnalyzeFileUseCase.kt
│   │   │   │   │   │       ├── AnalyzeCallUseCase.kt
│   │   │   │   │   │       └── GetAlertHistoryUseCase.kt
│   │   │   │   │   │
│   │   │   │   │   ├── listeners/              # OS-level event listeners
│   │   │   │   │   │   ├── SmsReceiver.kt
│   │   │   │   │   │   ├── CallReceiver.kt
│   │   │   │   │   │   ├── NotificationListener.kt
│   │   │   │   │   │   └── AccessibilityService.kt
│   │   │   │   │   │
│   │   │   │   │   ├── agents/                 # On-device agent coordinators
│   │   │   │   │   │   ├── base/
│   │   │   │   │   │   │   ├── BaseAgent.kt
│   │   │   │   │   │   │   └── AgentResult.kt
│   │   │   │   │   │   ├── SmsAgentCoordinator.kt
│   │   │   │   │   │   ├── CallAgentCoordinator.kt
│   │   │   │   │   │   ├── LinkAgentCoordinator.kt
│   │   │   │   │   │   ├── FileAgentCoordinator.kt
│   │   │   │   │   │   └── MessageAgentCoordinator.kt
│   │   │   │   │   │
│   │   │   │   │   ├── services/               # Android foreground services
│   │   │   │   │   │   ├── SentinelGuardService.kt
│   │   │   │   │   │   ├── ThreatMonitorService.kt
│   │   │   │   │   │   └── BackgroundSyncService.kt
│   │   │   │   │   │
│   │   │   │   │   └── ui/                     # Jetpack Compose UI
│   │   │   │   │       ├── navigation/
│   │   │   │   │       │   ├── SentinelNavGraph.kt
│   │   │   │   │       │   └── Screen.kt
│   │   │   │   │       ├── theme/
│   │   │   │   │       │   ├── Color.kt
│   │   │   │   │       │   ├── Typography.kt
│   │   │   │   │       │   └── Theme.kt
│   │   │   │   │       ├── screens/
│   │   │   │   │       │   ├── dashboard/
│   │   │   │   │       │   │   ├── DashboardScreen.kt
│   │   │   │   │       │   │   └── DashboardViewModel.kt
│   │   │   │   │       │   ├── alert/
│   │   │   │   │       │   │   ├── AlertScreen.kt
│   │   │   │   │       │   │   ├── AlertDetailScreen.kt
│   │   │   │   │       │   │   └── AlertViewModel.kt
│   │   │   │   │       │   ├── scanner/
│   │   │   │   │       │   │   ├── ScannerScreen.kt
│   │   │   │   │       │   │   └── ScannerViewModel.kt
│   │   │   │   │       │   ├── copilot/
│   │   │   │   │       │   │   ├── CopilotScreen.kt
│   │   │   │   │       │   │   └── CopilotViewModel.kt
│   │   │   │   │       │   ├── history/
│   │   │   │   │       │   │   ├── HistoryScreen.kt
│   │   │   │   │       │   │   └── HistoryViewModel.kt
│   │   │   │   │       │   └── settings/
│   │   │   │   │       │       ├── SettingsScreen.kt
│   │   │   │   │       │       └── SettingsViewModel.kt
│   │   │   │   │       └── components/
│   │   │   │   │           ├── RiskBadge.kt
│   │   │   │   │           ├── ThreatCard.kt
│   │   │   │   │           ├── AlertBanner.kt
│   │   │   │   │           └── CopilotChatBubble.kt
│   │   │   │   │
│   │   │   │   └── res/
│   │   │   │       ├── drawable/
│   │   │   │       ├── layout/
│   │   │   │       └── values/
│   │   │   │
│   │   │   └── test/
│   │   │       ├── unit/
│   │   │       └── integration/
│   │   │
│   │   ├── build.gradle.kts
│   │   └── proguard-rules.pro
│   │
│   ├── build.gradle.kts
│   └── settings.gradle.kts
│
├── backend/                                    # FastAPI Backend (Python)
│   ├── pyproject.toml
│   ├── requirements.txt
│   ├── requirements-dev.txt
│   ├── alembic.ini
│   ├── Dockerfile
│   │
│   ├── app/
│   │   ├── __init__.py
│   │   ├── main.py                             # FastAPI app entry point
│   │   ├── config.py                           # Settings & env config
│   │   ├── dependencies.py                     # Shared FastAPI dependencies
│   │   │
│   │   ├── api/                                # API route layer
│   │   │   ├── __init__.py
│   │   │   ├── v1/
│   │   │   │   ├── __init__.py
│   │   │   │   ├── router.py                   # Aggregated v1 router
│   │   │   │   ├── endpoints/
│   │   │   │   │   ├── analyze.py              # /analyze/sms, /analyze/link, etc.
│   │   │   │   │   ├── copilot.py              # /copilot/chat
│   │   │   │   │   ├── threats.py              # /threats (read threat DB)
│   │   │   │   │   ├── alerts.py               # /alerts (alert management)
│   │   │   │   │   ├── files.py                # /files/analyze
│   │   │   │   │   └── health.py               # /health
│   │   │   │   └── schemas/
│   │   │   │       ├── analyze.py
│   │   │   │       ├── copilot.py
│   │   │   │       ├── threat.py
│   │   │   │       └── alert.py
│   │   │   └── v2/                             # Placeholder for v2 API
│   │   │       └── __init__.py
│   │   │
│   │   ├── core/                               # Backend core infrastructure
│   │   │   ├── __init__.py
│   │   │   ├── security.py                     # JWT, API key validation
│   │   │   ├── logging.py                      # Structured logging (structlog)
│   │   │   ├── middleware.py                   # Rate limiting, CORS, request ID
│   │   │   ├── exceptions.py                   # Custom exception hierarchy
│   │   │   └── events.py                       # App startup/shutdown events
│   │   │
│   │   ├── agents/                             # Multi-Agent AI Engine (core)
│   │   │   ├── __init__.py
│   │   │   ├── base/
│   │   │   │   ├── __init__.py
│   │   │   │   ├── agent.py                    # BaseAgent abstract class
│   │   │   │   ├── agent_result.py             # AgentResult dataclass
│   │   │   │   ├── agent_registry.py           # Dynamic agent registration
│   │   │   │   └── agent_context.py            # Shared context object
│   │   │   │
│   │   │   ├── orchestrator/
│   │   │   │   ├── __init__.py
│   │   │   │   ├── threat_orchestrator.py      # Master agent coordinator
│   │   │   │   ├── risk_aggregator.py          # Aggregates multi-agent scores
│   │   │   │   └── decision_engine.py          # Final verdict engine
│   │   │   │
│   │   │   ├── sms/
│   │   │   │   ├── __init__.py
│   │   │   │   ├── sms_agent.py                # SMS fraud detection agent
│   │   │   │   ├── sms_classifier.py           # NLP classifier
│   │   │   │   └── sms_patterns.py             # Scam pattern library
│   │   │   │
│   │   │   ├── call/
│   │   │   │   ├── __init__.py
│   │   │   │   ├── call_agent.py               # Call risk detection agent
│   │   │   │   ├── call_transcript_analyzer.py
│   │   │   │   └── digital_arrest_detector.py  # Specialized DA detector
│   │   │   │
│   │   │   ├── link/
│   │   │   │   ├── __init__.py
│   │   │   │   ├── link_agent.py               # URL intelligence agent
│   │   │   │   ├── domain_analyzer.py          # Domain reputation
│   │   │   │   ├── url_structure_analyzer.py   # URL pattern analysis
│   │   │   │   └── brand_impersonation.py      # Brand spoofing detection
│   │   │   │
│   │   │   ├── file/
│   │   │   │   ├── __init__.py
│   │   │   │   ├── file_agent.py               # File intelligence agent
│   │   │   │   ├── pdf_analyzer.py             # PDF threat analysis
│   │   │   │   ├── apk_analyzer.py             # Android APK analysis
│   │   │   │   └── image_analyzer.py           # Image-based fraud detection
│   │   │   │
│   │   │   ├── message/
│   │   │   │   ├── __init__.py
│   │   │   │   ├── whatsapp_agent.py           # WhatsApp fraud shield
│   │   │   │   ├── telegram_agent.py           # Telegram fraud shield
│   │   │   │   └── email_agent.py              # Gmail threat scanner
│   │   │   │
│   │   │   ├── digital_arrest/
│   │   │   │   ├── __init__.py
│   │   │   │   ├── digital_arrest_agent.py     # Specialized DA agent
│   │   │   │   ├── authority_impersonation.py  # CBI/ED/Police patterns
│   │   │   │   ├── threat_language_detector.py
│   │   │   │   └── coercion_detector.py        # Financial coercion signals
│   │   │   │
│   │   │   ├── risk/
│   │   │   │   ├── __init__.py
│   │   │   │   ├── risk_scoring_agent.py       # Risk score calculator
│   │   │   │   ├── confidence_scorer.py        # Confidence estimation
│   │   │   │   └── risk_level.py               # GREEN/YELLOW/RED/CRITICAL
│   │   │   │
│   │   │   ├── context/
│   │   │   │   ├── __init__.py
│   │   │   │   └── context_agent.py            # Historical context enrichment
│   │   │   │
│   │   │   └── explanation/
│   │   │       ├── __init__.py
│   │   │       ├── explanation_agent.py        # Human-readable explanations
│   │   │       └── recommendation_engine.py    # Action recommendations
│   │   │
│   │   ├── copilot/                            # AI Security Copilot
│   │   │   ├── __init__.py
│   │   │   ├── copilot_service.py              # Copilot orchestration
│   │   │   ├── conversation_manager.py         # Multi-turn conversation state
│   │   │   ├── intent_classifier.py            # User intent detection
│   │   │   ├── response_generator.py           # LLM-backed response gen
│   │   │   └── safety_guardrails.py            # Output safety filters
│   │   │
│   │   ├── intelligence/                       # Threat Intelligence Layer
│   │   │   ├── __init__.py
│   │   │   ├── feeds/
│   │   │   │   ├── __init__.py
│   │   │   │   ├── phishing_feed.py            # OpenPhish, PhishTank
│   │   │   │   ├── scam_db_feed.py             # Public scam databases
│   │   │   │   └── feed_manager.py             # Feed aggregator
│   │   │   ├── enrichment/
│   │   │   │   ├── __init__.py
│   │   │   │   ├── whois_enricher.py
│   │   │   │   ├── geo_enricher.py
│   │   │   │   └── reputation_enricher.py
│   │   │   └── cache/
│   │   │       ├── __init__.py
│   │   │       └── intelligence_cache.py       # Redis-backed intelligence cache
│   │   │
│   │   ├── models/                             # ML Models
│   │   │   ├── __init__.py
│   │   │   ├── loaders/
│   │   │   │   ├── __init__.py
│   │   │   │   ├── model_loader.py             # Model loading infrastructure
│   │   │   │   └── model_registry.py           # Versioned model registry
│   │   │   ├── classifiers/
│   │   │   │   ├── __init__.py
│   │   │   │   ├── scam_classifier.py          # Text scam classifier
│   │   │   │   ├── phishing_classifier.py      # Phishing NLP model
│   │   │   │   └── digital_arrest_classifier.py
│   │   │   └── embeddings/
│   │   │       ├── __init__.py
│   │   │       └── text_embedder.py            # Sentence transformer embeddings
│   │   │
│   │   ├── db/                                 # Database layer
│   │   │   ├── __init__.py
│   │   │   ├── postgres/
│   │   │   │   ├── __init__.py
│   │   │   │   ├── session.py                  # SQLAlchemy async session
│   │   │   │   ├── base.py                     # Declarative base
│   │   │   │   └── models/
│   │   │   │       ├── threat.py
│   │   │   │       ├── alert.py
│   │   │   │       ├── scan_history.py
│   │   │   │       └── user_feedback.py
│   │   │   ├── neo4j/
│   │   │   │   ├── __init__.py
│   │   │   │   ├── driver.py                   # Neo4j async driver
│   │   │   │   ├── queries/
│   │   │   │   │   ├── __init__.py
│   │   │   │   │   ├── threat_graph.py         # Threat relationship queries
│   │   │   │   │   ├── fraud_network.py        # Fraud network traversal
│   │   │   │   │   ├── scam_campaign.py        # Scam campaign detection
│   │   │   │   │   └── entity_resolution.py    # Entity deduplication
│   │   │   │   └── models/
│   │   │   │       ├── __init__.py
│   │   │   │       ├── nodes.py                # Node type definitions
│   │   │   │       └── relationships.py        # Relationship type definitions
│   │   │   └── redis/
│   │   │       ├── __init__.py
│   │   │       └── client.py                   # Redis async client
│   │   │
│   │   ├── services/                           # Business service layer
│   │   │   ├── __init__.py
│   │   │   ├── threat_analysis_service.py      # Orchestrates threat analysis
│   │   │   ├── alert_service.py                # Alert lifecycle management
│   │   │   ├── scan_history_service.py
│   │   │   ├── file_upload_service.py          # Secure file handling
│   │   │   └── notification_service.py         # Push notification dispatch
│   │   │
│   │   └── tasks/                              # Async background tasks (Celery)
│   │       ├── __init__.py
│   │       ├── celery_app.py
│   │       ├── threat_sync_task.py             # Background intelligence sync
│   │       ├── feed_update_task.py             # Threat feed refreshes
│   │       └── model_update_task.py            # ML model hot-reload
│   │
│   ├── alembic/                                # DB migrations (PostgreSQL)
│   │   ├── env.py
│   │   ├── script.py.mako
│   │   └── versions/
│   │       └── 0001_initial_schema.py
│   │
│   └── tests/
│       ├── conftest.py
│       ├── unit/
│       │   ├── agents/
│       │   │   ├── test_sms_agent.py
│       │   │   ├── test_link_agent.py
│       │   │   ├── test_digital_arrest_agent.py
│       │   │   └── test_risk_scoring_agent.py
│       │   ├── services/
│       │   │   └── test_threat_analysis_service.py
│       │   └── models/
│       │       └── test_scam_classifier.py
│       ├── integration/
│       │   ├── test_analyze_endpoint.py
│       │   ├── test_copilot_endpoint.py
│       │   └── test_neo4j_queries.py
│       └── e2e/
│           └── test_full_threat_pipeline.py
│
├── ml/                                         # ML Training & Model Management
│   ├── README.md
│   ├── requirements.txt
│   ├── Dockerfile.training
│   │
│   ├── datasets/
│   │   ├── README.md
│   │   ├── raw/                                # Raw data (gitignored)
│   │   ├── processed/                          # Processed datasets
│   │   └── synthetic/
│   │       ├── scam_generator.py               # Synthetic scam data generator
│   │       └── digital_arrest_generator.py
│   │
│   ├── training/
│   │   ├── train_scam_classifier.py
│   │   ├── train_phishing_classifier.py
│   │   ├── train_digital_arrest_classifier.py
│   │   └── finetune_llm.py                     # LLM fine-tuning scripts
│   │
│   ├── evaluation/
│   │   ├── evaluate_model.py
│   │   ├── benchmark.py
│   │   └── confusion_matrix.py
│   │
│   ├── notebooks/
│   │   ├── 01_data_exploration.ipynb
│   │   ├── 02_feature_engineering.ipynb
│   │   ├── 03_model_comparison.ipynb
│   │   └── 04_error_analysis.ipynb
│   │
│   └── registry/                               # Model artifact store
│       ├── scam_classifier/
│       ├── phishing_classifier/
│       └── digital_arrest_classifier/
│
├── neo4j/                                      # Neo4j Graph Schema & Seed Data
│   ├── README.md
│   ├── schema/
│   │   ├── constraints.cypher               # Uniqueness & existence constraints
│   │   ├── indexes.cypher                   # Performance indexes
│   │   └── schema_overview.md
│   ├── seed/
│   │   ├── seed_fraud_network.cypher
│   │   ├── seed_known_scam_numbers.cypher
│   │   └── seed_phishing_domains.cypher
│   └── migrations/
│       └── v1_initial_graph.cypher
│
├── infra/                                      # Infrastructure as Code
│   ├── docker/
│   │   ├── backend.Dockerfile
│   │   ├── celery.Dockerfile
│   │   └── nginx.conf
│   ├── k8s/                                    # Kubernetes manifests
│   │   ├── namespace.yaml
│   │   ├── backend-deployment.yaml
│   │   ├── celery-deployment.yaml
│   │   ├── neo4j-statefulset.yaml
│   │   ├── postgres-statefulset.yaml
│   │   ├── redis-deployment.yaml
│   │   └── ingress.yaml
│   ├── terraform/                              # Cloud provisioning (future)
│   │   └── README.md
│   └── scripts/
│       ├── init_db.sh
│       ├── init_neo4j.sh
│       └── seed_data.sh
│
├── shared/                                     # Shared contracts & schemas
│   ├── proto/                                  # Protobuf definitions (future gRPC)
│   │   └── threat.proto
│   └── schemas/
│       ├── threat_event.json               # JSON Schema for threat events
│       ├── risk_score.json
│       └── alert.json
│
├── docs/                                       # Project documentation
│   ├── architecture/
│   │   ├── REPOSITORY_ARCHITECTURE.md
│   │   ├── agent_design.md
│   │   ├── neo4j_graph_model.md
│   │   └── api_design.md
│   ├── api/
│   │   └── openapi.yaml                        # Auto-generated from FastAPI
│   ├── adr/                                    # Architecture Decision Records
│   │   ├── ADR-001-android-first.md
│   │   ├── ADR-002-multi-agent.md
│   │   ├── ADR-003-neo4j-for-fraud-graph.md
│   │   └── ADR-004-on-device-first.md
│   └── runbooks/
│       ├── local_setup.md
│       ├── deployment.md
│       └── incident_response.md
│
└── scripts/                                    # Developer scripts
    ├── setup_dev.sh
    ├── run_tests.sh
    ├── lint.sh
    └── generate_synthetic_data.py
```

---

## 2. Folder Explanations

### `android/`
The primary user-facing Android application built with **Kotlin + Jetpack Compose**. Uses Clean Architecture (Data → Domain → UI) to maintain separation of concerns. All OS-level interception (SMS, calls, notifications) is isolated in `listeners/`. The `agents/` subdirectory holds lightweight on-device coordinators that dispatch to the backend and surface results.

| Subdirectory | Purpose |
|---|---|
| `core/` | Dependency injection (Hilt), networking (Retrofit/OkHttp), encryption, permissions |
| `data/` | Room database, remote API clients, DTOs, and repositories |
| `domain/` | Pure Kotlin business logic — models, use cases (zero Android dependency) |
| `listeners/` | Android OS hooks: SmsReceiver, CallReceiver, NotificationListener, AccessibilityService |
| `agents/` | On-device agent coordinators: lightweight, event-driven, dispatch to backend |
| `services/` | Long-running foreground services: SentinelGuardService (always-on), BackgroundSyncService |
| `ui/` | Jetpack Compose screens, ViewModels (MVVM), reusable components, navigation graph |

---

### `backend/`
A **FastAPI** Python backend serving as the brain of Sentinel AI. All heavy AI inference, graph queries, and threat intelligence aggregation happens here.

| Subdirectory | Purpose |
|---|---|
| `app/api/` | Versioned REST endpoints (`/v1/analyze`, `/v1/copilot`, `/v1/threats`). Clean route separation. |
| `app/core/` | JWT security, structured logging (structlog), middleware (rate limiting, CORS, request tracing), exception hierarchy |
| `app/agents/` | The multi-agent AI engine. Each agent is independent, stateless, and registered dynamically |
| `app/agents/base/` | Abstract `BaseAgent`, `AgentResult`, `AgentRegistry` — the plugin interface for all current and future agents |
| `app/agents/orchestrator/` | `ThreatOrchestrator` fans out to relevant agents, collects results, runs risk aggregation |
| `app/agents/digital_arrest/` | Specialized high-priority agent for CBI/ED/Police impersonation patterns |
| `app/copilot/` | AI Security Copilot service — multi-turn conversation, intent classification, LLM-backed response generation, safety guardrails |
| `app/intelligence/` | External threat feed ingestion (OpenPhish, PhishTank), domain/IP enrichment, Redis-backed intelligence cache |
| `app/models/` | ML model loaders, versioned model registry, NLP classifiers, text embedders |
| `app/db/postgres/` | SQLAlchemy async ORM — threats, alerts, scan history, user feedback |
| `app/db/neo4j/` | Async Neo4j driver, Cypher query modules, node/relationship type definitions |
| `app/db/redis/` | Cache layer for hot intelligence data and rate limiting |
| `app/services/` | Business service layer — sits between API endpoints and agents/db |
| `app/tasks/` | Celery async tasks for background intelligence sync, feed updates, model hot-reload |
| `alembic/` | PostgreSQL schema migration management |

---

### `ml/`
Isolated ML training environment. Completely decoupled from the serving backend. Contains dataset management, training scripts, evaluation pipelines, and Jupyter notebooks for research.

| Subdirectory | Purpose |
|---|---|
| `datasets/synthetic/` | Synthetic scam/digital-arrest sample generators to overcome dataset scarcity |
| `training/` | Individual model training scripts |
| `evaluation/` | Standardized evaluation benchmarks (precision/recall/F1) |
| `notebooks/` | Exploratory research, feature engineering, error analysis |
| `registry/` | Versioned model artifacts (ONNX/GGUF/safetensors) consumed by backend |

---

### `neo4j/`
Graph database configuration and seeding. Neo4j models the fraud **network graph** — relationships between phone numbers, domains, scam campaigns, known fraudsters, and victims.

| Subdirectory | Purpose |
|---|---|
| `schema/` | Cypher scripts to create constraints and indexes on startup |
| `seed/` | Initial data seeding: known scam numbers, phishing domains, fraud networks |
| `migrations/` | Version-controlled graph schema evolution |

---

### `infra/`
All infrastructure concerns: Docker images, Kubernetes manifests, and Terraform stubs. Separates infrastructure from application code.

---

### `shared/`
Language-agnostic contracts: JSON Schemas for threat events and risk scores, and Protobuf definitions (for future gRPC migration). Both Android and backend consume these contracts.

---

### `docs/`
Living documentation: ADRs (Architecture Decision Records) explaining **why** decisions were made, API specs, graph model documentation, runbooks.

---

## 3. Module Ownership

| Module | Owner Role | Technology | Boundary |
|---|---|---|---|
| `android/listeners/` | Android Platform Engineer | Kotlin, Android SDK | OS event collection. No business logic. |
| `android/agents/` | Android ML Engineer | Kotlin Coroutines | Lightweight dispatch only. No inference. |
| `android/ui/` | Android UI Engineer | Jetpack Compose, MVVM | Display and user interaction only. |
| `android/data/` | Android Data Engineer | Room, Retrofit, Hilt | Data access layer. No UI, no business logic. |
| `android/domain/` | Backend/Full-Stack Engineer | Pure Kotlin | Business rules. Zero Android imports. |
| `backend/app/api/` | Backend Engineer | FastAPI, Pydantic | Request validation, routing. No business logic. |
| `backend/app/agents/base/` | Principal Architect | Python ABC | Agent contract. Changes require arch review. |
| `backend/app/agents/orchestrator/` | AI/ML Engineer | Python, asyncio | Agent fan-out, result aggregation. |
| `backend/app/agents/sms/` | NLP Engineer | Python, Transformers | SMS fraud NLP pipeline. |
| `backend/app/agents/call/` | NLP / Speech Engineer | Python | Call transcript analysis. |
| `backend/app/agents/link/` | Security Engineer | Python | URL/domain intelligence. |
| `backend/app/agents/file/` | Security Engineer | Python | Malware/file pattern detection. |
| `backend/app/agents/digital_arrest/` | Domain Expert + NLP Engineer | Python | Digital arrest patterns — high-stakes. |
| `backend/app/agents/risk/` | AI/ML Engineer | Python | Risk scoring, confidence calibration. |
| `backend/app/agents/explanation/` | AI/ML Engineer | Python, LLM | Explainable AI outputs. |
| `backend/app/copilot/` | AI Product Engineer | Python, LLM API | Conversational copilot. |
| `backend/app/intelligence/` | Security/Data Engineer | Python, Redis | Threat feed ingestion and caching. |
| `backend/app/models/` | ML Engineer | Python, ONNX | Model serving infrastructure. |
| `backend/app/db/postgres/` | Data Engineer | SQLAlchemy, Alembic | Relational persistence. |
| `backend/app/db/neo4j/` | Graph Data Engineer | Neo4j, Cypher | Fraud network graph. |
| `backend/app/tasks/` | DevOps / Backend Engineer | Celery, Redis | Async background processing. |
| `ml/` | ML Research Engineer | Python, PyTorch, HuggingFace | Training only. No production serving code. |
| `neo4j/schema/` | Graph Data Engineer | Cypher | Schema migrations and indexing. |
| `infra/` | DevOps Engineer | Docker, Kubernetes | Infrastructure and deployment. |
| `shared/schemas/` | Principal Architect | JSON Schema, Protobuf | Cross-boundary contracts. Changes require arch review. |

---

## 4. Data Flow Between Modules

### 4.1 Primary Threat Analysis Flow

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         ANDROID DEVICE                                       │
│                                                                               │
│  OS Event                                                                     │
│  (SMS / Call / Notification)                                                  │
│        │                                                                      │
│        ▼                                                                      │
│  ┌─────────────┐      ┌─────────────────────┐      ┌──────────────────────┐ │
│  │  Listener   │─────▶│  Agent Coordinator  │─────▶│  Domain Use Case     │ │
│  │  (SmsRcvr,  │      │  (SmsAgentCoord,    │      │  (AnalyzeSmsUseCase) │ │
│  │   CallRcvr) │      │   LinkAgentCoord)   │      └──────────┬───────────┘ │
│  └─────────────┘      └─────────────────────┘                 │             │
│                                                                │             │
│                                                    ┌───────────▼───────────┐ │
│                                                    │   ThreatApiService    │ │
│                                                    │   (Retrofit HTTP)     │ │
│                                                    └───────────┬───────────┘ │
└────────────────────────────────────────────────────────────────┼─────────────┘
                                                                 │ HTTPS/TLS
                                                                 ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                         FASTAPI BACKEND                                       │
│                                                                               │
│  ┌────────────────────┐                                                       │
│  │  API Endpoint      │  POST /v1/analyze/sms                                 │
│  │  (analyze.py)      │  POST /v1/analyze/link                                │
│  └────────┬───────────┘  POST /v1/analyze/file                                │
│           │                                                                   │
│           ▼                                                                   │
│  ┌────────────────────┐                                                       │
│  │  ThreatAnalysis    │  Validates request, applies rate limiting             │
│  │  Service           │  Dispatches to orchestrator                           │
│  └────────┬───────────┘                                                       │
│           │                                                                   │
│           ▼                                                                   │
│  ┌────────────────────────────────────────────────────────────┐              │
│  │                  THREAT ORCHESTRATOR                        │              │
│  │                                                             │              │
│  │  Determines relevant agents based on input type:           │              │
│  │                                                             │              │
│  │  ┌──────────────┐  ┌──────────────┐  ┌───────────────────┐│              │
│  │  │  SMS Agent   │  │  Link Agent  │  │DigitalArrestAgent ││              │
│  │  │              │  │              │  │                   ││              │
│  │  │ NLP Classify │  │ Domain Reptn │  │ Authority Pattern ││              │
│  │  │ Pattern Match│  │ URL Structure│  │ Coercion Signal   ││              │
│  │  └──────┬───────┘  └──────┬───────┘  └────────┬──────────┘│              │
│  │         │                 │                    │            │              │
│  │         └─────────────────┴────────────────────┘           │              │
│  │                           │  AgentResult[]                  │              │
│  │                           ▼                                 │              │
│  │                  ┌─────────────────┐                        │              │
│  │                  │ Risk Aggregator │                        │              │
│  │                  │ + Confidence    │                        │              │
│  │                  └────────┬────────┘                        │              │
│  │                           ▼                                 │              │
│  │                  ┌─────────────────┐                        │              │
│  │                  │ Decision Engine │  GREEN/YELLOW/RED/CRIT │              │
│  │                  └────────┬────────┘                        │              │
│  │                           ▼                                 │              │
│  │                  ┌─────────────────┐                        │              │
│  │                  │Explanation Agent│  Human-readable output │              │
│  │                  └────────┬────────┘                        │              │
│  └───────────────────────────┼─────────────────────────────────┘              │
│                              │                                                │
│  ┌───────────────────────────┼────────────────────────────────┐              │
│  │           INTELLIGENCE LAYER (parallel enrichment)         │              │
│  │  ┌────────────────┐  ┌───────────────┐  ┌───────────────┐ │              │
│  │  │  Redis Cache   │  │  Neo4j Graph  │  │  Postgres DB  │ │              │
│  │  │  (Hot Intel)   │  │  (Fraud Net)  │  │  (History)    │ │              │
│  │  └────────────────┘  └───────────────┘  └───────────────┘ │              │
│  └────────────────────────────────────────────────────────────┘              │
│                              │                                                │
│                     ThreatResponse (JSON)                                     │
└──────────────────────────────┼───────────────────────────────────────────────┘
                               │ HTTPS
                               ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                         ANDROID DEVICE                                        │
│                                                                               │
│  ┌────────────────────┐      ┌────────────────────┐      ┌────────────────┐ │
│  │  Repository        │─────▶│  Use Case Result   │─────▶│  ViewModel     │ │
│  │  (ThreatRepo)      │      │  (ScanResult)      │      │  (AlertVM)     │ │
│  └────────────────────┘      └────────────────────┘      └───────┬────────┘ │
│                                                                   │          │
│                                                                   ▼          │
│                                                      ┌────────────────────┐  │
│                                                      │  Alert UI          │  │
│                                                      │  (AlertScreen,     │  │
│                                                      │   AlertBanner,     │  │
│                                                      │   RiskBadge)       │  │
│                                                      └────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

### 4.2 AI Security Copilot Flow

```
User Question (natural language)
        │
        ▼
[Android CopilotScreen]
        │  POST /v1/copilot/chat
        ▼
[CopilotService]
        │
        ├── IntentClassifier        →  Determines: link check / SMS check / general advice
        │
        ├── ConversationManager     →  Retrieves multi-turn history
        │
        ├── ThreatOrchestrator      →  If threat analysis needed, fans out to agents
        │
        ├── ResponseGenerator       →  LLM call with enriched context + threat signals
        │
        └── SafetyGuardrails        →  Output filtering before response
                │
                ▼
        JSON Response with verdict + reasoning + recommended action
```

---

### 4.3 Background Intelligence Sync Flow

```
Celery Beat Scheduler (cron)
        │
        ├── FeedUpdateTask          →  Polls phishing/scam feeds
        │        │
        │        └── FeedManager   →  Normalizes feed data
        │                │
        │                └── Redis Intelligence Cache (hot data)
        │                └── Neo4j Graph (persistent relationships)
        │                └── Postgres (scan-ready reference data)
        │
        └── ModelUpdateTask        →  Checks ML model registry for new versions
                 │
                 └── ModelLoader   →  Hot-reloads models without service restart
```

---

### 4.4 Neo4j Fraud Graph Data Model

```
Neo4j Nodes:
  (:PhoneNumber {number, country, reportCount, lastSeen})
  (:Domain {fqdn, registrar, registrationDate, riskScore})
  (:IPAddress {ip, asn, country, abuseScore})
  (:ScamCampaign {id, name, type, startDate, active})
  (:FraudMessage {hash, content, channel, detectedAt})
  (:KnownFraudster {id, aliases[], country})
  (:Victim {id, anonymizedHash, city, lossAmount})

Neo4j Relationships:
  (PhoneNumber)-[:USED_IN]->(ScamCampaign)
  (Domain)-[:PART_OF]->(ScamCampaign)
  (FraudMessage)-[:SENT_FROM]->(PhoneNumber)
  (FraudMessage)-[:CONTAINS_LINK]->(Domain)
  (KnownFraudster)-[:OPERATES]->(PhoneNumber)
  (Victim)-[:TARGETED_BY]->(ScamCampaign)
  (IPAddress)-[:HOSTS]->(Domain)
  (Domain)-[:REDIRECTS_TO]->(Domain)
```

---

## 5. Future Scalability Considerations

### 5.1 Agent Extensibility

The `BaseAgent` contract in `backend/app/agents/base/agent.py` is the **single extension point** for all future agents. Adding a new agent (e.g., Voice Scam Detector in V2) requires only:

1. Subclass `BaseAgent`
2. Implement `async def analyze(context: AgentContext) -> AgentResult`
3. Register in `AgentRegistry` with capability tags
4. The `ThreatOrchestrator` auto-discovers and routes to it — zero changes elsewhere

```python
# Future agent example — Voice Scam Agent (V2 Roadmap)
class VoiceScamAgent(BaseAgent):
    capabilities = ["audio", "speech", "voice_fraud"]

    async def analyze(self, context: AgentContext) -> AgentResult:
        transcript = await self.transcribe(context.audio_data)
        score = await self.voice_classifier.predict(transcript)
        return AgentResult(risk_score=score, signals=[...])
```

---

### 5.2 Multi-Language Support (V2)

The NLP pipeline in `backend/app/agents/sms/sms_classifier.py` is designed around interchangeable model backends. Switching from English-only to multilingual (Hindi, Tamil, Telugu, Bengali, Marathi, Kannada) requires only swapping the embedding model in `app/models/embeddings/text_embedder.py`. The Indic NLP stack (IndicBERT, MuRIL) is a drop-in replacement.

---

### 5.3 Neo4j Fraud Network Intelligence (V3)

The graph schema is designed for progressive enrichment:

- **V1:** Individual threat nodes (PhoneNumber, Domain, IP)
- **V2:** Campaign-level graph (ScamCampaign relationships, cross-user aggregation once privacy consent framework is in place)
- **V3:** Geospatial fraud mapping (city/state-level Victim nodes), community fraud intelligence (crowdsourced alert graph)

The `backend/app/db/neo4j/queries/` module uses parameterized Cypher — all graph traversal depth and relationship types are runtime-configurable, not hardcoded.

---

### 5.4 gRPC Migration Path

The `shared/proto/threat.proto` Protobuf definition mirrors the JSON schemas today. When throughput demands it (high-traffic production), the Android ↔ Backend transport can migrate from REST/JSON to gRPC/Protobuf with no business logic changes — only the `ApiClient.kt` and `api/v1/endpoints/` transport layer changes.

---

### 5.5 On-Device Inference (Privacy-First V2)

The `android/agents/` coordinators are designed to support **local inference** as a first-class option. When on-device models (ONNX Runtime for Android, MLC-LLM) mature sufficiently:

- Lightweight classifiers can be bundled in the APK
- `AgentCoordinator` falls back to local inference when network is unavailable
- Sensitive content (call audio, SMS text) never leaves the device for those use cases

---

### 5.6 Horizontal Scaling

| Component | Scaling Mechanism |
|---|---|
| FastAPI backend | Stateless — scale horizontally behind a load balancer (Kubernetes HPA) |
| Celery workers | Scale worker replicas independently per task queue |
| Neo4j | Neo4j Aura (managed) or causal cluster for read replicas |
| PostgreSQL | Read replicas + PgBouncer connection pooling |
| Redis | Redis Cluster for intelligence cache sharding |
| ML inference | GPU node pools in Kubernetes; model server (Triton/TorchServe) as a separate deployment |

---

### 5.7 Law Enforcement & Enterprise Dashboard (V4)

The `backend/app/api/v2/` placeholder is reserved for enterprise APIs (read-only aggregate intelligence, heatmaps, investigation support). All sensitive operations are gated behind a separate API key scope — no architectural changes required, only a new FastAPI router and Neo4j aggregate query module.

---

*"Think Before You Click. Sentinel Thinks Before You Do."*

---
**Document Version:** 1.0.0  
**Status:** Draft for Hackathon MVP  
**Last Updated:** 2026-06-23
