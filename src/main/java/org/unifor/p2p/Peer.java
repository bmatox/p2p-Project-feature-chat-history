package org.unifor.p2p;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.*;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class Peer {
    private final String userName;
    private final String peerId;
    private final int tcpPort; // A porta para o chat (TCP)
    private final ServerSocket serverSocket;
    private final Consumer<String> onMessageCallback;
    private final ChatHistory history;
    private final List<PeerConnection> connections = new CopyOnWriteArrayList<>();

    // NOVO: Configurações para a descoberta via UDP Multicast
    private MulticastSocket multicastSocket;
    private static final String MULTICAST_GROUP = "230.0.0.0";
    private static final int MULTICAST_PORT = 4446; // Porta para "anúncios" (UDP)

    public Peer(String userName, int port, Consumer<String> onMessageCallback) throws IOException {
        this.userName = userName;
        this.tcpPort = port;
        this.onMessageCallback = onMessageCallback;

        String id = PeerIdentity.getPeerId(userName);
        if (id == null) {
            id = PeerIdentity.createPeerId(userName);
        }
        this.peerId = id;
        this.history = new ChatHistory(peerId);

        try {
            // Inicia o servidor TCP para o chat
            this.serverSocket = new ServerSocket(this.tcpPort, 50, InetAddress.getByName("0.0.0.0"));
            System.out.println("[INFO] Peer '" + userName + "' está ouvindo para chat na porta TCP " + this.tcpPort);

            // NOVO: Inicia o socket UDP para descoberta de peers
            this.multicastSocket = new MulticastSocket(MULTICAST_PORT);
            InetAddress group = InetAddress.getByName(MULTICAST_GROUP);
            this.multicastSocket.joinGroup(group);
            System.out.println("[INFO] Juntou-se ao grupo de descoberta em " + MULTICAST_GROUP + ":" + MULTICAST_PORT);

        } catch (IOException e) {
            System.err.println("[ERRO CRÍTICO] Falha ao iniciar sockets: " + e.getMessage());
            throw e;
        }
    }

    public void start() {
        // Inicia a thread para ouvir conexões de chat (TCP)
        new Thread(this::listenForTcpConnections).start();
        // NOVO: Inicia a thread para ouvir "anúncios" de descoberta (UDP)
        new Thread(this::listenForDiscoveryPackets).start();
    }

    // --- LÓGICA DE DESCOBERTA (PONTO 6) ---

    private void listenForDiscoveryPackets() {
        byte[] buf = new byte[256];
        while (multicastSocket != null && !multicastSocket.isClosed()) {
            try {
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                multicastSocket.receive(packet); // Espera por um "anúncio"

                String received = new String(packet.getData(), 0, packet.getLength());
                // Formato do anúncio: "DISCOVER:tcpPort:username"
                if (received.startsWith("DISCOVER:")) {
                    String[] parts = received.split(":", 3);
                    int peerTcpPort = Integer.parseInt(parts[1]);
                    InetAddress peerAddress = packet.getAddress();

                    if (!isSelf(peerAddress, peerTcpPort) && !isAlreadyConnected(peerAddress, peerTcpPort)) {
                        String peerUserName = parts[2];
                        System.out.println("[DESCOBERTA] Peer '" + peerUserName + "' encontrado em " + peerAddress.getHostAddress() + ":" + peerTcpPort);
                        onMessageCallback.accept("[SISTEMA] Peer '" + peerUserName + "' encontrado! Tentando conectar...");
                        // Conecta-se automaticamente ao peer descoberto
                        connectToPeer(peerAddress.getHostAddress(), peerTcpPort);
                    }
                }
            } catch (IOException e) {
                if (!multicastSocket.isClosed()) System.err.println("[ERRO] Falha ao receber pacote de descoberta.");
            }
        }
    }

    /**
     * Envia um "anúncio" para a rede, avisando sobre sua presença.
     */
    public void broadcastDiscoveryPacket() {
        try {
            String message = "DISCOVER:" + this.tcpPort + ":" + this.userName;
            byte[] buf = message.getBytes();
            InetAddress group = InetAddress.getByName(MULTICAST_GROUP);
            DatagramPacket packet = new DatagramPacket(buf, buf.length, group, MULTICAST_PORT);
            multicastSocket.send(packet);
            System.out.println("[INFO] Pacote de descoberta enviado para a rede.");
        } catch (IOException e) {
            System.err.println("[ERRO] Falha ao enviar pacote de descoberta: " + e.getMessage());
        }
    }

    /**
     * Método robusto para verificar se o pacote recebido veio do próprio peer,
     * checando contra todas as interfaces de rede locais.
     */
    private boolean isSelf(InetAddress address, int port) {
        if (port != this.tcpPort) {
            return false;
        }
        try {
            for (NetworkInterface networkInterface : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                for (InetAddress inetAddress : Collections.list(networkInterface.getInetAddresses())) {
                    if (inetAddress.equals(address)) {
                        return true;
                    }
                }
            }
        } catch (SocketException e) {
            System.err.println("[ERRO] Não foi possível verificar os endereços de rede locais.");
        }
        return false;
    }

    private boolean isAlreadyConnected(InetAddress address, int port) {
        for (PeerConnection pc : connections) {
            if (pc.socket.getInetAddress().equals(address) && pc.socket.getPort() == port) {
                return true;
            }
        }
        return false;
    }

    // --- O RESTO DA CLASSE (LÓGICA DE CHAT TCP) ---
    // A maioria dos métodos abaixo permanece como estava, com pequenas adições de feedback.

    private void listenForTcpConnections() {
        while (serverSocket != null && !serverSocket.isClosed()) {
            try {
                Socket socket = serverSocket.accept();
                PeerConnection pc = new PeerConnection(socket);
                connections.add(pc);
                String statusMessage = "[SISTEMA] Nova conexão recebida de " + socket.getRemoteSocketAddress();
                System.out.println(statusMessage);
                onMessageCallback.accept(statusMessage);
                new Thread(() -> handleConnection(pc)).start();
            } catch (IOException e) {
                if (!serverSocket.isClosed())
                    System.err.println("[ERRO] Falha ao aceitar nova conexão de chat: " + e.getMessage());
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
            // Acontece quando o outro peer se desconecta
        } finally {
            connections.remove(pc);
            String statusMessage = "[SISTEMA] Conexão com " + pc.socket.getRemoteSocketAddress() + " foi encerrada.";
            System.out.println(statusMessage);
            onMessageCallback.accept(statusMessage);
            pc.close();
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

    public boolean connectToPeer(String host, int port) {
        try {
            Socket socket = new Socket(host, port);
            PeerConnection pc = new PeerConnection(socket);
            pc.out.println("/id:" + this.peerId);

            String response = pc.in.readLine();
            if (response != null && response.startsWith("/id:")) {
                pc.remotePeerId = response.substring(4).trim();
                System.out.println("[INFO] Recebido peerId remoto: " + pc.remotePeerId);
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
            return true;
        } catch (IOException e) {
            System.err.println("[ERRO] Falha ao conectar ao peer " + host + ":" + port + ". Motivo: " + e.getMessage());
            return false;
        }
    }

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
                // Silencioso
            }
        }
    }
}