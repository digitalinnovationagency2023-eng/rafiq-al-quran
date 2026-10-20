package com.rqmbooks.rafiqalquran;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.Base64;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Reads an attachment chosen by the user.
 *
 * <p>Plain text and .docx files are read entirely on the device. Images are kept
 * as bytes and can only be read by the optional AI service on the app's server.
 * Nothing is uploaded unless the user sends a message with an image attached and
 * AI assistance is enabled.
 */
public final class Attachments {
    public static final int MAX_BYTES = 5 * 1024 * 1024;
    public static final int MAX_TEXT_CHARS = 8000;

    private Attachments() {}

    public static final class File {
        public String name = "";
        public String mime = "";
        /** Extracted text for text/.docx files (empty for images). */
        public String text = "";
        /** Raw bytes for images only. */
        public byte[] imageBytes;
        public boolean image;
        /** True when the app can use the attachment without AI. */
        public boolean readableLocally;
        public String error;

        public String summary() {
            if (image) return name + " (image — AI-assisted reading)";
            if (readableLocally) return name + " (" + text.length() + " characters read)";
            return name;
        }
    }

    /** Reads the picked document. Never throws; check {@link File#error}. */
    public static File read(Context context, Uri uri) {
        File result = new File();
        if (uri == null) {
            result.error = "No file was selected.";
            return result;
        }
        try {
            result.name = displayName(context, uri);
            String mime = context.getContentResolver().getType(uri);
            result.mime = mime == null ? "" : mime;
            byte[] bytes = readBytes(context, uri);
            if (bytes == null) {
                result.error = "The file could not be read.";
                return result;
            }
            String lower = result.name.toLowerCase(Locale.ROOT);
            if (result.mime.startsWith("text/") || lower.endsWith(".txt") || lower.endsWith(".md")) {
                result.text = truncate(new String(bytes, StandardCharsets.UTF_8));
                result.readableLocally = !result.text.trim().isEmpty();
                if (!result.readableLocally) result.error = "The text file appears to be empty.";
                return result;
            }
            if (lower.endsWith(".docx")) {
                result.text = truncate(docxText(bytes));
                result.readableLocally = !result.text.trim().isEmpty();
                if (!result.readableLocally) result.error = "No readable text was found in this .docx file.";
                return result;
            }
            if (result.mime.startsWith("image/")) {
                result.image = true;
                result.imageBytes = bytes;
                result.readableLocally = false;
                return result;
            }
            result.error = "This file type is not supported yet. Attach a .txt or .docx file, or an image "
                    + "when AI reading is enabled.";
            return result;
        } catch (Exception error) {
            result.error = "The file could not be read: " + error.getMessage();
            return result;
        }
    }

    /** Base64 string used for the optional AI image request. */
    public static String base64(byte[] data) {
        return Base64.encodeToString(data, Base64.NO_WRAP);
    }

    /** Small JPEG data URL transform is not needed; the mime type is sent separately. */
    public static boolean looksLikeImage(String mime) {
        return mime != null && mime.startsWith("image/");
    }

    /** Best-effort thumbnail decode used to show the user what was attached. */
    public static Bitmap thumbnail(byte[] data) {
        if (data == null) return null;
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = 4;
            return BitmapFactory.decodeByteArray(data, 0, data.length, options);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String displayName(Context context, Uri uri) {
        String name = null;
        try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0 && !cursor.isNull(index)) name = cursor.getString(index);
            }
        } catch (Exception ignored) {
            // fall through to the URI's last path segment
        }
        if (name == null || name.trim().isEmpty()) {
            String segment = uri.getLastPathSegment();
            name = segment == null ? "attachment" : segment;
        }
        return name;
    }

    private static byte[] readBytes(Context context, Uri uri) throws Exception {
        try (InputStream input = context.getContentResolver().openInputStream(uri)) {
            if (input == null) return null;
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[16 * 1024];
            int total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > MAX_BYTES) return null;
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    private static String truncate(String value) {
        if (value == null) return "";
        String cleaned = value.replace("\r\n", "\n").replace('\r', '\n');
        if (cleaned.length() > MAX_TEXT_CHARS) cleaned = cleaned.substring(0, MAX_TEXT_CHARS);
        return cleaned;
    }

    /** Extracts text from a .docx archive without any external dependency. */
    static String docxText(byte[] data) {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(data))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!"word/document.xml".equals(entry.getName())) continue;
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int read;
                while ((read = zip.read(buffer)) != -1) output.write(buffer, 0, read);
                String xml = new String(output.toByteArray(), StandardCharsets.UTF_8);
                xml = xml.replaceAll("(?i)</w:p>", "\n");
                xml = xml.replaceAll("(?i)<w:tab[^>]*/>", " ");
                xml = xml.replaceAll("<[^>]+>", "");
                xml = xml.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                        .replace("&quot;", "\"").replace("&apos;", "'");
                return xml.replaceAll("\\n{3,}", "\n\n").trim();
            }
        } catch (Exception ignored) {
            // fall through to empty text
        }
        return "";
    }
}
