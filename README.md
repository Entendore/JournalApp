# My Journal

Encrypted, multilingual journal (Kotlin + Room/SQLCipher).
Open in Android Studio (it generates the Gradle wrapper), sync, run.

    ./gradlew testDebugUnitTest            # JVM + Robolectric tests
    ./gradlew connectedDebugAndroidTest    # instrumented tests (device/emulator)

First build exports the Room schema to app/schemas/4.json.
