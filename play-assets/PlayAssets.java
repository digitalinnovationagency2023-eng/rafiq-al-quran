import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.LinearGradientPaint;
import java.awt.Paint;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import javax.imageio.ImageIO;

/**
 * Rafiq Al-Quran - Play Store asset generator.
 *
 * <p>Single-file, dependency-free Java AWT renderer. Run from the repository root:
 *
 * <pre>java play-assets/PlayAssets.java</pre>
 *
 * <p>Produces, deterministically and without any external image or network input:
 *
 * <ul>
 *   <li>{@code play-assets/play-icon-512.png} - 512x512, 32-bit PNG Play icon
 *   <li>{@code play-assets/play-icon-1024-master.png} - 1024x1024 master of the same mark
 *   <li>{@code play-assets/feature-graphic-1024x500.png} - opaque 1024x500 feature graphic
 *   <li>{@code mobile/android/res/drawable-xxhdpi/ic_launcher.png} - 432x432 legacy launcher icon
 * </ul>
 *
 * <p>Pass {@code --preview} to additionally write small-size preview PNGs into the system temp
 * folder (useful for checking 48 px legibility); deliverables are unchanged by that flag.
 */
public final class PlayAssets {

    /* Headless must be set before any AWT class is initialised (this runs first). */
    static {
        System.setProperty("java.awt.headless", "true");
    }

    /* ------------------------------------------------------------------ palette */

    static final Color INK = new Color(0x0B0B0B);
    static final Color GOLD = new Color(0xB8912F);
    static final Color GOLD_DEEP = new Color(0x8C6D1F);
    static final Color GOLD_LIGHT = new Color(0xD9B45E);
    static final Color GOLD_PALE = new Color(0xF0D997);
    static final Color SILVER = new Color(0xC9C9C4);
    static final Color PAPER = new Color(0xFFFFFF);
    static final Color PAPER_WARM = new Color(0xF6F2E8);
    static final Color PAPER_DIM = new Color(0xE2DAC5);
    static final Color PAPER_DEEP = new Color(0xCEC4A9);
    static final Color GILT = new Color(0xC4A75B);
    static final Color GREEN = new Color(0x0E5A3A);
    static final Color GREEN_LIT = new Color(0x14734C);
    static final Color GREEN_DARK = new Color(0x0A3F2A);

    /** Design space of the icon mark: everything is authored on a 1024 x 1024 grid. */
    static final double D = 1024.0;

    /** Half-width of the icon's frame in design units; the mark scales relative to this. */
    static final double FRAME_HALF = 456.0;

    /* ------------------------------------------------------------------ entry point */

    public static void main(String[] args) throws Exception {
        boolean preview = false;
        for (String a : args) {
            if ("--preview".equals(a) || "-p".equals(a)) {
                preview = true;
            }
        }

        File root = findRepoRoot();
        File playDir = new File(root, "play-assets");
        File xxhdpi = new File(root, "mobile/android/res/drawable-xxhdpi");
        playDir.mkdirs();
        xxhdpi.mkdirs();

        BufferedImage icon512 = renderIcon(512);
        BufferedImage iconMaster = renderIcon(1024);
        BufferedImage feature = renderFeature(1024, 500);
        BufferedImage launcher = renderLauncher(432);

        File fIcon = new File(playDir, "play-icon-512.png");
        File fMaster = new File(playDir, "play-icon-1024-master.png");
        File fFeature = new File(playDir, "feature-graphic-1024x500.png");
        File fLauncher = new File(xxhdpi, "ic_launcher.png");

        writePng(icon512, fIcon);
        writePng(iconMaster, fMaster);
        writePng(feature, fFeature);
        writePng(launcher, fLauncher);

        System.out.println("Rafiq Al-Quran - Play asset build");
        System.out.println();
        verify(fIcon, 512, 512, true, true);
        verify(fMaster, 1024, 1024, true, true);
        verify(fFeature, 1024, 500, false, true);
        verify(fLauncher, 432, 432, true, false);
        System.out.println();
        reportLegibility(icon512);
        reportLauncherShape(launcher);
        System.out.println();
        System.out.println("Done.");
        if (preview) {
            writePreviews(icon512, feature);
        }
    }

    /* ------------------------------------------------------------------ rendering: Play icon */

    static BufferedImage renderIcon(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = gfx(img);
        g.scale(size / D, size / D);
        drawIconDesign(g);
        g.dispose();
        return img;
    }

    static BufferedImage renderLauncher(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = gfx(img);
        g.scale(size / D, size / D);
        drawIconDesign(g);
        g.dispose();

        /* Rounded tile, antialiased via a DstIn alpha mask (clip() would alias the corners). */
        BufferedImage mask = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D mg = gfx(mask);
        mg.setColor(Color.WHITE);
        double radius = size * 0.185;
        mg.fill(new RoundRectangle2D.Double(0, 0, size, size, radius, radius));
        mg.dispose();

        Graphics2D g2 = gfx(img);
        g2.setComposite(AlphaComposite.DstIn);
        g2.drawImage(mask, 0, 0, null);
        g2.dispose();
        return img;
    }

