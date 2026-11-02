# Companion
Android application for structured life-event tracking and personal pattern discovery, combining individual baselines, statistical insights and evidence-backed narrative summaries

# Dzire Companion

**A personal life-awareness and self-development Android app.**

Dzire Companion brings together daily reflections, personal tracking, historical timelines and evidence-based insights. Instead of relying on fixed wellness rules, its analytics layer looks for patterns in the events a user actually records and compares recent activity with that user's own history.

> Dzire describes patterns in recorded data. It does not diagnose medical or mental-health conditions, establish causation, or replace professional advice.

## Highlights

- **Life tracking:** record events across areas such as psychology, physical activity, mental state, spirituality, food and education.
- **Local-first event history:** structured life events are stored with Room.
- **Pattern discovery:** explore statistical relationships in recorded measurements.
- **Statistical reliability:** evaluate evidence volume, coverage and consistency before surfacing findings.
- **Temporal analysis:** examine event timing and time-linked patterns.
- **Personal baselines:** compare recent observations with an individual's historical measurements.
- **Personal states:** group similar observed days and inspect recurring state transitions.
- **Personal narrative:** assemble analytical findings into a neutral, evidence-backed recent-history summary.
- **Remote quotes and advice:** load editorial content from Firebase Firestore so content can be updated without publishing a new APK.
- **Google Play subscriptions:** includes the Android Billing integration for a premium subscription product.

## Technology

| Area | Technology |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose, Material 3 |
| Navigation | Navigation Compose |
| Local persistence | Room |
| Dependency processing | KSP |
| Backend/content | Firebase Authentication, Cloud Firestore |
| Sign-in support | AndroidX Credentials, Google Identity |
| Subscriptions | Google Play Billing Library |
| Build | Gradle, Android Gradle Plugin |

## Project requirements

- Android Studio with a compatible Android SDK.
- JDK 11 or a JDK supported by the installed Android Gradle Plugin.
- Android SDK Platform 36.
- A Firebase project configured for the Android application ID `com.dzire.dzirecompanion`.
- Google Play Console access to configure and test subscriptions.

The project currently declares `minSdk 24`, `targetSdk 36`, Kotlin `2.0.21`, Android Gradle Plugin `8.13.2`, and Gradle wrapper `8.13`. Check the Android Gradle Plugin and Android Studio compatibility notes if you update these versions.

## Getting started

1. Clone this repository:

   ```bash
   git clone <your-repository-url>
   cd <repository-directory>
   ```

2. Open the project in Android Studio and allow Gradle sync to finish.

3. Configure Firebase:
   - Create or select a Firebase project.
   - Register an Android app with package name `com.dzire.dzirecompanion`.
   - Add the Firebase configuration file as `app/google-services.json`.
   - Enable the Firebase products used by the app, including Cloud Firestore and the required authentication provider(s).

4. Review Firestore security rules before running the app. The Android app should only read published editorial content from the public content collections. Do not allow the client to create or edit editorial content.

5. Run the `app` configuration on an Android emulator or device.

### Build from the command line

On macOS/Linux:

```bash
./gradlew assembleDebug
```

On Windows:

```bat
gradlew.bat assembleDebug
```

Run unit tests:

```bash
./gradlew test
```

The debug APK is normally generated under:

```text
app/build/outputs/apk/debug/
```

## Firebase remote content

The app reads editorial content from two Firestore collections:

- `content_quotes`
- `content_advice`

A document can use the following fields:

| Field | Type | Purpose |
|---|---|---|
| `active` | Boolean | Whether the item is published |
| `activeFrom` | Timestamp or null | Optional publication start |
| `activeUntil` | Timestamp or null | Optional expiry |
| `title` | String | Content heading |
| `text` | String | Quote or advice body |
| `author` | String | Optional quote attribution |
| `tags` | Array of strings | Editorial tags used for filtering |
| `priority` | Number | Editorial ordering/priority |
| `premium` | Boolean | Whether the item is premium content |

Example quote document:

```json
{
  "active": true,
  "activeFrom": null,
  "activeUntil": null,
  "title": "A daily perspective",
  "text": "Add your quote text here.",
  "author": "Author name",
  "tags": ["GENERAL"],
  "priority": 10,
  "premium": false
}
```

Example advice document:

```json
{
  "active": true,
  "activeFrom": null,
  "activeUntil": null,
  "title": "A small next step",
  "text": "Add your advice text here.",
  "author": "",
  "tags": ["REFLECTION"],
  "priority": 20,
  "premium": true
}
```

