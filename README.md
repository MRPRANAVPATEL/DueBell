# DueBell

**Thoughtfully built by Pranav Patel to make everyday tasks easier to remember.**

DueBell is an offline Android app for reminders that stay pending until you mark them done and wake-up alarms that ask you to solve simple math. You can add a 10-step challenge after the math or set a ringtone failsafe for an important reminder.

## Download

**[Download DueBell.apk](https://github.com/MRPRANAVPATEL/DueBell/releases/download/v1.0.0-preview/DueBell.apk)** · [Version v1.0.0-preview release](https://github.com/MRPRANAVPATEL/DueBell/releases/tag/v1.0.0-preview)

Open the APK on a phone running Android 8.0 or later and follow Android's installation prompts.

> Built for personal use and Android development practice. Install it at your own discretion.

## Set up alerts on Samsung

1. Open **DueBell → Alert setup**. Allow notifications and **Alarms & reminders** so alarms can ring at their scheduled time.
2. Allow **Full-screen alerts** if you want ringing alarms to open over the lock screen. Android can show a banner while the phone is unlocked.
3. In Samsung's **Do Not Disturb** settings, add DueBell under **App notifications** and allow **Alarms** under **Alarms and sounds**.
4. In DueBell, use **Test reminder and failsafe** or **Test math alarm**. Lock the phone and leave Do Not Disturb on to check both sound and visibility.

If alerts are delayed, check **Battery → Background usage limits** and move DueBell out of **Deep sleeping apps**. Menu names depend on the Android and One UI version.

## Reminders

- Set a title, date, and time. Use **Done** or **Remind again** from the notification or in the app. If you swipe away a notification, the task stays in **Pending**.
- Turn on **Remind on unlock** for a follow-up after you tap **Remind again**. If Android does not deliver the next unlock to DueBell, a scheduled notification is the backup after 15 minutes.
- Turn on **Call-style failsafe** to ring locally if the task is still pending after a selected delay. Use the phone's call ringtone or choose a tone in **Alert setup**. **Snooze 1 hour** schedules another ring. **Done** clears every follow-up.

## Wake-up alarms

Choose a time and repeat **Once**, **Every day**, or **Weekdays**. Solve the addition problem to dismiss the alarm. With **Also walk 10 steps** enabled, carry your phone and take 10 steps after answering. Walking mode uses the phone's step detector and needs **Physical activity** permission. If step detection is unavailable at ring time, the alarm presents the math challenge.

DueBell stores alarms and reminders on the phone. It restores schedules when Android delivers the boot-completed event after the phone's first unlock.

## Build and release

DueBell uses Kotlin, Jetpack Compose, and Material 3. It targets Android API 34 and uses Java 17, Gradle 8.7, and Android SDK 34 with Build Tools 34.0.0. Set `JAVA_HOME` and `ANDROID_HOME` and run from the repository root.

```sh
gradle testDebugUnitTest lintDebug assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

[Build DueBell APK](https://github.com/MRPRANAVPATEL/DueBell/actions/workflows/build.yml) tests and builds on pushes to `main` that include changes outside this README. Each successful run uploads the **DueBell-apk** artifact. The first successful build for a version also publishes a preview release with `DueBell.apk`.

### Sign future releases with your own key

The preview APK uses a test signing key. Android requires a matching signing key for in-place updates, so use the same private release key for the releases you plan to distribute. Uninstalling a previous copy removes its local reminders and alarms.

Generate a key with Java 17 or use an existing DueBell signing key. Keep the keystore and the encoded output private.

```sh
keytool -genkeypair -keystore duebell-release.jks -storetype JKS \
  -alias duebell -keyalg RSA -keysize 3072 -validity 10000
base64 < duebell-release.jks | tr -d '\r\n'
```

Add the following repository secrets under **Settings → Secrets and variables → Actions**.

| Secret | Value |
| --- | --- |
| `DUEBELL_KEYSTORE` | Single-line Base64 encoding of the keystore |
| `DUEBELL_STORE_PASSWORD` | Keystore password |
| `DUEBELL_KEY_ALIAS` | Signing key alias |
| `DUEBELL_KEY_PASSWORD` | Signing key password |

Set `duebellVersion` and increase `duebellVersionCode` in `gradle.properties`. Push a matching tag such as `v1.0.0`. **Publish signed DueBell APK** builds, verifies, and attaches the signed APK to the tagged release.

---

*Thoughtfully built by Pranav Patel for the everyday things you want to remember.*
