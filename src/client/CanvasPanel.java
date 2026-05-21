package client;

import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;

public class CanvasPanel extends JPanel {

    private final List<DrawCommand> commands = new ArrayList<>();

    public CanvasPanel() {
        setBackground(Color.WHITE);
    }

    public synchronized void addDrawCommand(DrawCommand command) {
        commands.add(command);
        repaint();
    }

    public synchronized void clearCanvas() {
        commands.clear();
        repaint();
    }

    @Override
    protected synchronized void paintComponent(Graphics g) {
        super.paintComponent(g);

        Graphics2D g2d = (Graphics2D) g;

        for (DrawCommand command : commands) {
            g2d.setColor(command.getColor());
            g2d.setStroke(new BasicStroke(
                    command.getThickness(),
                    BasicStroke.CAP_ROUND,
                    BasicStroke.JOIN_ROUND
            ));

            g2d.drawLine(
                    command.getX1(),
                    command.getY1(),
                    command.getX2(),
                    command.getY2()
            );
        }
    }
}