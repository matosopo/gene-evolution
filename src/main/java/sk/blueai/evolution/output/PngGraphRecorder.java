package sk.blueai.evolution.output;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import sk.blueai.evolution.config.SimulationConfig;
import sk.blueai.evolution.model.SpeciesSnapshot;

public final class PngGraphRecorder implements SimulationListener {

    private static final int WIDTH = 1600;
    private static final int HEIGHT = 900;
    private static final int MARGIN_LEFT = 80;
    private static final int MARGIN_RIGHT = 220;
    private static final int MARGIN_TOP = 40;
    private static final int MARGIN_BOTTOM = 60;

    private final Path path;
    private final Map<Long, SpeciesTimeseries> series = new LinkedHashMap<>();
    private int totalSteps;
    private long globalPeak;

    public PngGraphRecorder(Path path) {
        this.path = path;
    }

    @Override
    public void onStart(SimulationConfig config) {
        totalSteps = config.finalStepCount();
        series.clear();
        globalPeak = 0L;
    }

    @Override
    public void onStep(int step, List<SpeciesSnapshot> species, long totalN) {
        for (SpeciesSnapshot s : species) {
            SpeciesTimeseries ts = series.computeIfAbsent(
                    s.replicator().id(),
                    id -> new SpeciesTimeseries(id, totalSteps));
            ts.set(step, s.count());
            if (s.count() > globalPeak) globalPeak = s.count();
        }
    }

    @Override
    public void onEnd() {
        try {
            Path parent = path.toAbsolutePath().getParent();
            if (parent != null) Files.createDirectories(parent);
            BufferedImage img = render();
            ImageIO.write(img, "png", path.toFile());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private BufferedImage render() {
        BufferedImage img = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, WIDTH, HEIGHT);

            int plotW = WIDTH - MARGIN_LEFT - MARGIN_RIGHT;
            int plotH = HEIGHT - MARGIN_TOP - MARGIN_BOTTOM;
            int yBottom = MARGIN_TOP + plotH;

            long yMax = Math.max(1L, globalPeak);
            int xMax = Math.max(1, totalSteps - 1);

            drawAxesAndGrid(g, plotW, plotH, xMax, yMax);

            List<SpeciesTimeseries> ordered = new ArrayList<>(series.values());
            for (SpeciesTimeseries ts : ordered) {
                g.setColor(colorFor(ts.id));
                g.setStroke(new BasicStroke(1.4f));
                int prevX = -1;
                int prevY = -1;
                for (int step = 0; step < ts.length(); step++) {
                    long v = ts.get(step);
                    if (v < 0) continue;
                    int x = MARGIN_LEFT + Math.round((float) step / xMax * plotW);
                    int y = yBottom - Math.round((float) v / yMax * plotH);
                    if (prevX >= 0) g.drawLine(prevX, prevY, x, y);
                    prevX = x;
                    prevY = y;
                }
            }

            drawLegend(g, ordered, yMax);
        } finally {
            g.dispose();
        }
        return img;
    }

    private void drawAxesAndGrid(Graphics2D g, int plotW, int plotH, int xMax, long yMax) {
        int xRight = MARGIN_LEFT + plotW;
        int yBottom = MARGIN_TOP + plotH;

        g.setColor(new Color(0xEE, 0xEE, 0xEE));
        g.setStroke(new BasicStroke(1f));
        for (int i = 1; i < 10; i++) {
            int y = MARGIN_TOP + i * plotH / 10;
            g.drawLine(MARGIN_LEFT, y, xRight, y);
            int x = MARGIN_LEFT + i * plotW / 10;
            g.drawLine(x, MARGIN_TOP, x, yBottom);
        }

        g.setColor(Color.BLACK);
        g.setStroke(new BasicStroke(1.5f));
        g.drawLine(MARGIN_LEFT, MARGIN_TOP, MARGIN_LEFT, yBottom);
        g.drawLine(MARGIN_LEFT, yBottom, xRight, yBottom);

        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        for (int i = 0; i <= 10; i++) {
            int y = yBottom - i * plotH / 10;
            long label = Math.round((double) yMax * i / 10);
            String s = Long.toString(label);
            g.drawString(s, MARGIN_LEFT - 8 - g.getFontMetrics().stringWidth(s), y + 4);
        }
        for (int i = 0; i <= 10; i++) {
            int x = MARGIN_LEFT + i * plotW / 10;
            int label = Math.round((float) xMax * i / 10);
            String s = Integer.toString(label);
            g.drawString(s, x - g.getFontMetrics().stringWidth(s) / 2, yBottom + 18);
        }

        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        g.drawString("step", MARGIN_LEFT + plotW / 2 - 14, yBottom + 40);
        g.drawString("count", MARGIN_LEFT - 50, MARGIN_TOP - 12);
    }

    private void drawLegend(Graphics2D g, List<SpeciesTimeseries> all, long yMax) {
        long threshold = Math.max(5L, yMax / 100L);
        List<SpeciesTimeseries> shown = new ArrayList<>();
        for (SpeciesTimeseries ts : all) {
            if (ts.peak() >= threshold) shown.add(ts);
        }
        shown.sort(Comparator.comparingLong(SpeciesTimeseries::peak).reversed());
        if (shown.size() > 25) shown = shown.subList(0, 25);

        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        int x = WIDTH - MARGIN_RIGHT + 16;
        int y = MARGIN_TOP + 4;
        g.setColor(Color.BLACK);
        g.drawString("species (peak)", x, y);
        y += 16;

        for (SpeciesTimeseries ts : shown) {
            g.setColor(colorFor(ts.id));
            g.setStroke(new BasicStroke(3f));
            g.drawLine(x, y - 4, x + 20, y - 4);
            g.setColor(Color.BLACK);
            g.drawString("#" + ts.id + "  (" + ts.peak() + ")", x + 26, y);
            y += 16;
            if (y > HEIGHT - MARGIN_BOTTOM) break;
        }

        int hidden = all.size() - shown.size();
        if (hidden > 0) {
            g.setColor(new Color(0x55, 0x55, 0x55));
            g.drawString("+" + hidden + " smaller", x, Math.min(y + 4, HEIGHT - 12));
        }
    }

    private static Color colorFor(long id) {
        float hue = (float) ((id * 0.6180339887) % 1.0);
        return Color.getHSBColor(hue, 0.75f, 0.85f);
    }

    private static final class SpeciesTimeseries {
        final long id;
        private final long[] counts;
        private long peak;

        SpeciesTimeseries(long id, int steps) {
            this.id = id;
            this.counts = new long[steps];
            java.util.Arrays.fill(this.counts, -1L);
            this.peak = 0L;
        }

        int length() { return counts.length; }
        long get(int step) { return counts[step]; }
        long peak() { return peak; }

        void set(int step, long value) {
            counts[step] = value;
            if (value > peak) peak = value;
        }
    }
}
