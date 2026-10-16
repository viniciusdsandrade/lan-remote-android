# Plano de atualização: ligar a TV pela TV Box, controle didático e ROAP corrigido

> **Para o agente que vai implementar.** Este arquivo é a especificação completa do trabalho. Leia até o
> fim antes de mexer no código: as seções 1 a 5 explicam o porquê de cada decisão; as seções 6 a 8 dizem
> o que fazer, em que ordem, e como provar que funciona.
>
> - **Repositório:** `lan-remote-android` (público). Clone local: `~/lan-remote-android`.
> - **Branch de trabalho:** `feat/ligar-tv-pela-tvbox`. Ela já contém este plano, e o PR em rascunho dela é o PR da implementação.
> - **Escrito em:** 2026-10-16, logo depois da mesma atualização nos apps de Mac e iOS. Nada daqui foi implementado ainda.
> - **Referência de paridade:** iOS `lan-remote-ios`, commits `a03a8f9` (TV Box + botões didáticos) e
>   `f486dcc` (ícone). Mac: `~/lan-remote-mac/mac_app` (só local, sem git).
> - **Idioma:** código e identificadores como já estão no projeto; textos de interface, commits e PR em português.

---

## 1. Contexto

O usuário controla pela rede uma TV **LG 42LN5700** (NetCast 4.0, 2013, firmware 05.02.11), IP `192.0.2.100`,
protocolo ROAP na porta 8080. Em standby essa TV **desliga a placa de rede**: nenhum app consegue ligá-la
diretamente (Wake-on-LAN na TV não tem efeito). O caminho que funciona foi validado de ponta a ponta no app de
Mac (TV em standby e TV Box em repouso profundo → TV ligada e respondendo em ~42 s) e portado para o iOS:

1. **Wake-on-LAN** para a **TV Box da sala** (Android TV / Google TV) ligada no HDMI da TV: IP `192.0.2.101`, nome
   Bonjour `TV Box da sala`. Ela entra em repouso profundo poucos segundos depois que a TV vai para standby, mas a
   rede dela acorda com o pacote mágico; a porta 6466 responde em ~1 s.
2. Nessa janela o app aperta **POWER (keycode 26)** pelo **protocolo do controle remoto do Android TV v2** (o mesmo do
   app Google TV: TLS com certificado do cliente e mensagens protobuf).
3. Ao acordar, a TV Box manda **HDMI-CEC One Touch Play**: a TV liga já na entrada dela. A imagem volta em
   segundos; a rede da TV, em **~40–55 s**.

Pré-requisitos no ambiente do usuário (já atendidos): **SIMPLINK ligado** na TV (sem ele a TV ignora o CEC) e
**HDMI-CEC ativo** na TV Box. Nesta TV Box, o MAC do Wi-Fi é o **MAC Bluetooth − 1** (o Bluetooth aparece no
registro TXT `bt=` do Bonjour). `KEYCODE_TV_POWER` (177) e `KEYCODE_WAKEUP` (224) **não funcionam** nesta TV Box:
não perca tempo com eles.

Além de ligar a TV, o usuário pediu no Mac e no iOS, e quer igual no Android:

- botões **didáticos**: saber o que cada botão faz sem decorar siglas, seguindo princípios de UI/UX;
- **nenhuma mensagem de log** na tela ("Power enviado", "Falha: …");
- ícone do app com a mesma cara do controle do Mac e do iOS.

## 2. Estado atual (linha de base: `main` @ `d3b4455`)

App Kotlin + Jetpack Compose + Ktor CIO + DataStore; `minSdk 26`, `compileSdk`/`targetSdk 35`, AGP 8.7.3,
Kotlin 2.0.21, Gradle 8.11.1. Build e testes rodados em 2026-10-16:

```bash
cd ~/lan-remote-android
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
./gradlew --no-daemon test assembleDebug     # BUILD SUCCESSFUL: 11 testes, 0 falhas
```

Os 11 testes: `TVKeyTest` (2), `KtorTVClientTest` (5), `RemoteViewModelTest` (4). Já `./gradlew lintDebug` **falha**:
5 erros, todos no `QuickControlTile` (P7 e P11 abaixo), e 64 avisos (36 de versões de dependência, 18 de recursos sem
uso, 2 `MonochromeLauncherIcon`, entre outros). Ferramentas nesta máquina:
SDK em `~/Library/Android/sdk` (plataformas 35 e 37; build-tools 34, 36 e 37), `adb` em
`~/Library/Android/sdk/platform-tools/adb` (fora do `PATH`), AVD `Pixel_9`, Android Studio em
`~/Applications/Android Studio.app`. Não há CI (`.github/workflows` não existe).

### 2.1 Problemas encontrados no código (todos entram no escopo)