    static void drawIconDesign(Graphics2D g) {
        /* Deep black field with a restrained warm centre so the mark sits in light. */
        g.setColor(INK);
        g.fillRect(0, 0, (int) D, (int) D);
        g.setPaint(new RadialGradientPaint(new Point2D.Double(512, 500), 720,
                new float[] {0f, 0.52f, 1f},
                new Color[] {new Color(0x1A160E), new Color(0x100E0B), INK}));
        g.fill(new Rectangle2D.Double(0, 0, D, D));

        drawFrame(g, 512, 512, FRAME_HALF);
        drawStarPanel(g, 512, 512, 1.0);
        drawBook(g, 512, 512, 1.0);
    }

    /* ------------------------------------------------------------------ icon furniture */

    /** Octagonal double frame with a jewel at each chamfer. Shared by icon and feature plate. */
    static void drawFrame(Graphics2D g, double cx, double cy, double half) {
        double s = half / FRAME_HALF;
        double chamfer = 100.0 * s;
        Path2D.Double outer = chamferedSquare(cx, cy, half, chamfer);

        /* Outer hairline echo, then a soft bloom under the gilding. */
        g.setStroke(hair(Math.max(3.0, 11.0 * s)));
        g.setPaint(rgba(GOLD.getRGB(), 0.09));
        g.draw(outer);

        g.setStroke(hair(Math.max(1.4, 5.4 * s)));
        g.setPaint(gild(cx - half, cy - half, cx + half, cy + half, 1.0));
        g.draw(outer);

        Path2D.Double inner = chamferedSquare(cx, cy, half - 13 * s, chamfer - 3.2 * s);
        g.setStroke(hair(Math.max(0.85, 1.25 * s)));
        g.setPaint(rgba(GOLD.getRGB(), 0.52));
        g.draw(inner);

        /* Corner medallion: a gold lozenge stud interrupting the frame. */
        double cs = half - chamfer / 2.0;
        double[][] corners = {{cx - cs, cy - cs}, {cx + cs, cy - cs}, {cx + cs, cy + cs}, {cx - cs, cy + cs}};
        for (double[] c : corners) {
            double r = 13.0 * s;
            g.setPaint(gild(c[0] - r, c[1] - r, c[0] + r, c[1] + r, 1.0));
            g.fill(diamond(c[0], c[1], r));
            g.setStroke(hair(Math.max(0.7, 1.2 * s)));
            g.setPaint(rgba(INK.getRGB(), 0.85));
            g.draw(diamond(c[0], c[1], r));
            g.setPaint(rgba(GOLD_PALE.getRGB(), 0.95));
            g.fill(diamond(c[0], c[1], 2.4 * s));
        }
    }

    /**
     * The eight-fold khatim panel: a solid dark star, gilded edge, an inset echo star and the
     * octagon joining the inner points - a restrained girih construction.
     */
    static void drawStarPanel(Graphics2D g, double cx, double cy, double s) {
        double rOut = 396 * s;
        double rIn = 250 * s;
        Path2D.Double star = star8(cx, cy, rOut, rIn, -90);

        /* Gilded bloom outside the panel edge. */
        g.setStroke(hair(Math.max(2.5, 12.0 * s)));
        g.setPaint(rgba(GOLD.getRGB(), 0.08));
        g.draw(star);

        g.setPaint(new RadialGradientPaint(new Point2D.Double(cx, cy), (float) (rOut * 1.02),
                new float[] {0f, 0.55f, 1f},
                new Color[] {new Color(0x2A2115), new Color(0x19140D), new Color(0x100D09)}));
        g.fill(star);

        /* Inset echo star - visible mainly along the eight points. */
        Path2D.Double echo = star8(cx, cy, rOut - 21 * s, rIn - 18 * s, -90);
        g.setStroke(hair(Math.max(0.8, 1.5 * s)));
        g.setPaint(rgba(GOLD.getRGB(), 0.26));
        g.draw(echo);

        /* Main gilded outline. */
        g.setStroke(hair(Math.max(1.3, 4.0 * s)));
        g.setPaint(gild(cx - rOut, cy - rOut, cx + rOut, cy + rOut, 1.0));
        g.draw(star);

        /* Diamond finials on the eight points. */
        for (int i = 0; i < 8; i++) {
            double a = Math.toRadians(-90 + i * 45);
            double x = cx + Math.cos(a) * (rOut + 1.5 * s);
            double y = cy + Math.sin(a) * (rOut + 1.5 * s);
            g.setPaint(rgba(INK.getRGB(), 0.9));
            g.fill(diamond(x, y, 10.0 * s));
            g.setPaint(gild(x - 12 * s, y - 12 * s, x + 12 * s, y + 12 * s, 1.0));
            g.fill(diamond(x, y, 7.5 * s));
        }
    }

    /* ------------------------------------------------------------------ the mushaf */

