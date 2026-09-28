package ke.bscodes.possystemfortavuzigadgetsbackend.receipt;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * Sends receipt bytes to a network thermal printer (raw TCP, usually port 9100).
 * Shared module from BS Codes.
 */
public final class NetworkPrinter {

  private NetworkPrinter() {}

  public static void print(String host, int port, byte[] bytes) throws IOException {
    try (Socket socket = new Socket()) {
      socket.connect(new InetSocketAddress(host, port), 5000);
      socket.setSoTimeout(5000);
      OutputStream out = socket.getOutputStream();
      out.write(bytes);
      out.flush();
    }
  }
}
