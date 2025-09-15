package org.unifor.p2p;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Representa um nó (peer) na rede P2P. Desacoplado do console para integração web.
 */
public class Peer {
    private final String userName;
    private final String peerId;
    private final ServerSocket serverSocket;
    private final ChatHistory history;
    private final Consumer<String> onMessageCallback;
    private final List<PeerConnection> connections = new CopyOnWriteArrayList<>();

    public Peer(String userName, int port, Consumer<String> onMessageCallback) throws IOException {
        String id = PeerIdentity.getPeerId(userName);
        if (id == null) {
            id = PeerIdentity.createPeerId(userName);
            System.out.println("[INFO] Novo ID criado para " + userName + ": " + id);
        } else {
            System.out.println("[INFO] ID existente encontrado para " + userName + ": " + id);
        }
        this.peerId = id;
        this.userName = userName;
        this.onMessageCallback = onMessageCallback;

        try {
            this.serverSocket = new ServerSocket(port, 50, InetAddress.getByName("0.0.0.0"));
            this.history = new ChatHistory(peerId);
            System.out.println("[INFO] Peer '" + userName + "' está ouvindo na porta " + port);
        } catch (IOException e) {
            System.err.println("[ERRO CRÍTICO] Não foi possível ouvir na porta " + port + ". Ela pode já estar em uso.");
            throw e;
        }
    }

    public void start() {
        new Thread(this::listenForConnections).start();
    }

    private void listenForConnections() {
        while (true) {
            try {
                Socket socket = serverSocket.accept();
                PeerConnection pc = new PeerConnection(socket);
                connections.add(pc);

                // ALTERAÇÃO: Notifica a interface sobre a nova conexão recebida.
                String statusMessage = "[SISTEMA] Nova conexão recebida de " + socket.getRemoteSocketAddress();
                System.out.println(statusMessage); // Mantém o log no console
                onMessageCallback.accept(statusMessage); // Envia para a UI

                new Thread(() -> handleConnection(pc)).start();
            } catch (IOException e) {
                System.err.println("[ERRO] Falha ao aceitar nova conexão: " + e.getMessage());
            }
        }
    }

    private void handleConnection(PeerConnection pc) {
        try {
            while (pc.remotePeerId == null) {
                String firstLine = pc.in.readLine();
                if (firstLine == null) throw new IOException("Conexão encerrada antes de enviar ID");
                if (firstLine.startsWith("/id:")) {
                    pc.remotePeerId = firstLine.substring(4).trim();
                    System.out.println("[INFO] Conectado ao peer remoto com ID: " + pc.remotePeerId);
                    pc.out.println("/id:" + this.peerId);
                    var oldMessages = history.loadHistory(pc.remotePeerId);
                    if (!oldMessages.isEmpty()) {
                        onMessageCallback.accept("[SISTEMA] ---- Carregando histórico com " + pc.remotePeerId.substring(0, 8) + "... ----");
                        oldMessages.forEach(onMessageCallback);
                        onMessageCallback.accept("[SISTEMA] ---- Fim do histórico ----");
                    }
                }
            }
            String message;
            while ((message = pc.in.readLine()) != null) {
                onMessageCallback.accept(message);
                history.saveMessage(pc.remotePeerId, message);
            }
        } catch (IOException e) {
            System.out.println("[AVISO] Conexão com " + pc.socket.getRemoteSocketAddress() + " foi perdida.");
        } finally {
            connections.remove(pc);

            // ALTERAÇÃO: Notifica a interface sobre a desconexão.
            String statusMessage = "[SISTEMA] Conexão com " + pc.socket.getRemoteSocketAddress() + " foi encerrada.";
            System.out.println(statusMessage); // Mantém o log no console
            onMessageCallback.accept(statusMessage); // Envia para a UI

            pc.close(); // Fecha os recursos da conexão
        }
    }

    public void broadcastMessage(String message) {
        String formattedMessage = "[" + userName + "]: " + message;
        onMessageCallback.accept(formattedMessage);
        for (PeerConnection pc : connections) {
            pc.out.println(formattedMessage);
            if (pc.remotePeerId != null) {
                history.saveMessage(pc.remotePeerId, formattedMessage);
            }
        }
    }

    /**
     * ALTERAÇÃO: O método agora retorna boolean para indicar sucesso ou falha.
     */
    public boolean connectToPeer(String host, int port) {
        try {
            Socket socket = new Socket(host, port);
            PeerConnection pc = new PeerConnection(socket);
            pc.out.println("/id:" + this.peerId);

            String response = pc.in.readLine();
            if (response != null && response.startsWith("/id:")) {
                pc.remotePeerId = response.substring(4).trim();
                System.out.println("[INFO] Recebido peerId remoto: " + pc.remotePeerId);
            } else {
                System.out.println("[WARN] Não recebeu peerId do remoto.");
            }

            connections.add(pc);

            if (pc.remotePeerId != null) {
                var old = history.loadHistory(pc.remotePeerId);
                if (!old.isEmpty()) {
                    onMessageCallback.accept("[SISTEMA] ---- Carregando histórico com " + pc.remotePeerId.substring(0, 8) + "... ----");
                    old.forEach(onMessageCallback);
                    onMessageCallback.accept("[SISTEMA] ---- Fim do histórico ----");
                }
            }

            new Thread(() -> handleConnection(pc)).start();
            System.out.println("[INFO] Conectado com sucesso ao peer em " + host + ":" + port);
            return true; // Retorna true em caso de sucesso
        } catch (IOException e) {
            System.err.println("[ERRO] Falha ao conectar ao peer " + host + ":" + port + ". Motivo: " + e.getMessage());
            return false; // Retorna false em caso de falha
        }
    }

    /**
     * ALTERAÇÃO: A classe interna agora tem um método para fechar seus recursos.
     */
    private static class PeerConnection {
        Socket socket;
        BufferedReader in;
        PrintWriter out;
        String remotePeerId;

        PeerConnection(Socket socket) throws IOException {
            this.socket = socket;
            this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            this.out = new PrintWriter(socket.getOutputStream(), true);
        }

        void close() {
            try {
                if (in != null) in.close();
                if (out != null) out.close();
                if (socket != null && !socket.isClosed()) socket.close();
            } catch (IOException e) {
                // Erros ao fechar são geralmente ignorados
            }
        }
    }
}