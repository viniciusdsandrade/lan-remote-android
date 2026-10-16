# Plano de Arquitetura e Implementação: Android (Jetpack Compose + Ktor Client)

> **Projeto:** `lan-remote-android`  
> **Objetivo:** Aplicativo Android nativo para controle remoto direto pela rede local (LAN) da TV LG NetCast (47LN5700), sem servidores intermediários, sem nuvem, com resposta instantânea e feedback háptico realista.

---

## 1. Stack Tecnológica Selecionada

| Camada | Tecnologia | Justificativa |
|---|---|---|
| **Linguagem** | Kotlin 2.0+ | Compilador K2 moderno, expressividade, coroutines nativas. |
| **Interface (UI)** | Jetpack Compose + Material 3 | Layout declarativo simétrico ao SwiftUI do iOS, reativo, suporte nativo a temas escuros OLED. |
| **Cliente HTTP** | Ktor Client 3.x (Engine CIO/Android) | Assíncrono nativo em Coroutines, leve, suporte a customização granular de headers e timeouts curtos. |
| **Assincronia & Estado** | Kotlin Coroutines + `StateFlow` + `ViewModel` | Fila serial de comandos com *debounce* (300ms) sem travar a thread principal. |
| **Descoberta de Rede** | `DatagramSocket` UDP + `WifiManager.MulticastLock` | Descoberta SSDP M-SEARCH direta na rede local (`239.255.255.250:1900`). |
| **Persistência Local** | Jetpack DataStore Preferences | Armazenamento reativo de IP, porta, chave de pareamento e identificador de sessão. |
| **Feedback Háptico** | Android `Vibrator` / `VibrationEffect` | Vibração com latência < 10ms a cada toque de botão. |
| **Integrações de Sistema** | `TileService` (Quick Settings) & Jetpack Glance | Atalho no painel superior do Android e widget de controle na tela inicial. |

---

## 2. Configurações Críticas de Rede no Android

### 2.1. Liberação de HTTP sem TLS (Cleartext Traffic)
A TV opera exclusivamente em HTTP puro na porta `8080`. O Android 9+ (API 28+) bloqueia texto claro por padrão.

Arquivo: `res/xml/network_security_config.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <!-- Permite HTTP apenas em sub-redes privadas locais comuns -->
    <domain-config cleartextTrafficPermitted="true">
        <domain includeSubdomains="true">192.168.1.100</domain>
        <domain includeSubdomains="true">192.168.0.0</domain>
        <domain includeSubdomains="true">192.168.1.0</domain>
        <domain includeSubdomains="true">10.0.0.0</domain>
    </domain-config>
    <base-config cleartextTrafficPermitted="false" />
</network-security-config>
```

### 2.2. Permissões no `AndroidManifest.xml`
```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
    <uses-permission android:name="android.permission.CHANGE_WIFI_MULTICAST_STATE" />
    <uses-permission android:name="android.permission.VIBRATE" />

    <application
        android:name=".LanRemoteApp"
        android:networkSecurityConfig="@xml/network_security_config"
        android:usesCleartextTraffic="true"
        android:theme="@style/Theme.LanRemote"
        android:icon="@mipmap/ic_launcher"
        android:label="Controle LAN">
        ...
    </application>
</manifest>
```

### 2.3. Regra Fundamental do User-Agent (NetCast Firmware 05.02.11)
- O Ktor **NUNCA DEVE** enviar o User-Agent padrão ou contendo `"UDAP/2.0"` ao chamar endpoints ROAP (`/roap/api/command`).
- Se contiver, a TV retorna `HTTP 400 Bad Request`.
- O header configurado no Ktor será:
  ```
  User-Agent: lg_roap_control/1.0.0 (android)
  Content-Type: application/atom+xml
  Connection: Close
  ```

---

## 3. Estrutura Proposta de Diretórios e Pacotes

```
app/src/main/
├── AndroidManifest.xml
├── res/
│   ├── xml/network_security_config.xml
│   ├── values/colors.xml, strings.xml, styles.xml
│   └── mipmap/ic_launcher...
└── java/com/lanremote/android/
    ├── LanRemoteApp.kt
    │
    ├── data/
    │   ├── model/
    │   │   ├── TVKey.kt            # Enum com os 40+ key codes da TV (Power=1, Vol+=24, etc.)
    │   │   ├── TVConfig.kt         # Data class (host, port, pairingKey, session, model)
    │   │   └── TVStatus.kt         # Data class (volumeLevel, isMuted, inputSource)
    │   ├── network/
    │   │   ├── KtorTVClient.kt     # Motor HTTP Ktor com XML payloads, timeouts e retry
    │   │   └── SsdpScanner.kt      # Descoberta UDP M-SEARCH com MulticastLock
    │   └── repository/
    │       └── SettingsRepo.kt     # Jetpack DataStore para persistir configurações
    │
    ├── ui/
    │   ├── theme/
    │   │   ├── Color.kt            # Paleta Dark Titanium / Obsidian OLED (#121214)
    │   │   ├── Theme.kt            # MaterialTheme configurado para Dark Mode
    │   │   └── Type.kt
    │   ├── components/
    │   │   ├── CircularDPad.kt     # Direcional circular tátil (▲, ▼, ◀, ▶, OK)
    │   │   ├── RockerSwitch.kt     # Gangorras de Volume (+ VOL -) e Canal (+ CH -)
    │   │   ├── RemoteButton.kt     # Botões com feedback visual de pressão
    │   │   └── StatusHeader.kt     # LED verde online, status de volume e entrada HDMI
    │   ├── remote/
    │   │   ├── RemoteScreen.kt     # Tela principal montando todo o controle
    │   │   └── RemoteViewModel.kt  # Fila assíncrona, debounce de 0.3s e StateFlow
    │   └── settings/
    │       └── SettingsDialog.kt   # Diálogo para alterar IP e solicitar código na TV
    │
    ├── system/
    │   ├── HapticFeedbackHelper.kt # Disparo de vibrações precisas (EFFECT_CLICK)
    │   └── VolumeKeyReceiver.kt    # Interceptação de teclas físicas do celular
    │
    └── tile/
        └── QuickControlTile.kt     # Quick Settings Tile no menu superior do Android
```

