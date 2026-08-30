# GameSentinel — App Android

**Licença:** este projeto é gratuito para uso pessoal e não-comercial, sob a [PolyForm Noncommercial License 1.0.0](https://github.com/Henriquegp1/App-Alarme/blob/master/LICENSE). Uso comercial (venda, revenda ou distribuição paga) não é permitido sem autorização do autor.

**Autor:** Henrique Gonçalves Pereira — [github.com/Henriquegp1](https://github.com/Henriquegp1)

Se quiser apoiar o projeto, considere uma doação: https://ko-fi.com/henweekz

## 📥 Download

Baixe a versão mais recente (instalador do PC + APK do Android, juntos) na página de [Releases](https://github.com/Henriquegp1/App-Alarme/releases).

## 🖥️ Cliente PC (Windows)

Este aplicativo Android é o complemento do sistema para PC. O código-fonte do cliente Windows está disponível em:
👉 [github.com/Henriquegp1/Alarme-Overwach](https://github.com/Henriquegp1/Alarme-Overwach)

---

O **GameSentinel** é a parte mobile do sistema (pacote técnico `com.henrique.gamesentinel`). Este aplicativo Android se conecta ao sistema rodando no seu PC via WebSocket para alertar instantaneamente (com som e vibração personalizada) quando uma partida for encontrada em jogos como Overwatch, Valorant e Dead by Daylight.

## 🚀 Principais Funcionalidades

* **🎮 Controle Remoto do PC:** Inicie ou pare o monitoramento de tela do seu computador diretamente pelo celular.
* **🔄 Sincronização de Status:** Veja em tempo real o que o PC está fazendo (Monitorando, Em Cooldown ou Pronto) e qual jogo está ativo.
* **🎵 Sons Personalizados por Jogo:** Defina áudios diferentes para cada jogo (Overwatch, Valorant, etc.) para saber qual partida foi encontrada apenas pelo som.
* **🔇 DND Bypass (Furar Mudo):** Opção para o alarme tocar no volume máximo mesmo que o celular esteja no modo silencioso ou "Não Perturbe".
* **⚡ Atalho nas Configurações Rápidas:** Adicione um botão (Tile) na barra de notificações do Android para conectar/desconectar instantaneamente.
* **🏠 Widget de Status:** Acompanhe a conexão e o jogo ativo diretamente na tela inicial do seu celular.
* **🌎 Multi-idioma:** Suporte nativo completo para **Português (Brasil)** e **Inglês**.
* **🔐 Segurança Máxima:** Credenciais e tokens protegidos utilizando o **Android Keystore** (criptografia AES-256).
* **📋 Histórico Retrátil:** Log de atividades detalhado que pode ser ocultado para manter a interface limpa.
* **🔋 Guia de Estabilidade:** Detector de otimização de bateria para garantir que o Android não feche o app em segundo plano.
* **✨ Verificador de Atualizações:** Botão integrado para checar novas versões diretamente do GitHub.

## 🛠️ Como Abrir e Compilar

1. **Requisitos:** Android Studio Hedgehog (ou superior).
2. **Abrir:** `File > Open` e selecione a pasta do projeto.
3. **Sincronização:** O Gradle baixará as dependências automaticamente na primeira abertura.
4. **Recursos Obrigatórios:**
   * Coloque um arquivo `alarme.mp3` em `app/src/main/res/raw/` para o som padrão.
   * O ícone do app pode ser personalizado via `New > Image Asset` no Android Studio.

## 📱 Como Usar

1. Certifique-se de que o **Cliente PC** está rodando e com o monitoramento iniciado.
2. **Conectar:**
   * No app, toque em "Ler QR Code" e aponte para a tela do PC.
   * OU selecione um perfil salvo nas configurações.
3. **Configurações:** Toque no ícone de engrenagem (topo direito) para:
   * Personalizar os sons de cada jogo.
   * Ativar o **Bypass de Silencioso**.
   * **IMPORTANTE:** Toque em "Configurar Bateria" para garantir execução estável.
4. **Perfis:** Salve seus PCs na seção "Segurança e Perfis" para trocas rápidas.

## ⚠️ Solução de Problemas

* **Não conecta:** Verifique se o Celular e o PC estão na **mesma rede Wi-Fi**. O Firewall do Windows também deve permitir a porta 8000.
* **O alarme não toca em segundo plano:** Certifique-se de que o Status de Execução nas configurações está como **"Protegido"**.
* **Erro de Autenticação:** Verifique se a senha no PC e no App são as mesmas. O log de erros no celular dará o motivo exato.

## 📦 Gerar Versão para Distribuição (APK)

1. Vá em `Build > Generate Signed Bundle / APK`.
2. Selecione **APK** e clique em `Next`.
3. Use sua Key Store de produção.
4. Selecione a variante **release**.
5. O arquivo final estará em `app/release/app-release.apk`.

---

Desenvolvido como parte do ecossistema GameSentinel.
