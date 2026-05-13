package com.example.situation.security;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import org.springframework.stereotype.Service;

@Service
public class MfaQrCodeService {

    private static final int QR_SIZE = 240;
    private static final int QUIET_ZONE = 4;
    private static final String DARK = "#1F2937";
    private static final String LIGHT = "#FFFFFF";

    public String buildSvg(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        try {
            BitMatrix matrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, QR_SIZE, QR_SIZE);
            return toSvg(matrix);
        } catch (WriterException ex) {
            throw new IllegalStateException("Unable to generate MFA QR code", ex);
        }
    }

    private static String toSvg(BitMatrix matrix) {
        int width = matrix.getWidth();
        int height = matrix.getHeight();
        int viewBoxWidth = width + (QUIET_ZONE * 2);
        int viewBoxHeight = height + (QUIET_ZONE * 2);

        StringBuilder builder = new StringBuilder(viewBoxWidth * viewBoxHeight * 2);
        builder.append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 ")
            .append(viewBoxWidth)
            .append(' ')
            .append(viewBoxHeight)
            .append("\" shape-rendering=\"crispEdges\">")
            .append("<rect width=\"100%\" height=\"100%\" fill=\"")
            .append(LIGHT)
            .append("\"/>")
            .append("<path fill=\"")
            .append(DARK)
            .append("\" d=\"");

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (matrix.get(x, y)) {
                    builder.append('M')
                        .append(x + QUIET_ZONE)
                        .append(' ')
                        .append(y + QUIET_ZONE)
                        .append("h1v1h-1z");
                }
            }
        }

        builder.append("\"/></svg>");
        return builder.toString();
    }
}
