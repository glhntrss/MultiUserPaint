package client;

import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

public class CanvasPanel extends JPanel {

    private final List<DrawCommand> commands = new ArrayList<>();

    private Rectangle selectionRect = null;
    private int selStartX, selStartY;

    private BufferedImage pastePreview = null;
    private int pastePreviewX = 0;
    private int pastePreviewY = 0;

    public CanvasPanel() {
        setBackground(Color.WHITE);
    }

    public synchronized void addDrawCommand(DrawCommand command) {
        commands.add(command);
        repaint();
    }

    public synchronized void clearCanvas() {
        commands.clear();
        selectionRect = null;
        pastePreview = null;
        repaint();
    }

    public synchronized void startSelection(int x, int y) {
        selStartX = x;
        selStartY = y;
        selectionRect = new Rectangle(x, y, 0, 0);
        pastePreview = null;
        repaint();
    }

    public synchronized void updateSelection(int x, int y) {
        if (selectionRect == null)
            return;
        int rx = Math.min(selStartX, x);
        int ry = Math.min(selStartY, y);
        int rw = Math.abs(x - selStartX);
        int rh = Math.abs(y - selStartY);
        selectionRect = new Rectangle(rx, ry, rw, rh);
        repaint();
    }

    public synchronized void clearSelection() {
        selectionRect = null;
        repaint();
    }

    public synchronized Rectangle getSelectionRect() {
        return selectionRect == null ? null : new Rectangle(selectionRect);
    }

    public synchronized BufferedImage captureRegion(int x, int y, int w, int h) {
        BufferedImage full = new BufferedImage(
                Math.max(getWidth(), 1),
                Math.max(getHeight(), 1),
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = full.createGraphics();
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, full.getWidth(), full.getHeight());
        drawCommandsOnGraphics(g2d);
        g2d.dispose();

        int cx = Math.max(0, x);
        int cy = Math.max(0, y);
        int cw = Math.min(w, full.getWidth() - cx);
        int ch = Math.min(h, full.getHeight() - cy);

        if (cw <= 0 || ch <= 0)
            return null;

        return full.getSubimage(cx, cy, cw, ch);
    }

    public synchronized void eraseRegion(int x, int y, int w, int h) {
        commands.add(new DrawCommand(x, y, x + w, y + h, Color.WHITE, 1, w, h, DrawCommand.Type.ERASE_RECT));
        repaint();
    }

    public synchronized void showPastePreview(BufferedImage img, int x, int y) {
        pastePreview = img;
        pastePreviewX = x;
        pastePreviewY = y;
        repaint();
    }

    public synchronized void movePastePreview(int x, int y) {
        pastePreviewX = x;
        pastePreviewY = y;
        repaint();
    }

    public synchronized void clearPastePreview() {
        pastePreview = null;
        repaint();
    }

    public boolean hasPastePreview() {
        return pastePreview != null;
    }

    public int getPastePreviewX() {
        return pastePreviewX;
    }

    public int getPastePreviewY() {
        return pastePreviewY;
    }

    public synchronized void applyPaste(BufferedImage img, int x, int y) {
        commands.add(new DrawCommand(x, y, img, DrawCommand.Type.PASTE_IMAGE));
        pastePreview = null;
        repaint();
    }

    @Override
    protected synchronized void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        drawCommandsOnGraphics(g2d);

        if (pastePreview != null) {
            g2d.setComposite(java.awt.AlphaComposite.getInstance(
                    java.awt.AlphaComposite.SRC_OVER, 0.7f));
            g2d.drawImage(pastePreview, pastePreviewX, pastePreviewY, null);
            g2d.setComposite(java.awt.AlphaComposite.getInstance(
                    java.awt.AlphaComposite.SRC_OVER, 1.0f));
        }

        if (selectionRect != null && selectionRect.width > 0 && selectionRect.height > 0) {
            g2d.setColor(new Color(0, 120, 215));
            g2d.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                    10, new float[] { 6, 4 }, 0));
            g2d.draw(selectionRect);

            g2d.setColor(new Color(0, 120, 215, 30));
            g2d.fill(selectionRect);
        }
    }

    private void drawCommandsOnGraphics(Graphics2D g2d) {
        for (DrawCommand cmd : commands) {
            switch (cmd.getType()) {
                case LINE:
                    g2d.setColor(cmd.getColor());
                    g2d.setStroke(new BasicStroke(cmd.getThickness(),
                            BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2d.drawLine(cmd.getX1(), cmd.getY1(), cmd.getX2(), cmd.getY2());
                    break;

                case ERASE:
                    g2d.setColor(Color.WHITE);
                    g2d.setStroke(new BasicStroke(cmd.getThickness(),
                            BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2d.drawLine(cmd.getX1(), cmd.getY1(), cmd.getX2(), cmd.getY2());
                    break;

                case ERASE_RECT:
                    g2d.setColor(Color.WHITE);
                    g2d.fillRect(cmd.getX1(), cmd.getY1(), cmd.getRectW(), cmd.getRectH());
                    break;

                case PASTE_IMAGE:
                    if (cmd.getImage() != null) {
                        g2d.drawImage(cmd.getImage(), cmd.getX1(), cmd.getY1(), null);
                    }
                    break;
            }
        }
    }
}