    /**
     * Draws the open Mushaf mark centred on (cx, cy). Geometry is expressed in design units
     * scaled by {@code s}: half width 234, half height 134, gutter dip 14, wing lift 2.
     */
    static void drawBook(Graphics2D g, double cx, double cy, double s) {
        final double hw = 234 * s;
        final double hh = 134 * s;
        final double dip = 14 * s;
        final double wing = 2 * s;
        final double bulge = 6 * s;
        final double cr = 17 * s;

        /* ---- page silhouettes (left page; right page is mirrored around the gutter) ---- */
        Path2D.Double left = new Path2D.Double();
        left.moveTo(cx, cy - hh + dip);
        left.curveTo(cx - hw * 0.34, cy - hh + dip * 0.62,
                cx - hw * 0.70, cy - hh - wing * 0.85,
                cx - hw + cr, cy - hh - wing - cr * 0.10);
        left.quadTo(cx - hw, cy - hh - wing, cx - hw - bulge * 0.35, cy - hh - wing + cr);
        left.curveTo(cx - hw - bulge, cy - hh * 0.55,
                cx - hw - bulge, cy + hh * 0.55,
                cx - hw - bulge * 0.35, cy + hh + wing - cr);
        left.quadTo(cx - hw, cy + hh + wing, cx - hw + cr, cy + hh + wing + cr * 0.10);
        left.curveTo(cx - hw * 0.70, cy + hh + wing * 0.85,
                cx - hw * 0.34, cy + hh - dip * 0.62,
                cx, cy + hh - dip);
        left.closePath();

        AffineTransform mirror = new AffineTransform();
        mirror.translate(2 * cx, 0);
        mirror.scale(-1, 1);
        Path2D.Double right = (Path2D.Double) left.clone();
        right.transform(mirror);

        Area pages = new Area(left);
        pages.add(new Area(right));

        /* Soft contact shadow beneath the spread, grounding it on the dark panel. */
        Graphics2D gsh = (Graphics2D) g.create();
        gsh.translate(cx, cy + hh + wing + 18 * s);
        gsh.scale(1.0, 0.20);
        double hr = hw * 1.10;
        gsh.setPaint(new RadialGradientPaint(new Point2D.Double(0, 0), (float) hr,
                new float[] {0f, 0.55f, 1f},
                new Color[] {rgba(0x000000, 0.50), rgba(0x000000, 0.24), rgba(0x000000, 0.0)}));
        gsh.fill(new Ellipse2D.Double(-hr, -hr, 2 * hr, 2 * hr));
        gsh.dispose();

        /* ---- gilded page block (the thickness of the leaves) ---- */
        Area block = new Area(pages);
        AffineTransform grow = new AffineTransform();
        grow.translate(cx, cy + 9.0 * s);
        grow.scale(1.014, 1.0);
        grow.translate(-cx, -cy);
        block.transform(grow);

        g.setPaint(new LinearGradientPaint(new Point2D.Double(cx, cy),
                new Point2D.Double(cx, cy + hh + wing + 12 * s),
                new float[] {0f, 0.5f, 1f},
                new Color[] {new Color(0x8C6D1F), GOLD, new Color(0xD6B25E)}));
        g.fill(block);
        g.setStroke(hair(Math.max(0.8, 0.9 * s)));
        g.setPaint(rgba(0x5C4A16, 0.55));
        g.draw(block);
        g.setStroke(hair(Math.max(0.7, 0.9 * s)));
        g.setPaint(rgba(GOLD_PALE.getRGB(), 0.62));
        g.draw(bottomContour(cx, cy, hw, hh, dip, wing, cr, 7.0 * s));

        /* ---- green silk bookmark tab in the gutter (the single colour accent) ---- */
        double tabW = 13.5 * s;
        double tabTop = cy - hh + dip - 44 * s;
        double tabBottom = cy - hh + dip + 16 * s;
        Path2D.Double tab = new Path2D.Double();
        tab.moveTo(cx - tabW, tabTop);
        tab.lineTo(cx, tabTop + 8 * s);
        tab.lineTo(cx + tabW, tabTop);
        tab.lineTo(cx + tabW, tabBottom);
        tab.lineTo(cx - tabW, tabBottom);
        tab.closePath();
        g.setPaint(new LinearGradientPaint(new Point2D.Double(cx, tabTop), new Point2D.Double(cx, tabBottom),
                new float[] {0f, 0.55f, 1f}, new Color[] {GREEN_LIT, GREEN, GREEN_DARK}));
        g.fill(tab);
        g.setStroke(hair(Math.max(0.9, 2.3 * s)));
        g.setPaint(gild(cx - tabW, tabTop, cx + tabW, tabBottom, 1.0));
        g.draw(tab);

        /* ---- paper surfaces: light from the upper left, shadow gathering at the gutter ---- */
        g.setPaint(new LinearGradientPaint(new Point2D.Double(cx - hw, cy), new Point2D.Double(cx, cy),
                new float[] {0f, 0.10f, 0.48f, 0.86f, 1f},
                new Color[] {new Color(0xF4EFE3), PAPER, PAPER, PAPER_WARM, PAPER_DEEP}));
        g.fill(left);

        g.setPaint(new LinearGradientPaint(new Point2D.Double(cx, cy), new Point2D.Double(cx + hw, cy),
                new float[] {0f, 0.12f, 0.52f, 0.88f, 1f},
                new Color[] {PAPER_DEEP, new Color(0xF8F4EA), PAPER, PAPER_WARM, PAPER_DIM}));
        g.fill(right);

        /* Warm light falling across the leaves; keeps the paper white but gives it body. */
        Graphics2D gt = (Graphics2D) g.create();
        gt.clip(pages);
        gt.setPaint(new LinearGradientPaint(new Point2D.Double(0, cy - hh - wing),
                new Point2D.Double(0, cy + hh + wing),
                new float[] {0f, 0.52f, 1f},
                new Color[] {rgba(0xFFFFFF, 0.0), rgba(0xFFFFFF, 0.0), rgba(0xA98D4E, 0.11)}));
        gt.fill(new Rectangle2D.Double(cx - hw - 20 * s, cy - hh - wing - 10 * s,
                2 * (hw + 20 * s), 2 * (hh + wing) + 20 * s));
        gt.dispose();

        /* Illuminated inner rule, as found on a mushaf page. */
        AffineTransform inset = new AffineTransform();
        inset.translate(cx, cy);
        inset.scale(0.930, 0.885);
        inset.translate(-cx, -cy);
        Path2D.Double leftRule = (Path2D.Double) left.clone();
        leftRule.transform(inset);
        Path2D.Double rightRule = (Path2D.Double) right.clone();
        rightRule.transform(inset);
        Graphics2D gr = (Graphics2D) g.create();
        gr.clip(pages);
        gr.setStroke(hair(Math.max(0.7, 0.8 * s)));
        gr.setPaint(rgba(GOLD_DEEP.getRGB(), 0.16));
        gr.draw(leftRule);
        gr.draw(rightRule);
        gr.dispose();

        /* Gilded page rim plus catch-lights on the leading edges. */
        g.setStroke(hair(Math.max(1.0, 2.0 * s)));
        g.setPaint(gild(cx - hw, cy - hh, cx + hw, cy + hh, 0.82));
        g.draw(pages);
        g.setStroke(hair(Math.max(0.7, 1.0 * s)));
        g.setPaint(rgba(GOLD_PALE.getRGB(), 0.78));
        g.draw(topContour(cx, cy, hw, hh, dip, wing, cr, 1.4 * s));
        g.setStroke(hair(Math.max(0.6, 0.8 * s)));
        g.setPaint(rgba(SILVER.getRGB(), 0.48));
        g.draw(topContour(cx, cy, hw, hh, dip, wing, cr, 3.2 * s));

        /* Gutter shading, clipped to the paper so it never bleeds onto the frame. */
        Graphics2D gs = (Graphics2D) g.create();
        gs.clip(pages);
        double gw = 24 * s;
        gs.setPaint(new LinearGradientPaint(new Point2D.Double(cx - gw, cy), new Point2D.Double(cx + gw, cy),
                new float[] {0f, 0.24f, 0.45f, 0.5f, 0.55f, 0.76f, 1f},
                new Color[] {rgba(0x1B150A, 0.0), rgba(0x1B150A, 0.10), rgba(0x1B150A, 0.34),
                        rgba(0x0A0804, 0.40), rgba(0x1B150A, 0.34), rgba(0x1B150A, 0.10), rgba(0x1B150A, 0.0)}));
        gs.fill(new Rectangle2D.Double(cx - gw, cy - hh - wing - 8 * s, 2 * gw, 2 * (hh + wing) + 16 * s));
        gs.dispose();

        /* Gold crease and its silver highlight. */
        g.setStroke(hair(Math.max(1.0, 2.8 * s)));
        g.setPaint(gild(cx, cy - hh + dip, cx, cy + hh - dip, 1.0));
        g.draw(new Line2D.Double(cx, cy - hh + dip + 1.5 * s, cx, cy + hh - dip - 1.5 * s));
        g.setStroke(hair(Math.max(0.7, 0.8 * s)));
        g.setPaint(rgba(SILVER.getRGB(), 0.60));
        g.draw(new Line2D.Double(cx - 2.6 * s, cy - hh + dip + 5 * s, cx - 2.6 * s, cy + hh - dip - 5 * s));

        /* Two hairlines beneath the pages suggest the stacked leaves of the block. */
        g.setStroke(hair(Math.max(0.6, 0.8 * s)));
        g.setPaint(rgba(0x7A5D16, 0.45));
        g.draw(bottomContour(cx, cy, hw, hh, dip, wing, cr, 4.5 * s));
        g.setPaint(rgba(0x7A5D16, 0.25));
        g.draw(bottomContour(cx, cy, hw, hh, dip, wing, cr, 8.5 * s));
    }

