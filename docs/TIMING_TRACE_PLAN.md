# Android Timing Trace Plan

Objetivo: medir con timestamps dónde aparece el retraso entre:

1. recepción del push FCM
2. arranque de sync contra backend
3. descarga de mensajes pendientes
4. inicio real del envío SMS
5. callback de estado `sent` / `delivered`
6. subida del estado al backend

Este repo público no contiene el código runtime completo de la app. El código real está dentro del bundle privado cifrado:

- `restore-bundle-private-2026-04-12.zip`

Con la contraseña correcta, los ficheros a instrumentar son:

- `official-android-sms-gateway-src/app/src/main/java/me/capcom/smsgateway/services/PushService.kt`
- `official-android-sms-gateway-src/app/src/main/java/me/capcom/smsgateway/modules/gateway/GatewayService.kt`
- `official-android-sms-gateway-src/app/src/main/java/me/capcom/smsgateway/modules/messages/MessagesService.kt`
- `official-android-sms-gateway-src/app/src/main/java/me/capcom/smsgateway/modules/receiver/ReceiverService.kt`

## Trazas a añadir

### `PushService.kt`

- log al entrar en `onMessageReceived`
- `event_type`
- timestamp UTC
- tiempo hasta lanzar sync / wake-up de gateway
- `push_token` refrescado en `onNewToken` si aplica

Mensajes de log previstos:

- `push received from FCM`
- `push triggered gateway sync`
- `push token refreshed`

### `GatewayService.kt`

- log al iniciar sync con backend
- log al terminar fetch de mensajes pendientes
- número de mensajes recibidos
- duración total del sync
- si hay modos `AUTO` / `SSE_ONLY`, dejar claro cuál está activo

Mensajes de log previstos:

- `gateway sync started`
- `gateway pending messages fetched`
- `gateway sync finished`

### `MessagesService.kt`

- log cuando empieza `sendPendingMessages`
- log por cada mensaje antes del envío SMS real
- timestamp de creación del intento local
- tiempo hasta callback `sent`
- tiempo hasta callback `delivered`
- tiempo hasta subida de estado al backend

Mensajes de log previstos:

- `pending message processing started`
- `sms send started`
- `sms sent callback received`
- `sms delivered callback received`
- `message state synced to backend`

### `ReceiverService.kt`

- log al recibir SMS entrante
- timestamp de recepción Android
- tiempo hasta persistir / reenviar al backend

Mensajes de log previstos:

- `incoming sms received`
- `incoming sms forwarded to backend`

## Correlación con backend

El backend ya quedó instrumentado en `server-ha-postgres` para estos hitos:

- petición HTTP de envío aceptada
- mensaje persistido
- evento publicado
- push despachado a provider
- SSE despachado

La correlación mínima entre backend y móvil debería apoyarse en:

- `message_id`
- `device_id`
- `event_type`
- timestamps UTC

## Resultado esperado

Con ambas trazas activas se podrá responder:

1. si el retraso está antes del push
2. si el push llega tarde al móvil
3. si el móvil despierta pero tarda en hacer sync
4. si el fetch es rápido pero el envío SMS se atasca
5. si el callback Android llega rápido pero el backend se entera tarde
