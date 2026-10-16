# LAN Remote Android (LG NetCast TV)

Aplicativo Android nativo para controle remoto direto pela rede local (LAN) da TV LG NetCast (ex.: 42LN5700), sem servidores intermediários, sem nuvem, com resposta instantânea e feedback háptico.

---

## 🚀 Tecnologias

- **Linguagem:** Kotlin 2.0+ (K2 compiler, Coroutines & Flow)
- **Interface UI:** Jetpack Compose + Material 3 (paleta Obsidian/Titanium OLED `#121214`)
- **Cliente HTTP:** Ktor Client 3.x (Engine CIO)
- **Descoberta na LAN:** SSDP M-SEARCH (UDP Multicast `239.255.255.250:1900`) com `MulticastLock` e fallback de varredura de sub-rede
- **Persistência:** Jetpack DataStore Preferences
- **Feedback Tátil:** Android `Vibrator` / `VibrationEffect` (< 10ms)
- **Integração com Sistema:**
  - `TileService` (Quick Settings Tile "TV Mudo" na barra de status superior)
  - Interceptação de teclas físicas laterais de volume do smartphone (`onKeyDown`)

---

## 📡 Protocolo ROAP & Regras Críticas de Firmware

A TV LG NetCast (firmware 05.02.11) roda o servidor HTTP ROAP na porta `8080`.
- **Regra Fundamental de User-Agent:** Requisições para `/roap/api/command` que contenham `"UDAP/2.0"` no `User-Agent` são rejeitadas com `HTTP 400 Bad Request`. O cliente Ktor utiliza exclusivamente `lg_roap_control/1.0.0 (android)` com header `Connection: close`.
- **Fila Serial com Debounce (300ms):** Protege contra perda de pacotes quando botões são pressionados em rajada.
- **Auto Reautenticação:** Quando a TV reinicia e a sessão expira (retornando `HTTP 401`), o cliente reautentica silenciosamente usando a chave salva.

---

## 🛠️ Como Compilar e Testar

### Pré-requisitos
- JDK 17 ou 21 (ex.: Homebrew OpenJDK 21)
- Android SDK Platform 35 e Build-Tools

### Executar Testes Unitários
```bash
./gradlew test
```

### Gerar APK Debug
```bash
./gradlew assembleDebug
```
O APK final será gerado em `app/build/outputs/apk/debug/app-debug.apk`.