    /** Top page edge (both leaves), optionally offset downward into the paper. */
    static Path2D.Double topContour(double cx, double cy, double hw, double hh, double dip, double wing,
            double cr, double down) {
        Path2D.Double p = new Path2D.Double();
        p.moveTo(cx - hw + cr, cy - hh - wing - cr * 0.10 + down);
        p.curveTo(cx - hw * 0.70, cy - hh - wing * 0.85 + down,
                cx - hw * 0.34, cy - hh + dip * 0.62 + down,
                cx, cy - hh + dip + down);
        p.moveTo(cx + hw - cr, cy - hh - wing - cr * 0.10 + down);
        p.curveTo(cx + hw * 0.70, cy - hh - wing * 0.85 + down,
                cx + hw * 0.34, cy - hh + dip * 0.62 + down,
                cx, cy - hh + dip + down);
        return p;
    }

    /** Bottom page edge (both leaves), optionally offset downward. */
    static Path2D.Double bottomContour(double cx, double cy, double hw, double hh, double dip, double wing,
            double cr, double down) {
        Path2D.Double p = new Path2D.Double();
        p.moveTo(cx - hw + cr, cy + hh + wing + cr * 0.10 + down);
        p.curveTo(cx - hw * 0.70, cy + hh + wing * 0.85 + down,
                cx - hw * 0.34, cy + hh - dip * 0.62 + down,
                cx, cy + hh - dip + down);
        p.moveTo(cx + hw - cr, cy + hh + wing + cr * 0.10 + down);
        p.curveTo(cx + hw * 0.70, cy + hh + wing * 0.85 + down,
                cx + hw * 0.34, cy + hh - dip * 0.62 + down,
                cx, cy + hh - dip + down);
        return p;
    }

