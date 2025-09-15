# Projeto: Chat P2P (Relatório / README)

> **Resumo:** este projeto implementa um chat ponto-a-ponto (P2P) em Java com interface web (Spring Boot + Thymeleaf). O repositório contém a implementação do nó P2P (classe `Peer`), gerenciamento de identidade (`PeerIdentity` / UUID), armazenamento simples de histórico em disco (`chat_history/`) e uma camada web (controller/service/templates) para demonstrar o funcionamento via navegador.

---

## 1. Visão geral da arquitetura

```
+----------------+        TCP sockets         +----------------+
|  Cliente Web   | <---- HTTP (Spring UI) -->|  Nó P2P (Peer) |<---> Outros Peers (TCP)
| (Thymeleaf JS) |                           |  (ServerSocket) |
+----------------+                           +----------------+
         ^                                             ^
         |                                             |
   /messages (polling)                              chat_history/
   /send, /connect
```

Componentes principais:

* **Spring Boot (Web UI)**

    * `ChatController` — endpoints HTTP/HTML (página principal, `/messages`, `/send`, `/connect`).
    * `ChatService` — camada de aplicação que integra a UI com a camada P2P.
    * `src/main/resources/templates/chat.html` — interface web (Thymeleaf + JS com polling AJAX).

* **Camada P2P (java puro, sockets)**

    * `org.unifor.p2p.Peer` — classe que gerencia o `ServerSocket`, aceita conexões e mantém `PeerConnection`s ativas; provê método `connectToPeer(host, port)` para iniciar conexão a outro nó.
    * `org.unifor.p2p.PeerIdentity` — gerenciamento de identidade do nó (UUID por usuário).
    * `org.unifor.p2p.ChatHistory` — persistência simples em arquivos (pasta `chat_history/`), uma pasta por peer id e arquivos de histórico.

* **Persistência**

    * Arquivos simples no diretório `chat_history/`. Cada arquivo contém linhas das mensagens trocadas com o peer correspondente.

---

## 2. Decisões técnicas (por que isso foi escolhido)

* **Java + Spring Boot**: permite criar rapidamente uma interface web e APIs REST/HTML para demonstração sem complicar com dependências frontend.

* **Sockets TCP diretos para P2P**: implementação simples e didática do modelo ponto-a-ponto — cada nó abre um `ServerSocket` e conecta-se diretamente aos outros nós. Foi escolhido para demonstrar conceitos de descoberta/conexão e troca de mensagens sem depender de servidores centrais.

* **Persistência em arquivos**: solução leve e suficiente para demo; facilita visualização do histórico sem banco de dados.

* **CopyOnWriteArrayList / estruturas thread-safe**: para evitar condições de corrida entre threads que recebem mensagens e threads que servem a UI.

* **Thymeleaf + polling AJAX**: solução simples para atualizar mensagens na UI sem introduzir WebSockets nesta etapa. Polling facilita demonstração e compatibilidade com infraestrutura mínima.

---

## 3. Estrutura do projeto (arquivos principais)

```
/src/main/java/org/unifor/
  Main.java                # Entrada Spring Boot
  /controller/ChatController.java
  /service/ChatService.java
  /p2p/
    Peer.java
    PeerIdentity.java
    ChatHistory.java
/src/main/resources/
  application.properties   # porta do servidor web e porta P2P padrão
  templates/chat.html      # página HTML do chat
chat_history/              # pasta gerada em runtime com histórico (UUIDs)

bin/ target/ etc. (artefatos de build)
```

---

## 4. Como executar (exemplos)

> **Pré-requisitos:** Java 17+ e Maven (ou executar o JAR empacotado).

### Com Maven (modo desenvolvimento)

1. Gerar/rodar com porta padrão (conforme `src/main/resources/application.properties`):

```bash
mvn spring-boot:run
# app disponível em http://localhost:8080
```

2. Rodar duas instâncias na mesma máquina (exemplo):

```bash
# Instância A (janela/terminal A)
mvn -DskipTests=true spring-boot:run -Dspring-boot.run.arguments="--server.port=8080 --p2p.port=8081 --p2p.username=PeerA"

# Instância B (janela/terminal B)
mvn -DskipTests=true spring-boot:run -Dspring-boot.run.arguments="--server.port=8082 --p2p.port=8083 --p2p.username=PeerB"
```

