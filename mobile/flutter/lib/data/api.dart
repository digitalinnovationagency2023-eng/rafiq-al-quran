import 'dart:convert';

import 'package:http/http.dart' as http;

/// Thrown for HTTP status >= 400; the message is the response body.
class ApiException implements Exception {
  final int status;
  final String message;

  const ApiException(this.status, this.message);

  @override
  String toString() => message;
}

/// Optional online services (assistant, feedback, update check, operator
/// diagnostics). The core reading and search experience never calls this class.
/// If the base URL is empty, the app stays fully offline. Port of `Api.java`.
class Api {
  Api._();

  /// Same origin as the Android `api_base_url` string.
  static String baseUrl = 'https://rqmbooks.com/rafiq-api';

  static const Duration connectTimeout = Duration(seconds: 12);

  /// Overridable for tests.
  static http.Client Function() clientFactory = http.Client.new;

  static Future<Map<String, dynamic>> get(String path, {String? adminToken}) =>
      _request('GET', path, null, adminToken, const Duration(seconds: 15));

  static Future<Map<String, dynamic>> post(String path, Map<String, dynamic> body) =>
      _request('POST', path, body, null, const Duration(seconds: 15));

  /// Optional assistant status check (does not send any user text).
  static Future<Map<String, dynamic>> assistantStatus() =>
      _request('GET', '/api/assistant/status', null, null, const Duration(seconds: 8));

  /// Latest published app version, used only for the in-app update prompt.
  static Future<Map<String, dynamic>> appVersion() =>
      _request('GET', '/api/app/version', null, null, const Duration(seconds: 8));

  /// Optional AI assistance: the model only returns a short chat reply, search
  /// terms and/or OCR text for an attached image. Quran text is never requested
  /// from, or accepted from, the model.
  static Future<Map<String, dynamic>> assistant(Map<String, dynamic> body) =>
      _request('POST', '/api/assistant', body, null, const Duration(seconds: 45));

  /// Optional grounded summary of the verses the app already retrieved.
  static Future<Map<String, dynamic>> assistantSummary(Map<String, dynamic> body) =>
      _request('POST', '/api/assistant/summary', body, null, const Duration(seconds: 45));

  /// Deeper Quran-only reasoning answer over a larger supplied verse set.
  static Future<Map<String, dynamic>> assistantAnswer(Map<String, dynamic> body) =>
      _request('POST', '/api/assistant/answer', body, null, const Duration(seconds: 60));

  static Future<Map<String, dynamic>> _request(
      String method, String path, Map<String, dynamic>? body, String? adminToken, Duration readTimeout) async {
    final base = baseUrl.trim();
    if (base.isEmpty) throw StateError('Online services are not configured in this build.');
    final url = Uri.parse(base.replaceAll(RegExp(r'/$'), '') + path);
    final headers = <String, String>{'Accept': 'application/json'};
    if (adminToken != null && adminToken.isNotEmpty) headers['x-admin-token'] = adminToken;
    final client = clientFactory();
    try {
      http.Response response;
      final timeout = connectTimeout + readTimeout;
      if (body != null) {
        headers['Content-Type'] = 'application/json; charset=utf-8';
        response = await client.post(url, headers: headers, body: jsonEncode(body)).timeout(timeout);
      } else {
        response = await client.get(url, headers: headers).timeout(timeout);
      }
      final payload = utf8.decode(response.bodyBytes, allowMalformed: true);
      if (response.statusCode >= 400) {
        throw ApiException(response.statusCode, payload.isEmpty ? 'Request failed (${response.statusCode})' : payload);
      }
      if (payload.isEmpty) return <String, dynamic>{};
      final decoded = jsonDecode(payload);
      return decoded is Map ? Map<String, dynamic>.from(decoded) : <String, dynamic>{};
    } finally {
      client.close();
    }
  }

  static List<dynamic> array(Map<String, dynamic>? object, String key) {
    final value = object?[key];
    return value is List ? value : <dynamic>[];
  }

  /// A 429 or an explicit quota message from the assistant endpoints.
  static bool isQuotaError(Object? error) {
    if (error is ApiException && error.status == 429) return true;
    final message = (error?.toString() ?? '').toLowerCase();
    return message.contains('429') ||
        message.contains('daily ai') ||
        message.contains('ai limit') ||
        message.contains('quota');
  }

  static String optString(Map<String, dynamic>? object, String key, [String fallback = '']) {
    final value = object?[key];
    return value == null ? fallback : value.toString();
  }

  static bool optBool(Map<String, dynamic>? object, String key, [bool fallback = false]) {
    final value = object?[key];
    if (value is bool) return value;
    if (value is String) return value.toLowerCase() == 'true';
    return fallback;
  }

  static int optInt(Map<String, dynamic>? object, String key, [int fallback = 0]) {
    final value = object?[key];
    if (value is int) return value;
    if (value is num) return value.toInt();
    return int.tryParse(value?.toString() ?? '') ?? fallback;
  }

  static Map<String, dynamic>? optObject(Map<String, dynamic>? object, String key) {
    final value = object?[key];
    return value is Map ? Map<String, dynamic>.from(value) : null;
  }
}
