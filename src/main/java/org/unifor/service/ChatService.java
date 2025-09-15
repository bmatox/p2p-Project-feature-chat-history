package org.unifor.service;

import org.unifor.p2p.Peer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class ChatService {

    private final List<String> messages = new CopyOnWriteArrayList<>();
    private Peer peer;

    @Value("${p2p.username}")
    private String userName;

    @Value("${p2p.port}")
    private int port;

    @PostConstruct
    private void init() {
        try {
            // A lógica de inicialização do Peer com o callback permanece a mesma.
            peer = new Peer(userName, port, this::onMessageReceived);
            peer.start();
        } catch (IOException e) {
            e.printStackTrace();
            addMessage("[ERRO CRÍTICO] Falha ao iniciar o Peer na porta " + port);
        }
    }

    /**
     * Método chamado pelo Peer sempre que uma nova mensagem (chat ou status) é recebida.
     */
    public void onMessageReceived(String message) {
        messages.add(message);
    }

    /**
     * Adiciona uma mensagem diretamente à lista (usado para erros de sistema).
     */
    public void addMessage(String message) {
        messages.add(message);
    }

    /**
     * Envia uma mensagem de chat para a rede.
     */
    public void sendMessage(String message) {
        if (peer != null) {
            peer.broadcastMessage(message);
        }
    }

    /**
     * ALTERADO: Tenta se conectar a outro peer e retorna o status da operação.
     * @param host O IP do peer de destino.
     * @param port A porta do peer de destino.
     * @return true se a conexão foi bem-sucedida, false caso contrário.
     */
    public boolean connectToPeer(String host, int port) {
        if (peer != null) {
            // Retorna o resultado booleano vindo diretamente da camada P2P.
            return peer.connectToPeer(host, port);
        }
        return false; // Retorna falso se o peer não foi inicializado.
    }

    /**
     * Retorna a lista de mensagens para ser exibida na UI.
     */
    public List<String> getMessages() {
        return Collections.unmodifiableList(messages);
    }
}