| # | Onde | Problema | Correção |
|---|---|---|---|
| P1 | `KtorTVClient.kt:116-131` e o reenvio em `:141-147` | O corpo do comando usa `<name>HandleKeyInput</name>` e a sessão vai num header `Session`. O formato validado nesta TV (`lan-remote-mac/PROTOCOLO.md` §4, apps de Mac/iOS, ferramenta Python) é `<command><session>ID</session><type>HandleKeyInput</type><value>N</value></command>`. Não há registro de teste na TV real: os testes usam `MockEngine` e afirmam o formato errado. | Trocar o XML, tirar o header `Session`, atualizar `KtorTVClientTest`. |
| P2 | `KtorTVClient.kt:168` e `:207` | Consultas sem `&session=`. Sem sessão válida a TV responde 401 (PROTOCOLO.md §5), então o volume nunca aparece; `checkOnline` só usa o 401 como "online". | `?target=volume_info&session=$session`. Num 401, refazer o `AuthReq` com a chave salva e repetir uma vez (como `query()` na ferramenta Python). |
| P3 | `KtorTVClient.kt:100-101` | `authenticate` devolve `"authenticated"` quando a resposta não traz `<session>`, e essa string vira a sessão salva. | Resposta sem `<session>` = falha. |
| P4 | `RemoteViewModel.kt:96` e `:109`, `RemoteScreen.kt:220-238` | `lastMessage` ("Power enviado", "Falha: …") mostrada como toast: são exatamente as "mensagens de log" que o usuário mandou tirar do Mac. | Remover `lastMessage` do estado e da tela; o feedback passa a ser háptico + estado da TV. |
| P5 | `StatusHeader.kt:133-141` | Power manda a tecla direto, sem confirmação, e não existe como ligar. | Seção 4.5: Desligar com confirmação, Ligar pela TV Box. |
| P6 | `RemoteButton.kt:62-76` | Com `icon` e `text`, só o ícone aparece (o texto vira `contentDescription`): Entrada, Mudo, Voltar, Home, Menu, Sair e as setas ficam sem rótulo; os botões coloridos são "●"; siglas como "Q.View", "INFO", "GUIA" e "TV". | Seção 4.5: ícone + rótulo em português, segurar para aprender. |
| P7 | `QuickControlTile.kt:42-46` | `startActivityAndCollapse(Intent)` lança `UnsupportedOperationException` em apps com `targetSdk` 34 ou mais (este tem 35): o bloco quebra justamente quando a TV está offline. O lint acusa (`StartActivityAndCollapseDeprecated`). Ele também usa o `sendCommand` de P1. | `startActivityAndCollapse(PendingIntent)` no API 34+ (o overload com `Intent` só abaixo disso). |
| P11 | `QuickControlTile.kt:38`, `:40`, `:58`, `:62` | `Tile.subtitle` só existe a partir do API 29, mas o `minSdk` é 26: no Android 8 e 9 o bloco quebra (`NoSuchMethodError`). São 4 dos 5 erros do lint (`NewApi`). | Só definir `subtitle` com `Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q`. |
| P8 | `RemoteViewModel.kt:53-61` | O polling a cada 5 s continua com o app em segundo plano (o `ViewModel` sobrevive à tela). | Polling preso ao ciclo de vida (`repeatOnLifecycle(Lifecycle.State.STARTED)`, ou iniciar e parar em `onStart`/`onStop`). |
| P9 | `res/drawable/ic_launcher_foreground.xml` | Desenho simples, diferente do controle do Mac/iOS, e sem camada `monochrome` (ícone temático do Android 13+). | Seção 4.8. |
| P10 | `.gitignore` | Não ignora `.kotlin/` (pasta que o Kotlin 2.x cria na raiz). | Acrescentar `.kotlin/`. |

## 3. Objetivo

Deixar o app Android **no mesmo ponto do iOS** (commits `a03a8f9` e `f486dcc`):

1. **ROAP correto**: teclas e consultas funcionam na TV real, e o estado da TV fica visível (Ligada, Desligada,
   Ligando…, Verificando…).
2. **Ligar a TV pelo app** via TV Box (WoL + Android TV Remote v2 + CEC), com **pareamento único** da TV Box em Ajustes.
3. **Controle didático**: ícone + rótulo em português, botões agrupados por função, **segurar** um botão explica o que
   ele faz sem enviar nada, **Guia dos botões** completo, TalkBack com os mesmos textos.
4. **Sem mensagens de log** na tela e **confirmação** antes de desligar.
5. **Ícone novo** (o mesmo controle do Mac/iOS), adaptativo, com versão monocromática.
6. **CI** no GitHub Actions (testes de unidade + APK debug).

Fora do escopo: widget Glance, publicação na Play Store, variante UDAP, mudar `minSdk`/`targetSdk`, mexer nos apps
de Mac e iOS.

## 4. Desenho da solução

### 4.1 Mapa de arquivos

Novos, no pacote `com.lanremote.android`:

```
data/model/TVPowerState.kt           UNKNOWN, ON, OFF, TURNING_ON
data/model/AndroidTvBox.kt           name, host, paired, mac?, bluetoothMac?, wakeMacs
data/androidtv/Proto.kt              ProtoWriter, ProtoMessage, enquadramento com varint
data/androidtv/PairingMath.kt        parse(código), secret(cliente, servidor, nonce), unsignedBytes(BigInteger)
data/androidtv/MacAddress.kt         normalize, bytes, offset(±n)
data/androidtv/WakeOnLan.kt          magicPacket(mac) + envio UDP
data/androidtv/AndroidTvError.kt     sealed class com as mensagens em português (seção 4.3)
data/androidtv/AndroidTvIdentity.kt  chave RSA no AndroidKeyStore + certificado autoassinado
data/androidtv/TlsChannel.kt         SSLSocket com mensagens prefixadas pelo tamanho (implementa FramedChannel)
data/androidtv/AndroidTvPairing.kt   porta 6467: start() faz o código aparecer na TV; finish(código)
data/androidtv/RemoteSession.kt      handshake da porta 6466 sobre um FramedChannel (testável sem rede)
data/androidtv/AndroidTvRemote.kt    press(teclas), switchTvOn(host, wakeMacs), waitUntilReachable
data/androidtv/TvBoxDiscovery.kt     NsdManager, tipo "_androidtvremote2._tcp", TXT "bt"
ui/help/ControlHelp.kt               catálogo único de textos dos controles (seção 4.5)
ui/help/HelpPanel.kt                 painel "segure para aprender"
ui/help/ButtonGuideSheet.kt          Guia dos botões (ModalBottomSheet)
ui/components/PowerBanner.kt         cartão "A TV está desligada" / "Ligando a TV…"
ui/components/RemoteSection.kt       título de seção + conteúdo
scripts/make_app_icon.swift          gerador do ícone (porte do iOS com saída para Android)
.github/workflows/android-ci.yml
```

Alterados: `KtorTVClient.kt`, `TVKey.kt`, `TVStatus.kt`, `SettingsRepository.kt`, `RemoteUiState.kt`,
`RemoteViewModel.kt`, `RemoteScreen.kt`, `StatusHeader.kt`, `RemoteButton.kt`, `RockerSwitch.kt`, `CircularDPad.kt`,
`NumericKeypad.kt`, `ColorButtonsRow.kt`, `MediaControlsRow.kt`, `SettingsDialog.kt`, `MainActivity.kt`,
`LanRemoteApp.kt`, `QuickControlTile.kt`, `res/mipmap-*`, `app/build.gradle.kts` (versão 1.1.0, code 2), os três
arquivos de teste existentes, `README.md` e `.gitignore`.

De onde portar (leia a fonte antes de escrever o equivalente). Links:
`lan-remote-ios/blob/f486dcc/<caminho>`; o clone local do iOS fica em
`~/lan-remote-ios`.

