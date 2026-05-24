package client;

import java.awt.image.BufferedImage;

public class ClipboardData {

    public enum Operation {
        CUT, COPY
    }

    private final Operation operation;
    private final BufferedImage image;
    private final int srcX;
    private final int srcY;
    private final int width;
    private final int height;

    public ClipboardData(Operation operation, BufferedImage image,
            int srcX, int srcY, int width, int height) {
        this.operation = operation;
        this.image = image;
        this.srcX = srcX;
        this.srcY = srcY;
        this.width = width;
        this.height = height;
    }

    public Operation getOperation() {
        return operation;
    }

    public BufferedImage getImage() {
        return image;
    }

    public int getSrcX() {
        return srcX;
    }

    public int getSrcY() {
        return srcY;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }
}