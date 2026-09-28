package com.mandelbulb.export;

import com.mandelbulb.rendering.RenderBackend;
import com.mandelbulb.rendering.RenderResult;
import com.mandelbulb.scene.Scene;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleConsumer;

/**
 * Writes rendered pixels to PNG/JPEG. Export always re-renders the scene off-screen at the
 * requested resolution; it never captures the UI.
 */
public final class ImageExporter {

    public enum Format {
        PNG("png", "PNG image"),
        JPEG("jpg", "JPEG image");

        public final String extension;
        public final String description;

        Format(String extension, String description) {
            this.extension = extension;
            this.description = description;
        }

        @Override
        public String toString() {
            return name();
        }
    }

    private ImageExporter() {
    }

    public static String defaultFileName(int width, int height, Format format) {
        return String.format(Locale.ROOT, "mandelbulb_%dx%d.%s", width, height, format.extension);
    }

    public static Format formatFor(File file) {
        String n = file.getName().toLowerCase(Locale.ROOT);
        return n.endsWith(".jpg") || n.endsWith(".jpeg") ? Format.JPEG : Format.PNG;
    }

    /** Ensures the file name carries the right extension. */
    public static File withExtension(File file, Format format) {
        String n = file.getName().toLowerCase(Locale.ROOT);
        boolean ok = format == Format.PNG ? n.endsWith(".png") : (n.endsWith(".jpg") || n.endsWith(".jpeg"));
        return ok ? file : new File(file.getParentFile(), file.getName() + "." + format.extension);
    }

    /** Renders {@code scene} at full quality and writes it to {@code file}. */
    public static RenderResult renderAndSave(RenderBackend backend, Scene scene, int width, int height, int supersampling,
                                             File file, Format format, DoubleConsumer progress,
                                             BooleanSupplier cancelled) throws IOException {
        RenderResult result = backend.render(scene, width, height, supersampling, progress, cancelled);
        write(result.pixels(), result.width(), result.height(), file, format, 0.95f);
        return result;
    }

    public static void write(int[] argb, int width, int height, File file, Format format, float jpegQuality) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, width, height, argb, 0, width);
        File parent = file.getAbsoluteFile().getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Cannot create directory " + parent);
        }
        if (format == Format.PNG) {
            if (!ImageIO.write(image, "png", file)) {
                throw new IOException("No PNG writer available");
            }
            return;
        }
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) {
            throw new IOException("No JPEG writer available");
        }
        ImageWriter writer = writers.next();
        try (ImageOutputStream out = ImageIO.createImageOutputStream(file)) {
            if (out == null) throw new IOException("Cannot open " + file);
            writer.setOutput(out);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(jpegQuality);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
    }
}
