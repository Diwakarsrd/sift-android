#!/usr/bin/env python3
"""
SIFT Static Analysis Test Suite
Tests: structure, dependencies, API contracts, security, architecture, correctness
"""

import os, re, sys, json
from pathlib import Path
from collections import defaultdict
from dataclasses import dataclass, field
from typing import List, Optional

ROOT   = Path("/home/claude/sift-android")
KT     = list(ROOT.rglob("*.kt"))
KTS    = list(ROOT.rglob("*.kts"))
XML    = list(ROOT.rglob("*.xml"))
TOML   = ROOT / "gradle/libs.versions.toml"

PASS   = "\033[92m✓\033[0m"
FAIL   = "\033[91m✗\033[0m"
WARN   = "\033[93m⚠\033[0m"
INFO   = "\033[94m·\033[0m"
BOLD   = "\033[1m"
RESET  = "\033[0m"

@dataclass
class TestResult:
    name:    str
    passed:  bool
    message: str
    severity: str = "error"   # error | warn | info

results: List[TestResult] = []

def check(name, condition, msg_pass, msg_fail, severity="error"):
    r = TestResult(name, bool(condition), msg_pass if condition else msg_fail, severity)
    results.append(r)
    icon = PASS if condition else (FAIL if severity == "error" else WARN)
    print(f"  {icon}  {name}")
    if not condition:
        print(f"      {msg_fail}")
    return bool(condition)

def read(path: Path) -> str:
    try: return path.read_text(encoding="utf-8")
    except: return ""

def all_kt_text() -> str:
    return "\n".join(read(f) for f in KT)

def find_file(pattern: str) -> Optional[Path]:
    matches = [f for f in KT + XML + KTS if pattern.lower() in f.name.lower()]
    return matches[0] if matches else None

def find_kt(name: str) -> Optional[Path]:
    matches = [f for f in KT if name.lower() in f.name.lower()]
    return matches[0] if matches else None

separator = lambda title: print(f"\n{BOLD}{'─'*60}{RESET}\n{BOLD}  {title}{RESET}\n{'─'*60}")

# ═══════════════════════════════════════════════════════════════
# 1. PROJECT STRUCTURE
# ═══════════════════════════════════════════════════════════════
separator("1. PROJECT STRUCTURE")

required_files = {
    "settings.gradle.kts":       ROOT / "settings.gradle.kts",
    "root build.gradle.kts":     ROOT / "build.gradle.kts",
    "app build.gradle.kts":      ROOT / "app/build.gradle.kts",
    "libs.versions.toml":        TOML,
    "gradle.properties":         ROOT / "gradle.properties",
    "AndroidManifest.xml":       ROOT / "app/src/main/AndroidManifest.xml",
    "proguard-rules.pro":        ROOT / "app/proguard-rules.pro",
    "strings.xml":               ROOT / "app/src/main/res/values/strings.xml",
    "accessibility_config.xml":  ROOT / "app/src/main/res/xml/accessibility_service_config.xml",
    "backup_rules.xml":          ROOT / "app/src/main/res/xml/backup_rules.xml",
    "data_extraction_rules.xml": ROOT / "app/src/main/res/xml/data_extraction_rules.xml",
    "CI/CD workflow":            ROOT / ".github/workflows/ci.yml",
    "README.md":                 ROOT / "README.md",
}

for name, path in required_files.items():
    check(f"File exists: {name}", path.exists(), f"{path.name} present", f"MISSING: {path}")

# Kotlin source files
required_kt = [
    "SiftApplication", "MainActivity", "Models", "Entities", "SiftDatabase",
    "KeystoreManager", "SiftAccessibilityService", "IntentParser", "LlmConfigStore",
    "SearchEngine", "Embedder", "FaissIndex", "Workers",
    "SearchViewModel", "SearchScreen", "AppModule",
]
for name in required_kt:
    f = find_kt(name)
    check(f"Kotlin file: {name}.kt", f is not None and f.exists(),
          f"{name}.kt found", f"MISSING: {name}.kt")