| Android | iOS (`lan-remote-ios` @ `f486dcc`) | Mac (`lan-remote-mac/mac_app`) |
|---|---|---|
| Proto, PairingMath, MacAddress, WakeOnLan, AndroidTvError | `LanRemote/Services/AndroidTVProtocol.swift` | `AndroidTVRemote.swift` |
| TlsChannel, AndroidTvPairing, RemoteSession, AndroidTvRemote | `LanRemote/Services/AndroidTVRemote.swift` | `AndroidTVRemote.swift` |
| AndroidTvIdentity | `LanRemote/Services/AndroidTVIdentity.swift` (no Android o Keystore já gera o certificado) | — |
| AndroidTvBox, TvBoxDiscovery, ligar e parear no ViewModel | `LanRemote/Services/TVBoxLink.swift` | `TVBoxLink.swift` |
| ROAP, estado de energia, regra das duas falhas | `LanRemote/Services/LGTVClient.swift` (linhas 107–262) | `LGTVClient.swift` |
| ControlHelp, HelpPanel, ButtonGuideSheet | `LanRemote/Views/RemoteHelp.swift` | `RemoteHelp.swift` |
| RemoteScreen, PowerBanner, seções | `LanRemote/Views/RemoteView.swift`, `RemoteComponents.swift` | `RemoteView.swift`, `RemoteComponents.swift` |
| Ajustes da TV Box | `LanRemote/Views/SettingsView.swift` | `SettingsView.swift` |
| Testes | `LanRemoteTests/LanRemoteTests.swift` | — |
| Ícone | `scripts/make_app_icon.swift` | `make_icon.swift` |

### 4.2 ROAP: correções e estado de energia

- **Comando:** `POST /roap/api/command` com o corpo
  `<?xml version="1.0" encoding="utf-8"?><command><session>$session</session><type>HandleKeyInput</type><value>$code</value></command>`
  e os headers `User-Agent: lg_roap_control/1.0.0 (android)` (nunca com `UDAP/2.0`: a TV responde 400),
  `Content-Type: application/atom+xml; charset=utf-8` e `Connection: close`. Resposta boa: 200 com `<ROAPError>200</ROAPError>`.
- **Consultas:** `GET /roap/api/data?target=volume_info&session=$session` (`<mute>`, `<level>`, `<minLevel>`,
  `<maxLevel>`) e `GET /roap/api/data?target=cur_channel&session=$session` (`<inputSourceName>`, por exemplo `HDMI2`;
  `<major>` igual a `65520` quer dizer "sem canal"). Preencha `TVStatus.inputSource`/`currentChannel`, que já existem
  e estão sem uso.
- **401** em comando ou consulta: `AuthReq` com a chave salva, grava a nova sessão e repete **uma vez**.
- **Intervalo entre teclas:** 300 ms (já existe). A TV também descarta dois `mute` com menos de ~2 s entre eles
  (PROTOCOLO.md): é da TV, não "corrija".
- **Estado de energia** (`TVPowerState`), igual ao iOS:
  - qualquer resposta HTTP (200 ou 401) → `ON`: a rede da TV está de pé;
  - falha de rede com o estado `ON` ou `UNKNOWN` → espera 1,5 s e tenta **mais uma vez** antes de marcar `OFF` (a
    primeira requisição depois de abrir o app às vezes falha com a TV ligada);
  - sem resposta → `OFF` (nesta TV, sem rede = standby), exceto durante `TURNING_ON`;
  - tecla POWER aceita (200) → `OFF` na hora;
  - com `OFF` ou `TURNING_ON`, `sendKey` **não envia**: incrementa `attentionPulse` (o banner pulsa) e dá um háptico de aviso;
  - botões físicos de volume com a TV `OFF`: devolva o evento ao sistema (`super.onKeyDown`) para o volume do
    celular continuar funcionando.

### 4.3 Protocolo do controle remoto do Android TV (v2)

Portas: **6467** = pareamento (uma vez só) e **6466** = controle. As duas usam TLS com **certificado de cliente
autoassinado**, e cada mensagem protobuf vai **precedida do próprio tamanho em varint**. Não há `.proto` no projeto e
não precisa: escreva os campos à mão com um `ProtoWriter`/`ProtoMessage` mínimo (varint e length-delimited; nos
wire types 1 e 5, pule 8 e 4 bytes). Nenhuma dependência nova.

**Pareamento (porta 6467).** Toda mensagem do cliente leva `1: protocol_version = 2` e `2: status = 200`. Cada resposta
do servidor traz `2: status` (`200` = ok, `402` = código errado, outro = recusado) e o campo esperado.

| Passo | Cliente envia | Espera |
|---|---|---|
| 1 | `10 pairing_request { 1: "atvremote", 2: "LanRemote (Android)" }` (o nome aparece na TV) | campo `11` |
| 2 | `20 options { 1 input_encodings { 1: 3 (HEX), 2: 6 }, 3: 1 (preferred_role INPUT) }` | campo `20` |
| 3 | `30 configuration { 1 encoding { 1: 3, 2: 6 }, 2: 1 (client_role INPUT) }` | campo `31`: **o código de 6 caracteres aparece na TV** |
| 4 | `40 secret { 1: segredo }` | campo `41` |

Segredo = `SHA-256(módulo do cliente ‖ expoente do cliente ‖ módulo do servidor ‖ expoente do servidor ‖ nonce)`, cada
inteiro em big-endian **sem zeros à esquerda** (`BigInteger.toByteArray()` traz um `0x00` de sinal: remova). O
`nonce` são os bytes dos 4 últimos caracteres hexadecimais do código. O **primeiro byte do hash tem que ser igual aos 2
primeiros caracteres** do código (checksum): se não for, o usuário digitou errado; avise sem enviar nada, porque a
TV Box continua esperando. O certificado do servidor vem de `sslSocket.session.peerCertificates[0]`. Para o código
aparecer, a TV tem que estar ligada **na entrada da TV Box**.

**Controle (porta 6466).** O servidor fala primeiro:

| Servidor envia | Cliente responde |
|---|---|
| `1 remote_configure` | `1 { 1: 622 (features), 2 device_info { 1: Build.MODEL, 2: Build.MANUFACTURER, 3: 1, 4: "1", 5: "lanremote", 6: "1.1.0" } }` |
| `2 remote_set_active` | `2 { 1: 622 }` |
| `8 remote_ping_request { 1: n }` | `9 { 1: n }` (eco; responder sempre, também depois do handshake) |
| `40 remote_start { 1: started }` | nada: `started == 1` quer dizer que a **tela da TV Box está ligada** |
| `3 remote_error` | abortar |

