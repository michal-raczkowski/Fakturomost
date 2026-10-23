// fakturomost-desktop/src/test/java/.../desktop/NoListeningSocketsTest.java
package dev.michalraczkowski.fakturomost.desktop;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.springframework.context.ConfigurableApplicationContext;

@EnabledOnOs(OS.LINUX)
class NoListeningSocketsTest {

    @Test
    void detectorSeesAServerSocketOpenedByThisProcess() throws IOException {
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            assertThat(ListeningSockets.ofCurrentProcess())
                    .anyMatch(s -> s.port() == server.getLocalPort());
        }
    }

    @Test
    void desktopContextOpensNoListeningSockets() throws IOException {
        Set<ListeningSockets.Socket> before = ListeningSockets.ofCurrentProcess();

        try (ConfigurableApplicationContext context = DesktopContext.start()) {
            Set<ListeningSockets.Socket> opened = new HashSet<>(ListeningSockets.ofCurrentProcess());
            opened.removeAll(before);

            assertThat(opened)
                    .as("listening sockets opened by the desktop Spring context")
                    .isEmpty();
        }
    }
}