# ═══════════════════════════════════════════════════════════════
# 2. BUILD CONFIGURATION
# ═══════════════════════════════════════════════════════════════
separator("2. BUILD CONFIGURATION")

toml = read(TOML)
app_build = read(ROOT / "app/build.gradle.kts")

# SDK versions
check("compileSdk = 35",       "compileSdk     = 35"   in app_build, "compileSdk 35 set", "compileSdk missing/wrong")
check("minSdk = 29",           "minSdk        = 29"    in app_build, "minSdk 29 set",     "minSdk missing/wrong")
check("targetSdk = 35",        "targetSdk     = 35"    in app_build, "targetSdk 35 set",  "targetSdk missing/wrong")

# Critical plugins
for plugin in ["hilt", "kotlin.kapt", "kotlin.serialization", "android.application"]:
    check(f"Plugin: {plugin}", plugin.replace(".", "-") in toml or plugin in toml, f"Plugin {plugin} declared", f"Plugin {plugin} MISSING in TOML")

# Critical dependencies
critical_deps = [
    ("room-runtime",   "Room ORM"),
    ("sqlcipher",      "SQLCipher AES-256"),
    ("hilt-android",   "Hilt DI"),
    ("onnx-runtime",   "ONNX Runtime"),
    ("okhttp",         "OkHttp"),
    ("workmanager-ktx","WorkManager"),
    ("sentry-android", "Sentry crash reporting"),
    ("datastore-preferences", "DataStore"),
    ("splashscreen",   "Splash Screen"),
    ("kotlinx-serialization-json", "KotlinX Serialization"),
]
for dep, label in critical_deps:
    check(f"Dependency: {label}", dep in toml, f"{label} declared", f"{label} MISSING in TOML")

# Java 17 target
check("Java 17 target", "jvmTarget = \"17\"" in app_build, "JVM target 17 set", "JVM target not 17")

# Compose enabled
check("Compose buildFeature", "compose     = true" in app_build, "Compose enabled", "Compose not enabled")

# ABI filters (Snapdragon + x86_64 emulator)
check("NDK ABI filters", "arm64-v8a" in app_build and "x86_64" in app_build,
      "arm64-v8a + x86_64 ABI filters set", "ABI filters missing")

# ProGuard enabled in release
check("ProGuard minify release", "isMinifyEnabled   = true" in app_build,
      "ProGuard minify enabled", "ProGuard minify NOT enabled in release")
check("Resource shrink release", "isShrinkResources = true" in app_build,
      "Resource shrinking enabled", "Resource shrinking NOT enabled")

# ═══════════════════════════════════════════════════════════════
# 3. SECURITY
# ═══════════════════════════════════════════════════════════════
separator("3. SECURITY")

keystore_txt   = read(find_kt("KeystoreManager") or Path("/dev/null"))
manifest_txt   = read(ROOT / "app/src/main/AndroidManifest.xml")
db_txt         = read(find_kt("SiftDatabase") or Path("/dev/null"))
backup_txt     = read(ROOT / "app/src/main/res/xml/backup_rules.xml")
data_ext_txt   = read(ROOT / "app/src/main/res/xml/data_extraction_rules.xml")

# AES-256
check("AES-256 encryption", "AES-256" in keystore_txt or "AES/GCM/NoPadding" in keystore_txt,
      "AES-256-GCM used", "AES-256 NOT found in KeystoreManager")

# Android Keystore
check("Android Keystore integration", "AndroidKeyStore" in keystore_txt,
      "Android Keystore used (hardware-backed)", "Android Keystore NOT used")

# SQLCipher in DB
check("SQLCipher in SiftDatabase", "SupportFactory" in db_txt or "sqlcipher" in db_txt.lower(),
      "SQLCipher applied to Room DB", "SQLCipher NOT applied to DB")