O handshake termina quando `remote_configure` e `remote_set_active` foram respondidos; depois disso espere **até 1 s**
por `remote_start` (prazo total: 8 s). Tecla: `10 remote_key_inject { 1: keycode, 2: 3 (SHORT) }`, com 300 ms entre
teclas. POWER = **26**. (`BuildConfig` está desligado no AGP 8; o `versionName` pode ir fixo.)

Vetores para os testes (calculados pelo mesmo algoritmo do iOS; bytes em hexadecimal):

```
pairing_request enquadrada:  27 08 02 10 C8 01 52 20 0A 09 61 74 76 72 65 6D 6F 74 65 12 13 4C 61 6E 52 65 6D 6F 74 65 20 28 41 6E 64 72 6F 69 64 29
pairing_option enquadrada:   10 08 02 10 C8 01 A2 01 08 0A 04 08 03 10 06 18 01
pairing_configuration:       10 08 02 10 C8 01 F2 01 08 0A 04 08 03 10 06 10 01
key_inject POWER:            06 52 04 08 1A 10 03
resposta ao set_active:      05 12 03 08 EE 04
ping_response com n = 7:     04 4A 02 08 07
varint(150) = 96 01          varint(300) = AC 02          campo 1 = 150 → 08 96 01
secret(cliente {mod 01 02 03, exp 01 00 01}, servidor {mod 09 08 07, exp 01 00 01}, nonce 54 34)
  = b0d4f9c2a661eaf53c593a24ea35123c634fb4bcea9bd08f0fe515d814b3c491
código " 8e5434 " → checksum 0x8E, nonce 54 34; "8E543" e "8E543G" → inválidos
```

**Identidade do app (AndroidKeyStore).** A TV Box guarda o certificado no pareamento, então ele precisa sobreviver a
reinícios. No Android não é preciso montar o X.509 à mão como no iOS: o AndroidKeyStore gera o certificado
autoassinado junto com o par de chaves.

```kotlin
val now = System.currentTimeMillis()
val spec = KeyGenParameterSpec.Builder("lanremote_androidtv", KeyProperties.PURPOSE_SIGN)
    .setAlgorithmParameterSpec(RSAKeyGenParameterSpec(2048, RSAKeyGenParameterSpec.F4))
    .setDigests(KeyProperties.DIGEST_NONE, KeyProperties.DIGEST_SHA256,
                KeyProperties.DIGEST_SHA384, KeyProperties.DIGEST_SHA512)
    .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1, KeyProperties.SIGNATURE_PADDING_RSA_PSS)
    .setCertificateSubject(X500Principal("CN=LanRemote"))
    .setCertificateSerialNumber(BigInteger.valueOf(now))
    .setCertificateNotBefore(Date(now - 86_400_000L))
    .setCertificateNotAfter(Date(now + 3650L * 86_400_000L))
    .build()
KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore").run {
    initialize(spec)
    generateKeyPair()
}
```

- **PSS é obrigatório:** no TLS 1.3 a assinatura do cliente (CertificateVerify) é RSA-PSS; sem
  `SIGNATURE_PADDING_RSA_PSS` o handshake falha dentro do Keystore. PKCS#1 com SHA-256 é o que faz o certificado sair
  assinado de verdade.
- **Correção feita na implementação (Fase 3):** o Conscrypt aplica o enchimento PSS no BoringSSL e pede ao Keystore só
  a operação RSA crua (`Cipher "RSA/ECB/NoPadding"` com a chave privada = assinar com `KM_PAD_NONE` e `DIGEST_NONE`;
  ver `CryptoUpcalls.rsaOpWithPrivateKey` e `AndroidKeyStoreRSACipherSpi.NoPadding`). Por isso a chave precisa também
  de `setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)`. Conferido no emulador (Android 17) contra um
  servidor TLS 1.3 que exige certificado de cliente: com a especificação acima e sem esse enchimento, o keystore2
  recusa com `INCOMPATIBLE_PADDING_MODE`; com ele, o handshake fecha (TLS 1.3 e 1.2).
- Sem `setUserAuthenticationRequired` e sem StrongBox (RSA no StrongBox é lento ou inexistente em muitos aparelhos).
- **KeyManager:** um `X509ExtendedKeyManager` próprio que **sempre** devolve o alias (`chooseClientAlias` e
  `chooseEngineClientAlias`), a cadeia `[certificado]` e a chave privada do Keystore. Não use `KeyManagerFactory`:
  ele filtra pelos emissores que o servidor pede e pode acabar não mandando o certificado.
- **TrustManager:** aceita o certificado autoassinado da TV Box (a confiança vem do pareamento, como no Mac e no
  iOS). Anote com `@SuppressLint("CustomX509TrustManager", "TrustAllX509TrustManager")` e um comentário explicando o
  porquê. Fixar o certificado da TV Box no pareamento é uma melhoria possível, fora do escopo.
- "Esquecer TV Box" apaga a entrada do Keystore; o pareamento seguinte gera outra.
- Não existe Keystore nos testes JVM: esconda a identidade atrás de uma interface (`ClientIdentity`, com
  `privateKey` e `certificate`) e não teste o Keystore na JVM.

**TLS e erros.** `SSLContext.getInstance("TLS").init(arrayOf(keyManager), arrayOf(trustManager), null)`; crie o
socket desconectado, `connect(InetSocketAddress(host, port), 6000)`, `startHandshake()`; leia com `soTimeout` igual
ao tempo que resta do prazo; tudo em `Dispatchers.IO`. Mapeamento (mensagens de `AndroidTVProtocol.swift:9-31`):

