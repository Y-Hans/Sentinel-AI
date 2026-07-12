# ANDROID_ARCHITECTURE.md
# Sentinel AI — Android Architecture Design
**Version:** 1.0.0  
**Role:** Staff Android Architect  
**Hackathon:** ET AI Hackathon 2026 — Problem Statement 6  
**Platform:** Android (API 26+ / Oreo) | Kotlin | Jetpack Compose | MVVM

---

## Table of Contents

1. [Android Package Structure](#1-android-package-structure)
2. [Module Structure](#2-module-structure)
3. [MVVM Design](#3-mvvm-design)
4. [State Management](#4-state-management)
5. [Dependency Injection Design](#5-dependency-injection-design)
6. [Local Storage Design](#6-local-storage-design)
7. [Recommended Libraries](#7-recommended-libraries)

---

## 1. Android Package Structure

### 1.1 Full Package Tree

```
android/
├── build.gradle.kts                    # Root Gradle build
├── settings.gradle.kts                 # Module includes
├── gradle.properties
│
└── app/
    ├── build.gradle.kts
    ├── proguard-rules.pro
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
        │   ├── res/
        │   │   ├── drawable/
        │   │   ├── values/
        │   │   │   ├── strings.xml
        │   │   │   ├── colors.xml
        │   │   │   └── themes.xml
        │   │   ├── xml/
        │   │   │   ├── accessibility_service_config.xml
        │   │   │   └── notification_listener_config.xml
        │   │   └── raw/                # Local ML models (ONNX, TFLite)
        │   │
        │   └── java/com/sentinel/ai/
        │       │
        │       ├── SentinelApp.kt              # @HiltAndroidApp Application class
        │       │
        │       ├── core/                       # Cross-cutting Android infrastructure
        │       │   ├── di/                     # Hilt DI modules
        │       │   │   ├── AppModule.kt
        │       │   │   ├── NetworkModule.kt
        │       │   │   ├── DatabaseModule.kt
        │       │   │   ├── AgentModule.kt
        │       │   │   └── ServiceModule.kt
        │       │   ├── network/
        │       │   │   ├── ApiClient.kt
        │       │   │   ├── RetrofitBuilder.kt
        │       │   │   ├── OkHttpProvider.kt
        │       │   │   └── interceptors/
        │       │   │       ├── AuthInterceptor.kt
        │       │   │       ├── LoggingInterceptor.kt
        │       │   │       └── RetryInterceptor.kt
        │       │   ├── permissions/
        │       │   │   ├── PermissionManager.kt
        │       │   │   ├── PermissionState.kt
        │       │   │   └── PermissionRationale.kt
        │       │   ├── security/
        │       │   │   ├── EncryptedPrefs.kt
        │       │   │   ├── KeystoreManager.kt
        │       │   │   └── CertificatePinner.kt
        │       │   ├── coroutines/
        │       │   │   ├── DispatcherProvider.kt
        │       │   │   └── CoroutineScopes.kt
        │       │   └── utils/
        │       │       ├── Extensions.kt
        │       │       ├── Logger.kt
        │       │       └── NetworkUtils.kt
        │       │
        │       ├── data/                       # Data layer (repositories + sources)
        │       │   ├── local/
        │       │   │   ├── db/
        │       │   │   │   ├── SentinelDatabase.kt         # @Database Room class
        │       │   │   │   ├── dao/
        │       │   │   │   │   ├── ThreatDao.kt
        │       │   │   │   │   ├── AlertDao.kt
        │       │   │   │   │   └── ScanHistoryDao.kt
        │       │   │   │   ├── entities/
        │       │   │   │   │   ├── ThreatEntity.kt
        │       │   │   │   │   ├── AlertEntity.kt
        │       │   │   │   │   └── ScanHistoryEntity.kt
        │       │   │   │   └── converters/
        │       │   │   │       ├── RiskLevelConverter.kt
        │       │   │   │       └── DateConverter.kt
        │       │   │   └── prefs/
        │       │   │       ├── UserPreferences.kt          # DataStore<Preferences>
        │       │   │       └── PreferenceKeys.kt
        │       │   ├── remote/
        │       │   │   ├── api/
        │       │   │   │   ├── ThreatApiService.kt
        │       │   │   │   ├── LinkApiService.kt
        │       │   │   │   ├── FileApiService.kt
        │       │   │   │   └── CopilotApiService.kt
        │       │   │   └── dto/
        │       │   │       ├── request/
        │       │   │       │   ├── SmsAnalysisRequest.kt
        │       │   │       │   ├── LinkAnalysisRequest.kt
        │       │   │       │   ├── FileAnalysisRequest.kt
        │       │   │       │   └── CopilotChatRequest.kt
        │       │   │       └── response/
        │       │   │           ├── ThreatResponse.kt
        │       │   │           ├── RiskScoreDto.kt
        │       │   │           └── CopilotResponse.kt
        │       │   ├── repository/
        │       │   │   ├── ThreatRepository.kt             # Interface
        │       │   │   ├── ThreatRepositoryImpl.kt         # Implementation
        │       │   │   ├── AlertRepository.kt
        │       │   │   ├── AlertRepositoryImpl.kt
        │       │   │   ├── ScanHistoryRepository.kt
        │       │   │   ├── ScanHistoryRepositoryImpl.kt
        │       │   │   └── CopilotRepository.kt
        │       │   └── mappers/
        │       │       ├── ThreatMapper.kt                 # Entity ↔ Domain model
        │       │       ├── AlertMapper.kt
        │       │       └── ScanResultMapper.kt
        │       │
        │       ├── domain/                     # Pure Kotlin business logic
        │       │   ├── model/
        │       │   │   ├── Threat.kt
        │       │   │   ├── RiskLevel.kt                    # GREEN/YELLOW/RED/CRITICAL
        │       │   │   ├── Alert.kt
        │       │   │   ├── ScanResult.kt
        │       │   │   ├── ScanSource.kt                   # SMS/CALL/WHATSAPP/etc.
        │       │   │   └── CopilotMessage.kt
        │       │   ├── usecase/
        │       │   │   ├── AnalyzeSmsUseCase.kt
        │       │   │   ├── AnalyzeLinkUseCase.kt
        │       │   │   ├── AnalyzeFileUseCase.kt
        │       │   │   ├── AnalyzeCallUseCase.kt
        │       │   │   ├── GetAlertHistoryUseCase.kt
        │       │   │   ├── DismissAlertUseCase.kt
        │       │   │   ├── SendCopilotMessageUseCase.kt
        │       │   │   └── GetScanHistoryUseCase.kt
        │       │   └── repository/             # Repository interfaces (domain contracts)
        │       │       ├── IThreatRepository.kt
        │       │       ├── IAlertRepository.kt
        │       │       └── IScanHistoryRepository.kt
        │       │
        │       ├── listeners/                  # OS-level event receivers
        │       │   ├── SmsReceiver.kt                      # BroadcastReceiver
        │       │   ├── CallReceiver.kt                     # BroadcastReceiver
        │       │   ├── SentinelNotificationListener.kt     # NotificationListenerService
        │       │   └── SentinelAccessibilityService.kt     # AccessibilityService
        │       │
        │       ├── agents/                     # On-device agent coordinators
        │       │   ├── base/
        │       │   │   ├── BaseAgent.kt
        │       │   │   └── AgentResult.kt
        │       │   ├── SmsAgentCoordinator.kt
        │       │   ├── CallAgentCoordinator.kt
        │       │   ├── LinkAgentCoordinator.kt
        │       │   ├── FileAgentCoordinator.kt
        │       │   └── MessageAgentCoordinator.kt
        │       │
        │       ├── services/                   # Android foreground services
        │       │   ├── SentinelGuardService.kt             # Foreground service (always-on)
        │       │   ├── ThreatMonitorService.kt             # Threat event dispatcher
        │       │   ├── BackgroundSyncService.kt            # WorkManager worker wrapper
        │       │   └── OverlayAlertService.kt              # System overlay (SYSTEM_ALERT_WINDOW)
        │       │
        │       └── ui/                         # Jetpack Compose UI
        │           ├── MainActivity.kt
        │           ├── navigation/
        │           │   ├── SentinelNavGraph.kt
        │           │   ├── Screen.kt                       # Sealed class route definitions
        │           │   └── NavArgs.kt
        │           ├── theme/
        │           │   ├── Color.kt
        │           │   ├── Typography.kt
        │           │   ├── Shape.kt
        │           │   └── Theme.kt
        │           ├── screens/
        │           │   ├── onboarding/
        │           │   │   ├── OnboardingScreen.kt
        │           │   │   ├── PermissionRequestScreen.kt
        │           │   │   └── OnboardingViewModel.kt
        │           │   ├── dashboard/
        │           │   │   ├── DashboardScreen.kt
        │           │   │   ├── DashboardViewModel.kt
        │           │   │   └── DashboardUiState.kt
        │           │   ├── alert/
        │           │   │   ├── AlertScreen.kt
        │           │   │   ├── AlertDetailScreen.kt
        │           │   │   ├── AlertViewModel.kt
        │           │   │   └── AlertUiState.kt
        │           │   ├── scanner/
        │           │   │   ├── ScannerScreen.kt
        │           │   │   ├── ScannerViewModel.kt
        │           │   │   └── ScannerUiState.kt
        │           │   ├── copilot/
        │           │   │   ├── CopilotScreen.kt
        │           │   │   ├── CopilotViewModel.kt
        │           │   │   └── CopilotUiState.kt
        │           │   ├── history/
        │           │   │   ├── HistoryScreen.kt
        │           │   │   ├── HistoryViewModel.kt
        │           │   │   └── HistoryUiState.kt
        │           │   └── settings/
        │           │       ├── SettingsScreen.kt
        │           │       ├── SettingsViewModel.kt
        │           │       └── SettingsUiState.kt
        │           └── components/
        │               ├── RiskBadge.kt
        │               ├── ThreatCard.kt
        │               ├── AlertBanner.kt
        │               ├── CopilotChatBubble.kt
        │               ├── OverlayAlertWindow.kt
        │               ├── RiskLevelIndicator.kt
        │               └── ScanSourceChip.kt
        │
        ├── test/
        │   └── java/com/sentinel/ai/
        │       ├── usecase/
        │       ├── repository/
        │       ├── viewmodel/
        │       └── agents/
        │
        └── androidTest/
            └── java/com/sentinel/ai/
                ├── ui/
                ├── db/
                └── services/
```

---

### 1.2 Package Naming Convention

| Package | Purpose | Android Imports Allowed |
|---|---|---|
| `com.sentinel.ai.core` | Cross-cutting infrastructure | Yes |
| `com.sentinel.ai.data` | Data sources, repositories, DTOs | Yes (Room, Retrofit) |
| `com.sentinel.ai.domain` | Business rules, models, use cases | **No** — pure Kotlin only |
| `com.sentinel.ai.listeners` | OS event collection | Yes (BroadcastReceiver, AccessibilityService) |
| `com.sentinel.ai.agents` | Agent coordination, dispatch | Coroutines only |
| `com.sentinel.ai.services` | Foreground services, overlay | Yes (Service, WorkManager) |
| `com.sentinel.ai.ui` | Compose screens, ViewModels | Yes (Compose, ViewModel) |

---

## 2. Module Structure

Sentinel AI uses a **single-app multi-layer** structure for hackathon speed, with clean layer boundaries that allow extraction to Gradle modules in production.

### 2.1 Logical Module Boundaries

```
┌──────────────────────────────────────────────────────────────────┐
│                          :app                                    │
│                                                                  │
│  ┌─────────────┐  ┌──────────────┐  ┌──────────────────────┐   │
│  │    :core    │  │   :domain    │  │       :data          │   │
│  │             │  │  (pure KT)   │  │  (Room/Retrofit/DS)  │   │
│  │  DI, Net,   │  │  Models,     │  │  DAOs, Repos,        │   │
│  │  Security,  │  │  UseCases,   │  │  DTOs, Mappers       │   │
│  │  Utils      │  │  Interfaces  │  │                      │   │
│  └──────┬──────┘  └──────┬───────┘  └──────────┬───────────┘   │
│         │                │                      │               │
│         └────────────────┴──────────────────────┘               │
│                          │                                       │
│  ┌───────────────────────┼──────────────────────────────────┐   │
│  │                  :listeners                              │   │
│  │  SmsReceiver, CallReceiver, NotificationListenerService  │   │
│  │  AccessibilityService                                    │   │
│  └───────────────────────┬──────────────────────────────────┘   │
│                          │                                       │
│  ┌───────────────────────┼──────────────────────────────────┐   │
│  │                   :agents                                │   │
│  │  BaseAgent, SmsAgentCoordinator, LinkAgentCoordinator    │   │
│  │  CallAgentCoordinator, FileAgentCoordinator              │   │
│  └───────────────────────┬──────────────────────────────────┘   │
│                          │                                       │
│  ┌───────────────────────┼──────────────────────────────────┐   │
│  │                  :services                               │   │
│  │  SentinelGuardService, OverlayAlertService               │   │
│  │  ThreatMonitorService, BackgroundSyncService             │   │
│  └───────────────────────┬──────────────────────────────────┘   │
│                          │                                       │
│  ┌───────────────────────┼──────────────────────────────────┐   │
│  │                     :ui                                  │   │
│  │  Navigation, Screens, ViewModels, Components, Theme      │   │
│  └──────────────────────────────────────────────────────────┘   │
└──────────────────────────────────────────────────────────────────┘
```

### 2.2 Dependency Rule

```
ui → domain ← data
 ↓              ↑
 └─── core ─────┘
      ↑
   listeners
   agents
   services
```

The **domain layer has zero dependencies on android.* or data layer types**. All other layers depend inward toward domain — never outward.

### 2.3 Layer Responsibilities

**`:core`**
- Hilt DI module definitions
- Retrofit / OkHttp setup
- Room database builder
- Android Keystore / EncryptedSharedPreferences
- Coroutine dispatcher providers
- Logging (Timber)
- Extension functions and utilities

**`:domain`**
- Kotlin data classes: `Threat`, `Alert`, `ScanResult`, `RiskLevel`, `CopilotMessage`
- Use case classes (`AnalyzeSmsUseCase`, `AnalyzeLinkUseCase`, etc.)
- Repository interfaces (`IThreatRepository`, `IAlertRepository`)
- No Android SDK imports allowed

**`:data`**
- Room entities, DAOs, `SentinelDatabase`
- DataStore preferences
- Retrofit API service interfaces and DTOs
- Repository implementations (consume DAOs + API services)
- Mappers (entity ↔ domain model)

**`:listeners`**
- `SmsReceiver` — BroadcastReceiver for `SMS_RECEIVED`
- `CallReceiver` — BroadcastReceiver for call state
- `SentinelNotificationListener` — NotificationListenerService
- `SentinelAccessibilityService` — AccessibilityService for WhatsApp/Telegram

**`:agents`**
- Lightweight Kotlin coroutine coordinators
- Dispatch OS events to backend via use cases
- No ML inference logic (inference lives on backend)
- Implement offline fallback routing

**`:services`**
- `SentinelGuardService` — always-on foreground service, holds WakeLock
- `OverlayAlertService` — draws `TYPE_APPLICATION_OVERLAY` window
- `ThreatMonitorService` — threat event bus coordinator
- `BackgroundSyncService` — WorkManager `CoroutineWorker` for intelligence sync

**`:ui`**
- Jetpack Compose screens and ViewModels
- Navigation graph
- Design system (theme, components)

---

## 3. MVVM Design

### 3.1 Architecture Overview

```
┌──────────────────────────────────────────────────────────────────┐
│                          UI Layer                                │
│                                                                  │
│   Composable Screen                                              │
│   @Composable fun DashboardScreen(vm: DashboardViewModel)        │
│          │                                                        │
│          │ collectAsStateWithLifecycle()                         │
│          ▼                                                        │
│   UiState (sealed/data class) — single source of truth          │
│          ▲                                                        │
│          │ emit via StateFlow<UiState>                           │
│          │                                                        │
│   ViewModel (Hilt-injected)                                      │
│          │ calls                                                  │
│          ▼                                                        │
├──────────────────────────────────────────────────────────────────┤
│                        Domain Layer                              │
│                                                                  │
│   UseCase (plain Kotlin class, suspend fun)                      │
│          │                                                        │
│          │ invokes                                               │
│          ▼                                                        │
│   Repository Interface (IThreatRepository)                       │
│                                                                  │
├──────────────────────────────────────────────────────────────────┤
│                         Data Layer                               │
│                                                                  │
│   Repository Implementation                                      │
│          ├── Remote: ThreatApiService (Retrofit)                 │
│          └── Local:  ThreatDao (Room)                            │
│                                                                  │
└──────────────────────────────────────────────────────────────────┘
```

### 3.2 ViewModel Contracts

Each ViewModel follows a strict three-type contract:

```kotlin
// UiState — immutable snapshot of what the screen shows
// UiEvent — one-time side effects (navigate, show snackbar)
// UiAction — user intents dispatched to ViewModel

// ─────────────────────────────────────────────────────────────
// Dashboard
// ─────────────────────────────────────────────────────────────
data class DashboardUiState(
    val isLoading: Boolean = false,
    val guardActive: Boolean = false,
    val recentAlerts: List<Alert> = emptyList(),
    val threatSummary: ThreatSummary = ThreatSummary(),
    val error: String? = null
)

sealed interface DashboardUiEvent {
    data class NavigateToAlertDetail(val alertId: String) : DashboardUiEvent
    data class ShowError(val message: String) : DashboardUiEvent
}

sealed interface DashboardUiAction {
    object ToggleGuard : DashboardUiAction
    data class DismissAlert(val alertId: String) : DashboardUiAction
    object RefreshStatus : DashboardUiAction
}

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val getAlertHistoryUseCase: GetAlertHistoryUseCase,
    private val dismissAlertUseCase: DismissAlertUseCase,
    private val userPreferences: UserPreferences,
    private val dispatcherProvider: DispatcherProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private val _uiEvent = Channel<DashboardUiEvent>(Channel.BUFFERED)
    val uiEvent: Flow<DashboardUiEvent> = _uiEvent.receiveAsFlow()

    fun onAction(action: DashboardUiAction) {
        when (action) {
            is DashboardUiAction.ToggleGuard -> toggleGuard()
            is DashboardUiAction.DismissAlert -> dismissAlert(action.alertId)
            is DashboardUiAction.RefreshStatus -> loadDashboard()
        }
    }

    init { loadDashboard() }

    private fun loadDashboard() {
        viewModelScope.launch(dispatcherProvider.io) {
            _uiState.update { it.copy(isLoading = true) }
            getAlertHistoryUseCase()
                .onSuccess { alerts ->
                    _uiState.update { it.copy(isLoading = false, recentAlerts = alerts) }
                }
                .onFailure { err ->
                    _uiState.update { it.copy(isLoading = false) }
                    _uiEvent.send(DashboardUiEvent.ShowError(err.message ?: "Unknown error"))
                }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Alert
// ─────────────────────────────────────────────────────────────
data class AlertUiState(
    val isLoading: Boolean = false,
    val alerts: List<Alert> = emptyList(),
    val selectedAlert: Alert? = null,
    val filter: RiskLevel? = null
)

sealed interface AlertUiAction {
    data class SelectAlert(val alertId: String) : AlertUiAction
    data class FilterByRisk(val level: RiskLevel?) : AlertUiAction
    data class DismissAlert(val alertId: String) : AlertUiAction
}

// ─────────────────────────────────────────────────────────────
// Scanner
// ─────────────────────────────────────────────────────────────
data class ScannerUiState(
    val scanInput: String = "",
    val scanType: ScanType = ScanType.TEXT,
    val isScanning: Boolean = false,
    val scanResult: ScanResult? = null,
    val error: String? = null
)

enum class ScanType { TEXT, LINK, FILE }

// ─────────────────────────────────────────────────────────────
// AI Security Copilot
// ─────────────────────────────────────────────────────────────
data class CopilotUiState(
    val messages: List<CopilotMessage> = emptyList(),
    val inputText: String = "",
    val isTyping: Boolean = false,         // AI is generating response
    val error: String? = null
)

sealed interface CopilotUiAction {
    data class UpdateInput(val text: String) : CopilotUiAction
    object SendMessage : CopilotUiAction
    object ClearHistory : CopilotUiAction
}
```

### 3.3 Use Case Design

Use cases are **single-responsibility suspend functions** returning `Result<T>`:

```kotlin
class AnalyzeSmsUseCase @Inject constructor(
    private val threatRepository: IThreatRepository
) {
    suspend operator fun invoke(
        sender: String,
        body: String,
        timestamp: Long
    ): Result<ScanResult> = runCatching {
        threatRepository.analyzeSms(
            SmsAnalysisRequest(sender = sender, body = body, timestamp = timestamp)
        )
    }
}

class AnalyzeLinkUseCase @Inject constructor(
    private val threatRepository: IThreatRepository
) {
    suspend operator fun invoke(url: String): Result<ScanResult> = runCatching {
        threatRepository.analyzeLink(LinkAnalysisRequest(url = url))
    }
}

class GetAlertHistoryUseCase @Inject constructor(
    private val alertRepository: IAlertRepository
) {
    suspend operator fun invoke(
        limit: Int = 50,
        riskFilter: RiskLevel? = null
    ): Result<List<Alert>> = runCatching {
        alertRepository.getAlerts(limit, riskFilter)
    }
}
```

### 3.4 Composable Screen Pattern

```kotlin
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = hiltViewModel(),
    onNavigateToAlertDetail: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    // One-shot events handled here, not inside the ViewModel
    LaunchedEffect(lifecycleOwner) {
        viewModel.uiEvent.flowWithLifecycle(lifecycleOwner.lifecycle)
            .collect { event ->
                when (event) {
                    is DashboardUiEvent.NavigateToAlertDetail ->
                        onNavigateToAlertDetail(event.alertId)
                    is DashboardUiEvent.ShowError ->
                        /* show Snackbar */ Unit
                }
            }
    }

    DashboardContent(
        uiState = uiState,
        onAction = viewModel::onAction
    )
}

// Stateless content composable — fully testable in Preview
@Composable
private fun DashboardContent(
    uiState: DashboardUiState,
    onAction: (DashboardUiAction) -> Unit
) {
    // Compose UI tree
}
```

---

## 4. State Management

### 4.1 State Hierarchy

```
Application State
├── Global (App-scoped, lives in SentinelGuardService)
│   ├── guardActive: Boolean
│   ├── activeThreats: List<Threat>
│   └── broadcastChannel: SharedFlow<ThreatEvent>
│
├── Screen State (ViewModel-scoped StateFlow<UiState>)
│   ├── DashboardUiState
│   ├── AlertUiState
│   ├── ScannerUiState
│   ├── CopilotUiState
│   ├── HistoryUiState
│   └── SettingsUiState
│
├── Persistent State (Room + DataStore)
│   ├── Alerts (Room — AlertEntity)
│   ├── Threat History (Room — ScanHistoryEntity)
│   └── User Preferences (DataStore — guardEnabled, notificationsEnabled, etc.)
│
└── In-Flight State (Overlay / Notification)
    └── OverlayAlertState (managed by OverlayAlertService)
```

### 4.2 ThreatEvent Global Bus

Services, listeners, and ViewModels communicate via a `SharedFlow` event bus scoped to the Application:

```kotlin
// Defined in SentinelApp, injected via Hilt
class ThreatEventBus @Inject constructor() {
    private val _events = MutableSharedFlow<ThreatEvent>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: SharedFlow<ThreatEvent> = _events.asSharedFlow()

    suspend fun emit(event: ThreatEvent) = _events.emit(event)
}

sealed interface ThreatEvent {
    data class SmsThreatDetected(val scanResult: ScanResult) : ThreatEvent
    data class CallThreatDetected(val scanResult: ScanResult) : ThreatEvent
    data class LinkThreatDetected(val scanResult: ScanResult) : ThreatEvent
    data class FileThreatDetected(val scanResult: ScanResult) : ThreatEvent
    data class CriticalThreatAlert(val threat: Threat) : ThreatEvent
    object GuardActivated : ThreatEvent
    object GuardDeactivated : ThreatEvent
}
```

**Flow of a threat event:**

```
SmsReceiver (BroadcastReceiver)
    │  BroadcastChannel scope
    ▼
SmsAgentCoordinator.process(sms)
    │  AnalyzeSmsUseCase → ThreatRepository → API
    ▼
ThreatEventBus.emit(SmsThreatDetected(result))
    │
    ├──▶ SentinelGuardService  — saves to Room, triggers notification
    ├──▶ OverlayAlertService   — draws overlay if CRITICAL/RED
    └──▶ AlertViewModel        — updates UI state (if app is foreground)
```

### 4.3 UiState Update Pattern

All state mutations use `StateFlow.update {}` (atomic, thread-safe):

```kotlin
// Never: _uiState.value = _uiState.value.copy(isLoading = true)
// Always:
_uiState.update { current -> current.copy(isLoading = true) }
```

### 4.4 Offline-First State Strategy

```
ViewModel requests data
    │
    ▼
Repository checks Room cache first → emit cached state immediately
    │
    ▼ (in parallel)
Repository calls API → on success: update Room → Room Flow re-emits
    │
    ▼
ViewModel state updates automatically via Room's Flow<List<AlertEntity>>
```

This is achieved by exposing Room queries as `Flow<List<T>>` in DAOs:

```kotlin
@Dao
interface AlertDao {
    @Query("SELECT * FROM alerts ORDER BY timestamp DESC LIMIT :limit")
    fun observeAlerts(limit: Int): Flow<List<AlertEntity>>
}
```

### 4.5 Permission State

Permission state is tracked as a `StateFlow` in `PermissionManager`, consumed by the Onboarding screen and the Settings screen:

```kotlin
data class PermissionStatus(
    val smsGranted: Boolean = false,
    val callLogGranted: Boolean = false,
    val notificationListenerEnabled: Boolean = false,
    val accessibilityEnabled: Boolean = false,
    val overlayPermissionGranted: Boolean = false,
    val storageGranted: Boolean = false
)

val allCriticalGranted: Boolean
    get() = smsGranted && callLogGranted && notificationListenerEnabled
```

---

## 5. Dependency Injection Design

### 5.1 Hilt Component Hierarchy

```
@Singleton (ApplicationComponent)
├── AppModule          — Application-wide singletons
├── NetworkModule      — OkHttp, Retrofit, API services
├── DatabaseModule     — Room, DAOs
├── AgentModule        — Agent coordinators
└── ServiceModule      — ThreatEventBus, PermissionManager

@ActivityScoped (ActivityComponent)
└── MainActivity       — injected with nav controller, permission launcher

@ViewModelScoped (ViewModelComponent)
└── All ViewModels     — injected via @HiltViewModel
```

### 5.2 Module Definitions

```kotlin
// ─── AppModule.kt ───────────────────────────────────────────
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideThreatEventBus(): ThreatEventBus = ThreatEventBus()

    @Provides
    @Singleton
    fun provideDispatcherProvider(): DispatcherProvider = DispatcherProvider()

    @Provides
    @Singleton
    fun provideUserPreferences(
        @ApplicationContext context: Context
    ): UserPreferences = UserPreferences(context)
}

// ─── NetworkModule.kt ───────────────────────────────────────
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(
        authInterceptor: AuthInterceptor,
        loggingInterceptor: LoggingInterceptor
    ): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(loggingInterceptor)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .certificatePinner(CertificatePinner.Builder()
            .add("api.sentinel.ai", "sha256/...")
            .build())
        .build()

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    @Provides @Singleton
    fun provideThreatApiService(retrofit: Retrofit): ThreatApiService =
        retrofit.create(ThreatApiService::class.java)

    @Provides @Singleton
    fun provideLinkApiService(retrofit: Retrofit): LinkApiService =
        retrofit.create(LinkApiService::class.java)

    @Provides @Singleton
    fun provideCopilotApiService(retrofit: Retrofit): CopilotApiService =
        retrofit.create(CopilotApiService::class.java)
}

// ─── DatabaseModule.kt ──────────────────────────────────────
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideSentinelDatabase(
        @ApplicationContext context: Context
    ): SentinelDatabase = Room.databaseBuilder(
        context,
        SentinelDatabase::class.java,
        "sentinel_db"
    )
        .addMigrations(MIGRATION_1_2)
        .fallbackToDestructiveMigrationOnDowngrade()
        .build()

    @Provides fun provideThreatDao(db: SentinelDatabase): ThreatDao = db.threatDao()
    @Provides fun provideAlertDao(db: SentinelDatabase): AlertDao = db.alertDao()
    @Provides fun provideScanHistoryDao(db: SentinelDatabase): ScanHistoryDao = db.scanHistoryDao()
}

// ─── AgentModule.kt ─────────────────────────────────────────
@Module
@InstallIn(SingletonComponent::class)
object AgentModule {

    @Provides @Singleton
    fun provideSmsAgentCoordinator(
        analyzeSmsUseCase: AnalyzeSmsUseCase,
        threatEventBus: ThreatEventBus
    ): SmsAgentCoordinator = SmsAgentCoordinator(analyzeSmsUseCase, threatEventBus)

    @Provides @Singleton
    fun provideLinkAgentCoordinator(
        analyzeLinkUseCase: AnalyzeLinkUseCase,
        threatEventBus: ThreatEventBus
    ): LinkAgentCoordinator = LinkAgentCoordinator(analyzeLinkUseCase, threatEventBus)

    @Provides @Singleton
    fun provideCallAgentCoordinator(
        analyzeCallUseCase: AnalyzeCallUseCase,
        threatEventBus: ThreatEventBus
    ): CallAgentCoordinator = CallAgentCoordinator(analyzeCallUseCase, threatEventBus)

    @Provides @Singleton
    fun provideFileAgentCoordinator(
        analyzeFileUseCase: AnalyzeFileUseCase,
        threatEventBus: ThreatEventBus
    ): FileAgentCoordinator = FileAgentCoordinator(analyzeFileUseCase, threatEventBus)
}

// ─── Repository bindings ─────────────────────────────────────
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds @Singleton
    abstract fun bindThreatRepository(
        impl: ThreatRepositoryImpl
    ): IThreatRepository

    @Binds @Singleton
    abstract fun bindAlertRepository(
        impl: AlertRepositoryImpl
    ): IAlertRepository

    @Binds @Singleton
    abstract fun bindScanHistoryRepository(
        impl: ScanHistoryRepositoryImpl
    ): IScanHistoryRepository
}
```

### 5.3 Service and Receiver Injection

Android components not constructable by Hilt require `@AndroidEntryPoint`:

```kotlin
@AndroidEntryPoint
class SmsReceiver : BroadcastReceiver() {
    @Inject lateinit var smsAgentCoordinator: SmsAgentCoordinator

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            messages.forEach { sms ->
                goAsync().also { pending ->
                    CoroutineScope(Dispatchers.IO).launch {
                        smsAgentCoordinator.process(
                            sender = sms.originatingAddress ?: "",
                            body = sms.messageBody,
                            timestamp = sms.timestampMillis
                        )
                        pending.finish()
                    }
                }
            }
        }
    }
}

@AndroidEntryPoint
class SentinelGuardService : Service() {
    @Inject lateinit var threatEventBus: ThreatEventBus
    @Inject lateinit var alertRepository: IAlertRepository
    // ...
}

@AndroidEntryPoint
class SentinelAccessibilityService : AccessibilityService() {
    @Inject lateinit var messageAgentCoordinator: MessageAgentCoordinator
    // ...
}
```

### 5.4 DispatcherProvider (Testability)

```kotlin
open class DispatcherProvider {
    open val main: CoroutineDispatcher = Dispatchers.Main
    open val io: CoroutineDispatcher = Dispatchers.IO
    open val default: CoroutineDispatcher = Dispatchers.Default
}

// In tests — swap with:
class TestDispatcherProvider : DispatcherProvider() {
    val testDispatcher = UnconfinedTestDispatcher()
    override val main: CoroutineDispatcher = testDispatcher
    override val io: CoroutineDispatcher = testDispatcher
    override val default: CoroutineDispatcher = testDispatcher
}
```

---

## 6. Local Storage Design

### 6.1 Room Database Schema

```kotlin
@Database(
    entities = [ThreatEntity::class, AlertEntity::class, ScanHistoryEntity::class],
    version = 1,
    exportSchema = true
)
@TypeConverters(RiskLevelConverter::class, DateConverter::class, ScanSourceConverter::class)
abstract class SentinelDatabase : RoomDatabase() {
    abstract fun threatDao(): ThreatDao
    abstract fun alertDao(): AlertDao
    abstract fun scanHistoryDao(): ScanHistoryDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Reserved for schema evolution
            }
        }
    }
}
```

#### ThreatEntity

```kotlin
@Entity(tableName = "threats")
data class ThreatEntity(
    @PrimaryKey val id: String,                  // UUID from backend
    val source: String,                           // ScanSource name
    val content: String,                          // Raw content (SMS body, URL, etc.)
    val riskLevel: RiskLevel,
    val riskScore: Float,                         // 0.0–1.0
    val explanation: String,
    val recommendation: String,
    val signals: String,                          // JSON array of signal strings
    val timestamp: Long,
    val isAcknowledged: Boolean = false
)
```

#### AlertEntity

```kotlin
@Entity(tableName = "alerts")
data class AlertEntity(
    @PrimaryKey val id: String,
    val threatId: String,
    val title: String,
    val summary: String,
    val riskLevel: RiskLevel,
    val timestamp: Long,
    val isDismissed: Boolean = false,
    val isRead: Boolean = false
)
```

#### ScanHistoryEntity

```kotlin
@Entity(tableName = "scan_history")
data class ScanHistoryEntity(
    @PrimaryKey val id: String,
    val source: String,
    val inputPreview: String,                    // First 120 chars, no sensitive data
    val riskLevel: RiskLevel,
    val riskScore: Float,
    val timestamp: Long
)
```

### 6.2 DAO Contracts

```kotlin
@Dao
interface AlertDao {
    // Observe — Room emits on every change (offline-first)
    @Query("SELECT * FROM alerts WHERE isDismissed = 0 ORDER BY timestamp DESC LIMIT :limit")
    fun observeActiveAlerts(limit: Int = 50): Flow<List<AlertEntity>>

    @Query("SELECT * FROM alerts WHERE riskLevel = :level ORDER BY timestamp DESC")
    fun observeAlertsByRisk(level: RiskLevel): Flow<List<AlertEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlert(alert: AlertEntity)

    @Query("UPDATE alerts SET isDismissed = 1 WHERE id = :alertId")
    suspend fun dismissAlert(alertId: String)

    @Query("UPDATE alerts SET isRead = 1 WHERE id = :alertId")
    suspend fun markAsRead(alertId: String)

    @Query("DELETE FROM alerts WHERE timestamp < :cutoffMs")
    suspend fun deleteOlderThan(cutoffMs: Long)
}

@Dao
interface ScanHistoryDao {
    @Query("SELECT * FROM scan_history ORDER BY timestamp DESC LIMIT :limit")
    fun observeHistory(limit: Int = 100): Flow<List<ScanHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScan(scan: ScanHistoryEntity)

    @Query("SELECT COUNT(*) FROM scan_history WHERE riskLevel IN ('RED','CRITICAL') AND timestamp > :sinceMs")
    suspend fun countHighRiskScansAfter(sinceMs: Long): Int
}
```

### 6.3 DataStore Preferences

```kotlin
object PreferenceKeys {
    val GUARD_ENABLED = booleanPreferencesKey("guard_enabled")
    val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
    val OVERLAY_ALERTS_ENABLED = booleanPreferencesKey("overlay_alerts_enabled")
    val MINIMUM_ALERT_RISK_LEVEL = stringPreferencesKey("minimum_alert_risk_level")
    val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
    val API_AUTH_TOKEN = stringPreferencesKey("api_auth_token")   // stored in EncryptedDataStore
    val LAST_SYNC_TIMESTAMP = longPreferencesKey("last_sync_timestamp")
    val SCAN_COUNT_TODAY = intPreferencesKey("scan_count_today")
}

class UserPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    // Standard DataStore for non-sensitive prefs
    private val dataStore: DataStore<Preferences> = context.createDataStore("sentinel_prefs")

    val guardEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[PreferenceKeys.GUARD_ENABLED] ?: true
    }

    val overlayEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[PreferenceKeys.OVERLAY_ALERTS_ENABLED] ?: true
    }

    suspend fun setGuardEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[PreferenceKeys.GUARD_ENABLED] = enabled }
    }
}
```

### 6.4 Encrypted Storage

Sensitive data (auth tokens, user ID) uses `EncryptedSharedPreferences` backed by Android Keystore:

```kotlin
class EncryptedPrefs @Inject constructor(
    @ApplicationContext context: Context
) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "sentinel_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun putAuthToken(token: String) = prefs.edit().putString("auth_token", token).apply()
    fun getAuthToken(): String? = prefs.getString("auth_token", null)
    fun clearAll() = prefs.edit().clear().apply()
}
```

### 6.5 Data Retention Policy

| Table / Store | Retention | Cleanup Trigger |
|---|---|---|
| `alerts` (dismissed) | 30 days | WorkManager daily task |
| `scan_history` | 90 days | WorkManager weekly task |
| `threats` (acknowledged) | 60 days | WorkManager weekly task |
| DataStore prefs | Permanent until uninstall | — |
| EncryptedPrefs (tokens) | Until logout | User action |

```kotlin
class DataRetentionWorker @Inject constructor(
    context: Context,
    params: WorkerParameters,
    private val alertDao: AlertDao,
    private val scanHistoryDao: ScanHistoryDao
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
        alertDao.deleteOlderThan(thirtyDaysAgo)
        return Result.success()
    }
}
```

---

## 7. Recommended Libraries

### 7.1 Core Android & Kotlin

| Library | Version | Purpose |
|---|---|---|
| `kotlin-stdlib` | 2.0.x | Kotlin standard library |
| `kotlinx-coroutines-android` | 1.8.x | Structured concurrency, Flow |
| `androidx.core:core-ktx` | 1.13.x | Kotlin Android extensions |
| `androidx.lifecycle:lifecycle-viewmodel-ktx` | 2.8.x | ViewModel + viewModelScope |
| `androidx.lifecycle:lifecycle-runtime-compose` | 2.8.x | `collectAsStateWithLifecycle` |

### 7.2 Jetpack Compose

| Library | Version | Purpose |
|---|---|---|
| `androidx.compose.bom` | 2024.06.x | Compose BOM — pins all Compose versions |
| `androidx.compose.ui:ui` | BOM | Core Compose UI |
| `androidx.compose.material3:material3` | BOM | Material 3 design components |
| `androidx.compose.ui:ui-tooling-preview` | BOM | `@Preview` support |
| `androidx.activity:activity-compose` | 1.9.x | `setContent {}`, `LocalContext` |
| `androidx.navigation:navigation-compose` | 2.7.x | Compose Navigation Graph |
| `androidx.hilt:hilt-navigation-compose` | 1.2.x | `hiltViewModel()` in Compose |

### 7.3 Dependency Injection

| Library | Version | Purpose |
|---|---|---|
| `com.google.dagger:hilt-android` | 2.51.x | Hilt DI runtime |
| `com.google.dagger:hilt-android-compiler` | 2.51.x | Hilt annotation processor (kapt) |
| `androidx.hilt:hilt-work` | 1.2.x | Hilt integration with WorkManager |
| `androidx.hilt:hilt-compiler` | 1.2.x | Hilt WorkManager compiler |

### 7.4 Local Storage

| Library | Version | Purpose |
|---|---|---|
| `androidx.room:room-runtime` | 2.6.x | Room ORM runtime |
| `androidx.room:room-ktx` | 2.6.x | Room coroutine/Flow extensions |
| `androidx.room:room-compiler` | 2.6.x | Room annotation processor (kapt) |
| `androidx.datastore:datastore-preferences` | 1.1.x | Typed DataStore for preferences |
| `androidx.security:security-crypto` | 1.1.x | EncryptedSharedPreferences |

### 7.5 Networking

| Library | Version | Purpose |
|---|---|---|
| `com.squareup.retrofit2:retrofit` | 2.11.x | HTTP client |
| `com.squareup.retrofit2:converter-gson` | 2.11.x | JSON serialization |
| `com.squareup.okhttp3:okhttp` | 4.12.x | HTTP engine |
| `com.squareup.okhttp3:logging-interceptor` | 4.12.x | Request/response logging |
| `com.google.code.gson:gson` | 2.10.x | JSON parsing |

### 7.6 Background Work

| Library | Version | Purpose |
|---|---|---|
| `androidx.work:work-runtime-ktx` | 2.9.x | WorkManager — background sync |
| `androidx.work:work-hilt` | 2.9.x | Hilt WorkManager integration |

### 7.7 Android Services

| Library | Version | Purpose |
|---|---|---|
| `androidx.core:core-ktx` | 1.13.x | NotificationCompat, Service helpers |
| Android SDK `AccessibilityService` | Built-in | WhatsApp/Telegram monitoring |
| Android SDK `NotificationListenerService` | Built-in | Notification interception |
| Android SDK `SYSTEM_ALERT_WINDOW` | Built-in | Overlay alert window |

### 7.8 Permissions

| Library | Version | Purpose |
|---|---|---|
| `com.google.accompanist:accompanist-permissions` | 0.34.x | Compose permission handling |

### 7.9 Logging & Observability

| Library | Version | Purpose |
|---|---|---|
| `com.jakewharton.timber:timber` | 5.0.x | Structured logging (no-op in release) |
| `androidx.startup:startup-runtime` | 1.1.x | App startup initializer for Timber |

### 7.10 Testing

| Library | Version | Purpose |
|---|---|---|
| `junit:junit` | 4.13.x | Unit test runner |
| `org.jetbrains.kotlinx:kotlinx-coroutines-test` | 1.8.x | `runTest`, `TestDispatcher` |
| `androidx.arch.core:core-testing` | 2.2.x | `InstantTaskExecutorRule` |
| `io.mockk:mockk` | 1.13.x | Kotlin-native mocking |
| `com.google.dagger:hilt-android-testing` | 2.51.x | Hilt instrumentation test support |
| `androidx.room:room-testing` | 2.6.x | In-memory Room for tests |
| `androidx.test.espresso:espresso-core` | 3.5.x | UI instrumentation tests |
| `androidx.compose.ui:ui-test-junit4` | BOM | Compose UI testing |

### 7.11 Build Tooling

| Tool | Version | Purpose |
|---|---|---|
| AGP (Android Gradle Plugin) | 8.5.x | Android build system |
| Kotlin | 2.0.x | Language compiler |
| KSP (Kotlin Symbol Processing) | 2.0.x | Preferred over kapt for Room/Hilt where supported |
| `gradle.properties` | — | `org.gradle.parallel=true`, `org.gradle.caching=true` |

---

### 7.12 AndroidManifest Permissions Reference

```xml
<!-- SMS -->
<uses-permission android:name="android.permission.RECEIVE_SMS" />
<uses-permission android:name="android.permission.READ_SMS" />

<!-- Calls -->
<uses-permission android:name="android.permission.READ_CALL_LOG" />
<uses-permission android:name="android.permission.READ_PHONE_STATE" />

<!-- Network -->
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

<!-- Foreground service -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />

<!-- Overlay (alert windows) -->
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />

<!-- Wake lock (guard service) -->
<uses-permission android:name="android.permission.WAKE_LOCK" />

<!-- File analysis -->
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"
    android:maxSdkVersion="32" />
<uses-permission android:name="android.permission.READ_MEDIA_IMAGES"
    android:minSdkVersion="33" />

<!-- Notification listener — granted via Settings, not runtime -->
<!-- AccessibilityService — granted via Settings, not runtime -->
```

---

## Architecture Decision Records (ADRs)

### ADR-001: Single-Module, Multi-Layer vs Multi-Module Gradle
**Decision:** Single Gradle module with strict package boundaries for hackathon MVP.  
**Rationale:** Multi-module Gradle setup adds 30–40% build time overhead and significant configuration cost. Package-level layer enforcement achieves the same isolation for an MVP. Migration to Gradle modules is a pre-V2 task.

### ADR-002: StateFlow + sealed UiState over LiveData
**Decision:** `StateFlow<UiState>` with sealed UiEvent channel for side effects.  
**Rationale:** StateFlow is lifecycle-safe with `collectAsStateWithLifecycle`, eliminates the LiveData dependency, and is idiomatic with Compose. The sealed UiEvent channel prevents one-shot events (navigation, Snackbar) from re-firing on recomposition.

### ADR-003: ThreatEventBus (SharedFlow) over LocalBroadcastManager
**Decision:** Kotlin SharedFlow for inter-component communication.  
**Rationale:** `LocalBroadcastManager` is deprecated. SharedFlow is type-safe, coroutine-native, and respects backpressure. It allows `SmsReceiver`, `SentinelAccessibilityService`, and `SentinelGuardService` to communicate without tight coupling.

### ADR-004: Offline-First with Room + DataStore
**Decision:** Room as the single source of truth; API calls write to Room, UI observes Room flows.  
**Rationale:** Fraud alerts must be accessible even without connectivity. Room's `Flow<List<T>>` eliminates manual cache invalidation and ensures the UI always reflects persisted data.

### ADR-005: EncryptedSharedPreferences for Auth Tokens
**Decision:** Android Keystore-backed `EncryptedSharedPreferences` for tokens; plain DataStore for non-sensitive preferences.  
**Rationale:** Auth tokens must never be stored in plain text. AES256-GCM via Android Keystore ensures tokens survive app uninstall only if device is unrooted. Plain DataStore is sufficient for UI preferences.

### ADR-006: Accessibility Service Scope
**Decision:** `SentinelAccessibilityService` reads notification content only; it does not intercept keystrokes or read clipboard unprompted.  
**Rationale:** Minimal-permission approach builds user trust. WhatsApp and Telegram content is accessible via `AccessibilityNodeInfo` from the notification shade — no need for invasive input monitoring.

---

*"Think Before You Click. Sentinel Thinks Before You Do."*

---
**Document Version:** 1.0.0  
**Status:** Draft for Hackathon MVP  
**Last Updated:** 2026-06-23
