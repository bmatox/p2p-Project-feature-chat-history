package org.unifor.p2p;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;


public class ChatHistory {

    private static final String HISTORY_DIR = "chat_history";
    private final Path userHistoryDir;

    public ChatHistory(String localPeerId) throws IOException {
        this.userHistoryDir = Paths.get(HISTORY_DIR, localPeerId);
        Files.createDirectories(userHistoryDir);
    }

    /**
     * Salva uma mensagem no histórico do par local x remoto
     */
    public void saveMessage(String remotePeerId, String message) {
        try {
            Path file = userHistoryDir.resolve(remotePeerId + ".txt");
            Files.writeString(file, message + System.lineSeparator(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.err.println("[ERRO] Falha ao salvar mensagem no histórico: " + e.getMessage());
        }
    }

    /**
     * Carrega todo o histórico do par local x remoto
     */
    public List<String> loadHistory(String remotePeerId) {
        List<String> messages = new ArrayList<>();
        Path file = userHistoryDir.resolve(remotePeerId + ".txt");

        if (Files.exists(file)) {
            try (BufferedReader reader = Files.newBufferedReader(file)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    messages.add(line);
                }
            } catch (IOException e) {
                System.err.println("[ERRO] Falha ao carregar histórico: " + e.getMessage());
            }
        }
        return messages;
    }
}
