// fakturomost-desktop/src/test/java/.../desktop/ListeningSockets.java
package dev.michalraczkowski.fakturomost.desktop;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Linux-only: lists sockets of the current JVM that accept incoming traffic. */
final class ListeningSockets {

    record Socket(String protocol, String localAddressHex, int port) {
    }

    private static final String TCP_LISTEN = "0A";

    private ListeningSockets() {
    }

    static Set<Socket> ofCurrentProcess() throws IOException {
        Set<String> ownInodes = socketInodesOfCurrentProcess();
        Set<Socket> result = new HashSet<>();
        for (String protocol : List.of("tcp", "tcp6", "udp", "udp6")) {
            Path table = Path.of("/proc/net", protocol);
            if (!Files.exists(table)) {
                continue; // e.g. IPv6 disabled
            }
            List<String> lines = Files.readAllLines(table);
            for (String line : lines.subList(1, lines.size())) { // skip header
                String[] f = line.trim().split("\\s+");
                String local = f[1];
                String state = f[3];
                String inode = f[9];
                boolean listening = protocol.startsWith("udp") || state.equals(TCP_LISTEN);
                if (listening && ownInodes.contains(inode)) {
                    int port = Integer.parseInt(local.substring(local.indexOf(':') + 1), 16);
                    result.add(new Socket(protocol, local, port));
                }
            }
        }
        return result;
    }

    /** /proc/net/* covers the whole network namespace; /proc/self/fd narrows it to this JVM. */
    private static Set<String> socketInodesOfCurrentProcess() throws IOException {
        Set<String> inodes = new HashSet<>();
        try (DirectoryStream<Path> fds = Files.newDirectoryStream(Path.of("/proc/self/fd"))) {
            for (Path fd : fds) {
                try {
                    String target = Files.readSymbolicLink(fd).toString();
                    if (target.startsWith("socket:[")) {
                        inodes.add(target.substring("socket:[".length(), target.length() - 1));
                    }
                } catch (IOException closedMeanwhile) {
                    // another thread closed this descriptor while we iterated
                }
            }
        }
        return inodes;
    }
}