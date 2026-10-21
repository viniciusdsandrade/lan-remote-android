# LAN Remote Android (LG NetCast TV)

Aplicativo Android nativo para controlar pela rede local (LAN) a TV LG NetCast (série LN5700, 2013), sem servidores intermediários, sem nuvem, com resposta instantânea e feedback háptico. Mesma funcionalidade dos apps de Mac e iOS do projeto.

---

## 🚀 Destaques

- **Liga a TV sem levantar do sofá**: em standby a LG NetCast desliga a rede, então o app acorda a **TV Box** (Android TV / Google TV) ligada no HDMI e ela liga a TV pelo **HDMI-CEC (SIMPLINK)**. Detalhes em [Ligar a TV pelo app](#-ligar-a-tv-pelo-app).
- **Botões que se explicam**: ícone + nome em português em cada botão, agrupados por função. **Segurar** um botão mostra o que ele faz, sem enviar nada à TV; o **?** abre o **Guia dos botões**. Detalhes em [Botões que se explicam](#-botões-que-se-explicam).
- **Acha a TV sozinho**: no primeiro uso o app procura a TV no Wi-Fi (SSDP e varredura da porta 8080) e guarda o endereço; se o roteador der outro IP a ela, o app a reencontra. Só conta como TV quem se apresenta como uma TV LG pela descrição UDAP, então outros aparelhos com a porta 8080 aberta ficam de fora.
- **Estado da TV à vista**: `Ligada`, `Desligada`, `Ligando…` ou `Verificando…` no topo, com volume e entrada atual. Com a TV desligada o controle fica esmaecido, nenhuma tecla é enviada e um cartão mostra o botão **Ligar**. Desligar pede confirmação.
- **Sem mensagens de log na tela**: o retorno é o háptico e o estado da TV.
- **Botões físicos de volume** do celular controlam a TV (com a TV desligada eles voltam a ser do celular).
- **Bloco "TV Mudo"** nas Configurações rápidas.
- **Ícone adaptativo** com o mesmo controle dos ícones do Mac e do iOS, inclusive no ícone temático do Android 13+.

## 🧰 Tecnologias

- **Kotlin 2.0** (corrotinas e Flow) e **Jetpack Compose** + Material 3
- **Ktor Client 3** (engine CIO) para o ROAP da TV
- **Protocolo do controle remoto do Android TV v2** escrito à mão: TLS com certificado de cliente (`SSLSocket`), protobuf mínimo, sem dependências novas
- **AndroidKeyStore** para a chave e o certificado do app, **NsdManager** para achar a TV Box, UDP para o Wake-on-LAN
- **DataStore Preferences**, `Vibrator`/`VibrationEffect`, `TileService`

---

## 🔌 Ligar a TV pelo app

A TV não liga pela rede (em standby o controlador de rede dela desliga; Wake-on-LAN nela não tem efeito). O caminho é a **TV Box da sala** (Android TV / Google TV) ligada no HDMI, que acorda a TV pelo CEC:

1. O app manda **Wake-on-LAN** para a TV Box (unicast + broadcast de sub-rede e `255.255.255.255`, reenviando a cada ciclo de sondagem para que mesmo com o cache ARP frio a resolução ARP ocorra na primeira tentativa e o pacote mágico chegue em seguida). A rede da TV Box responde na porta do controle em ~1–2 s.
2. Nessa janela o app aperta **POWER** pelo protocolo do controle remoto do Android TV (o mesmo do app Google TV: portas 6466/6467, TLS com certificado próprio, mensagens protobuf). Se a tela da TV Box já estava ligada, o app a põe para dormir, espera 3 s e a acorda de novo: só um despertar novo manda o One Touch Play.
3. Ao acordar, a TV Box manda **One Touch Play** pelo HDMI-CEC: a TV liga, já na entrada dela. A imagem volta em segundos; a rede da TV, em ~40–55 s, e aí o controle volta a responder (o app consulta a TV a cada 1,5 s por até 90 s).

Uma vez só, em **Ajustes › Ligar a TV pelo app**: com a TV ligada na entrada da TV Box, toque em **Parear**. A TV Box mostra um código de 6 caracteres na TV e o app o lê sozinho: fotografa a tela da TV (consulta `screen_image` do ROAP), lê o código por OCR (ML Kit, modelo latino embutido no app, sem rede) e confere o dígito verificador antes de enviar, o que separa as confusões do OCR (0/D, 8/B); se não conseguir, o campo para digitar fica na tela (o primeiro par do código é um checksum: um erro de digitação é avisado sem incomodar a TV Box). O app gera uma chave RSA e um certificado de 20 anos no **AndroidKeyStore** (a chave nunca sai dele) e confere a conexão na porta 6466 sem apertar nada. O pareamento dura enquanto essa chave existir: ela some ao desinstalar o app ou em **Esquecer**, e uma chave nova exige parear de novo. Requisitos:

- **SIMPLINK ligado na TV** (botão SIMPLINK do app, ou Configurações › Opções › SIMPLINK). Sem ele a TV ignora o CEC.
- **HDMI-CEC na TV Box** (Configurações › Tela e som ou Sistema › Entradas › HDMI-CEC): mantenha o **HDMI-CEC ativado** e desative **"Desligar automaticamente"**.
- **Modo de energia na TV Box** (Configurações › Sistema › Energia e alimentação): selecione **Modo de consumo elevado (Sempre conectado)** e timer de desligamento em **Nunca**, para a TV Box não desconectar do Wi-Fi nem parar de responder ARP após horas em repouso.
- **MAC do Wi-Fi da TV Box**: o Android não lê a tabela ARP, então o app tenta os endereços vizinhos ao MAC Bluetooth anunciado na rede (`bt=` do serviço `_androidtvremote2._tcp`; nesta TV Box o Wi-Fi é o Bluetooth − 1). Se não acordar, digite o MAC em Ajustes (na TV Box: Configurações › Sistema › Sobre › Status).
- **IP fixo para a TV Box** (reserva DHCP no roteador): o app também atualiza o endereço pela rede automaticamente a cada 5 min.
- **Celular no Wi-Fi de casa**: sem Wi-Fi o app avisa em vez de tentar.

Não adianta usar `KEYCODE_TV_POWER` (177) nem `KEYCODE_WAKEUP` (224): a TV Box da sala ignora os dois.

Só o app para Mac tem uma reserva quando a TV Box sai do Wi-Fi: o PS5. Acordado pela rede o PS5 não manda o One Touch Play, então o app do Mac o reinicia por uma sessão do Uso remoto, o que não existe no celular. Se o botão Ligar não achar a TV Box, confira o modo de energia dela (acima) e o Wi-Fi.

Detalhe de implementação que vale registrar: no TLS 1.3 a assinatura do cliente é RSA-PSS, mas o Conscrypt aplica o enchimento PSS no BoringSSL e pede ao Keystore a operação RSA crua. Por isso a chave é gerada também com `ENCRYPTION_PADDING_NONE`; sem isso o keystore2 recusa com `INCOMPATIBLE_PADDING_MODE`.

## 🧭 Botões que se explicam

Princípios de usabilidade, os mesmos do Mac e do iOS:

- **reconhecer em vez de lembrar**: ícone + rótulo em português em todo botão (Entrada, Números, Último, Info, Ajuste rápido, Início, Configurações, Voltar, Sair, Programação, Mudo, TV ao vivo, Apps, Formato, 3D, SIMPLINK); reprodução e botões coloridos ficam só com ícone ou cor;
- **agrupamento por função**: Navegação, Volume e canal, Recursos da TV, Reprodução e Botões coloridos, cada seção com título;
- **divulgação progressiva**: **segurar** um botão explica o que ele faz, sem enviar nada à TV (o painel embaixo some sozinho em 7 s); o **?** abre o **Guia dos botões** completo;
- **visibilidade do estado**: Ligada/Desligada/Ligando…, volume e entrada sempre à vista;
- **prevenção de erro**: confirmar antes de desligar;
- **linguagem do usuário** em vez das siglas da LG.

Um catálogo único (`ui/help/ControlHelp.kt`) alimenta o botão, o painel, o guia e o **TalkBack**, que lê o título e a explicação de cada controle e oferece a ação "Explicar o botão".

---

## 📡 Protocolo ROAP

A TV LG NetCast (firmware 05.02.11) roda o servidor HTTP ROAP na porta `8080`. Formato validado na TV real:

| Operação | Método | Endpoint | Corpo XML |
|---|---|---|---|
| Pedir código na TV | `POST` | `/roap/api/auth` | `<auth><type>AuthKeyReq</type></auth>` |
| Parear | `POST` | `/roap/api/auth` | `<auth><type>AuthReq</type><value>123456</value></auth>` → `<session>` |
| Tecla | `POST` | `/roap/api/command` | `<command><session>…</session><type>HandleKeyInput</type><value>20</value></command>` |
| Volume | `GET` | `/roap/api/data?target=volume_info&session=…` | resposta com `<level>`, `<mute>` |
| Canal e entrada | `GET` | `/roap/api/data?target=cur_channel&session=…` | resposta com `<inputSourceName>` |

- **User-Agent**: requisições ROAP **não** podem conter `"UDAP/2.0"` (a TV responde `400`). O app usa `lg_roap_control/1.0.0 (android)` com `Connection: close`.
- **Sessão**: morre quando a TV desliga e liga. Num `401` o app refaz o `AuthReq` com a chave salva, grava a sessão nova e repete a requisição uma vez.
- **Estado da TV**: qualquer resposta HTTP quer dizer ligada; sem resposta duas vezes seguidas (1,5 s entre elas), desligada.
- **Endereço da TV**: nenhum vem de fábrica. Sem endereço salvo o app procura a TV a cada 30 s; enquanto ela liga, a cada 10 s; e, se ela parar de responder no endereço salvo, a cada 2 min (em standby a busca só não acha nada). A TV encontrada é confirmada por `GET /udap/api/data?target=rootservice.xml` (que responde sem pareamento, com o `modelName`); com várias TVs LG na rede, o app só troca sozinho para a do mesmo modelo. A chave de pareamento continua valendo no endereço novo.
- **Intervalo de 300 ms entre teclas**: a TV descarta teclas iguais em rajada.

---

## 🛠️ Como compilar e testar

Pré-requisitos: JDK 21 (ex.: `brew install openjdk@21`) e o Android SDK com a plataforma 35.

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home

# Testes de unidade, lint e APK debug (o mesmo que o CI roda)
./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug

# Instalar no celular (depuração USB ligada) ou no emulador
./gradlew installDebug

# (opcional) Regenerar o ícone: o mesmo controle dos ícones do Mac e do iOS
swift scripts/make_app_icon.swift --android app/src/main/res
```

O APK fica em `app/build/outputs/apk/debug/app-debug.apk`.

No **emulador** a descoberta automática da TV Box não funciona (o mDNS e o broadcast não passam pelo NAT): em Ajustes, digite o IP dela. O ROAP da TV, a conexão TLS com a TV Box e o Wake-on-LAN direto ao IP funcionam.

## 🛡️ CI

O GitHub Actions (`.github/workflows/android-ci.yml`) roda em cada push e pull request para a `main`: confere que nenhuma chave ou certificado (`*.jks`, `*.keystore`, `*.p12`) está versionado, roda os testes de unidade, o lint e gera o APK debug, publicado como artefato.

---

## 📄 Licença

Uso pessoal e livre para automação residencial e controle local.