| Situação | Erro | Mensagem |
|---|---|---|
| `SSLHandshakeException`/`SSLException` no handshake | `SecureChannel` (na 6466 vira `NotPaired`) | "A TV Box recusou a conexão segura." |
| conexão cai ou EOF **antes** do `remote_configure` (6466) | `NotPaired` | "O app ainda não está pareado com a TV Box." |
| `ConnectException` | `Connection("conexão recusada")` | "Não consegui falar com a TV Box (conexão recusada)." |
| `NoRouteToHostException` | `Connection("aparelho fora da rede")` | "Não consegui falar com a TV Box (aparelho fora da rede)." |
| `SocketTimeoutException` ou prazo | `Timeout` | "A TV Box não respondeu a tempo." |
| status 402 ou checksum errado | `WrongCode` | "Código incorreto. Confira os 6 caracteres na tela da TV." |
| porta 6466 não abre depois do WoL | `Asleep` | "A TV Box está em repouso e não acordou pelo Wi-Fi." |

Pegadinha do TLS 1.3: a TV Box só rejeita um certificado desconhecido **depois** que o `startHandshake()` do cliente
já voltou com sucesso; o erro aparece na primeira leitura (alerta TLS ou EOF). Por isso qualquer falha antes do
`remote_configure` vira `NotPaired`, como faz o iOS em `AndroidTVRemote.swift:280-286`.

**Testabilidade.** Escreva o handshake (`RemoteSession`) contra uma interface
`FramedChannel { suspend fun send(payload: ByteArray); suspend fun receive(timeoutMs: Long): ByteArray; fun close() }`.
O `TlsChannel` implementa essa interface; os testes usam um canal falso com um roteiro de mensagens.

### 4.4 Ligar a TV: sequência e tempos validados

`switchTvOn(host, wakeMacs)`, porte de `AndroidTVRemote.switchTVOn` (iOS `AndroidTVRemote.swift:331-345`):

1. **Wake-on-LAN.** No Android dá para usar broadcast (o iOS não pode): mande cada pacote para o `host` (unicast)
   **e** para o broadcast da sub-rede do Wi-Fi (por exemplo `192.0.2.255`, calculado de `LinkProperties`) e
   `255.255.255.255`, nas portas 9 e 7, com `DatagramSocket().apply { broadcast = true }`. O broadcast acorda a TV
   Box mesmo se o IP dela tiver mudado.
2. **`waitUntilReachable(host, timeout = 4 s sem MAC conhecido, 6 s com MAC)`**: tenta `Socket.connect(6466)` com
   600 ms, a cada 300 ms. Se não abrir, procure a TV Box pelo nome via NSD (3 s); achando com outro IP, atualize e
   salve o `host` e tente de novo; senão, `Asleep`.
3. Conecte, faça o handshake **sem tecla** e leia o `remote_start`. Se a **tela da TV Box estiver ligada** (TV
   desligada mas TV Box acordada): `POWER` (ela dorme), espere **3 s**, mande o WoL de novo e
   `waitUntilReachable(12 s)`. Só um despertar novo manda o One Touch Play.
4. `POWER` (26).
5. No ViewModel: estado `TURNING_ON` com o passo "A TV está acendendo. O controle volta a responder em até 1
   minuto…"; consulte a TV a cada **1,5 s** por até **90 s**. Respondeu → `ON` + háptico de sucesso. Estourou →
   `OFF` + "A TV Box recebeu o pedido, mas a TV não respondeu. Confira se o SIMPLINK está ligado na TV e o controle
   HDMI (CEC) na TV Box."

`wakeMacs` (iOS `TVBoxLink.swift:22-26`): o MAC digitado pelo usuário, se houver; senão `[bt − 1, bt + 1, bt]` a
partir do `bt=` do Bonjour; senão, lista vazia. Pacotes a mais não fazem mal.

Mensagens de erro ao ligar (iOS `TVBoxLink.swift:146-189`, trocando "iPhone" por "celular"):

- `NotPaired`: marque `paired = false` e mostre "A TV Box não reconhece mais este celular. Pareie de novo em Ajustes."
- `Asleep` sem MAC: "A TV Box está em repouso e o app não sabe o MAC do Wi-Fi dela para acordá-la. Informe em Ajustes."
- `Asleep` com MAC: "A TV Box não acordou pelo Wi-Fi. Se o IP dela mudou, ligue a TV uma vez pelo controle e o app atualiza o endereço."
- outros: "{mensagem do erro} Ela está ligada na tomada e no Wi-Fi?"

**Endereço sempre fresco:** com a TV `ON` e a TV Box pareada, redescubra a TV Box pelo NSD **a cada 5 min** e
atualize `host`/`bt` (iOS `refreshBoxAddressIfNeeded`). **Sem Wi-Fi** (a rede ativa não tem
`NetworkCapabilities.TRANSPORT_WIFI`), não tente: mostre "Conecte o celular ao Wi-Fi de casa para ligar a TV."

**Descoberta (NSD):** `NsdManager.discoverServices("_androidtvremote2._tcp", NsdManager.PROTOCOL_DNS_SD, …)` por 3 s.
Resolva cada serviço (antes do API 34 só **um** `resolveService` por vez, então use uma fila; no API 34+ prefira
`registerServiceInfoCallback`), pegue o IPv4 e `attributes["bt"]` (bytes UTF-8 → `MacAddress.normalize`). O
`NsdManager` cuida do multicast; não precisa de `MulticastLock`. **No emulador o mDNS não atravessa o NAT:** teste a
descoberta em aparelho real e, no emulador, digite o IP.

**Persistência:** DataStore (mesmo arquivo `lan_remote_settings`) com `box_name`, `box_host`, `box_paired`, `box_mac`
e `box_bt_mac`. A chave privada fica só no Keystore. Não grave o código de pareamento.

### 4.5 Controle didático (UI/UX)

Princípios, os mesmos do Mac e do iOS:

- **reconhecer em vez de lembrar**: ícone + rótulo em português em todo botão;
- **agrupamento por função**, com título em cada seção;
- **divulgação progressiva**: segurar um botão explica o que ele faz; o "?" abre o guia completo;
- **visibilidade do estado**: Ligada/Desligada/Ligando…, volume e entrada sempre à vista;
- **prevenção de erro**: confirmar antes de desligar;
- **linguagem do usuário** em vez das siglas da LG.

Um **catálogo único** (`ControlHelp(id, label, title, detail, icon, tip?, tint?)`) alimenta o botão, o painel, o guia
e o TalkBack. **Porte os textos de `RemoteHelp.swift` literalmente**, trocando "iPhone" por "celular" (por exemplo,
a dica do volume vira "Botões laterais do celular"). Os textos podem ficar em Kotlin, como no iOS: o app só fala
português. Rótulos: Desligar/Ligar, Entrada, Números, Último (era Q.View), Info, Ajuste rápido (Q.Menu), Início
(Home), Configurações (Menu), OK, Voltar, Sair, Programação (EPG), Volume, Mudo ("Sem som" quando ativo), TV ao vivo,
Canal, Apps, Formato, 3D, SIMPLINK. Reprodução e botões coloridos ficam só com ícone ou cor, e a explicação vem ao
segurar.

