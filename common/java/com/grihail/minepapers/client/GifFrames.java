package com.grihail.minepapers.client;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;

import org.w3c.dom.Node;

/** Кадры анимированного GIF. ImageIO отдаёт кадры как есть — кусками со своим местом на экране и способом очистки, — поэтому собираем их в полные картинки сами, как это делают браузеры. Работает в фоновом потоке. */
final class GifFrames {

    private static final String IMAGE_FORMAT = "javax_imageio_gif_image_1.0";
    private static final String STREAM_FORMAT = "javax_imageio_gif_stream_1.0";
    /** Меньшие задержки браузеры считают ошибкой файла и показывают кадр 100 мс — делаем так же. */
    private static final int MIN_DELAY_MS = 20, DEFAULT_DELAY_MS = 100;
    /** Кадры не уменьшаются мельче этого по большей стороне, даже если их очень много: лучше пропустить часть кадров. */
    private static final int MIN_SIDE = 128;

    private GifFrames() {}

    /** Пиксели кадров (ARGB, строки сверху вниз), их размер, длительности в мс и исходный размер GIF. */
    static final class Animation {
        final List<int[]> frames = new ArrayList<int[]>();
        final List<Integer> delays = new ArrayList<Integer>();
        int width, height, origW, origH;
    }

    /** GIF больше чем из одного кадра. Читатель должен быть открыт с метаданными и без seekForwardOnly. */
    static boolean isAnimated(ImageReader reader) throws IOException {
        return "gif".equalsIgnoreCase(reader.getFormatName()) && reader.getNumImages(true) > 1;
    }

    /** Все кадры, собранные целиком. Кадры уменьшаются так, чтобы большая сторона была не больше maxSide, а все кадры вместе — не больше budget пикселей; если и этого мало — берётся каждый k-й кадр, а его длительность — сумма пропущенных. */
    static Animation read(ImageReader reader, int maxSide, long budget) throws IOException {
        int n = reader.getNumImages(true);
        int[] screen = screenSize(reader);
        int sw = screen[0], sh = screen[1];
        double side = Math.max(sw, sh);
        double fit = Math.sqrt((double) budget / ((double) n * sw * sh));
        double scale = Math.min(1, Math.min(maxSide / side, Math.max(fit, MIN_SIDE / side)));

        Animation anim = new Animation();
        anim.origW = sw;
        anim.origH = sh;
        anim.width = Math.max(1, (int) Math.round(sw * scale));
        anim.height = Math.max(1, (int) Math.round(sh * scale));
        int step = (int) Math.max(1, Math.ceil((double) n * anim.width * anim.height / budget));

        BufferedImage canvas = new BufferedImage(sw, sh, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = canvas.createGraphics();
        try {
            for (int i = 0; i < n; i++) {
                BufferedImage part = reader.read(i);
                IIOMetadataNode meta = tree(reader.getImageMetadata(i), IMAGE_FORMAT);
                IIOMetadataNode descriptor = child(meta, "ImageDescriptor");
                IIOMetadataNode control = child(meta, "GraphicControlExtension");
                int left = intAttr(descriptor, "imageLeftPosition", 0);
                int top = intAttr(descriptor, "imageTopPosition", 0);
                String disposal = control != null ? control.getAttribute("disposalMethod") : "none";
                int delay = intAttr(control, "delayTime", 0) * 10;
                if (delay < MIN_DELAY_MS) delay = DEFAULT_DELAY_MS;

                int[] previous = "restoreToPrevious".equals(disposal) ? canvas.getRGB(0, 0, sw, sh, null, 0, sw) : null;
                g.setComposite(AlphaComposite.SrcOver);
                g.drawImage(part, left, top, null);

                if (i % step == 0) {
                    anim.frames.add(pixels(canvas, anim.width, anim.height));
                    anim.delays.add(delay);
                } else {
                    int last = anim.delays.size() - 1;
                    anim.delays.set(last, anim.delays.get(last) + delay);
                }

                // Что останется под следующим кадром.
                if ("restoreToBackgroundColor".equals(disposal)) {
                    g.setComposite(AlphaComposite.Clear);
                    g.fillRect(left, top, part.getWidth(), part.getHeight());
                } else if (previous != null) {
                    canvas.setRGB(0, 0, sw, sh, previous, 0, sw);
                }
            }
        } finally {
            g.dispose();
        }
        return anim;
    }

    /** Размер "экрана" GIF — общий для всех кадров. */
    static int[] screenSize(ImageReader reader) throws IOException {
        IIOMetadataNode screen = child(tree(reader.getStreamMetadata(), STREAM_FORMAT), "LogicalScreenDescriptor");
        return new int[] { intAttr(screen, "logicalScreenWidth", 0), intAttr(screen, "logicalScreenHeight", 0) };
    }

    private static int[] pixels(BufferedImage canvas, int w, int h) {
        BufferedImage img = canvas;
        if (w != canvas.getWidth() || h != canvas.getHeight()) {
            img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(canvas, 0, 0, w, h, null);
            g.dispose();
        }
        return img.getRGB(0, 0, w, h, null, 0, w);
    }

    private static IIOMetadataNode tree(IIOMetadata metadata, String format) {
        return (IIOMetadataNode) metadata.getAsTree(format);
    }

    private static IIOMetadataNode child(IIOMetadataNode parent, String name) {
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (name.equals(n.getNodeName())) return (IIOMetadataNode) n;
        }
        return null;
    }

    private static int intAttr(IIOMetadataNode node, String name, int def) {
        return node != null ? Integer.parseInt(node.getAttribute(name)) : def;
    }
}
