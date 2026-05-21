package client;

import protocol.MupProtocol;

import javax.swing.*;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
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

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            PaintGuiClient client = new PaintGuiClient();
            client.setVisible(true);
        });
    }

    public PaintGuiClient() {
        setTitle("MUP Multi-User Paint Client");
        setSize(1000, 700);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        buildInterface();
        askUsernameAndConnect();
    }

    private void buildInterface() {
        setLayout(new BorderLayout());

        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));

        JButton listButton = new JButton("Dosya Listesi");
        JButton createButton = new JButton("Yeni Dosya");
        JButton openButton = new JButton("Dosya Aç");
        JButton colorButton = new JButton("Renk Seç");
        JButton clearButton = new JButton("Ekranı Temizle");

        String[] toolOptions = {"Kalem", "Silgi"};
        JComboBox<String> toolCombo = new JComboBox<>(toolOptions);

        String[] thicknessOptions = {"1", "2", "3", "5", "8", "12"};
        JComboBox<String> thicknessCombo = new JComboBox<>(thicknessOptions);
        thicknessCombo.setSelectedItem("3");

        topPanel.add(listButton);
        topPanel.add(createButton);
        topPanel.add(openButton);
        topPanel.add(new JLabel("Araç:"));
        topPanel.add(toolCombo);
        topPanel.add(colorButton);
        topPanel.add(new JLabel("Kalınlık:"));
        topPanel.add(thicknessCombo);
        topPanel.add(clearButton);
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
        infoArea.setRows(4);
        infoArea.setText(
                "Kullanım:\n" +
                "1) Dosya Listesi butonuna bas.\n" +
                "2) Bir dosya seçip Dosya Aç'a bas.\n" +
                "3) Tuval üzerinde mouse ile çizim yap.\n"
        );
        add(new JScrollPane(infoArea), BorderLayout.SOUTH);

        listButton.addActionListener(e -> sendListRequest());
        createButton.addActionListener(e -> createFile());
        openButton.addActionListener(e -> openSelectedFile());

        colorButton.addActionListener(e -> {
            Color newColor = JColorChooser.showDialog(this, "Renk Seç", selectedColor);
            if (newColor != null) {
                selectedColor = newColor;
            }
        });

        toolCombo.addActionListener(e -> {
            String selected = (String) toolCombo.getSelectedItem();

            if ("Silgi".equals(selected)) {
                    selectedTool = "ERASE";
                } else {
                    selectedTool = "LINE";
                }
            });

        thicknessCombo.addActionListener(e -> {
            String value = (String) thicknessCombo.getSelectedItem();
            selectedThickness = Integer.parseInt(value);
        });

        clearButton.addActionListener(e -> canvasPanel.clearCanvas());

        canvasPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                lastPoint = e.getPoint();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                lastPoint = null;
            }
        });

        canvasPanel.addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (activeFileId == null) {
                    JOptionPane.showMessageDialog(
                            PaintGuiClient.this,
                            "Önce bir dosya açmalısın."
                    );
                    return;
                }

                if (lastPoint == null) {
                    lastPoint = e.getPoint();
                    return;
                }

                Point currentPoint = e.getPoint();

                sendDrawLine(
                        activeFileId,
                        lastPoint.x,
                        lastPoint.y,
                        currentPoint.x,
                        currentPoint.y
                );

                lastPoint = currentPoint;
            }
        });
    }

    private void askUsernameAndConnect() {
        username = JOptionPane.showInputDialog(
                this,
                "Kullanıcı adınızı girin:",
                "MUP Bağlantı",
                JOptionPane.PLAIN_MESSAGE
        );

        if (username == null || username.trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, "Kullanıcı adı boş olamaz.");
            System.exit(0);
        }

        username = username.trim();

        try {
            socket = new Socket(SERVER_HOST, SERVER_PORT);

            reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), "UTF-8")
            );

            writer = new BufferedWriter(
                    new OutputStreamWriter(socket.getOutputStream(), "UTF-8")
            );

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
        Thread listenerThread = new Thread(() -> {
            try {
                String line;

                while ((line = reader.readLine()) != null) {
                    String message = line;
                    SwingUtilities.invokeLater(() -> handleServerMessage(message));
                }

            } catch (IOException e) {
                SwingUtilities.invokeLater(() ->
                        connectionLabel.setText("Bağlantı koptu")
                );
            }
        });

        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    private void handleServerMessage(String message) {
        System.out.println("[SERVER] " + message);

        if (message.startsWith(MupProtocol.FILES)) {
            updateFileList(message);

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

        if (parts.length < 2) {
            return;
        }

        int count;

        try {
            count = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            return;
        }

        int index = 2;

        for (int i = 0; i < count; i++) {
            if (index + 1 >= parts.length) {
                return;
            }

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

        int result = JOptionPane.showConfirmDialog(
                this,
                panel,
                "Yeni Dosya Oluştur",
                JOptionPane.OK_CANCEL_OPTION
        );

        if (result == JOptionPane.OK_OPTION) {
            String fileName = nameField.getText().trim();
            String width = widthField.getText().trim();
            String height = heightField.getText().trim();

            send(MupProtocol.CREATE + " " + fileName + " " + width + " " + height);
        }
    }

    private void openSelectedFile() {
        FileItem selectedFile = fileList.getSelectedValue();

        if (selectedFile == null) {
            JOptionPane.showMessageDialog(this, "Önce listeden bir dosya seç.");
            return;
        }

        send(MupProtocol.OPEN + " " + selectedFile.getId());
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

        if (parts.length < 3) {
            return;
        }

        String embeddedOperation = parts[2];

        if (embeddedOperation.startsWith(MupProtocol.DRAW_BCAST)) {
            handleDrawBroadcast(embeddedOperation);
        }
    }

private void handleDrawBroadcast(String message) {
    // LINE format:
    // DRAW_BCAST fileId opId author LINE x1 y1 x2 y2 color thickness
    //
    // ERASE format:
    // DRAW_BCAST fileId opId author ERASE x1 y1 x2 y2 thickness

    String[] parts = message.split(" ");

    if (parts.length < 10) {
        return;
    }

    String tool = parts[4];

    try {
        if (tool.equals("LINE")) {
            if (parts.length < 11) {
                return;
            }

            int x1 = Integer.parseInt(parts[5]);
            int y1 = Integer.parseInt(parts[6]);
            int x2 = Integer.parseInt(parts[7]);
            int y2 = Integer.parseInt(parts[8]);

            Color color = Color.decode("#" + parts[9]);
            int thickness = Integer.parseInt(parts[10]);

            DrawCommand command = new DrawCommand(x1, y1, x2, y2, color, thickness);
            canvasPanel.addDrawCommand(command);

        } else if (tool.equals("ERASE")) {
            int x1 = Integer.parseInt(parts[5]);
            int y1 = Integer.parseInt(parts[6]);
            int x2 = Integer.parseInt(parts[7]);
            int y2 = Integer.parseInt(parts[8]);

            int thickness = Integer.parseInt(parts[9]);

            DrawCommand command = new DrawCommand(
                    x1,
                    y1,
                    x2,
                    y2,
                    Color.WHITE,
                    thickness
            );

            canvasPanel.addDrawCommand(command);
        }

    } catch (Exception e) {
        System.out.println("DRAW_BCAST parse edilemedi: " + message);
    }
}
    private void sendListRequest() {
        send(MupProtocol.LIST);
    }

private void sendDrawLine(int fileId, int x1, int y1, int x2, int y2) {
    String message;

    if ("ERASE".equals(selectedTool)) {
        message = MupProtocol.DRAW + " "
                + fileId + " "
                + "ERASE" + " "
                + x1 + " "
                + y1 + " "
                + x2 + " "
                + y2 + " "
                + selectedThickness;
    } else {
        String colorHex = colorToHex(selectedColor);

        message = MupProtocol.DRAW + " "
                + fileId + " "
                + "LINE" + " "
                + x1 + " "
                + y1 + " "
                + x2 + " "
                + y2 + " "
                + colorHex + " "
                + selectedThickness;
    }

    send(message);
}

    private String colorToHex(Color color) {
        return String.format("%02X%02X%02X",
                color.getRed(),
                color.getGreen(),
                color.getBlue()
        );
    }

    private synchronized void send(String message) {
        try {
            writer.write(message + MupProtocol.CRLF);
            writer.flush();
            System.out.println("[CLIENT] " + message);

        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Mesaj gönderilemedi: " + e.getMessage());
        }
    }

    private static class FileItem {

        private final int id;
        private final String name;

        public FileItem(int id, String name) {
            this.id = id;
            this.name = name;
        }

        public int getId() {
            return id;
        }

        @Override
        public String toString() {
            return id + " - " + name;
        }
    }
}