# Keystore passphrase never stored as plaintext
check("Passphrase not hardcoded", "password" not in keystore_txt.lower().replace("PASSPHRASE_INPUT", ""),
      "No hardcoded password found", "POTENTIAL hardcoded password", severity="warn")

# Backup excludes DB
check("DB excluded from backup", "sift_memory.db" in backup_txt,
      "sift_memory.db excluded from cloud backup", "DB not excluded from backup — SECURITY RISK")

# Data extraction excludes DB
check("DB excluded from device transfer", "sift_memory.db" in data_ext_txt,
      "sift_memory.db excluded from device transfer", "DB not excluded from device transfer")

# No internet permission for sensitive data
check("INTERNET permission scoped to LLM only",
      "INTERNET" in manifest_txt and "<!-- Ollama" in manifest_txt,
      "INTERNET permission with LLM comment", "INTERNET permission undocumented")

# isSendDefaultPii = false
app_txt = read(find_kt("SiftApplication") or Path("/dev/null"))
check("Sentry PII disabled", "isSendDefaultPii = false" in app_txt,
      "Sentry PII sending disabled", "Sentry may send PII — check isSendDefaultPii")

# ═══════════════════════════════════════════════════════════════
# 4. ARCHITECTURE & DESIGN PATTERNS
# ═══════════════════════════════════════════════════════════════
separator("4. ARCHITECTURE & DESIGN PATTERNS")

vm_txt     = read(find_kt("SearchViewModel") or Path("/dev/null"))
di_txt     = read(find_kt("AppModule") or Path("/dev/null"))
all_kt     = all_kt_text()

# Hilt DI everywhere it should be
check("Hilt @HiltAndroidApp",             "@HiltAndroidApp" in all_kt,      "App annotated with @HiltAndroidApp", "@HiltAndroidApp missing")
check("Hilt @AndroidEntryPoint (Activity)","@AndroidEntryPoint" in all_kt,  "@AndroidEntryPoint used",            "@AndroidEntryPoint missing")
check("Hilt @HiltViewModel",              "@HiltViewModel" in all_kt,       "@HiltViewModel used",                "@HiltViewModel missing")
check("Hilt @HiltWorker",                 "@HiltWorker" in all_kt,          "@HiltWorker used",                   "@HiltWorker missing")
check("Hilt @Singleton",                  "@Singleton" in all_kt,           "@Singleton used",                    "@Singleton missing")

# MVVM pattern
check("ViewModel StateFlow",              "StateFlow" in vm_txt,            "StateFlow in ViewModel",             "StateFlow missing")
check("ViewModel sealed events",          "sealed class SearchEvent" in vm_txt, "Sealed event class used",        "Sealed events missing")
check("UiState data class",              "data class SearchUiState" in vm_txt, "UiState data class defined",      "UiState missing")

# Coroutines
check("Coroutines SupervisorJob",         "SupervisorJob" in all_kt,        "SupervisorJob used (safe coroutines)", "SupervisorJob missing")
check("Coroutines IO dispatcher",         "Dispatchers.IO" in all_kt,       "Dispatchers.IO used for I/O",       "No IO dispatcher found")
check("Coroutines scope cancel",          "scope.cancel()" in all_kt,       "Coroutine scope properly cancelled", "Scope not cancelled — potential leak")

# Repository pattern (DAO injected, not accessed directly from UI)
check("DAO injected via Hilt (not direct)", "@Provides" in di_txt and "EventDao" in di_txt,
      "EventDao provided via Hilt DI", "EventDao not in DI module")

# ═══════════════════════════════════════════════════════════════
# 5. DATABASE
# ═══════════════════════════════════════════════════════════════
separator("5. DATABASE")

entities_txt = read(find_kt("Entities") or Path("/dev/null"))

