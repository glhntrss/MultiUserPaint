package server;

import protocol.MupProtocol;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DrawingFile {

    private final int id;
    private final String name;
    private final int width;
    private final int height;

    private int nextOpId = 1;

    private final List<String> operations = new ArrayList<>();
    private final Set<ClientHandler> editors = new HashSet<>();

    public DrawingFile(int id, String name, int width, int height) {
        this.id = id;
        this.name = name;
        this.width = width;
        this.height = height;
    }

    public synchronized String createDrawBroadcast(String author, String tool, String args) {
        int opId = nextOpId++;

        String broadcastMessage = MupProtocol.DRAW_BCAST + " "
                + id + " "
                + opId + " "
                + author + " "
                + tool + " "
                + args;

        operations.add(broadcastMessage);

        return broadcastMessage;
    }

    public synchronized List<String> getOperationsCopy() {
        return new ArrayList<>(operations);
    }

    public synchronized void addEditor(ClientHandler handler) {
        editors.add(handler);
    }

    public synchronized void removeEditor(ClientHandler handler) {
        editors.remove(handler);
    }

    public synchronized Set<ClientHandler> getEditorsCopy() {
        return new HashSet<>(editors);
    }

    public synchronized int getEditorCount() {
        return editors.size();
    }

    public synchronized int getOperationCount() {
        return operations.size();
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }
}