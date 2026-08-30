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

O **GameSentinel** é a parte mobile do sistema (pacote técnico `com.henrique.gamesentinel`). Este aplicativo Android se conecta ao sistema rodando no seu PC via WebSocket para alertar instantaneamente (com som e vibração personalizada) quando uma partida for encontrada.

## 🚀 Principais Funcionalidades

* **Conexão Instantânea:** Leitura de QR Code ou entrada manual de IP para conexão via WebSocket.
* **Notificação Persistente:** Acompanhe o status da conexão diretamente na barra de notificações e desconecte com um toque.
* **Personalização de Alerta:**
  * Troque o som do alarme por qualquer arquivo de áudio do seu celular.
  * Ajuste o volume do alarme independente do sistema.
  * Escolha entre diferentes padrões de vibração (Curto, Longo ou Pulsação).
* **Gerenciamento de Perfis:** Salve múltiplos PCs (casa, trabalho, etc.) com nomes personalizados e credenciais criptografadas.
* **Histórico de Atividade:** Log em tempo real de conexões, alarmes recebidos e confirmações enviadas ao PC.
* **Segurança:** Armazenamento de senhas e tokens utilizando o Android Keystore (criptografia AES-256).
* **Guia de Estabilidade:** Detector integrado de otimização de bateria para garantir que o Android não feche o app em segundo plano.

## 🛠️ Como Abrir e Compilar

1. **Requisitos:** Android Studio Hedgehog (ou superior).
2. **Abrir:** `File > Open` e selecione a pasta `OwAlarm`.
3. **Sincronização:** O Gradle baixará as dependências automaticamente na primeira abertura.
4. **Recursos Obrigatórios:**
   * Coloque um arquivo `alarme.mp3` em `app/src/main/res/raw/` para o som padrão.
   * O ícone do app pode ser personalizado via `New > Image Asset` no Android Studio.

## 📱 Como Usar

1. Certifique-se de que o Cliente PC está rodando e com o monitoramento iniciado.
2. **Conectar:**
   * No app, toque em "Ler QR Code" e aponte para a tela do PC.
   * OU digite o `IP:PORTA` e a senha manualmente.
3. **Configurações:** Toque no ícone de engrenagem (topo direito) para:
   * Trocar o som do alarme.
   * Ajustar vibração e volume.
   * **IMPORTANTE:** Toque em "Desativar Otimização de Bateria" para garantir que o alarme toque mesmo após horas com a tela apagada.
4. **Perfis:** Salve suas configurações de rede na seção "Segurança e Perfis" para conexões futuras mais rápidas.

## ⚠️ Solução de Problemas

* **Não conecta:** Verifique se o Celular e o PC estão na mesma rede Wi-Fi. O Firewall do Windows também deve permitir a porta 8000.
* **O alarme não toca em segundo plano:** Verifique o "Status de Execução" nas configurações do app e certifique-se de que ele está como "Protegido".
* **Erro de Autenticação:** Verifique se o código da sessão (token) ou a senha configurada no PC coincidem com o que foi digitado no app.

## 📦 Gerar Versão para Distribuição (APK)

Para mandar o app para outras pessoas ou postar no GitHub:

1. Vá em `Build > Generate Signed Bundle / APK`.
2. Selecione APK e clique em `Next`.
3. Crie ou selecione uma Key Store (Chave de Assinatura).
4. Escolha o destino e selecione a variante de build release.
5. O arquivo final estará em `app/release/app-release.apk`.

---

Desenvolvido como parte do ecossistema GameSentinel.