    /* ------------------------------------------------------------------ feature graphic */

    static BufferedImage renderFeature(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = gfx(img);

        /* Opaque black plate with warm light rising behind the medallion. */
        g.setColor(INK);
        g.fillRect(0, 0, w, h);
        g.setPaint(new RadialGradientPaint(new Point2D.Double(w * 0.79, h * 0.50), (float) (h * 1.10),
                new float[] {0f, 0.48f, 1f},
                new Color[] {new Color(0x1D180E), new Color(0x110F0B), INK}));
        g.fillRect(0, 0, w, h);

        overlayGirih(g, w, h);
        drawFeatureBorder(g, w, h);
        drawFeatureText(g, w, h);
        drawFeatureMark(g, w, h);

        /* Gentle vignette to seat the composition. */
        g.setPaint(new RadialGradientPaint(new Point2D.Double(w * 0.5, h * 0.5), (float) (w * 0.80),
                new float[] {0f, 0.55f, 1f},
                new Color[] {new Color(0, 0, 0, 0), new Color(0, 0, 0, 0), rgba(0x000000, 0.32)}));
        g.fillRect(0, 0, w, h);

        g.dispose();
        return img;
    }

    /** Faint eight-fold lattice, faded away behind the wordmark so the type stays clean. */
    static void overlayGirih(Graphics2D g, int w, int h) {
        BufferedImage layer = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D lg = gfx(layer);
        lg.setStroke(hair(0.8));
        lg.setColor(GOLD);
        double step = 92.0;
        double rOct = step / (2.0 * Math.cos(Math.toRadians(22.5)));
        double rGap = step * (Math.sqrt(2.0) / 2.0 - 0.5);
        for (double y = -step; y < h + step; y += step) {
            for (double x = -step; x < w + step; x += step) {
                lg.draw(regularOctagon(x, y, rOct, 22.5));
                lg.draw(diamond(x + step / 2.0, y + step / 2.0, rGap * 1.02));
            }
        }
        lg.setComposite(AlphaComposite.DstIn);
        lg.setPaint(new GradientPaint(0f, 0f, new Color(255, 255, 255, 0),
                (float) (w * 0.72), 0f, new Color(255, 255, 255, 24)));
        lg.fillRect(0, 0, w, h);
        lg.dispose();
        g.drawImage(layer, 0, 0, null);
    }

    static void drawFeatureBorder(Graphics2D g, int w, int h) {
        Path2D.Double plate = chamferedRect(w / 2.0, h / 2.0, w / 2.0 - 24, h / 2.0 - 24, 30);
        g.setStroke(hair(1.1));
        g.setPaint(rgba(GOLD.getRGB(), 0.32));
        g.draw(plate);
        Path2D.Double plate2 = chamferedRect(w / 2.0, h / 2.0, w / 2.0 - 33, h / 2.0 - 33, 26);
        g.setStroke(hair(0.7));
        g.setPaint(rgba(GOLD.getRGB(), 0.14));
        g.draw(plate2);
    }