Layout, de cima para baixo (igual ao iOS `RemoteView.swift`):

1. **Cabeçalho:** LED + "TV LG 42LN5700" + estado (Ligada, Desligada, Ligando…, Verificando…) + volume + entrada;
   botões **?** (Guia) e **Ajustes**. O IP sai do cabeçalho e vai para Ajustes.
2. **PowerBanner** (só com `OFF` ou `TURNING_ON`): título "A TV está desligada" ou "Ligando a TV…", o texto do passo
   ou do erro, e o botão "Ligar a TV" / "Tentar de novo" (TV Box pareada) ou "Configurar para ligar pelo app" (abre
   Ajustes). Pulsa quando `attentionPulse` muda. Textos em `RemoteView.swift:366-441`.
3. **Ações principais:** Desligar (TV ligada → diálogo "Desligar a TV?" com o texto de `RemoteView.swift:89-101`) ou
   Ligar (TV desligada); Entrada; Números (abre e fecha o teclado).
4. **Teclado numérico** (recolhível): 1 a 9 e, na última linha, Último · 0 · Info.
5. **Navegação:** Ajuste rápido · Início · Configurações; setas com OK no meio; Voltar · Sair · Programação.
6. **Volume e canal:** gangorra VOLUME (+/−); na coluna do meio, Mudo e TV ao vivo; gangorra CANAL (˄/˅).
7. **Recursos da TV:** Apps (417) · Formato (46) · 3D (400) · SIMPLINK (411), códigos novos no `TVKey` (os mesmos do iOS).
8. **Reprodução:** retroceder, reproduzir, pausar, parar, avançar.
9. **Botões coloridos:** vermelho, verde, amarelo, azul.
10. **HelpPanel**, fixo embaixo enquanto houver uma explicação aberta.

Com a TV desligada, as seções 5 a 9 ficam com `alpha = 0.45`: continuam legíveis, mas a ação certa é o banner.

No Compose:

- `RemoteButton` deixa de usar o `Button` do Material3, que não tem clique longo: use `Surface`/`Box` com
  `Modifier.combinedClickable(onClick = …, onLongClick = { mostrar ajuda }, onLongClickLabel = "Explicar o botão")`
  (pode exigir `@OptIn(ExperimentalFoundationApi::class)`), mantendo o `interactionSource` do efeito de pressão.
  **Ícone em cima, rótulo embaixo** (11–12 sp). O clique longo **não envia a tecla**; ele toca o háptico
  `HapticFeedbackType.LongPress` e abre a explicação.
- Ponha a ação do clique longo no ViewModel (`explain(help)`), sem chamar o cliente da TV; assim dá para testar na
  JVM que segurar não envia nada.
- `HelpPanel`: ícone, título, texto, dica e o link "Ver todos os botões" (abre o guia). Some sozinho depois de **7 s**.
- Guia: `ModalBottomSheet` com as seções de `GuideSection.all` do iOS (Ligar e escolher a fonte; Navegação; Volume e
  canal; Recursos da TV; Reprodução; Botões coloridos; O app).
- TalkBack: `contentDescription` = título do catálogo, o `detail` como descrição de estado ou dica, e a ação de
  clique longo com rótulo ("Explicar o botão"). Alvos de toque com pelo menos 48 dp.
- Remova `lastMessage` e o toast. Nada de texto técnico na tela principal.

### 4.6 Ajustes

Transforme o `SettingsDialog` em tela cheia (`Dialog(properties = DialogProperties(usePlatformDefaultWidth = false))`
ou `ModalBottomSheet`) com duas seções:

- **TV LG:** o que já existe (IP, buscar, parear pelo ROAP) e o estado da TV.
- **Ligar a TV pelo app** (porte de `SettingsView.swift` do iOS): explicação curta; **Buscar TV Box** (NSD) → lista
  (no emulador, campo para digitar o IP); **Parear** → "Olhe a TV e digite o código de 6 caracteres" (campo em
  maiúsculas, só hexadecimal, 6 caracteres) → Confirmar. Erro de checksum mantém a TV Box esperando. Estados como no
  iOS (`BoxPairingStage`: parado, buscando, pedindo código, esperando código, confirmando, falhou). Pareada: nome,
  IP, ✓, campo **MAC do Wi-Fi (opcional)** validado por `MacAddress.normalize` e botão **Esquecer TV Box**.
  Requisitos listados na tela: SIMPLINK ligado na TV; HDMI-CEC ativo na TV Box (Configurações › Sistema ›
  Entradas); IP fixo para a TV Box (reserva DHCP no roteador); onde achar o MAC (Configurações › Sistema › Sobre ›
  Status).

### 4.7 Bloco de Configurações rápidas

Corrija P7 (`PendingIntent.getActivity(…, FLAG_IMMUTABLE)` no API 34+), P11 (`subtitle` só no API 29+) e P1. O
bloco continua alternando o Mudo.
Fora do escopo, como ideia futura: "Ligar a TV" pelo bloco, que pediria um serviço em primeiro plano ou WorkManager,
porque o ciclo leva até 90 s.

### 4.8 Ícone

O mesmo controle do Mac e do iOS. Copie `scripts/make_app_icon.swift` do iOS para `scripts/` e acrescente uma saída
Android (roda neste Mac: `swift scripts/make_app_icon.swift --android app/src/main/res`):

- `mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher_foreground.png` com 108, 162, 216, 324 e 432 px, fundo
  transparente, o controle dentro do **círculo seguro de 66 dp** (no máximo ~57% da altura do canvas, porque o
  controle é alto e estreito);
- fundo: o mesmo grafite do iOS (`#34373E` → `#15161A`), em PNG ou vetor com gradiente;
- `ic_launcher_monochrome.png`: branco com alfa vindo da luminância da versão "tinted" do iOS (assim os detalhes
  aparecem), referenciado por `<monochrome …/>` em `mipmap-anydpi-v26/ic_launcher.xml` e `ic_launcher_round.xml`
  (ícone temático do Android 13+);
