package client;

import java.awt.Color;
import java.awt.image.BufferedImage;

public class DrawCommand {

    public enum Type {
        LINE, ERASE, ERASE_RECT, PASTE_IMAGE
    }

    private final Type type;
    private final int x1, y1, x2, y2;
    private final Color color;
    private final int thickness;
    private final int rectW, rectH;
    private final BufferedImage image;

    public DrawCommand(int x1, int y1, int x2, int y2, Color color, int thickness) {
        this.type = color.equals(Color.WHITE) ? Type.ERASE : Type.LINE;
        this.x1 = x1;
        this.y1 = y1;
        this.x2 = x2;
        this.y2 = y2;
        this.color = color;
        this.thickness = thickness;
        this.rectW = 0;
        this.rectH = 0;
        this.image = null;
    }

    public DrawCommand(int x1, int y1, int x2, int y2, Color color, int thickness, int rectW, int rectH, Type type) {
        this.type = type;
        this.x1 = x1;
        this.y1 = y1;
        this.x2 = x2;
        this.y2 = y2;
        this.color = color;
        this.thickness = thickness;
        this.rectW = rectW;
        this.rectH = rectH;
        this.image = null;
    }

    public DrawCommand(int x, int y, BufferedImage image, Type type) {
        this.type = type;
        this.x1 = x;
        this.y1 = y;
        this.x2 = x;
        this.y2 = y;
        this.color = Color.BLACK;
        this.thickness = 1;
        this.rectW = image != null ? image.getWidth() : 0;
        this.rectH = image != null ? image.getHeight() : 0;
        this.image = image;
    }

    public Type getType() {
        return type;
    }

    public int getX1() {
        return x1;
    }

    public int getY1() {
        return y1;
    }

    public int getX2() {
        return x2;
    }

    public int getY2() {
        return y2;
    }

    public Color getColor() {
        return color;
    }

    public int getThickness() {
        return thickness;
    }

    public int getRectW() {
        return rectW;
    }

    public int getRectH() {
        return rectH;
    }

    public BufferedImage getImage() {
        return image;
    }
}