    static void drawFeatureText(Graphics2D g, int w, int h) {
        String family = serifFamily();
        double blockCx = 322;

        /* Ornament rule above the wordmark. */
        double oy = 141;
        g.setStroke(hair(1.0));
        g.setPaint(rgba(GOLD.getRGB(), 0.48));
        g.draw(new Line2D.Double(blockCx - 196, oy, blockCx - 52, oy));
        g.draw(new Line2D.Double(blockCx + 52, oy, blockCx + 196, oy));
        g.setPaint(rgba(SILVER.getRGB(), 0.40));
        g.fill(new Ellipse2D.Double(blockCx - 200, oy - 1.7, 3.4, 3.4));
        g.fill(new Ellipse2D.Double(blockCx + 196.6, oy - 1.7, 3.4, 3.4));
        g.setStroke(hair(1.5));
        g.setPaint(gild(blockCx - 20, oy - 20, blockCx + 20, oy + 20, 0.95));
        g.draw(star8(blockCx, oy, 17, 10, -90));
        g.fill(diamond(blockCx, oy, 2.8));

        /* RAFIQ - large gilded serif word. */
        double size1 = 120;
        Font f1 = fontOf(family, size1);
        double base1 = 249;
        FontMetrics fm1 = g.getFontMetrics(f1);
        double cap1 = fm1.getAscent() * 0.70;
        drawTracked(g, f1, "RAFIQ", blockCx, base1, 0.14, gildVertical(base1 - cap1, base1 + 7, 1.0));

        /* AL-QURAN - smaller, widely tracked silver line beneath. */
        double size2 = fitFontSize(g, family, "AL-QURAN", 384, 0.32, 54);
        Font f2 = fontOf(family, size2);
        double base2 = 316;
        drawTracked(g, f2, "AL-QURAN", blockCx, base2, 0.32, rgba(SILVER.getRGB(), 0.90));

        /* Hairline with a small emerald-set diamond. */
        double ry = 358;
        g.setStroke(hair(1.0));
        g.setPaint(rgba(GOLD.getRGB(), 0.48));
        g.draw(new Line2D.Double(blockCx - 162, ry, blockCx - 20, ry));
        g.draw(new Line2D.Double(blockCx + 20, ry, blockCx + 162, ry));
        g.setPaint(gild(blockCx - 9, ry - 9, blockCx + 9, ry + 9, 1.0));
        g.fill(diamond(blockCx, ry, 6.0));
        g.setPaint(GREEN);
        g.fill(diamond(blockCx, ry, 2.6));
    }

    static void drawFeatureMark(Graphics2D g, int w, int h) {
        double cx = 806;
        double cy = h * 0.5;
        double half = 190;
        double s = half / FRAME_HALF;

        /* Warm halo behind the medallion. */
        g.setPaint(new RadialGradientPaint(new Point2D.Double(cx, cy), 270,
                new float[] {0f, 0.55f, 1f},
                new Color[] {rgba(GOLD_LIGHT.getRGB(), 0.17), rgba(GOLD.getRGB(), 0.08), rgba(GOLD.getRGB(), 0.0)}));
        g.fill(new Ellipse2D.Double(cx - 270, cy - 270, 540, 540));

        drawFrame(g, cx, cy, half);
        drawStarPanel(g, cx, cy, s);
        drawBook(g, cx, cy, s);
    }

    /* ------------------------------------------------------------------ typography */

