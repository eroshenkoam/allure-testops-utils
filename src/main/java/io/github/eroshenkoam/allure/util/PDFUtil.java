package io.github.eroshenkoam.allure.util;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;

import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.file.Path;

public final class PDFUtil {

    private PDFUtil() {
    }

    public static void saveToFile(final Path htmlPath, final Path pdfPath) throws Exception {
        try (OutputStream os = new FileOutputStream(pdfPath.toFile())) {
            final PdfRendererBuilder builder = new PdfRendererBuilder()
                    .withUri(htmlPath.toUri().toString())
                    .toStream(os)
                    .useFastMode();
            builder.useFont(() -> ClassLoader.getSystemResourceAsStream("fonts/arial.ttf"), "Arial");
            builder.run();
        }
    }

}
