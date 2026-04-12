Build notes - public restore edition

Upstream base
- Repo:
  https://github.com/capcom6/android-sms-gateway
- Tag used locally:
  v1.57.0

Android app details
- applicationId:
  me.capcom.smsgateway
- Variant to build:
  debugInsecure
- Reason:
  allows cleartext HTTP for a self-hosted gateway

Firebase
- `google-services.json` is required
- it is not stored in this public backup
- if `applicationId` changes, regenerate the Firebase config

Server
- Example local server URL used in the fork:
  http://smsgateway.mihuerto.uk/api/mobile/v1
- The `private token` is intentionally not included here

Build command
```bash
HOME=/ruta/de/trabajo \
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 \
ANDROID_SDK_ROOT=/opt/android-sdk \
ANDROID_HOME=/opt/android-sdk \
ANDROID_USER_HOME=/ruta/de/trabajo/.android-home \
XDG_CACHE_HOME=/ruta/de/trabajo/.cache-home \
GRADLE_USER_HOME=/ruta/de/trabajo/.gradle-user \
./gradlew assembleDebugInsecure
```

Manual setup after install
1. Open the app
2. Enable Cloud Server
3. Go to Home > Start
4. Use Sign Up or Sign In
5. Set the private token manually if it is not patched into the source
6. Check Cloud settings for the FCM token

Operational note
- In the tested environment, `SSE_ONLY` helped the app pull the queue reliably when FCM was flaky
