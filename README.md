# androidsms-apk-restore

Backup publico para reconstruir el fork local de Android SMS Gateway sin depender de este equipo.

No incluye secretos.

No incluye:
- `private_token`
- `jwt_secret`
- usuarios y passwords reales
- `google-services.json`
- una APK compilada con credenciales incrustadas

## Que contiene

- `overlay/`
  Archivos modificados respecto a `capcom6/android-sms-gateway` `v1.57.0`.
- `docs/BUILD_NOTES_PUBLIC.md`
  Pasos de build y restore.
- `docs/SECRETS_TEMPLATE.md`
  Lista de valores privados que hay que reinyectar.

## Como reconstruir

1. Clona el upstream:

```bash
git clone https://github.com/capcom6/android-sms-gateway.git
cd android-sms-gateway
git checkout v1.57.0
```

2. Copia el contenido de `overlay/` encima del checkout del upstream.

3. Añade tu `app/google-services.json`.

4. Reinyecta tus valores privados:

- `private token`
- credenciales del gateway si quieres documentarlas fuera de la app
- cualquier valor local que no deba vivir en un repo publico

5. Compila la variante insegura para HTTP:

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

## Cambios principales del fork

- Permite `http` para `smsgateway.mihuerto.uk`
- Muestra el `FCM token` en ajustes Cloud
- Aclara el flujo `Sign Up` y `Sign In`
- Soporta precarga del `server URL`
- Fija `buildToolsVersion "33.0.2"`

## Nota importante

La APK local que se uso en pruebas reales no se publica aqui porque estaba compilada con un `private token` real incrustado.