- `app/src/main/ic_launcher-playstore.png` com 512×512.

Apague `drawable/ic_launcher_foreground.xml` (o vetor antigo) e confira no launcher do emulador (máscaras redonda e
squircle, tema claro e escuro, ícone temático).

### 4.9 CI

`.github/workflows/android-ci.yml`: dispara em `push` e `pull_request` para a `main`; `ubuntu-latest`;
`actions/checkout@v4`, `actions/setup-java@v4` (Temurin 21), `gradle/actions/setup-gradle@v4`;
`./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug`; publica `app-debug.apk` como artefato; falha se houver
`*.jks`, `*.keystore` ou `*.p12` versionados.

## 5. Riscos e pegadinhas

- **Cada cliente pareia sozinho.** O pareamento do Mac e o do iPhone não valem para o Android: o app precisa parear
  com a TV Box (código na tela) e com a TV (ROAP) por conta própria.
- **Emulador:** alcança a LAN por TCP e UDP unicast através do NAT do Mac (ROAP na 8080, TLS na 6466/6467 e WoL
  unicast funcionam), mas **não** faz mDNS nem broadcast. Digite os IPs.
- **Wi-Fi sem internet:** se o Android marcar o Wi-Fi de casa como "sem internet", a rede padrão pode virar a do
  celular e a LAN some. Só se acontecer: crie os sockets pela rede Wi-Fi (`Network.socketFactory` ou
  `bindProcessToNetwork`).
- **targetSdk futuro:** versões novas do Android estão restringindo o acesso à rede local para apps com `targetSdk`
  recente (permissão de rede local no grupo "Dispositivos próximos"). Este plano mantém o `targetSdk 35`; antes de
  subir para 36/37, confira a documentação oficial e peça a permissão se for exigida.
- **Firmware lento:** respostas de 2 a 4 s são normais na TV; mantenha os timeouts do Ktor (3,5 s) e a regra das duas falhas.
- **Sessão ROAP morre** quando a TV desliga e liga: o 401 seguido de reautenticação é o caminho normal, não erro.
- **Repositório público:** nada de chaves, `.p12`/`.jks`, código de pareamento ou sessão ROAP no git. Use MACs
  fictícios nos testes (por exemplo `02:00:00:00:00:01`).

## 6. Passo a passo

Cada fase termina com `./gradlew --no-daemon testDebugUnitTest assembleDebug` verde e um commit. Marque a fase na
seção 9 no mesmo commit.

**Fase 0: preparar.** `git -C ~/lan-remote-android fetch origin`, `git switch feat/ligar-tv-pela-tvbox`,
`git pull --ff-only`. Exporte o `JAVA_HOME` da seção 2 e confirme a linha de base (11 testes). Acrescente `.kotlin/`
ao `.gitignore`.

**Fase 1: ROAP certo e estado da TV** (P1 a P5, P8, seção 4.2).
- `KtorTVClient`: XML do comando, `session` nas consultas, `cur_channel`, `AuthReq` sem sessão = falha, 401 → reautentica e repete uma vez.
- `RemoteViewModel`/`RemoteUiState`: `powerState`, regra das duas falhas, teclas seguradas com a TV desligada
  (`attentionPulse`), POWER → `OFF`, sem `lastMessage`; polling preso ao ciclo de vida.
- Testes: `KtorTVClientTest` passa a afirmar `<session>…</session>` e `<type>HandleKeyInput</type>`, sem `<name>` e
  sem header `Session`; URL de volume com `session=`; leitura de `cur_channel`; `AuthReq` sem `<session>` falha.
  `RemoteViewModelTest`: sai a asserção de `lastMessage`; entram `ON` depois de 200, `OFF` depois de POWER, duas
  falhas antes de `OFF` e tecla não enviada com a TV `OFF`.
- Commit: `fix(android): formato ROAP correto e estado de energia da TV`.

**Fase 2: protocolo do Android TV, parte pura** (seção 4.3, só JVM).
- `Proto`, `PairingMath`, `MacAddress`, `WakeOnLan.magicPacket`, `AndroidTvBox.wakeMacs`, `RemoteSession` sobre `FramedChannel`.
- Testes: os vetores da seção 4.3; MAC (normalização de `02-00-00-00-00-F8`, `0200000000f8`, `a:3:b:c:d:5`;
  vizinhos com "vai um" de `…:22:ff` + 1 = `…:23:00`; `00:00:00:00:00:00` − 1 = nulo); pacote mágico de 102 bytes;
  ordem de `wakeMacs`; `RemoteSession` (configure → resposta, set_active → resposta, ping → eco, `remote_start {1: 1}`
  → tela ligada, EOF antes do configure → `NotPaired`, `remote_error` → erro); `switchTvOn` com falsos e tempo virtual
  (`runTest`): tela ligada → POWER, 3 s, WoL, POWER; tela desligada → só POWER; porta fechada → `Asleep`.
- Commit: `feat(android): protocolo do controle remoto do Android TV`.

**Fase 3: rede de verdade** (seções 4.3 e 4.4).
- `AndroidTvIdentity`, `TlsChannel`, `AndroidTvPairing`, `AndroidTvRemote`, envio do WoL, `TvBoxDiscovery` e persistência da TV Box.
- Verificação sem efeito colateral, com a TV ligada na entrada da TV Box: parear e depois fazer só o handshake na
  6466, sem tecla, conferindo que `remote_start` diz "tela ligada". Não aperte POWER nesta fase.
- Commit: `feat(android): pareamento e conexão com a TV Box`.

**Fase 4: ligar pela interface e Ajustes** (seções 4.4 a 4.6).
- ViewModel: `turnOn()`, `canTurnOnTv`, pareamento (`BoxPairingStage`), MAC opcional, esquecer, atualização do endereço a cada 5 min.
- `PowerBanner`, botão Ligar, diálogo "Desligar a TV?", seção "Ligar a TV pelo app" nos Ajustes.
- Commit: `feat(android): ligar a TV pela TV Box`.

