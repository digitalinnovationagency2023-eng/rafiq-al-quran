import 'dart:convert';
import 'dart:typed_data';

import 'package:archive/archive.dart';

/// A picked attachment (port of `Attachments.File`).
class AttachmentFile {
  String name = '';
  String mime = '';

  /// Extracted text for text/.docx files (empty for images).
  String text = '';

  /// Raw bytes for images only.
  Uint8List? imageBytes;
  bool image = false;

  /// True when the app can use the attachment without AI.
  bool readableLocally = false;
  String? error;

  String summary() {
    if (image) return '$name (image — AI-assisted reading)';
    if (readableLocally) return '$name (${text.length} characters read)';
    return name;
  }
}

/// Reads an attachment chosen by the user.
///
/// Plain text and .docx files are read entirely on the device. Images are kept
/// as bytes and can only be read by the optional AI service on the app's server.
/// Nothing is uploaded unless the user sends a message with an image attached and
/// AI assistance is enabled.
class Attachments {
  Attachments._();

  static const int maxBytes = 5 * 1024 * 1024;
  static const int maxTextChars = 8000;

  /// Reads picked bytes. Never throws; check [AttachmentFile.error].
  static AttachmentFile read(String? name, Uint8List? bytes, {String? mime}) {
    final result = AttachmentFile();
    if (name == null || bytes == null) {
      result.error = 'No file was selected.';
      return result;
    }
    try {
      result.name = name.trim().isEmpty ? 'attachment' : name;
      result.mime = mime ?? guessMime(result.name);
      if (bytes.length > maxBytes) {
        result.error = 'The file could not be read.';
        return result;
      }
      final lower = result.name.toLowerCase();
      if (result.mime.startsWith('text/') || lower.endsWith('.txt') || lower.endsWith('.md')) {
        result.text = truncate(utf8.decode(bytes, allowMalformed: true));
        result.readableLocally = result.text.trim().isNotEmpty;
        if (!result.readableLocally) result.error = 'The text file appears to be empty.';
        return result;
      }
      if (lower.endsWith('.docx')) {
        result.text = truncate(docxText(bytes));
        result.readableLocally = result.text.trim().isNotEmpty;
        if (!result.readableLocally) result.error = 'No readable text was found in this .docx file.';
        return result;
      }
      if (result.mime.startsWith('image/')) {
        result.image = true;
        result.imageBytes = bytes;
        result.readableLocally = false;
        return result;
      }
      result.error = 'This file type is not supported yet. Attach a .txt or .docx file, or an image '
          'when AI reading is enabled.';
      return result;
    } catch (error) {
      result.error = 'The file could not be read: $error';
      return result;
    }
  }

  /// Base64 string used for the optional AI image request.
  static String base64(Uint8List data) => base64Encode(data);

  static bool looksLikeImage(String? mime) => mime != null && mime.startsWith('image/');

  static String guessMime(String name) {
    final lower = name.toLowerCase();
    if (lower.endsWith('.txt')) return 'text/plain';
    if (lower.endsWith('.md')) return 'text/markdown';
    if (lower.endsWith('.docx')) return 'application/vnd.openxmlformats-officedocument.wordprocessingml.document';
    if (lower.endsWith('.png')) return 'image/png';
    if (lower.endsWith('.jpg') || lower.endsWith('.jpeg')) return 'image/jpeg';
    if (lower.endsWith('.webp')) return 'image/webp';
    if (lower.endsWith('.heic')) return 'image/heic';
    if (lower.endsWith('.gif')) return 'image/gif';
    if (lower.endsWith('.pdf')) return 'application/pdf';
    return '';
  }

  static String truncate(String? value) {
    if (value == null) return '';
    var cleaned = value.replaceAll('\r\n', '\n').replaceAll('\r', '\n');
    if (cleaned.length > maxTextChars) cleaned = cleaned.substring(0, maxTextChars);
    return cleaned;
  }

  /// Extracts text from a .docx archive (word/document.xml).
  static String docxText(Uint8List data) {
    try {
      final archive = ZipDecoder().decodeBytes(data);
      for (final entry in archive) {
        if (entry.name != 'word/document.xml' || !entry.isFile) continue;
        var xml = utf8.decode(entry.content as List<int>, allowMalformed: true);
        xml = xml.replaceAll(RegExp(r'</w:p>', caseSensitive: false), '\n');
        xml = xml.replaceAll(RegExp(r'<w:tab[^>]*/>', caseSensitive: false), ' ');
        xml = xml.replaceAll(RegExp(r'<[^>]+>'), '');
        xml = xml
            .replaceAll('&amp;', '&')
            .replaceAll('&lt;', '<')
            .replaceAll('&gt;', '>')
            .replaceAll('&quot;', '"')
            .replaceAll('&apos;', "'");
        return xml.replaceAll(RegExp(r'\n{3,}'), '\n\n').trim();
      }
    } catch (_) {
      // fall through to empty text
    }
    return '';
  }
}