    static String serifFamily() {
        String[] prefs = {"Palatino Linotype", "Garamond", "Constantia", "Book Antiqua",
                "Georgia", "Cambria", "Times New Roman"};
        Set<String> available = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        available.addAll(Arrays.asList(
                GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
        for (String p : prefs) {
            if (available.contains(p)) {
                return p;
            }
        }
        return Font.SERIF;
    }

    static Font fontOf(String family, double size) {
        return new Font(family, Font.PLAIN, 12).deriveFont(Font.PLAIN, (float) size);
    }

    static double fitFontSize(Graphics2D g, String family, String text, double maxWidth,
            double trackEm, double start) {
        double size = start;
        for (int i = 0; i < 4; i++) {
            double width = trackedWidth(g, fontOf(family, size), text, trackEm);
            if (width <= maxWidth || width <= 0) {
                break;
            }
            size *= maxWidth / width;
        }
        return size;
    }

    static double trackedWidth(Graphics2D g, Font f, String text, double trackEm) {
        FontMetrics fm = g.getFontMetrics(f);
        double track = trackEm * f.getSize2D();
        double w = 0;
        for (int i = 0; i < text.length(); i++) {
            w += fm.charWidth(text.charAt(i));
            if (i < text.length() - 1) {
                w += track;
            }
        }
        return w;
    }

    static void drawTracked(Graphics2D g, Font f, String text, double centerX, double baseline,
            double trackEm, Paint paint) {
        FontMetrics fm = g.getFontMetrics(f);
        double track = trackEm * f.getSize2D();
        double x = centerX - trackedWidth(g, f, text, trackEm) / 2.0;
        g.setFont(f);
        g.setPaint(paint);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            g.drawString(String.valueOf(c), (float) x, (float) baseline);
            x += fm.charWidth(c) + track;
        }
    }

    /* ------------------------------------------------------------------ geometry helpers */

    static Path2D.Double chamferedRect(double cx, double cy, double hw, double hh, double chamfer) {
        Path2D.Double p = new Path2D.Double();
        p.moveTo(cx - hw + chamfer, cy - hh);
        p.lineTo(cx + hw - chamfer, cy - hh);
        p.lineTo(cx + hw, cy - hh + chamfer);
        p.lineTo(cx + hw, cy + hh - chamfer);
        p.lineTo(cx + hw - chamfer, cy + hh);
        p.lineTo(cx - hw + chamfer, cy + hh);
        p.lineTo(cx - hw, cy + hh - chamfer);
        p.lineTo(cx - hw, cy - hh + chamfer);
        p.closePath();
        return p;
    }

    static Path2D.Double chamferedSquare(double cx, double cy, double half, double chamfer) {
        return chamferedRect(cx, cy, half, half, chamfer);
    }

    static Path2D.Double star8(double cx, double cy, double rOut, double rIn, double rotDeg) {
        Path2D.Double p = new Path2D.Double();
        for (int i = 0; i < 16; i++) {
            double a = Math.toRadians(rotDeg + i * 22.5);
            double r = (i % 2 == 0) ? rOut : rIn;
            double x = cx + Math.cos(a) * r;
            double y = cy + Math.sin(a) * r;
            if (i == 0) {
                p.moveTo(x, y);
            } else {
                p.lineTo(x, y);
            }
        }
        p.closePath();
        return p;
    }

    static Path2D.Double regularOctagon(double cx, double cy, double r, double rotDeg) {
        Path2D.Double p = new Path2D.Double();
        for (int i = 0; i < 8; i++) {
            double a = Math.toRadians(rotDeg + i * 45);
            double x = cx + Math.cos(a) * r;
            double y = cy + Math.sin(a) * r;
            if (i == 0) {
                p.moveTo(x, y);
            } else {
                p.lineTo(x, y);
            }
        }
        p.closePath();
        return p;
    }

    static Path2D.Double rotSquare(double cx, double cy, double r, double rotDeg) {
        Path2D.Double p = new Path2D.Double();
        for (int i = 0; i < 4; i++) {
            double a = Math.toRadians(rotDeg + i * 90);
            double x = cx + Math.cos(a) * r;
            double y = cy + Math.sin(a) * r;
            if (i == 0) {
                p.moveTo(x, y);
            } else {
                p.lineTo(x, y);
            }
        }
        p.closePath();
        return p;
    }

    static Path2D.Double diamond(double cx, double cy, double r) {
        return rotSquare(cx, cy, r, 0);
    }

    /* ------------------------------------------------------------------ painting helpers */

    static Graphics2D gfx(BufferedImage img) {
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION,
                RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        return g;
    }

    static BasicStroke hair(double width) {
        return new BasicStroke((float) width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    }

    static Color rgba(int rgb, double alpha) {
        double a = Math.max(0.0, Math.min(1.0, alpha));
        return new Color((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, (int) Math.round(a * 255));
    }

    /** Diagonal gilded-metal gradient used for frame, star and medallion strokes. */
    static Paint gild(double x0, double y0, double x1, double y1, double alpha) {
        return new LinearGradientPaint(new Point2D.Double(x0, y0), new Point2D.Double(x1, y1),
                new float[] {0f, 0.32f, 0.68f, 1f},
                new Color[] {rgba(GOLD_PALE.getRGB(), alpha), rgba(0xE5C67E, alpha),
                        rgba(GOLD.getRGB(), alpha), rgba(GOLD_DEEP.getRGB(), alpha)});
    }

    /** Vertical gilded gradient for lettering: bright crown, deep base. */
    static Paint gildVertical(double yTop, double yBottom, double alpha) {
        return new LinearGradientPaint(new Point2D.Double(0, yTop), new Point2D.Double(0, yBottom),
                new float[] {0f, 0.22f, 0.52f, 0.78f, 1f},
                new Color[] {rgba(0xF8E8B8, alpha), rgba(0xEDD394, alpha), rgba(0xD3AC4C, alpha),
                        rgba(GOLD.getRGB(), alpha), rgba(0x9C7A20, alpha)});
    }

    /* ------------------------------------------------------------------ output + verification */

    static File findRepoRoot() {
        File dir = new File(System.getProperty("user.dir")).getAbsoluteFile();
        File probe = dir;
        while (probe != null) {
            if (new File(probe, "play-assets/PlayAssets.java").isFile()) {
                return probe;
            }
            probe = probe.getParentFile();
        }
        return dir;
    }

    static void writePng(BufferedImage img, File file) throws IOException {
        File parent = file.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        if (!ImageIO.write(img, "png", file)) {
            throw new IOException("No PNG writer available for " + file);
        }
    }

    static void verify(File file, int expW, int expH, boolean expectAlpha, boolean requireOpaque)
            throws IOException {
        BufferedImage img = ImageIO.read(file);
        if (img == null) {
            throw new IllegalStateException("Unreadable PNG: " + file);
        }
        if (img.getWidth() != expW || img.getHeight() != expH) {
            throw new IllegalStateException("Wrong size for " + file.getName() + ": "
                    + img.getWidth() + "x" + img.getHeight() + ", expected " + expW + "x" + expH);
        }
        boolean hasAlpha = img.getColorModel().hasAlpha();
        int minAlpha = 255;
        int maxAlpha = 0;
        if (hasAlpha) {
            for (int y = 0; y < img.getHeight(); y++) {
                for (int x = 0; x < img.getWidth(); x++) {
                    int a = (img.getRGB(x, y) >>> 24) & 0xFF;
                    minAlpha = Math.min(minAlpha, a);
                    maxAlpha = Math.max(maxAlpha, a);
                }
            }
        }
        if (expectAlpha && !hasAlpha) {
            throw new IllegalStateException(file.getName() + " must be a 32-bit PNG with an alpha channel");
        }
        if (requireOpaque && hasAlpha && minAlpha != 255) {
            throw new IllegalStateException(file.getName() + " contains transparent pixels (min alpha "
                    + minAlpha + ")");
        }
        if (!expectAlpha && hasAlpha) {
            throw new IllegalStateException(file.getName() + " must not carry an alpha channel");
        }
        int depth = img.getColorModel().getPixelSize();
        String alphaNote = hasAlpha ? String.format(Locale.ROOT, "alpha %d..%d", minAlpha, maxAlpha)
                : "no alpha channel";
        System.out.println(String.format(Locale.ROOT,
                "  %-46s %4dx%-4d %2d-bit  %7d B  %s", file.getPath(), expW, expH, depth,
                file.length(), alphaNote));
        System.out.println("      sha256 " + sha256(file));
    }

    static String sha256(File file) throws IOException {
        try (InputStream in = new FileInputStream(file)) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                md.update(buf, 0, n);
            }
            StringBuilder sb = new StringBuilder();
            for (byte b : md.digest()) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }

    /** Checks that the mark still carries a strong light/dark silhouette when viewed at 48 px. */
    static void reportLegibility(BufferedImage icon) {
        BufferedImage small = boxDown(icon, 48, 48);
        int bright = 0;
        int dark = 0;
        long sum = 0;
        for (int y = 0; y < 48; y++) {
            for (int x = 0; x < 48; x++) {
                int p = small.getRGB(x, y);
                int lum = luminance(p);
                sum += lum;
                if (lum >= 180) {
                    bright++;
                }
                if (lum <= 40) {
                    dark++;
                }
            }
        }
        double n = 48.0 * 48.0;
        System.out.println(String.format(Locale.ROOT,
                "  legibility @48px: bright(paper)=%.1f%%  dark(field)=%.1f%%  meanLum=%.1f",
                100.0 * bright / n, 100.0 * dark / n, sum / n));
    }

    static void reportLauncherShape(BufferedImage launcher) {
        int transparent = 0;
        int w = launcher.getWidth();
        int h = launcher.getHeight();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (((launcher.getRGB(x, y) >>> 24) & 0xFF) == 0) {
                    transparent++;
                }
            }
        }
        System.out.println(String.format(Locale.ROOT,
                "  launcher tile: transparent corner pixels %d (%.2f%%)",
                transparent, 100.0 * transparent / (w * h)));
    }

