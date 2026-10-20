import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** Generates the Play Store icon and feature graphic from the app's visual identity. */
public final class PlayAssets {
    static final Color INK = new Color(0x0B0B0B);
    static final Color GOLD = new Color(0xB8912F);
    static final Color CREAM = new Color(0xF4EFE3);
    static final Color SILVER = new Color(0xC9C9C4);

    public static void main(String[] args) throws Exception {
        File out = new File(args.length > 0 ? args[0] : "play-assets");
        out.mkdirs();
        ImageIO.write(icon(512), "png", new File(out, "play-icon-512.png"));
        ImageIO.write(feature(1024, 500), "png", new File(out, "feature-graphic-1024x500.png"));
        System.out.println("written to " + out.getAbsolutePath());
    }

    static BufferedImage icon(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(INK);
        g.fillRoundRect(0, 0, size, size, size / 6, size / 6);
        g.setColor(GOLD);
        g.setStroke(new BasicStroke(size * 0.014f));
        g.drawRoundRect(size / 12, size / 12, size - size / 6, size - size / 6, size / 10, size / 10);
        drawBook(g, size / 2, size * 0.58f, size * 0.62f, 1.0f);
        g.dispose();
        return img;
    }

    static void drawBook(Graphics2D g, float cx, float cy, float w, float scale) {
        float half = w / 2, h = w * 0.52f;
        g.setColor(CREAM);
        Path2D left = new Path2D.Float();
        left.moveTo(cx - half, cy - h / 2);
        left.quadTo(cx - half * 0.55f, cy - h / 2 - w * 0.04f, cx - 2, cy - h / 2 + w * 0.03f);
        left.lineTo(cx - 2, cy + h / 2);
        left.quadTo(cx - half * 0.55f, cy + h / 2 - w * 0.04f, cx - half, cy + h / 2 - w * 0.02f);
        left.closePath();
        g.fill(left);
        Path2D right = new Path2D.Float();
        right.moveTo(cx + half, cy - h / 2);
        right.quadTo(cx + half * 0.55f, cy - h / 2 - w * 0.04f, cx + 2, cy - h / 2 + w * 0.03f);
        right.lineTo(cx + 2, cy + h / 2);
        right.quadTo(cx + half * 0.55f, cy + h / 2 - w * 0.04f, cx + half, cy + h / 2 - w * 0.02f);
        right.closePath();
        g.setColor(new Color(0xE6DDC8));
        g.fill(right);
        g.setColor(GOLD);
        g.setStroke(new BasicStroke(w * 0.018f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(Math.round(cx), Math.round(cy - h / 2 + w * 0.03f), Math.round(cx), Math.round(cy + h / 2));
        for (int i = 0; i < 3; i++) {
            float y = cy - h * 0.18f + i * h * 0.22f;
            g.draw(new QuadCurve2D.Float(cx - half * 0.72f, y, cx - half * 0.45f, y - h * 0.08f, cx - half * 0.22f, y));
            g.draw(new QuadCurve2D.Float(cx + half * 0.72f, y, cx + half * 0.45f, y - h * 0.08f, cx + half * 0.22f, y));
        }
    }

    static BufferedImage feature(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setPaint(new GradientPaint(0, 0, INK, w, h, new Color(0x171512)));
        g.fillRect(0, 0, w, h);
        g.setColor(GOLD);
        g.setStroke(new BasicStroke(3f));
        g.drawRoundRect(40, 40, w - 80, h - 80, 24, 24);
        drawBook(g, w * 0.72f, h * 0.5f, w * 0.28f, 1.0f);
        g.setColor(GOLD);
        g.setFont(new Font("Serif", Font.BOLD, 62));
        g.drawString("RAFIQ AL-QURAN", 80, h / 2 - 30);
        g.setColor(SILVER);
        g.setFont(new Font("SansSerif", Font.PLAIN, 27));
        g.drawString("Quran-only verse search", 82, h / 2 + 18);
        g.setFont(new Font("SansSerif", Font.PLAIN, 21));
        g.setColor(new Color(0xB9B4A6));
        g.drawString("Arabic text · identified translations · verifiable sources", 82, h / 2 + 62);
        g.dispose();
        return img;
    }
}