> Observação: altere `--server.port` (porta do servidor web) e `--p2p.port` (porta TCP usada pelo Peer) para evitar conflitos.

### Executando o JAR empacotado

```bash
mvn clean package
java -jar target/<nome-do-jar>.jar --server.port=8080 --p2p.port=8081 --p2p.username=PeerA
# em outra janela
java -jar target/<nome-do-jar>.jar --server.port=8082 --p2p.port=8083 --p2p.username=PeerB
```

---

## 5. Demonstração do funcionamento do chat P2P

1. Abra as UIs de ambas as instâncias (ex.: `http://localhost:8080` e `http://localhost:8082`).
2. Em `PeerA` use o formulário **Conectar** e informe `host=localhost` e `port=8083` (porta P2P da PeerB). Clique em conectar.
3. No log do terminal da PeerB deverá aparecer a conexão entrante; na UI podem aparecer mensagens de sistema.
4. Envie mensagens no formulário `Mensagem` (campo `message` -> botão Enviar). As mensagens são enviadas via socket TCP direto para o peer conectado.
5. As mensagens recebidas são exibidas no painel `Mensagens` e também gravadas em disco em `chat_history/<peer-id>/`.

### Endpoints úteis (para automação / debug)

* `GET /` — página principal (UI)
* `GET /messages` — retorna JSON com lista de mensagens atuais (usado pelo polling JS)
* `POST /connect` — conecta a outro peer. Parâmetros: `host`, `port`.
* `POST /send` — envia uma mensagem. Parâmetro: `message`.

### Exemplo via `curl`

Conectar (PeerA conectando ao PeerB):

```bash
curl -X POST "http://localhost:8080/connect" -d "host=localhost&port=8083"
```

Enviar mensagem:

```bash
curl -X POST "http://localhost:8080/send" -d "message=olá do curl"
```

Ver mensagens (JSON):

```bash
curl http://localhost:8080/messages
```

### Histórico em disco (exemplo)

A pasta `chat_history/` é populada com subpastas identificadas por UUID. Exemplo de arquivo de histórico encontrado no projeto:

```
chat_history/2a7e260d-1442-4469-8bdf-11471c733d59/d63223d0-239b-4e84-8387-2aecc638b275.txt

Conteúdo de exemplo:
[Peer_1]: oi
[Peer_1]: mensagem do peer1 para peer2
```

---

## 6. Dificuldades encontradas / limitações conhecidas

1. **Descoberta de peers**: não há mecanismo de discovery — as conexões dependem de informar host/porta manualmente.
2. **NAT / Firewall**: conexões P2P diretas via TCP podem falhar em redes NAT sem NAT traversal (STUN/ICE) ou portas públicas.
3. **Segurança / Criptografia**: mensagens são trocadas em texto plano. Não há TLS nem autenticação forte.
4. **Escalabilidade**: abordagem com threads e `ServerSocket` funciona para poucos peers, mas não escala bem para muitas conexões (precisa NIO/Netty ou broker).
5. **Persistência simples**: gravação em arquivos é suficiente para demo, mas não é transacional nem indexada.
6. **Ordenação / entrega**: sem garantias avançadas (retries, ACKs, ordenação global) — depende da ordem de chegada do TCP e do gerenciamento de conexões.
7. **UI: Polling**: atualizações da UI usam polling AJAX; para melhor UX seria ideal usar WebSocket para push em tempo real.

---

## 7. Sugestões de melhorias / próximos passos

* Implementar **WebSockets** para atualizações em tempo real da UI (elimina polling).
* Trocar sockets bloqueantes por **NIO/Netty** para melhor escalabilidade.
* Adicionar **TLS** e autenticação (certificados) para privacidade e identidade forte.
* Incluir **discovery** (tracker leve, DHT ou servidor de bootstrap) e **NAT traversal** (STUN/TURN) para conectividade pública.
* Migrar histórico para um banco leve (SQLite, H2) para consultas e integridade.
* Suporte a múltiplas salas, mensagens privadas com metadata (timestamp, id, delivery status).
* Testes automatizados (unit e integração) para as classes P2P (simular pares, conexões e falhas).

---

## 8. Logs / Debug

* Verifique a saída do terminal onde a aplicação roda — a classe `Peer` imprime eventos de conexão, desconexão e erros.
* Verifique `chat_history/` para confirmar gravação de mensagens.
* Se uma conexão falhar, verifique se a porta P2P está aberta e não conflita com outra instância.

---
