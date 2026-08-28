# OW Match Alarm — App Android (Etapa 2)

## Como abrir

1. Abra o **Android Studio**.
2. `File > Open` e selecione a pasta `OwAlarm` (a pasta raiz deste zip,
   que contém `build.gradle`, `settings.gradle` e `app/`).
3. Deixe o Android Studio baixar o Gradle Wrapper e sincronizar
   sozinho na primeira abertura (ele gera os arquivos de wrapper que
   não vieram neste zip — isso é normal e automático).

## Antes de compilar (obrigatório)

- Coloque um arquivo `alarme.mp3` em `app/src/main/res/raw/`
  (veja o `LEIA_ANTES_DE_COMPILAR.txt` que está lá). Sem isso o app
  compila normalmente, mas o alarme sonoro não toca.
- Você vai precisar de um ícone de app (`ic_launcher`). Se não
  fornecer um, o Android Studio geralmente usa um placeholder padrão
  — não é bloqueante para rodar em modo debug.

## Como testar

1. Rode o cliente PC (Etapa 1) e clique em "Iniciar Monitoramento".
2. Anote o IP e porta mostrados na tela do PC (ou use o QR Code).
3. No celular, com o app aberto: toque em "Ler QR Code do PC" e
   aponte a câmera, OU digite manualmente `IP:PORTA` no campo de
   texto e toque em "Conectar".
4. **Celular e PC precisam estar na mesma rede Wi-Fi/LAN.** Rede
   móvel (4G/5G) não enxerga o servidor local — isso é uma limitação
   de rede, não um bug do app.
5. Force uma "Partida Encontrada" no jogo (ou rode `teste_matching.py`
   manualmente no PC) e confira se o celular vibra e toca o alarme.

## Limitações conhecidas (leia antes de reportar bug)

- Sem TLS: a conexão é `ws://`, não `wss://`. Funciona bem numa rede
  doméstica confiável, mas qualquer dispositivo na mesma rede local
  teoricamente consegue conectar no servidor do PC — sem autenticação.
- Alguns fabricantes de Android (Xiaomi, Samsung, Huawei) têm gerenciamento
  de bateria agressivo que mata Foreground Services mesmo com WakeLock.
  Se o alarme parar de funcionar depois de um tempo com a tela apagada,
  procure "otimização de bateria" nas configurações do celular e
  desative para este app especificamente.
- O ícone do app e o app icon completo (mipmap) não foram incluídos —
  o Android Studio usa um placeholder até você adicionar o seu.
