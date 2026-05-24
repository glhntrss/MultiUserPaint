package server;

import protocol.MupProtocol;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class ClientHandler implements Runnable {

    private static final long PING_INTERVAL_MS = 30_000;
    private static final long PONG_TIMEOUT_MS = 10_000;

    private static final AtomicInteger SESSION_COUNTER = new AtomicInteger(1);
    private static final AtomicInteger FILE_COUNTER = new AtomicInteger(103);

    private static final Set<String> ACTIVE_USERS = Collections.synchronizedSet(new HashSet<>());

    private static final List<DrawingFile> SHARED_FILES = Collections.synchronizedList(new ArrayList<>());

    static {
        SHARED_FILES.add(new DrawingFile(101, "tasarim", 800, 600));
        SHARED_FILES.add(new DrawingFile(102, "manzara", 800, 600));
    }

    private final Socket socket;
    private BufferedReader reader;
    private BufferedWriter writer;

    private int sessionId;
    private String username;
    private boolean joined = false;

    private final Set<Integer> openFileIds = new HashSet<>();

    private final AtomicBoolean running = new AtomicBoolean(true);
    private volatile boolean waitingForPong = false;
    private volatile long pingSentAt = 0;

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try {
            reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), "UTF-8"));

            writer = new BufferedWriter(
                    new OutputStreamWriter(socket.getOutputStream(), "UTF-8"));

            handleClient();

        } catch (IOException e) {
            System.out.println("Client bağlantı hatası: " + e.getMessage());
        } finally {
            removeFromOpenFiles();
            removeUser();
            closeConnection();
        }
    }

    private void handleClient() throws IOException {
        String line = reader.readLine();

        if (line == null) {
            return;
        }

        System.out.println("Gelen mesaj: " + line);

        if (line.startsWith(MupProtocol.JOIN + " ")) {
            handleJoin(line);
        } else {
            sendMessage(MupProtocol.ERR + " 101 JOIN yapılmadan komut gönderildi");
            closeConnection();
            return;
        }

        if (!joined) {
            return;
        }

        startPingThread();

        while ((line = reader.readLine()) != null) {
            System.out.println("[" + username + "] mesaj: " + line.substring(0, Math.min(120, line.length())));

            if (line.equals(MupProtocol.LEAVE)) {
                System.out.println(username + " ayrıldı.");
                break;

            } else if (line.equals(MupProtocol.PONG)){
                handlePong();
                
            } else if (line.equals(MupProtocol.LIST)) {
                handleList();

            } else if (line.startsWith(MupProtocol.CREATE + " ")) {
                handleCreate(line);

            } else if (line.startsWith(MupProtocol.OPEN + " ")) {
                handleOpen(line);

            } else if (line.startsWith(MupProtocol.CLOSE + " ")) {
                handleClose(line);

            } else if (line.startsWith(MupProtocol.DRAW + " ")) {
                handleDraw(line);

            } else if (line.startsWith(MupProtocol.CUT + " ")) {
                handleCut(line);

            } else if (line.startsWith(MupProtocol.PASTE + " ")) {
                handlePaste(line);

            } else {
                sendMessage(MupProtocol.ERR + " 200 Bilinmeyen komut");
            }
        }
    }

    private void startPingThread() {
        Thread t = new Thread(() -> {
            try {
                while (running.get()) {
                    Thread.sleep(PING_INTERVAL_MS);
                    if (!running.get())
                        break;
                    waitingForPong = true;
                    pingSentAt = System.currentTimeMillis();
                    sendMessage(MupProtocol.PING);
                    System.out.println("[PING] → " + username);
                    long deadline = pingSentAt + PONG_TIMEOUT_MS;
                    while (waitingForPong && System.currentTimeMillis() < deadline) {
                        Thread.sleep(100);
                    }
                    if (waitingForPong) {
                        System.out.println("[PING] " + username + " PONG göndermedi, bağlantı kesiliyor.");
                        running.set(false);
                        closeConnection();
                        break;
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (IOException e) {
                // bağlantı zaten kopmuş
            }
        });
        t.setDaemon(true);
        t.start();
    }

    private void handlePong() {
        waitingForPong = false;
        System.out.println("[PONG] ← " + username + " (" + (System.currentTimeMillis() - pingSentAt) + " ms)");
    }

    private void handleJoin(String line) throws IOException {
        String[] parts = line.split(" ");

        if (parts.length != 2) {
            sendMessage(MupProtocol.REJECT + " Kullanıcı adı geçersiz");
            return;
        }

        String requestedUsername = parts[1].trim();

        if (requestedUsername.isEmpty() || requestedUsername.length() > 64) {
            sendMessage(MupProtocol.REJECT + " Kullanıcı adı 1-64 karakter olmalıdır");
            return;
        }

        synchronized (ACTIVE_USERS) {
            if (ACTIVE_USERS.contains(requestedUsername)) {
                sendMessage(MupProtocol.REJECT + " Kullanıcı adı zaten kullanılıyor");
                return;
            }

            ACTIVE_USERS.add(requestedUsername);
        }

        username = requestedUsername;
        joined = true;
        sessionId = SESSION_COUNTER.getAndIncrement();

        sendMessage(MupProtocol.WELCOME + " " + sessionId);

        System.out.println("Kullanıcı bağlandı: " + username + " | Session ID: " + sessionId);
        System.out.println("Aktif kullanıcılar: " + ACTIVE_USERS);
    }

    private void handleList() throws IOException {
        StringBuilder response = new StringBuilder();

        synchronized (SHARED_FILES) {
            response.append(MupProtocol.FILES)
                    .append(" ")
                    .append(SHARED_FILES.size());

            for (DrawingFile file : SHARED_FILES) {
                response.append(" ")
                        .append(file.getId())
                        .append(" ")
                        .append(file.getName());
            }
        }

        sendMessage(response.toString());
    }

    private void handleCreate(String line) throws IOException {
        String[] parts = line.split(" ");

        if (parts.length != 4) {
            sendMessage(MupProtocol.ERR + " 201 CREATE kullanımı: CREATE name width height");
            return;
        }

        String fileName = parts[1].trim();

        int width;
        int height;

        try {
            width = Integer.parseInt(parts[2]);
            height = Integer.parseInt(parts[3]);
        } catch (NumberFormatException e) {
            sendMessage(MupProtocol.ERR + " 201 Width ve height sayı olmalıdır");
            return;
        }

        if (fileName.isEmpty() || fileName.length() > 64) {
            sendMessage(MupProtocol.ERR + " 201 Dosya adı 1-64 karakter olmalıdır");
            return;
        }

        if (width <= 0 || height <= 0) {
            sendMessage(MupProtocol.ERR + " 201 Tuval boyutu pozitif olmalıdır");
            return;
        }

        synchronized (SHARED_FILES) {
            for (DrawingFile file : SHARED_FILES) {
                if (file.getName().equalsIgnoreCase(fileName)) {
                    sendMessage(MupProtocol.ERR + " 212 Dosya adı zaten kullanılıyor");
                    return;
                }
            }

            int newFileId = FILE_COUNTER.getAndIncrement();
            DrawingFile newFile = new DrawingFile(newFileId, fileName, width, height);
            SHARED_FILES.add(newFile);

            sendMessage(MupProtocol.CREATED + " "
                    + newFileId + " "
                    + fileName + " "
                    + width + " "
                    + height + " "
                    + username);

            System.out.println("Yeni dosya oluşturuldu: "
                    + newFileId + " | " + fileName + " | " + width + "x" + height);
        }
    }

    private void handleOpen(String line) throws IOException {
        String[] parts = line.split(" ");
        if (parts.length != 2) {
            sendMessage(MupProtocol.ERR + " 201 OPEN kullanımı: OPEN fileId");
            return;
        }

        int fileId;
        try {
            fileId = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            sendMessage(MupProtocol.ERR + " 201 fileId sayı olmalıdır");
            return;
        }

        DrawingFile selectedFile = findFileById(fileId);
        if (selectedFile == null) {
            sendMessage(MupProtocol.ERR + " 210 Belirtilen dosya bulunamadı");
            return;
        }

        openFileIds.add(fileId);

        broadcastToFileEditors(selectedFile, MupProtocol.USER_JOINED_FILE + " " + fileId + " " + username);
        selectedFile.addEditor(this);

        sendMessage(MupProtocol.OPENED + " " + fileId + " 0");
        List<String> oldOperations = selectedFile.getOperationsCopy();
        sendMessage(MupProtocol.STATE_BEGIN + " " + fileId + " " + oldOperations.size());
        for (String operation : oldOperations) {
            sendMessage(MupProtocol.STATE_OP + " " + fileId + " " + operation);
        }
        sendMessage(MupProtocol.STATE_END + " " + fileId);
    }

    private void handleClose(String line) throws IOException {
        String[] parts = line.split(" ");
        if (parts.length != 2) {
            sendMessage(MupProtocol.ERR + " 201 CLOSE kullanımı: CLOSE fileId");
            return;
        }

        int fileId;
        try {
            fileId = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            return;
        }

        DrawingFile file = findFileById(fileId);
        if (file != null) {
            file.removeEditor(this);
            broadcastToFileEditors(file, MupProtocol.USER_LEFT_FILE + " " + fileId + " " + username);
        }
        openFileIds.remove(fileId);
    }

    private void handleDraw(String line) throws IOException {
        String[] parts = line.split(" ");

        if (parts.length < 4) {
            sendMessage(MupProtocol.ERR + " 201 DRAW formatı hatalı");
            return;
        }

        int fileId;

        try {
            fileId = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            sendMessage(MupProtocol.ERR + " 201 fileId sayı olmalıdır");
            return;
        }

        if (!openFileIds.contains(fileId)) {
            sendMessage(MupProtocol.ERR + " 211 Dosya açık değil");
            return;
        }

        DrawingFile selectedFile = findFileById(fileId);

        if (selectedFile == null) {
            sendMessage(MupProtocol.ERR + " 210 Belirtilen dosya bulunamadı");
            return;
        }

        String tool = parts[2];

        if (!isSupportedTool(tool)) {
            sendMessage(MupProtocol.ERR + " 201 Desteklenmeyen çizim aracı");
            return;
        }

        String args = getArgsAfterTool(parts);

        String broadcastMessage = selectedFile.createDrawBroadcast(username, tool, args);

        broadcastToFileEditors(selectedFile, broadcastMessage);

        System.out.println("Çizim olayı yayınlandı: " + broadcastMessage);
        checkAutoSave(selectedFile);
    }

    private void handleCut(String line) throws IOException {
        String[] parts = line.split(" ");
        if (parts.length != 6) {
            sendMessage(MupProtocol.ERR + " 201 CUT kullanımı: CUT fileId x y w h");
            return;
        }

        int fileId, x, y, w, h;
        try {
            fileId = Integer.parseInt(parts[1]);
            x = Integer.parseInt(parts[2]);
            y = Integer.parseInt(parts[3]);
            w = Integer.parseInt(parts[4]);
            h = Integer.parseInt(parts[5]);
        } catch (NumberFormatException e) {
            sendMessage(MupProtocol.ERR + " 201 CUT argümanları sayı olmalı");
            return;
        }

        if (!openFileIds.contains(fileId)) {
            sendMessage(MupProtocol.ERR + " 211 Dosya açık değil");
            return;
        }
        DrawingFile file = findFileById(fileId);
        if (file == null) {
            sendMessage(MupProtocol.ERR + " 210 Dosya bulunamadı");
            return;
        }

        String bcast = file.createCutBroadcast(username, x, y, w, h);
        broadcastToFileEditors(file, bcast);
        checkAutoSave(file); // Auto-save kontrolü
    }

    private void handlePaste(String line) throws IOException {
        String[] parts = line.split(" ", 8);
        if (parts.length != 8) {
            sendMessage(MupProtocol.ERR + " 201 PASTE formatı hatalı");
            return;
        }

        int fileId, x, y, w, h, pxCount;
        try {
            fileId = Integer.parseInt(parts[1]);
            x = Integer.parseInt(parts[2]);
            y = Integer.parseInt(parts[3]);
            w = Integer.parseInt(parts[4]);
            h = Integer.parseInt(parts[5]);
            pxCount = Integer.parseInt(parts[6]);
        } catch (NumberFormatException e) {
            sendMessage(MupProtocol.ERR + " 201 PASTE argümanları sayı olmalı");
            return;
        }

        if (!openFileIds.contains(fileId)) {
            sendMessage(MupProtocol.ERR + " 211 Dosya açık değil");
            return;
        }
        DrawingFile file = findFileById(fileId);
        if (file == null) {
            sendMessage(MupProtocol.ERR + " 210 Dosya bulunamadı");
            return;
        }

        if (pxCount != w * h) {
            sendMessage(MupProtocol.ERR + " 201 PASTE pxCount w*h değerine eşit olmalıdır");
            return;
        }

        String pixelData = parts[7];
        String bcast = file.createPasteBroadcast(username, x, y, w, h, pixelData);
        broadcastToFileEditors(file, bcast);
        checkAutoSave(file); // Auto-save kontrolü
    }

    private void checkAutoSave(DrawingFile file) {
        if (file.checkAndResetAutoSave()) {
            long timestamp = System.currentTimeMillis() / 1000;
            broadcastToFileEditors(file, MupProtocol.SAVE_ACK + " " + file.getId() + " " + timestamp);
        }
    }

    private boolean isSupportedTool(String tool) {
        return tool.equals("LINE")
                || tool.equals("RECT")
                || tool.equals("CIRCLE")
                || tool.equals("FREEHAND")
                || tool.equals("ERASE");
    }

    private String getArgsAfterTool(String[] parts) {
        StringBuilder builder = new StringBuilder();

        for (int i = 3; i < parts.length; i++) {
            if (i > 3) {
                builder.append(" ");
            }
            builder.append(parts[i]);
        }

        return builder.toString();
    }

    private DrawingFile findFileById(int fileId) {
        synchronized (SHARED_FILES) {
            for (DrawingFile file : SHARED_FILES) {
                if (file.getId() == fileId) {
                    return file;
                }
            }
        }

        return null;
    }

    private void broadcastToFileEditors(DrawingFile file, String message) {
        Set<ClientHandler> editors = file.getEditorsCopy();

        for (ClientHandler editor : editors) {
            try {
                editor.sendMessage(message);
            } catch (IOException e) {
                System.out.println("Broadcast gönderilemedi: " + e.getMessage());
            }
        }
    }

    private void removeFromOpenFiles() {
        synchronized (SHARED_FILES) {
            for (DrawingFile file : SHARED_FILES) {
                if (file.getEditorsCopy().contains(this)) {
                    file.removeEditor(this);
                    if (username != null) {
                        broadcastToFileEditors(file, MupProtocol.USER_LEFT_FILE + " " + file.getId() + " " + username);
                    }
                }
            }
        }
    }

    private void removeUser() {
        if (joined && username != null) {
            ACTIVE_USERS.remove(username);
            System.out.println(username + " aktif kullanıcı listesinden çıkarıldı.");
            System.out.println("Aktif kullanıcılar: " + ACTIVE_USERS);
        }
    }

    public synchronized void sendMessage(String message) throws IOException {
        writer.write(message + MupProtocol.CRLF);
        writer.flush();
        System.out.println("Gönderilen mesaj: " + message);
    }

    private void closeConnection() {
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            System.out.println("Bağlantı kapatılırken hata oluştu: " + e.getMessage());
        }
    }
}