    static int luminance(int argb) {
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        return (int) Math.round(0.2126 * r + 0.7152 * g + 0.0722 * b);
    }

    /** Alpha-weighted box filter; used for previews and the 48 px legibility check. */
    static BufferedImage boxDown(BufferedImage src, int tw, int th) {
        int sw = src.getWidth();
        int sh = src.getHeight();
        BufferedImage out = new BufferedImage(tw, th, BufferedImage.TYPE_INT_ARGB);
        double xr = sw / (double) tw;
        double yr = sh / (double) th;
        for (int ty = 0; ty < th; ty++) {
            int y0 = (int) Math.floor(ty * yr);
            int y1 = Math.min(sh, (int) Math.ceil((ty + 1) * yr));
            for (int tx = 0; tx < tw; tx++) {
                int x0 = (int) Math.floor(tx * xr);
                int x1 = Math.min(sw, (int) Math.ceil((tx + 1) * xr));
                long sa = 0, sr = 0, sg = 0, sb = 0;
                int n = 0;
                for (int sy = y0; sy < y1; sy++) {
                    for (int sx = x0; sx < x1; sx++) {
                        int p = src.getRGB(sx, sy);
                        int a = (p >>> 24) & 0xFF;
                        sa += a;
                        sr += ((p >> 16) & 0xFF) * a;
                        sg += ((p >> 8) & 0xFF) * a;
                        sb += (p & 0xFF) * a;
                        n++;
                    }
                }
                int aa = (int) Math.round(sa / (double) n);
                int rr = sa == 0 ? 0 : (int) Math.round(sr / (double) sa);
                int gg = sa == 0 ? 0 : (int) Math.round(sg / (double) sa);
                int bb = sa == 0 ? 0 : (int) Math.round(sb / (double) sa);
                out.setRGB(tx, ty, (aa << 24) | (rr << 16) | (gg << 8) | bb);
            }
        }
        return out;
    }

    static void writePreviews(BufferedImage icon, BufferedImage feature) throws IOException {
        File dir = new File(System.getProperty("java.io.tmpdir"), "rafiq-play-previews");
        dir.mkdirs();
        for (int size : new int[] {48, 72, 96, 144, 192, 256}) {
            writePng(boxDown(icon, size, size), new File(dir, "icon-" + size + ".png"));
        }
        writePng(boxDown(feature, 512, 250), new File(dir, "feature-half.png"));
        writePng(boxDown(feature, 256, 125), new File(dir, "feature-quarter.png"));
        writePng(feature, new File(dir, "feature-actual.png"));
        System.out.println();
        System.out.println("Previews -> " + dir.getAbsolutePath());
    }
}