The app uses Firestore snapshot listeners for observed content, allowing published changes to appear while the app is running. The app filters by active status, optional date range, tags and premium status.

**Content administration:** manage these documents through the Firebase Console or a trusted administrative tool. Keep editorial write permissions off the Android client. Validate and moderate advice before publishing it.

## Google Play subscriptions

The current Android billing integration expects this subscription product ID:

```text
dzire_premium
```

To configure it:

1. Create `dzire_premium` as a subscription product in Google Play Console.
2. Add one or more auto-renewing base plans (for example, monthly and annual).
3. Configure pricing and any optional introductory offers.
4. Publish an eligible build to an internal testing track.
5. Add licensed testers and test purchase, cancellation and restore scenarios.

The app's billing layer queries product details, launches the Play purchase flow, checks existing purchases and acknowledges purchased subscriptions.

### Important: production entitlement verification

The current implementation is the **Android client-side billing layer**, not a complete production subscription backend. Do not rely solely on a local `isPremium` value to protect paid features.

Before commercial release, add a secure backend that:

1. Receives the purchase token from the app over an authenticated, secure connection.
2. Verifies the token with the Google Play Developer API.
3. Stores the verified entitlement against the authenticated user.
4. Processes Real-time Developer Notifications (RTDN) for renewals, cancellations, grace periods, account holds and expiry.
5. Exposes the verified entitlement to the app.
6. Supports restore and account/device changes safely.

Never put service-account credentials or private backend secrets in the Android app or repository.

## Analytics architecture

The analytics work is organized into progressive phases:

| Phase | Capability |
|---|---|
| 1 | Structured life-event data |
| 2 | Data-driven pattern discovery |
| 3 | Statistical reliability checks |
| 4 | Timeline and temporal analysis |
| 5 | Personal baselines and anomaly detection |
| 6 | Personal states and state transitions |
| 7 | Evidence-backed personal narrative |

The intended data flow is:

```text
Recorded life events
        |
        v
Structured local history
        |
        v
Pattern and temporal analysis
        |
        v
Personal baseline and state model
        |
        v
Evidence-backed findings
        |
        v
Personal narrative
```

The narrative layer should summarize analytical outputs, not invent causes, diagnoses or unsupported recommendations. Correlation and temporal proximity are associations, not proof of causation.

## Repository structure

```text
.
├── app/
│   ├── src/main/
│   │   ├── java/com/dzire/dzirecompanion/
│   │   │   ├── billing/       # Google Play Billing integration
│   │   │   ├── content/       # Firestore-driven quotes and advice
│   │   │   ├── data/          # Room entities and database
│   │   │   ├── insights/      # Analytics and narrative engines
│   │   │   ├── ui/            # Compose dashboard and theme
│   │   │   └── *Activity.kt   # App screens
│   │   └── res/               # Android resources
│   └── build.gradle.kts
├── gradle/
├── FIREBASE_CONTENT_AND_SUBSCRIPTIONS.md
├── PHASE_2.md
├── PHASE_4.md
├── PHASE_5.md
├── PHASE_6.md
└── PHASE_7.md
```

## Privacy and security checklist

Before publishing:

- Review what personal information is collected and why.
- Provide a clear privacy policy and in-app privacy disclosures.
- Use restrictive Firestore Security Rules and Firebase App Check where appropriate.
- Do not store private keys, service-account files or backend secrets in the app.
- Verify subscription entitlements on a trusted backend.
- Provide account/data deletion and data export processes where applicable.
- Test backup, restore and device migration behavior.
- Ensure insights are presented as informational self-reflection, not medical advice.

## Current implementation notes

- Remote quote/advice content depends on a correctly configured Firebase project and Firestore collections.
- Subscription product details will only be returned when the product and eligible base plan are configured in Play Console and the app is tested through an appropriate Play distribution track.
- Server-side purchase verification and subscription lifecycle processing remain required for production-grade premium access.
- Run a full Android build and test on a real device/emulator before release. A source archive or independent Kotlin compilation is not a substitute for a complete Gradle build and Play Billing test.

## Contributing

1. Create a feature branch.
2. Keep analytics generic; avoid adding hardcoded assumptions about what a category or measurement means.
3. Add or update tests for analytical behavior.
4. Keep user-facing findings traceable to the underlying recorded evidence.
5. Do not commit local machine configuration, signing keys, service-account credentials or other secrets.

## License

No license has been specified yet. Add a `LICENSE` file before accepting external contributions or granting reuse rights.