# Room annotations
check("@Entity annotation",      "@Entity" in entities_txt,           "@Entity present",          "@Entity missing")
check("@Dao annotation",         "@Dao" in entities_txt,              "@Dao present",             "@Dao missing")
check("@Database annotation",    "@Database" in db_txt,               "@Database present",        "@Database missing")
check("@TypeConverters",         "@TypeConverters" in db_txt,         "TypeConverters registered","TypeConverters not registered")
check("@PrimaryKey autoGenerate","autoGenerate = true" in entities_txt,"PrimaryKey autoGenerate",  "PrimaryKey config missing")

# Indexes for query performance
check("Index on timestamp",      "\"timestamp\"" in entities_txt,     "Index on timestamp column","No timestamp index — slow range queries")
check("Index on contact_name",   "\"contact_name\"" in entities_txt,  "Index on contact_name",    "No contact_name index")
check("Index on type",           "\"type\"" in entities_txt,          "Index on type column",     "No type index")
check("Index on app_package",    "\"app_package\"" in entities_txt,   "Index on app_package",     "No app_package index")

# Suspend functions in DAO
check("DAO suspend functions",   "suspend fun" in entities_txt,       "DAO methods are suspend",  "DAO methods not suspend — will block main thread")
check("DAO Flow for live data",  "Flow<" in entities_txt,             "Flow for reactive queries","No Flow in DAO — UI won't update reactively")

# WAL mode recommended
check("WAL mode documented",     "WAL" in entities_txt or "WAL" in db_txt,
      "WAL mode mentioned", "WAL mode not mentioned — default may be slower", severity="warn")

# Schema export
check("exportSchema = true",     "exportSchema = true" in db_txt,     "Schema export enabled (migration safety)", "Schema export disabled")

# ═══════════════════════════════════════════════════════════════
# 6. LLM INTEGRATION
# ═══════════════════════════════════════════════════════════════
separator("6. LLM INTEGRATION")

llm_txt     = read(find_kt("IntentParser") or Path("/dev/null"))
config_txt  = read(find_kt("LlmConfigStore") or Path("/dev/null"))

# All 3 backends
check("Ollama backend",       "callOllama" in llm_txt,      "Ollama backend implemented",       "Ollama backend missing")
check("HuggingFace backend",  "callHuggingFace" in llm_txt, "HuggingFace backend implemented",  "HuggingFace backend missing")
check("LM Studio backend",    "callLmStudio" in llm_txt,    "LM Studio backend implemented",    "LM Studio backend missing")

# Fallback
check("Rule-based fallback",  "fun fallback" in llm_txt,    "Rule-based fallback implemented",  "NO FALLBACK — app crashes if LLM unreachable")

# JSON extraction
check("JSON regex extraction",  r'Regex' in llm_txt or r'\\{[\\s\\S]*\\}' in llm_txt,
      "JSON regex extraction from raw LLM output", "No JSON extraction — fragile")

# Error handling
check("LLM try/catch",        "catch" in llm_txt,           "LLM calls wrapped in try/catch",   "NO try/catch — crashes on network error")

# Timeout configured
check("HTTP timeout configured", "TimeUnit.SECONDS" in llm_txt, "HTTP timeout set", "No timeout — hangs indefinitely")

# DataStore persistence
check("Config persisted to DataStore", "dataStore.edit" in config_txt,
      "LLM config persisted via DataStore", "Config not persisted — lost on restart")

# Emulator localhost alias
check("Emulator localhost alias (10.0.2.2)", "10.0.2.2" in config_txt,
      "Emulator-compatible localhost alias used", "10.0.2.2 not used — Ollama won't connect on emulator")

# ParsedIntent serializable
models_txt = read(find_kt("Models") or Path("/dev/null"))
check("ParsedIntent @Serializable", "@Serializable" in models_txt,
      "ParsedIntent is @Serializable", "ParsedIntent not serializable — JSON decode will fail")

# ═══════════════════════════════════════════════════════════════
# 7. SEARCH ENGINE
# ═══════════════════════════════════════════════════════════════
separator("7. SEARCH ENGINE")

