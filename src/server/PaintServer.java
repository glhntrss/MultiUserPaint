package server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class PaintServer {

    public static final int PORT = 9090;

    public static void main(String[] args) {
        System.out.println("MUP Paint Server başlatılıyor...");
        System.out.println("Port: " + PORT);

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Server hazır. Client bağlantısı bekleniyor...");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Yeni client bağlandı: " + clientSocket.getInetAddress());

                ClientHandler handler = new ClientHandler(clientSocket);
                Thread thread = new Thread(handler);
                thread.start();
            }

        } catch (IOException e) {
            System.out.println("Server hatası: " + e.getMessage());
            e.printStackTrace();
        }
    }
}