---

## 4. Arquitetura do Motor de Rede (Ktor + Debounce Queue)

### 4.1. Configuração do Ktor Client
```kotlin
val ktorClient = HttpClient(CIO) {
    install(HttpTimeout) {
        requestTimeoutMillis = 3500
        connectTimeoutMillis = 2000
        socketTimeoutMillis = 3500
    }
    defaultRequest {
        header("User-Agent", "lg_roap_control/1.0.0 (android)")
        header("Content-Type", "application/atom+xml")
        header("Connection", "Close")
    }
}
```

### 4.2. Fila Serial com Debounce e Proteção contra Perda de Teclas
O hardware da TV LG ignora repetições de teclas em menos de 0,3s (ex.: apertar Volume + três vezes em rajada). O `RemoteViewModel` gerencia isso elegantemente via Coroutines:

```kotlin
class RemoteViewModel(
    private val tvClient: KtorTVClient,
    private val haptic: HapticFeedbackHelper
) : ViewModel() {

    private val _uiState = MutableStateFlow(RemoteUiState())
    val uiState: StateFlow<RemoteUiState> = _uiState.asStateFlow()

    private val mutex = Mutex()
    private var lastSendTime = 0L
    private val minGapMillis = 300L

    fun sendKey(key: TVKey) {
        haptic.performClick()
        viewModelScope.launch {
            mutex.withLock {
                val now = System.currentTimeMillis()
                val elapsed = now - lastSendTime
                if (elapsed < minGapMillis) {
                    delay(minGapMillis - elapsed)
                }
                lastSendTime = System.currentTimeMillis()

                try {
                    tvClient.sendKey(key.code)
                    _uiState.update { it.copy(lastMessage = "${key.label} enviado") }
                } catch (e: Exception) {
                    _uiState.update { it.copy(lastMessage = "Erro: ${e.message}") }
                }
            }
        }
    }
}
```

---

## 5. Componentes de Interface Jetpack Compose

### 5.1. Paleta de Cores OLED / Dark Titanium
- **Fundo Principal:** `#121214` (preto profundo que economiza bateria em telas OLED).
- **Cartões e Seções:** `#1E1E24` com contorno sutil `#2E2E36`.
- **Botões Regulares:** `#2A2A32` com texto `#F0F0F5`.
- **Botão Power:** `#DC2626` com sombra avermelhada sutil.
- **Botão OK / Home:** `#1B4F8A` (azul escuro elegante).
- **Indicador LED Conectado:** `#22C55E` com glow.

### 5.2. Interceptação dos Botões Físicos de Volume
Na `MainActivity`:
```kotlin
override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
    return when (keyCode) {
        KeyEvent.KEYCODE_VOLUME_UP -> {
            viewModel.sendKey(TVKey.VOLUME_UP)
            true
        }
        KeyEvent.KEYCODE_VOLUME_DOWN -> {
            viewModel.sendKey(TVKey.VOLUME_DOWN)
            true
        }
        else -> super.onKeyDown(keyCode, event)
    }
}
```
*Resultado:* Enquanto o usuário estiver com o aplicativo aberto, os botões laterais de volume do próprio celular aumentam e diminuem o volume da TV diretamente.

---

## 6. Recursos Exclusivos do Android

1. **Quick Settings Tile (`TileService`)**:
   - Adiciona um botão personalizável na barra de notificações suspensa do Android.
   - Toque rápido: Alterna **Mudo**.
   - Toque longo: Abre o app completo.
2. **Descoberta SSDP com `MulticastLock`**:
   - Aciona a varredura M-SEARCH na rede local sem ser bloqueado pela economia de energia do Android.
   - Fallback instantâneo: Varredura de porta 8080 na sub-rede `/24` caso o roteador descarte multicast.

---

## 7. Roteiro Passo a Passo de Implementação

Quando for iniciar a codificação do projeto `lan-remote-android`:

1. **Etapa 1: Inicialização do Projeto**
   - Configurar `build.gradle.kts` com Compose BOM, Ktor 3.x, Coroutines, DataStore e Material 3.
   - Adicionar permissões e `network_security_config.xml`.
2. **Etapa 2: Motor de Protocolo (`KtorTVClient.kt`)**
   - Implementar requisições XML de `AuthKeyReq`, `AuthReq`, `HandleKeyInput` e `data?target=volume_info`.
   - Implementar renovação automática de sessão em caso de HTTP 401.
3. **Etapa 3: Interface Compose**
   - Construir os componentes: `StatusHeader`, `CircularDPad`, `RockerSwitch`, teclado numérico retrátil e botões de mídia/cores.
4. **Etapa 4: Integrações do Sistema**
   - Haptic Feedback nos toques.
   - `onKeyDown` para botões físicos de volume.
   - `SettingsDialog` para pareamento inicial e troca de IP.
5. **Etapa 5: Validação e Testes em Dispositivo Real**
   - Conectar o smartphone na mesma rede Wi-Fi da TV LG e validar todos os comandos.