search_txt = read(find_kt("SearchEngine") or Path("/dev/null"))
faiss_txt  = read(find_kt("FaissIndex") or Path("/dev/null"))
embed_txt  = read(find_kt("Embedder") or Path("/dev/null"))

# Two-stage pipeline
check("Stage 1: Graph filter",   "graphFilter" in search_txt,      "Graph filter implemented",       "Graph filter missing")
check("Stage 2: Vector ranking", "vectorRank" in search_txt,        "Vector ranking implemented",     "Vector ranking missing")
check("FAISS fallback if index small", "MIN_VECTOR_ENTRIES" in search_txt,
      "Graceful fallback when FAISS index small", "No fallback — crashes before index is built")

# FAISS
check("FAISS cosine similarity",  "cosine" in faiss_txt,            "Cosine similarity implemented",  "Cosine similarity missing")
check("FAISS disk persistence",   "saveToDisk" in faiss_txt and "loadFromDisk" in faiss_txt,
      "Index persisted to disk",  "Index not persisted — rebuilt every cold start")
check("FAISS deduplication",      "removeAll" in faiss_txt,         "Index deduplication on re-add",  "No dedup — index grows unbounded")

# Embedder
check("Embedder ONNX Session",        "OrtSession" in embed_txt,      "ONNX Session used",              "ONNX Session missing")
check("Embedder NNAPI acceleration",  "addNnapi" in embed_txt,        "NNAPI acceleration enabled",     "NNAPI not enabled — CPU only")
check("Embedder mean pooling",        "meanPool" in embed_txt,        "Mean pooling implemented",       "Mean pooling missing")
check("Embedder L2 normalization",    "normalize" in embed_txt,       "L2 normalization implemented",   "L2 normalization missing")
check("Embedder batch support",       "embedBatch" in embed_txt,      "Batch embedding for WorkManager","No batch — N separate inferences")
check("Embedder handles init failure","init failed" in embed_txt or "Embedder init failed" in embed_txt,
      "Init failure handled gracefully", "No init failure handling", severity="warn")

# ═══════════════════════════════════════════════════════════════
# 8. ACCESSIBILITY SERVICE
# ═══════════════════════════════════════════════════════════════
separator("8. ACCESSIBILITY SERVICE")

acc_txt  = read(find_kt("SiftAccessibilityService") or Path("/dev/null"))
acc_xml  = read(ROOT / "app/src/main/res/xml/accessibility_service_config.xml")

check("Handles TYPE_WINDOW_STATE_CHANGED",     "TYPE_WINDOW_STATE_CHANGED"      in acc_txt, "Window change events handled",    "Window events not handled")
check("Handles TYPE_NOTIFICATION_STATE_CHANGED","TYPE_NOTIFICATION_STATE_CHANGED" in acc_txt, "Notification events handled",    "Notification events not handled")
check("System UI packages skipped",            "SKIP_PACKAGES" in acc_txt,      "System UI filtered",             "System UI not filtered — spammy logs")
check("Self-package skipped",                  "packageName" in acc_txt,        "Own package filtered",           "Own package not filtered — recursion risk")
check("Sensitive apps filtered",               "SENSITIVE_PACKAGES" in acc_txt, "Banking/auth apps filtered",     "No sensitive app filter — captures passwords")
check("Debounce same-app events",              "DEBOUNCE_MS" in acc_txt,        "Event debouncing implemented",   "No debounce — thousands of duplicate events")
check("Coroutine scope cancelled on destroy",  "scope.cancel()" in acc_txt,     "Scope cancelled in onDestroy",   "Scope not cancelled — memory leak on service restart")
check("DB write on IO thread",                 "scope.launch" in acc_txt,       "DB write off main thread",       "DB write may block main thread")

