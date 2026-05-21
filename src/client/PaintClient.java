package client;

import protocol.MupProtocol;

import java.io.*;
import java.net.Socket;
import java.util.Scanner;

public class PaintClient {

    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 9090;

    private static volatile boolean running = true;
    private static volatile String activeFileId = null;

    public static void main(String[] args) {

        try (
                Scanner scanner = new Scanner(System.in);
                Socket socket = new Socket(SERVER_HOST, SERVER_PORT);
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(socket.getInputStream(), "UTF-8")
                );
                BufferedWriter writer = new BufferedWriter(
                        new OutputStreamWriter(socket.getOutputStream(), "UTF-8")
                )
        ) {
            System.out.print("Kullanıcı adınızı girin: ");
            String username = scanner.nextLine();

            send(writer, MupProtocol.JOIN + " " + username);

            String firstResponse = reader.readLine();

            if (firstResponse == null) {
                System.out.println("Server yanıt vermedi.");
                return;
            }

            System.out.println("Server yanıtı: " + firstResponse);

            if (firstResponse.startsWith(MupProtocol.REJECT)) {
                System.out.println("Bağlantı reddedildi.");
                return;
            }

            if (!firstResponse.startsWith(MupProtocol.WELCOME)) {
                System.out.println("Beklenmeyen yanıt: " + firstResponse);
                return;
            }

            System.out.println("Bağlantı başarılı!");

            Thread listenerThread = new Thread(() -> listenServer(reader));
            listenerThread.setDaemon(true);
            listenerThread.start();

            showMenu(scanner, writer);

            running = false;
            send(writer, MupProtocol.LEAVE);

        } catch (IOException e) {
            System.out.println("Client hatası: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void listenServer(BufferedReader reader) {
        try {
            String line;

            while (running && (line = reader.readLine()) != null) {
                handleServerMessage(line);
            }

        } catch (IOException e) {
            if (running) {
                System.out.println("Server dinlenirken hata oluştu: " + e.getMessage());
            }
        }
    }

    private static void handleServerMessage(String message) {
        System.out.println();
        System.out.println("[SERVER] " + message);

        if (message.startsWith(MupProtocol.FILES)) {
            printFiles(message);

        } else if (message.startsWith(MupProtocol.CREATED)) {
            System.out.println("Yeni dosya oluşturuldu.");

        } else if (message.startsWith(MupProtocol.OPENED)) {
            String[] parts = message.split(" ");
            if (parts.length >= 2) {
                activeFileId = parts[1];
                System.out.println("Dosya açıldı. Aktif dosya ID: " + activeFileId);
            }

        } else if (message.startsWith(MupProtocol.STATE_BEGIN)) {
            System.out.println("Tuval durumu aktarımı başladı.");

        } else if (message.startsWith(MupProtocol.STATE_OP)) {
            System.out.println("Geçmiş çizim olayı alındı.");

        } else if (message.startsWith(MupProtocol.STATE_END)) {
            System.out.println("Tuval hazır.");

        } else if (message.startsWith(MupProtocol.DRAW_BCAST)) {
            System.out.println("Çizim olayı alındı.");

        } else if (message.startsWith(MupProtocol.ERR)) {
            System.out.println("Hata mesajı alındı.");
        }
    }

    private static void showMenu(Scanner scanner, BufferedWriter writer) throws IOException {
        while (running) {
            System.out.println();
            System.out.println("===== MUP Client Menü =====");

            if (activeFileId == null) {
                System.out.println("Aktif dosya: Yok");
            } else {
                System.out.println("Aktif dosya: " + activeFileId);
            }

            System.out.println("1 - Dosya listesini getir");
            System.out.println("2 - Yeni dosya oluştur");
            System.out.println("3 - Dosya aç");
            System.out.println("4 - Aktif dosyaya örnek çizgi çiz");
            System.out.println("5 - Çıkış");
            System.out.print("Komut seç: ");

            String choice = scanner.nextLine();

            switch (choice) {
                case "1":
                    send(writer, MupProtocol.LIST);
                    break;

                case "2":
                    createFile(scanner, writer);
                    break;

                case "3":
                    openFile(scanner, writer);
                    break;

                case "4":
                    sendDraw(writer);
                    break;

                case "5":
                    running = false;
                    return;

                default:
                    System.out.println("Geçersiz seçim.");
                    break;
            }
        }
    }

    private static void createFile(Scanner scanner, BufferedWriter writer) throws IOException {
        System.out.print("Dosya adı: ");
        String fileName = scanner.nextLine();

        System.out.print("Genişlik: ");
        String width = scanner.nextLine();

        System.out.print("Yükseklik: ");
        String height = scanner.nextLine();

        send(writer, MupProtocol.CREATE + " " + fileName + " " + width + " " + height);
    }

    private static void openFile(Scanner scanner, BufferedWriter writer) throws IOException {
        System.out.print("Açmak istediğin dosya ID'si: ");
        String fileId = scanner.nextLine();

        send(writer, MupProtocol.OPEN + " " + fileId);
    }

    private static void sendDraw(BufferedWriter writer) throws IOException {
        if (activeFileId == null) {
            System.out.println("Önce bir dosya açmalısın. Menüden 3 ile dosya aç.");
            return;
        }

        String drawMessage = MupProtocol.DRAW + " "
                + activeFileId
                + " LINE 10 10 200 200 FF0000 3";

        send(writer, drawMessage);
    }

    private static void send(BufferedWriter writer, String message) throws IOException {
        writer.write(message + MupProtocol.CRLF);
        writer.flush();

        System.out.println("[CLIENT] Gönderildi: " + message);
    }

    private static void printFiles(String fileResponse) {
        String[] parts = fileResponse.split(" ");

        if (parts.length < 2) {
            System.out.println("Dosya listesi formatı hatalı.");
            return;
        }

        int count;

        try {
            count = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            System.out.println("Dosya sayısı okunamadı.");
            return;
        }

        if (count == 0) {
            System.out.println("Paylaşılan dosya yok.");
            return;
        }

        System.out.println("Paylaşılan dosyalar:");

        int index = 2;

        for (int i = 0; i < count; i++) {
            if (index + 1 >= parts.length) {
                System.out.println("Dosya listesi eksik geldi.");
                return;
            }

            String fileId = parts[index];
            String fileName = parts[index + 1];

            System.out.println("- ID: " + fileId + " | Ad: " + fileName);

            index += 2;
        }
    }
}