**Fase 5: controle didático** (seção 4.5).
- `ControlHelp` + `GuideSection`, novo `RemoteButton`, seções, `HelpPanel`, guia, TalkBack, teclas novas no `TVKey`.
- Testes: o guia cobre cada item do catálogo uma vez e todos têm título e texto (porte de
  `testGuideCoversEveryHelpOnceWithCompleteText`); todo `TVKey` usado na tela tem ajuda; `explain(help)` não chama o
  cliente da TV; códigos novos (411, 417, 46, 400) no `TVKeyTest`.
- Commit: `feat(android): botões que se explicam`.

**Fase 6: ícone e bloco rápido** (seções 4.7 e 4.8).
- Depois desta fase, `./gradlew lintDebug` tem que passar (0 erros; P7 e P11 resolvidos, aviso `MonochromeLauncherIcon` some).
- Commits: `feat(android): ícone do app com o controle remoto` e `fix(android): bloco de Configurações rápidas no Android 8, 9 e 14+`.

**Fase 7: CI, README e versão** (seção 4.9).
- `versionCode 2`, `versionName "1.1.0"`. README com as seções "Ligar a TV pelo app" e "Botões que se explicam"
  (como no README do iOS) e a atualização dos comandos de build.
- Commit: `ci(android): testes e APK no GitHub Actions`.

**Fase 8: validação na TV real e PR** (seção 7.2). Registre no PR o que foi validado, com os tempos.

## 7. Como provar que funciona

### 7.1 Critérios de aceite

1. **ROAP:** os testes provam o XML `<command><session>…</session><type>HandleKeyInput</type><value>…</value></command>`
   (sem `<name>` nem header `Session`), as consultas com `&session=` e o 401 → reautenticação → um reenvio.
2. **Estado e retorno:** `TVPowerState` segue a regra das duas falhas; com a TV desligada nenhuma tecla é enviada e o
   banner pulsa; desligar pede confirmação; não sobra `lastMessage` nem toast (`grep -rn lastMessage app/src` vazio).
3. **Protocolo do Android TV:** os testes passam com os vetores da seção 4.3 (enquadramento, mensagens de pareamento,
   segredo e checksum, tecla, eco do ping, handshake com `remote_start`, `NotPaired` antes do configure).
4. **Ligar:** a sequência de `switchTvOn` está coberta por testes com falsos (tela ligada, tela desligada, TV Box sem
   acordar); a TV Box pareada e a identidade do Keystore sobrevivem a fechar e abrir o app.
5. **Didático:** todo botão tem ícone + rótulo em português (exceto reprodução e cores), segurar mostra a explicação
   sem enviar nada, o guia cobre o catálogo inteiro e o TalkBack lê os mesmos textos.
6. **Ícone e bloco:** ícone adaptativo com camada monocromática, gerado do mesmo desenho do Mac/iOS; o bloco de
   Configurações rápidas usa `PendingIntent` no API 34+ e só define `subtitle` no API 29+.
7. **Entrega:** `./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug` verde aqui e no GitHub Actions
   (lint com 0 erros); README atualizado; versão 1.1.0.

### 7.2 Validação na TV real (obrigatória antes de tirar o PR de rascunho)

- **Avise o usuário** antes de desligar ou ligar a TV: é a TV da sala dele.
- Aparelho: pergunte se há um celular Android para testar (`adb devices`). Senão, use o AVD
  (`~/Library/Android/sdk/emulator/emulator -avd Pixel_9`) com as ressalvas da seção 5.
- Instalação e inspeção: `./gradlew installDebug` (ou `adb install -r app/build/outputs/apk/debug/app-debug.apk`);
  tela com `adb exec-out screencap -p > tela.png`; toques com `adb shell input tap X Y` e `adb shell uiautomator dump`.
- Conferência independente do app: `python3 ~/lan-remote-mac/lg_roap_control.py status` mostra
  volume, entrada e contexto da TV pela sessão do Mac (falha quando a TV está em standby).
- Os códigos de pareamento (ROAP e TV Box) aparecem **na tela da TV**: peça ao usuário para ler, é uma vez só.

Roteiro:

1. TV ligada: parear o ROAP → Vol+ e Vol− mudam o volume no `status`; Entrada muda a entrada.
2. Segurar um botão → o painel explica e nada chega à TV (o volume no `status` não muda).
3. Ajustes › Ligar a TV pelo app → Buscar (aparelho real) ou IP `192.0.2.101` (emulador) → Parear → código → ✓.
4. Desligar (com confirmação) → TV em standby → o app mostra "Desligada" e o banner.
5. Esperar pelo menos 30 s (a TV Box entra em repouso profundo) → Ligar → a TV acende e o app mostra "Ligada" em até
   90 s (no Mac foram ~42 s).
6. Se possível, repetir o passo 5 com a TV Box acordada (tela dela ligada e a TV desligada).

Se alguma parte não puder ser feita (TV ou aparelho indisponível), diga isso no PR e ao usuário, sem marcar como validado.

## 8. Git e entrega

- Trabalhe na branch `feat/ligar-tv-pela-tvbox`; o PR em rascunho já existe. Um commit por fase.
- Staging sempre explícito: `git add <caminhos>`, nunca `git add -A` nem `git add .`.
- **Push na `main` só com ordem explícita do usuário** neste repositório. (No iOS ele autorizou a `main`; isso não vale
  aqui sem ele dizer de novo.)
- Depois de cada push: `git rev-list --left-right --count origin/feat/ligar-tv-pela-tvbox...HEAD` tem que dar `0 0`.
- No fim: atualize a seção 9, tire o PR de rascunho e conte ao usuário o que foi validado na TV real e o que não foi.

## 9. Progresso

- [x] Fase 0: preparar
- [x] Fase 1: ROAP certo e estado da TV
- [x] Fase 2: protocolo do Android TV (parte pura)
- [x] Fase 3: rede de verdade (conferida no emulador contra uma TV Box falsa com TLS 1.3 e 1.2: pareamento,
  handshake da 6466, WoL e as duas sequências de ligar; a conferência na TV Box real ficou para a Fase 8, porque a TV
  estava em standby)
- [x] Fase 4: ligar pela interface e Ajustes (Ajustes em tela cheia no lugar do controle, não num `Dialog`: a janela
  do diálogo pedia a altura da tela inteira e o sistema a encaixava entre as barras, cortando o fim da rolagem)
- [x] Fase 5: controle didático
- [ ] Fase 6: ícone e bloco rápido
- [ ] Fase 7: CI, README e versão
- [ ] Fase 8: validação na TV real e PR