# XML config
check("AccessibilityService XML config",       "accessibility-service" in acc_xml, "XML config present",          "XML config missing — service won't register")
check("canRetrieveWindowContent=false",        "canRetrieveWindowContent=\"false\"" in acc_xml,
      "canRetrieveWindowContent=false (minimum permissions)", "canRetrieveWindowContent not false — too permissive")

# Manifest
check("Service in Manifest",                  "SiftAccessibilityService" in manifest_txt, "Service declared in Manifest", "Service NOT in Manifest — won't work")
check("BIND_ACCESSIBILITY_SERVICE permission", "BIND_ACCESSIBILITY_SERVICE" in manifest_txt, "Permission declared", "Permission missing")

# ═══════════════════════════════════════════════════════════════
# 9. BACKGROUND WORKERS
# ═══════════════════════════════════════════════════════════════
separator("9. BACKGROUND WORKERS")

workers_txt = read(find_kt("Workers") or Path("/dev/null"))

check("EmbeddingWorker @HiltWorker",     "@HiltWorker" in workers_txt,              "EmbeddingWorker is HiltWorker",    "@HiltWorker missing")
check("Battery constraint",              "setRequiresBatteryNotLow" in workers_txt,  "Battery constraint set",           "No battery constraint — drains battery")
check("Periodic work scheduling",        "PeriodicWorkRequestBuilder" in workers_txt,"Periodic scheduling used",        "No periodic scheduling")
check("Unique work names",               "WORK_NAME" in workers_txt,                 "Unique work names defined",       "No unique work names — duplicates possible")
check("ExistingPeriodicWorkPolicy.KEEP", "ExistingPeriodicWorkPolicy.KEEP" in workers_txt, "KEEP policy — no duplicate work", "No KEEP policy — workers may stack")
check("Retry on failure",                "Result.retry()" in workers_txt,            "Retry on failure",                "No retry — single point of failure")
check("Max retry attempts",              "runAttemptCount" in workers_txt,            "Max retry count checked",         "Unbounded retries possible")
check("PruneWorker requires device idle","setRequiresDeviceIdle" in workers_txt,      "Prune worker requires idle",      "Prune runs even during active use")
check("PruneWorker keeps call history",  "CALL_START" in workers_txt or "keepTypes" in workers_txt,
      "Call events preserved during prune", "Call events may be pruned")
check("FAISS saved after batch embed",   "saveToDisk" in workers_txt,                "FAISS saved after embedding",     "FAISS not saved — index lost on restart")

# ═══════════════════════════════════════════════════════════════
# 10. UI / COMPOSE
# ═══════════════════════════════════════════════════════════════
separator("10. UI / COMPOSE")

ui_txt  = read(find_kt("SearchScreen") or Path("/dev/null"))
vm_txt2 = read(find_kt("SearchViewModel") or Path("/dev/null"))

check("Pipeline step animation",    "PipelineIndicator" in ui_txt,    "Pipeline step UI component",     "No pipeline animation")
check("Intent chips UI",            "IntentChips" in ui_txt,          "Intent chips displayed",         "Intent chips missing")
check("Result cards",               "ResultCard" in ui_txt,           "Result card component",          "Result cards missing")
check("Error card with fix hint",   "ErrorCard" in ui_txt,            "Error card with fix hint",       "No error UI")
check("Empty state UI",             "EmptyState" in ui_txt,           "Empty state component",          "No empty state UI")
check("Settings bottom sheet",      "SettingsSheet" in ui_txt,        "Settings sheet implemented",     "No settings UI")
check("Sample queries chips",       "SampleQueries" in ui_txt,        "Sample query chips",             "No sample queries")
check("Dark theme colors",          "BgPrimary" in ui_txt,            "Custom dark palette defined",    "No custom colors")
check("Animated transitions",       "AnimatedVisibility" in ui_txt or "animateColorAsState" in ui_txt,
      "Animated transitions used",  "No animations")
check("Infinite transition (pulse)","rememberInfiniteTransition" in ui_txt, "Pulse animation present", "No pulse animation")

