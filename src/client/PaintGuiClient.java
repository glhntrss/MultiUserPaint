package client;

import protocol.MupProtocol;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.Socket;

public class PaintGuiClient extends JFrame {

    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 9090;

    private Socket socket;
    private BufferedReader reader;
    private BufferedWriter writer;

    private String username;
    private Integer activeFileId = null;

    private final DefaultListModel<FileItem> fileListModel = new DefaultListModel<>();
    private final JList<FileItem> fileList = new JList<>(fileListModel);
    private final CanvasPanel canvasPanel = new CanvasPanel();

    private final JLabel connectionLabel = new JLabel("Bağlantı yok");
    private final JLabel activeFileLabel = new JLabel("Aktif dosya: Yok");

    private Color selectedColor = Color.BLACK;
    private int selectedThickness = 3;
    private String selectedTool = "LINE";

    private Point lastPoint = null;

    private ClipboardData clipboard = null;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new PaintGuiClient().setVisible(true));
    }

    public PaintGuiClient() {
        setTitle("MUP Multi-User Paint Client");
        setSize(1100, 750);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        buildInterface();
        askUsernameAndConnect();
    }

    private void buildInterface() {
        setLayout(new BorderLayout());

        // --- Üst panel ---
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));

        JButton listButton = new JButton("Dosya Listesi");
        JButton createButton = new JButton("Yeni Dosya");
        JButton openButton = new JButton("Dosya Aç");
        JButton colorButton = new JButton("Renk Seç");
        JButton clearButton = new JButton("Ekranı Temizle");

        // CKY butonları
        JButton cutButton = new JButton("Kes (Ctrl+X)");
        JButton copyButton = new JButton("Kopyala (Ctrl+C)");
        JButton pasteButton = new JButton("Yapıştır (Ctrl+V)");

        String[] toolOptions = { "Kalem", "Silgi", "Seçim" };
        JComboBox<String> toolCombo = new JComboBox<>(toolOptions);

        String[] thicknessOptions = { "1", "2", "3", "5", "8", "12" };
        JComboBox<String> thicknessCombo = new JComboBox<>(thicknessOptions);
        thicknessCombo.setSelectedItem("3");

        topPanel.add(listButton);
        topPanel.add(createButton);
        topPanel.add(openButton);
        topPanel.add(new JSeparator(SwingConstants.VERTICAL));
        topPanel.add(new JLabel("Araç:"));
        topPanel.add(toolCombo);
        topPanel.add(colorButton);
        topPanel.add(new JLabel("Kalınlık:"));
        topPanel.add(thicknessCombo);
        topPanel.add(clearButton);
        topPanel.add(new JSeparator(SwingConstants.VERTICAL));
        topPanel.add(cutButton);
        topPanel.add(copyButton);
        topPanel.add(pasteButton);
        topPanel.add(connectionLabel);
        topPanel.add(activeFileLabel);
        add(topPanel, BorderLayout.NORTH);

        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.setPreferredSize(new Dimension(220, 0));
        leftPanel.add(new JLabel("Paylaşılan Dosyalar"), BorderLayout.NORTH);
        leftPanel.add(new JScrollPane(fileList), BorderLayout.CENTER);
        add(leftPanel, BorderLayout.WEST);

        add(canvasPanel, BorderLayout.CENTER);

        JTextArea infoArea = new JTextArea();
        infoArea.setEditable(false);
        infoArea.setRows(3);
        infoArea.setText(
                "Kullanım: 1) Dosya Listesi → Dosya Aç  2) Kalem ile çizim yap\n" +
                        "Kes/Kopyala: 'Seçim' aracını seç → sürükle → Kes veya Kopyala\n" +
                        "Yapıştır: Kopyaladıktan sonra Yapıştır'a bas, tuvalde tıklayarak konumlandır");
        add(new JScrollPane(infoArea), BorderLayout.SOUTH);

        listButton.addActionListener(e -> sendListRequest());
        createButton.addActionListener(e -> createFile());
        openButton.addActionListener(e -> openSelectedFile());
        clearButton.addActionListener(e -> canvasPanel.clearCanvas());

        colorButton.addActionListener(e -> {
            Color c = JColorChooser.showDialog(this, "Renk Seç", selectedColor);
            if (c != null)
                selectedColor = c;
        });

        toolCombo.addActionListener(e -> {
            String sel = (String) toolCombo.getSelectedItem();
            if ("Silgi".equals(sel))
                selectedTool = "ERASE";
            else if ("Seçim".equals(sel))
                selectedTool = "SELECT";
            else
                selectedTool = "LINE";

            if (!"SELECT".equals(selectedTool))
                canvasPanel.clearSelection();
        });

        thicknessCombo.addActionListener(e -> {
            selectedThickness = Integer.parseInt((String) thicknessCombo.getSelectedItem());
        });

        cutButton.addActionListener(e -> handleCut());
        copyButton.addActionListener(e -> handleCopy());
        pasteButton.addActionListener(e -> handlePaste());

        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke("ctrl X"), "cut");
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke("ctrl C"), "copy");
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke("ctrl V"), "paste");
        getRootPane().getActionMap().put("cut", new AbstractAction() {
            public void actionPerformed(java.awt.event.ActionEvent e) {
                handleCut();
            }
        });
        getRootPane().getActionMap().put("copy", new AbstractAction() {
            public void actionPerformed(java.awt.event.ActionEvent e) {
                handleCopy();
            }
        });
        getRootPane().getActionMap().put("paste", new AbstractAction() {
            public void actionPerformed(java.awt.event.ActionEvent e) {
                handlePaste();
            }
        });

        canvasPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if ("SELECT".equals(selectedTool)) {
                    // Yapıştır önizlemesi aktifse tıklama = yapıştırma konumunu onayla
                    if (canvasPanel.hasPastePreview()) {
                        confirmPaste(e.getX(), e.getY());
                        return;
                    }
                    canvasPanel.startSelection(e.getX(), e.getY());
                } else {
                    lastPoint = e.getPoint();
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (!"SELECT".equals(selectedTool)) {
                    lastPoint = null;
                }
            }
        });

        canvasPanel.addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if ("SELECT".equals(selectedTool)) {
                    if (canvasPanel.hasPastePreview()) {
                        canvasPanel.movePastePreview(e.getX(), e.getY());
                    } else {
                        canvasPanel.updateSelection(e.getX(), e.getY());
                    }
                    return;
                }

                if (activeFileId == null) {
                    JOptionPane.showMessageDialog(PaintGuiClient.this, "Önce bir dosya aç.");
                    return;
                }

                if (lastPoint == null) {
                    lastPoint = e.getPoint();
                    return;
                }
                Point cur = e.getPoint();
                sendDrawLine(activeFileId, lastPoint.x, lastPoint.y, cur.x, cur.y);
                lastPoint = cur;
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                // Paste önizlemesi farenin altında hareket etsin
                if (canvasPanel.hasPastePreview()) {
                    canvasPanel.movePastePreview(e.getX(), e.getY());
                }
            }
        });
    }

    private void handleCut() {
        if (activeFileId == null) {
            JOptionPane.showMessageDialog(this, "Önce bir dosya aç.");
            return;
        }
        Rectangle sel = canvasPanel.getSelectionRect();
        if (sel == null || sel.width == 0 || sel.height == 0) {
            JOptionPane.showMessageDialog(this, "Önce 'Seçim' aracıyla bir alan seç.");
            return;
        }

        BufferedImage captured = canvasPanel.captureRegion(sel.x, sel.y, sel.width, sel.height);
        if (captured == null)
            return;

        clipboard = new ClipboardData(ClipboardData.Operation.CUT,
                captured, sel.x, sel.y, sel.width, sel.height);

        send(MupProtocol.CUT + " " + activeFileId
                + " " + sel.x + " " + sel.y
                + " " + sel.width + " " + sel.height);

        canvasPanel.clearSelection();
    }

    private void handleCopy() {
        if (activeFileId == null || canvasPanel.getSelectionRect() == null)
            return;
        Rectangle sel = canvasPanel.getSelectionRect();
        BufferedImage captured = canvasPanel.captureRegion(sel.x, sel.y, sel.width, sel.height);
        if (captured == null)
            return;

        clipboard = new ClipboardData(ClipboardData.Operation.COPY, captured, sel.x, sel.y, sel.width, sel.height);

        canvasPanel.clearSelection();
        JOptionPane.showMessageDialog(this, "Kopyalandı (" + sel.width + "x" + sel.height
                + " px).\n'Yapıştır' butonuna bas, ardından tuvalde konumlandır.");
    }

    private void handlePaste() {
        if (clipboard == null) {
            JOptionPane.showMessageDialog(this, "Panoda kopyalanmış içerik yok.");
            return;
        }
        if (activeFileId == null) {
            JOptionPane.showMessageDialog(this, "Önce bir dosya aç.");
            return;
        }

        selectedTool = "SELECT";
        canvasPanel.showPastePreview(clipboard.getImage(), 0, 0);
        JOptionPane.showMessageDialog(this, "Tuvale tıklayarak yapıştır konumunu seç.");
    }

    private void confirmPaste(int x, int y) {
        if (clipboard == null || activeFileId == null)
            return;
        BufferedImage img = clipboard.getImage();
        int w = img.getWidth();
        int h = img.getHeight();


        int maxPxPerMessage = 400;

        for (int py = 0; py < h; py++) {
            for (int px = 0; px < w; px += maxPxPerMessage) {
                int chunkW = Math.min(maxPxPerMessage, w - px);
                StringBuilder pixels = new StringBuilder();

                for (int i = 0; i < chunkW; i++) {
                    int rgb = img.getRGB(px + i, py) & 0xFFFFFF;
                    pixels.append(String.format("%06X", rgb));
                    if (i < chunkW - 1)
                        pixels.append(" ");
                }

                send(MupProtocol.PASTE + " " + activeFileId + " " + (x + px) + " " + (y + py)
                        + " " + chunkW + " 1 " + chunkW + " " + pixels.toString());
            }
        }
        canvasPanel.clearPastePreview();
    }

    private void handleServerMessage(String message) {
        System.out.println("[SERVER] " + message);

        if (message.startsWith(MupProtocol.FILES)) {
            updateFileList(message);

        } else if (message.equals(MupProtocol.PING)){
            send(MupProtocol.PONG);

        } else if (message.startsWith(MupProtocol.REGION_BCAST)) {
            handleRegionBroadcast(message);

        } else if (message.startsWith(MupProtocol.SAVE_ACK)) {
            System.out.println("Dosya otomatik olarak kaydedildi.");

        } else if (message.startsWith(MupProtocol.USER_JOINED_FILE) || message.startsWith(MupProtocol.USER_LEFT_FILE)) {
            System.out.println("Sistem Bildirimi: " + message);

        } else if (message.startsWith(MupProtocol.CREATED)) {
            JOptionPane.showMessageDialog(this, "Yeni dosya oluşturuldu.");
            sendListRequest();

        } else if (message.startsWith(MupProtocol.OPENED)) {
            handleOpened(message);

        } else if (message.startsWith(MupProtocol.STATE_BEGIN)) {
            canvasPanel.clearCanvas();

        } else if (message.startsWith(MupProtocol.STATE_OP)) {
            handleStateOp(message);

        } else if (message.startsWith(MupProtocol.STATE_END)) {
            System.out.println("Tuval hazır.");

        } else if (message.startsWith(MupProtocol.DRAW_BCAST)) {
            handleDrawBroadcast(message);

        } else if (message.startsWith(MupProtocol.ERR)) {
            JOptionPane.showMessageDialog(this, "Server hatası: " + message);
        }
    }

    private void updateFileList(String message) {
        fileListModel.clear();
        String[] parts = message.split(" ");
        if (parts.length < 2)
            return;
        int count;
        try {
            count = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            return;
        }
        int index = 2;
        for (int i = 0; i < count; i++) {
            if (index + 1 >= parts.length)
                return;
            int fileId = Integer.parseInt(parts[index]);
            String fileName = parts[index + 1];
            fileListModel.addElement(new FileItem(fileId, fileName));
            index += 2;
        }
    }

    private void createFile() {
        JPanel panel = new JPanel(new GridLayout(3, 2));
        JTextField nameField = new JTextField("deneme");
        JTextField widthField = new JTextField("800");
        JTextField heightField = new JTextField("600");
        panel.add(new JLabel("Dosya adı:"));
        panel.add(nameField);
        panel.add(new JLabel("Genişlik:"));
        panel.add(widthField);
        panel.add(new JLabel("Yükseklik:"));
        panel.add(heightField);
        int result = JOptionPane.showConfirmDialog(this, panel, "Yeni Dosya Oluştur",
                JOptionPane.OK_CANCEL_OPTION);
        if (result == JOptionPane.OK_OPTION) {
            send(MupProtocol.CREATE + " "
                    + nameField.getText().trim() + " "
                    + widthField.getText().trim() + " "
                    + heightField.getText().trim());
        }
    }

    private void openSelectedFile() {
        FileItem sel = fileList.getSelectedValue();
        if (sel == null) {
            JOptionPane.showMessageDialog(this, "Listeden bir dosya seç.");
            return;
        }
        send(MupProtocol.OPEN + " " + sel.getId());
    }

    private void handleOpened(String message) {
        String[] parts = message.split(" ");
        if (parts.length >= 2) {
            activeFileId = Integer.parseInt(parts[1]);
            activeFileLabel.setText("Aktif dosya: " + activeFileId);
            canvasPanel.clearCanvas();
        }
    }

    private void handleStateOp(String message) {
        String[] parts = message.split(" ", 3);
        if (parts.length < 3)
            return;
        String embedded = parts[2];
        if (embedded.startsWith(MupProtocol.DRAW_BCAST)) {
            handleDrawBroadcast(embedded);
        } else if (embedded.startsWith(MupProtocol.REGION_BCAST)) {
            handleRegionBroadcast(embedded);
        }
    }

    private void handleRegionBroadcast(String message) {
        String[] p = message.split(" ", 10);
        if (p.length < 8) return;
        
        try {
            String subCmd = p[4];
            int x = Integer.parseInt(p[5]);
            int y = Integer.parseInt(p[6]);
            int w = Integer.parseInt(p[7]);
            int h = Integer.parseInt(p[8]);

            if ("CUT".equals(subCmd)) {
                canvasPanel.eraseRegion(x, y, w, h);
            } else if ("PASTE".equals(subCmd)) {
                if (p.length < 10) return;
                
                String pixelData = p[9];
                String[] hexValues = pixelData.split(" ");
                
                BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
                for (int py = 0; py < h; py++) {
                    for (int px = 0; px < w; px++) {
                        if (py * w + px >= hexValues.length) break; 
                        
                        int rgb = (int) Long.parseLong(hexValues[py * w + px], 16);
                        img.setRGB(px, py, 0xFF000000 | rgb);
                    }
                }
                canvasPanel.applyPaste(img, x, y);
            }
        } catch (Exception e) {
            System.out.println("REGION_BCAST parse hatası: " + e.getMessage());
        }
    }

    private void handleDrawBroadcast(String message) {
        String[] parts = message.split(" ");
        if (parts.length < 10)
            return;
        String tool = parts[4];
        try {
            if ("LINE".equals(tool)) {
                if (parts.length < 11)
                    return;
                int x1 = Integer.parseInt(parts[5]);
                int y1 = Integer.parseInt(parts[6]);
                int x2 = Integer.parseInt(parts[7]);
                int y2 = Integer.parseInt(parts[8]);
                Color color = Color.decode("#" + parts[9]);
                int thickness = Integer.parseInt(parts[10]);
                canvasPanel.addDrawCommand(new DrawCommand(x1, y1, x2, y2, color, thickness));

            } else if ("ERASE".equals(tool)) {
                int x1 = Integer.parseInt(parts[5]);
                int y1 = Integer.parseInt(parts[6]);
                int x2 = Integer.parseInt(parts[7]);
                int y2 = Integer.parseInt(parts[8]);
                int thickness = Integer.parseInt(parts[9]);
                canvasPanel.addDrawCommand(new DrawCommand(x1, y1, x2, y2, Color.WHITE, thickness));
            }
        } catch (Exception e) {
            System.out.println("DRAW_BCAST parse hatası: " + message);
        }
    }

    private void sendListRequest() {
        send(MupProtocol.LIST);
    }

    private void sendDrawLine(int fileId, int x1, int y1, int x2, int y2) {
        String msg;
        if ("ERASE".equals(selectedTool)) {
            msg = MupProtocol.DRAW + " " + fileId + " ERASE "
                    + x1 + " " + y1 + " " + x2 + " " + y2 + " " + selectedThickness;
        } else {
            msg = MupProtocol.DRAW + " " + fileId + " LINE "
                    + x1 + " " + y1 + " " + x2 + " " + y2 + " "
                    + colorToHex(selectedColor) + " " + selectedThickness;
        }
        send(msg);
    }

    private String colorToHex(Color c) {
        return String.format("%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue());
    }

    private void askUsernameAndConnect() {
        username = JOptionPane.showInputDialog(this, "Kullanıcı adınızı girin:",
                "MUP Bağlantı", JOptionPane.PLAIN_MESSAGE);
        if (username == null || username.trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, "Kullanıcı adı boş olamaz.");
            System.exit(0);
        }
        username = username.trim();
        try {
            socket = new Socket(SERVER_HOST, SERVER_PORT);
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
            writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"));

            send(MupProtocol.JOIN + " " + username);
            String response = reader.readLine();

            if (response == null) {
                JOptionPane.showMessageDialog(this, "Server yanıt vermedi.");
                System.exit(0);
            }

            if (response.startsWith(MupProtocol.WELCOME)) {
                connectionLabel.setText("Bağlandı: " + username);
                startListenerThread();
                sendListRequest();
            } else if (response.startsWith(MupProtocol.REJECT)) {
                JOptionPane.showMessageDialog(this, "Bağlantı reddedildi: " + response);
                System.exit(0);
            } else {
                JOptionPane.showMessageDialog(this, "Beklenmeyen yanıt: " + response);
                System.exit(0);
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Server'a bağlanılamadı: " + e.getMessage());
            System.exit(0);
        }
    }

    private void startListenerThread() {
        Thread t = new Thread(() -> {
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    final String msg = line;
                    SwingUtilities.invokeLater(() -> handleServerMessage(msg));
                }
            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> connectionLabel.setText("Bağlantı koptu"));
            }
        });
        t.setDaemon(true);
        t.start();
    }

    private synchronized void send(String message) {
        try {
            writer.write(message + MupProtocol.CRLF);
            writer.flush();
            System.out.println("[CLIENT] " + message.substring(0, Math.min(120, message.length())));
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Mesaj gönderilemedi: " + e.getMessage());
        }
    }

    // ----------------------------------------------------------------
    private static class FileItem {
        private final int id;
        private final String name;

        FileItem(int id, String name) {
            this.id = id;
            this.name = name;
        }

        int getId() {
            return id;
        }

        @Override
        public String toString() {
            return id + " - " + name;
        }
    }
}