package org.unifor.p2p;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * Gera e persiste um identificador único para este peer.
 */
public class PeerIdentity {
    private static final String FILE_NAME = "peer_id.txt";

    public static String getPeerId(String userName) throws IOException {
        Path path = Paths.get(FILE_NAME, userName + ".id");
        if (Files.exists(path)) {
            return Files.readString(path).trim();
        }
        return null;
    }

    public static String createPeerId(String userName) throws IOException {
        Path path = Paths.get(FILE_NAME, userName + ".id");

        if (!Files.exists(path)) {
            String newId = UUID.randomUUID().toString();
            Files.createDirectories(path.getParent());
            Files.writeString(path, newId);
            return newId;
        } else {
            return Files.readString(path).trim();
        }
    }
}