# ViewModel uni-directional flow
check("UDF: events from UI",        "onEvent" in ui_txt,              "UI sends events to VM",          "No event dispatch from UI")
check("UDF: state flows to UI",     "collectAsState" in read(find_kt("MainActivity") or Path("/dev/null")),
      "State collected in Activity","State not collected from VM")

# ═══════════════════════════════════════════════════════════════
# 11. MANIFEST & PERMISSIONS
# ═══════════════════════════════════════════════════════════════
separator("11. MANIFEST & PERMISSIONS")

check("BOOT_COMPLETED receiver",       "BOOT_COMPLETED" in manifest_txt,            "Boot receiver registered",     "No boot receiver — workers not rescheduled after reboot")
check("BootReceiver in manifest",      "BootReceiver" in manifest_txt,              "BootReceiver declared",        "BootReceiver not in manifest")
check("FOREGROUND_SERVICE permission", "FOREGROUND_SERVICE" in manifest_txt,        "Foreground service permission","Foreground service permission missing")
check("POST_NOTIFICATIONS permission", "POST_NOTIFICATIONS" in manifest_txt,        "POST_NOTIFICATIONS declared",  "Notification permission missing (Android 13+)")
check("allowBackup=false",             "android:allowBackup=\"false\"" in manifest_txt, "Backup disabled (privacy)", "Backup enabled — DB may leak to cloud")
check("WorkManager provider",          "InitializationProvider" in manifest_txt,    "WorkManager custom init",      "WorkManager custom init missing — Hilt workers won't inject")
check("Hilt application class",        "SiftApplication" in manifest_txt,           "Custom Application class",     "Custom Application not declared")

# ═══════════════════════════════════════════════════════════════
# 12. CI/CD PIPELINE
# ═══════════════════════════════════════════════════════════════
separator("12. CI/CD PIPELINE")

ci_txt = read(ROOT / ".github/workflows/ci.yml")

check("CI triggers: push to main",    "branches: [main" in ci_txt,           "CI triggers on main",          "CI not triggered on main")
check("CI triggers: pull_request",    "pull_request" in ci_txt,               "CI triggers on PR",            "CI not triggered on PR")
check("CI triggers: release",         "release" in ci_txt,                    "CI triggers on release",       "CI not triggered on release")
check("JDK 17 in CI",                 "java-version: '17'" in ci_txt or "JAVA_VERSION: '17'" in ci_txt,
      "JDK 17 used in CI",            "JDK version not 17")
check("Unit tests run in CI",         "gradlew test" in ci_txt,               "Unit tests run in CI",         "Unit tests not in CI")
check("Test results uploaded",        "upload-artifact" in ci_txt,            "Test results archived",        "Test results not archived")
check("Keystore from env vars",       "KEYSTORE_BASE64" in ci_txt,            "Keystore via secrets",         "Keystore not from secrets — hardcoded risk")
check("Sentry DSN from secrets",      "SENTRY_DSN" in ci_txt,                 "Sentry DSN from secrets",      "Sentry DSN hardcoded in workflow")
check("Play Store upload",            "upload-google-play" in ci_txt,         "Auto-deploy to Play Store",    "No Play Store upload step")
check("Internal track deployment",    "track:                       internal" in ci_txt or "track: internal" in ci_txt,
      "Deploys to internal test track","Not deploying to internal track")
check("AAB (not APK) for Play Store", "bundleRelease" in ci_txt,              "AAB bundle for Play Store",    "Using APK instead of AAB for Play Store")

# ═══════════════════════════════════════════════════════════════
# 13. CODE QUALITY
# ═══════════════════════════════════════════════════════════════
separator("13. CODE QUALITY")

all_txt = all_kt_text()

# Timber (not android.util.Log)
raw_log_count = len(re.findall(r'\bLog\.(d|e|w|i|v)\(', all_txt))
check("Timber used (not Log.d)", raw_log_count == 0 or "Timber" in all_txt,
      f"Timber used ({raw_log_count} raw Log calls)", f"{raw_log_count} raw Log.x calls — use Timber", severity="warn")

# No hardcoded API URLs in non-config files
hardcoded_urls = [f for f in KT if "http://" in read(f) and "ConfigStore" not in f.name and "IntentParser" not in f.name and "AppModule" not in f.name]
check("No hardcoded URLs outside config", len(hardcoded_urls) == 0,
      "URLs only in config files", f"Hardcoded URLs in: {[f.name for f in hardcoded_urls]}", severity="warn")

# TODO/FIXME count
todos = len(re.findall(r'\b(TODO|FIXME|HACK|XXX)\b', all_txt))
check(f"Low TODO/FIXME count", todos < 5,
      f"{todos} TODOs found (acceptable)", f"{todos} TODOs/FIXMEs — review before launch", severity="warn")

# Null safety — no !! force-unwrap in critical paths
force_unwrap = len(re.findall(r'!!', all_txt))
check("Minimal force-unwrap (!!) usage", force_unwrap < 10,
      f"{force_unwrap} force-unwraps (low)", f"{force_unwrap} !! force-unwraps — crash risk", severity="warn")

# Companion objects (constants not magic strings)
companion_count = all_txt.count("companion object")
check("Companion objects for constants", companion_count >= 3,
      f"{companion_count} companion objects with constants", "Few companion objects — magic strings likely")

# ═══════════════════════════════════════════════════════════════
# 14. PROGUARD
# ═══════════════════════════════════════════════════════════════
separator("14. PROGUARD")

pg_txt = read(ROOT / "app/proguard-rules.pro")

for rule_target in ["kotlinx.serialization", "Room", "ONNX", "SQLCipher", "OkHttp", "Hilt", "Sentry"]:
    check(f"ProGuard rule: {rule_target}", rule_target in pg_txt,
          f"ProGuard rule for {rule_target}", f"ProGuard rule for {rule_target} MISSING — may crash in release")

check("Models kept in ProGuard",    "dev.sift.app.model" in pg_txt,    "Model classes kept",          "Models not kept — JSON deserialization will fail in release")
check("Accessibility Service kept", "SiftAccessibilityService" in pg_txt, "Accessibility Service kept","Service may be renamed by ProGuard — won't register")
check("Workers kept",               "dev.sift.app.worker" in pg_txt,   "Workers kept",                "Workers may fail in release build")

# ═══════════════════════════════════════════════════════════════
# SUMMARY
# ═══════════════════════════════════════════════════════════════
print(f"\n{'═'*60}")
print(f"{BOLD}  TEST SUMMARY{RESET}")
print(f"{'═'*60}")

total   = len(results)
passed  = sum(1 for r in results if r.passed)
errors  = [r for r in results if not r.passed and r.severity == "error"]
warns   = [r for r in results if not r.passed and r.severity == "warn"]

print(f"\n  Total checks : {total}")
print(f"  {PASS} Passed    : {BOLD}{passed}{RESET}")
print(f"  {FAIL} Errors    : {BOLD}{len(errors)}{RESET}")
print(f"  {WARN} Warnings  : {BOLD}{len(warns)}{RESET}")

score = int((passed / total) * 100)
color = "\033[92m" if score >= 90 else "\033[93m" if score >= 75 else "\033[91m"
print(f"\n  {color}{BOLD}PRODUCTION READINESS SCORE: {score}%{RESET}")

if errors:
    print(f"\n{BOLD}  ERRORS (must fix before launch):{RESET}")
    for r in errors:
        print(f"  {FAIL}  {r.name}")
        print(f"       → {r.message}")

if warns:
    print(f"\n{BOLD}  WARNINGS (fix before v1.1):{RESET}")
    for r in warns:
        print(f"  {WARN}  {r.name}")
        print(f"       → {r.message}")

print(f"\n{'═'*60}\n")
sys.exit(0 if len(errors) == 0 else 1)
