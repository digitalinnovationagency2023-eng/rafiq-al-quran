import 'dart:convert';

import 'package:flutter/material.dart';

import '../data/api.dart';
import '../data/app_state.dart';
import 'theme.dart';
import 'widgets.dart';

/// Holds one operator snapshot.
class _AdminData {
  Map<String, dynamic>? summary;
  Map<String, dynamic>? datasets;
  Map<String, dynamic>? feedback;
  Map<String, dynamic>? audit;
  Map<String, dynamic>? premium;
  Map<String, dynamic>? reciters;
  String? datasetsError;
  String? feedbackError;
  String? auditError;
  String? premiumError;
  String? recitersError;
}

/// Operator-only screen for the token-protected read-only admin API. Shows
/// server health, feature flags, the imported dataset, recent feedback
/// metadata, audit entries and recitation rights status. The token is stored
/// on this device only and never shown after connecting. Port of `AdminActivity`.
class AdminScreen extends StatefulWidget {
  const AdminScreen({super.key});

  @override
  State<AdminScreen> createState() => _AdminScreenState();
}

class _AdminScreenState extends State<AdminScreen> {
  final TextEditingController _token = TextEditingController();
  String _status = 'Not connected yet. Enter the operator token and choose Connect.';
  _AdminData? _data;
  String? _fatal;
  bool _loading = false;

  @override
  void initState() {
    super.initState();
    _token.text = AppState.instance.prefs.adminToken;
  }

  @override
  void dispose() {
    _token.dispose();
    super.dispose();
  }

  // ------------------------------------------------------------- connecting
  void _connect() {
    final entered = _token.text.trim();
    final token = entered.isEmpty ? AppState.instance.prefs.adminToken : entered;
    if (token.isEmpty) {
      setState(() => _status = 'Enter the operator token to connect.');
      return;
    }
    AppState.instance.prefs.adminToken = token;
    // The token is never displayed after it has been stored.
    _token.clear();
    _load(token);
  }

  void _forgetToken() {
    AppState.instance.prefs.adminToken = '';
    _token.clear();
    setState(() {
      _data = null;
      _fatal = null;
      _status = 'Token forgotten on this device. Enter the token to reconnect.';
    });
    toast(context, 'Operator token removed from this device');
  }

  /// Reads every operator endpoint; the summary decides whether the token works.
  Future<void> _load(String token) async {
    setState(() {
      _loading = true;
      _data = null;
      _fatal = null;
      _status = 'Connecting to ${Api.baseUrl}…';
    });
    final data = _AdminData();
    String? fatal;
    try {
      data.summary = await Api.get('/api/admin/summary', adminToken: token);
    } catch (error) {
      fatal = _describeError(error);
    }
    if (fatal == null) {
      Future<Map<String, dynamic>?> read(String path, void Function(String) onError) async {
        try {
          return await Api.get(path, adminToken: token);
        } catch (error) {
          onError(_describeError(error));
          return null;
        }
      }

      data.datasets = await read('/api/admin/datasets', (e) => data.datasetsError = e);
      data.feedback = await read('/api/admin/feedback?limit=10', (e) => data.feedbackError = e);
      data.audit = await read('/api/admin/audit?limit=10', (e) => data.auditError = e);
      data.premium = await read('/api/admin/premium/features', (e) => data.premiumError = e);
      data.reciters = await read('/api/admin/reciters', (e) => data.recitersError = e);
    }
    if (!mounted) return;
    setState(() {
      _loading = false;
      _fatal = fatal;
      _data = fatal == null ? data : null;
      _status = fatal == null
          ? 'Connected. This screen only reads operator data; it never changes records.'
          : 'The operator data could not be loaded.';
    });
  }

  // ------------------------------------------------------------------ build
  @override
  Widget build(BuildContext context) {
    final palette = Palette.of(context);
    return PageScaffold(
      child: Column(
        children: [
          RafiqBar(
              title: 'Operator',
              subtitle: 'Read-only diagnostics',
              onBack: () => Navigator.pop(context),
              actions: const [ThemeToggle()]),
          const GoldRule(),
          Expanded(
            child: Container(
              color: palette.paper,
              child: ListView(
                padding: const EdgeInsets.fromLTRB(16, 18, 16, 28),
                children: [
                  CardBox(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        const SectionLabel('What this screen is for'),
                        Padding(
                          padding: const EdgeInsets.only(top: 8),
                          child: Text(
                              '• Operator-only, read-only diagnostics for the Rafiq API. It shows service status: '
                              'dataset counts, feature flags, the latest feedback reports, the audit log and '
                              'recitation rights status.\n'
                              '• It never changes records and never shows user data beyond report metadata '
                              '(no contact details from feedback).\n'
                              '• It needs the ADMIN_TOKEN configured on the Rafiq API server by the operator. '
                              'Ask the operator for the x-admin-token value; it is never bundled with the app.\n'
                              '• The token is stored on this device only and can be removed with Forget token.',
                              style: Fonts.body(12, palette.muted)),
                        ),
                      ],
                    ),
                  ),
                  const Padding(padding: EdgeInsets.only(top: 18), child: Label('Operator token')),
                  Padding(
                    padding: const EdgeInsets.only(top: 6),
                    child: RafiqField(controller: _token, hint: 'x-admin-token', obscure: true),
                  ),
                  Padding(
                    padding: const EdgeInsets.only(top: 6),
                    child: Text('Ask the operator for the x-admin-token value; it is never bundled with the app.',
                        style: Fonts.body(11, palette.muted)),
                  ),
                  Padding(
                    padding: const EdgeInsets.only(top: 10),
                    child: Wrap(
                      spacing: 8,
                      runSpacing: 8,
                      children: [
                        RafiqButton('Connect', primary: true, onPressed: _loading ? null : _connect),
                        RafiqButton('Forget token', onPressed: _forgetToken),
                      ],
                    ),
                  ),
                  Padding(padding: const EdgeInsets.only(top: 10), child: Text(_status, style: Fonts.body(12, palette.ink))),
                  Padding(
                    padding: const EdgeInsets.only(top: 8, bottom: 4),
                    child: Text(
                        'The token is stored on this device only. Requests are read-only and use the '
                        'configured data API; the token is never logged or shown after connecting.',
                        style: Fonts.body(11, palette.muted)),
                  ),
                  if (_loading) Text('Reading the operator overview…', style: Fonts.body(12, palette.muted)),
                  if (_fatal != null) _errorCard(palette, _fatal!),
                  if (_data != null) ..._connected(palette, _data!),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _errorCard(Palette palette, String message) => CardBox(
        margin: const EdgeInsets.only(top: 14),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Pill('CONNECTION FAILED', textColor: palette.danger, fill: palette.paper, stroke: palette.danger),
                const Spacer(),
                Flexible(child: Text(Api.baseUrl, style: Fonts.body(10, palette.muted), overflow: TextOverflow.ellipsis)),
              ],
            ),
            Padding(padding: const EdgeInsets.only(top: 10, bottom: 12), child: Text(message, style: Fonts.body(13, palette.ink))),
            RafiqButton('Retry', primary: true, onPressed: _connect),
            Padding(
              padding: const EdgeInsets.only(top: 10),
              child: Text(
                  "A 401 means the token does not match the server's ADMIN_TOKEN. A 503 means "
                  'administration is not enabled on the server at all.',
                  style: Fonts.body(11, palette.muted)),
            ),
          ],
        ),
      );

  List<Widget> _connected(Palette palette, _AdminData data) {
    final summary = data.summary ?? <String, dynamic>{};
    return [
      CardBox(
        margin: const EdgeInsets.only(top: 14),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Pill('CONNECTED', textColor: palette.green, fill: palette.greenSoft, stroke: palette.green),
                const Spacer(),
                Pill('READ-ONLY', textColor: palette.goldDeep, fill: palette.goldSoft, stroke: palette.line),
              ],
            ),
            _kv(palette, 'Data API', Api.baseUrl),
            _kv(palette, 'Generated', _formatTime(Api.optString(summary, 'generatedAt'))),
          ],
        ),
      ),
      _flags(palette, Api.optObject(summary, 'flags')),
      _dataset(palette, data),
      _feedback(palette, data),
      _audit(palette, data),
      _reciters(palette, data),
      _premium(palette, data),
    ];
  }

  Widget _section(Palette palette, String title, List<Widget> children) => CardBox(
        margin: const EdgeInsets.only(top: 14),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [SectionLabel(title), ...children]),
      );

  Widget _flags(Palette palette, Map<String, dynamic>? flags) => _section(palette, 'Feature flags', [
        _flagRow(palette, flags, 'donationsLive', 'Donation collection'),
        _flagRow(palette, flags, 'premiumEnabled', 'Premium tools'),
        _flagRow(palette, flags, 'recitationEnabled', 'Recitation audio'),
        _flagRow(palette, flags, 'aiConfigured', 'AI query interpretation'),
      ]);

  Widget _flagRow(Palette palette, Map<String, dynamic>? flags, String key, String title) {
    final enabled = Api.optBool(flags, key);
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 6),
      child: Row(
        children: [
          Expanded(child: Text(title, style: Fonts.body(12, palette.ink))),
          Pill(enabled ? 'ENABLED' : 'DISABLED',
              textColor: enabled ? palette.green : palette.muted,
              fill: enabled ? palette.greenSoft : palette.paper,
              stroke: enabled ? palette.green : palette.line),
        ],
      ),
    );
  }

  Widget _dataset(Palette palette, _AdminData data) {
    final summaryDataset = Api.optObject(data.summary, 'dataset');
    final counts = Api.optObject(data.summary, 'counts');
    final datasets = Api.array(data.datasets, 'datasets');
    final first = datasets.isNotEmpty && datasets.first is Map ? Map<String, dynamic>.from(datasets.first as Map) : null;
    String pick(String key, [String? summaryKey]) {
      var value = Api.optString(first, key);
      if (value.isEmpty) value = Api.optString(summaryDataset, summaryKey ?? key);
      return value;
    }

    var verses = Api.optString(first, 'verseCount');
    if (!RegExp(r'^\d+$').hasMatch(verses) && counts != null) verses = '${Api.optInt(counts, 'verses')}';
    var chapters = Api.optString(first, 'chapterCount');
    if (!RegExp(r'^\d+$').hasMatch(chapters) && counts != null) chapters = '${Api.optInt(counts, 'chapters')}';
    var importedAt = Api.optString(first, 'importedAt');
    if (importedAt.isEmpty) {
      importedAt = Api.optString(summaryDataset, 'imported_at', Api.optString(summaryDataset, 'importedAt'));
    }
    final checksum = pick('checksum');
    return _section(palette, 'Imported dataset', [
      _kv(palette, 'Dataset', pick('dataset')),
      _kv(palette, 'Version', pick('version')),
      _kv(palette, 'Verses', verses),
      _kv(palette, 'Chapters', chapters),
      _kv(palette, 'Translations', counts == null ? '' : '${Api.optInt(counts, 'translations')}'),
      _kv(palette, 'Checksum', checksum.length > 16 ? '${checksum.substring(0, 16)}…' : checksum),
      _kv(palette, 'Imported', _formatTime(importedAt)),
      if (data.datasetsError != null) _sectionError(palette, data.datasetsError!),
    ]);
  }

  Widget _feedback(Palette palette, _AdminData data) {
    if (data.feedbackError != null) return _section(palette, 'Latest feedback', [_sectionError(palette, data.feedbackError!)]);
    final rows = Api.array(data.feedback, 'feedback').whereType<Map>().take(10).toList();
    return _section(palette, 'Latest feedback', [
      for (var i = 0; i < rows.length; i++) ...[
        if (i > 0) const Hairline(),
        Builder(builder: (context) {
          final row = Map<String, dynamic>.from(rows[i]);
          final status = Api.optString(row, 'status', 'open');
          return Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Expanded(
                    child: Text('#${Api.optString(row, 'id', '?')}  ·  ${Api.optString(row, 'category', 'uncategorised')}',
                        style: Fonts.body(12, palette.ink)),
                  ),
                  Pill(status.toUpperCase(),
                      textColor: _statusColor(palette, status), fill: _statusFill(palette, status), stroke: _statusColor(palette, status)),
                ],
              ),
              Padding(
                padding: const EdgeInsets.only(top: 2),
                child: Text(_formatTime(Api.optString(row, 'createdAt')), style: Fonts.body(10, palette.muted)),
              ),
            ],
          );
        }),
      ],
      if (rows.isEmpty) _emptyRow(palette, 'No feedback rows returned.'),
    ]);
  }

  Widget _audit(Palette palette, _AdminData data) {
    if (data.auditError != null) return _section(palette, 'Recent audit entries', [_sectionError(palette, data.auditError!)]);
    final entries = Api.array(data.audit, 'entries').whereType<Map>().take(10).toList();
    return _section(palette, 'Recent audit entries', [
      for (var i = 0; i < entries.length; i++) ...[
        if (i > 0) const Hairline(),
        Text(Api.optString(Map<String, dynamic>.from(entries[i]), 'action', 'unknown action'), style: Fonts.body(12, palette.ink)),
        Padding(
          padding: const EdgeInsets.only(top: 2),
          child: Text(
              '${Api.optString(Map<String, dynamic>.from(entries[i]), 'actor', 'admin')}  ·  '
              '${_formatTime(Api.optString(Map<String, dynamic>.from(entries[i]), 'at'))}',
              style: Fonts.body(10, palette.muted)),
        ),
      ],
      if (entries.isEmpty) _emptyRow(palette, 'No audit entries returned.'),
    ]);
  }

  Widget _reciters(Palette palette, _AdminData data) {
    if (data.recitersError != null) return _section(palette, 'Recitation reciters', [_sectionError(palette, data.recitersError!)]);
    final reciters = Api.array(data.reciters, 'reciters').whereType<Map>().take(10).toList();
    return _section(palette, 'Recitation reciters', [
      for (var i = 0; i < reciters.length; i++) ...[
        if (i > 0) const Hairline(),
        Builder(builder: (context) {
          final reciter = Map<String, dynamic>.from(reciters[i]);
          final rights = Api.optString(reciter, 'rightsStatus', 'unverified');
          final enabled = Api.optBool(reciter, 'enabled');
          return Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Expanded(child: Text(Api.optString(reciter, 'name', 'Reciter'), style: Fonts.body(12, palette.ink))),
                  Pill(rights.toUpperCase(),
                      textColor: _rightsColor(palette, rights), fill: _rightsFill(palette, rights), stroke: _rightsColor(palette, rights)),
                ],
              ),
              Padding(
                padding: const EdgeInsets.only(top: 2),
                child: Text(
                    '${Api.optString(reciter, 'style', 'Recitation')}  ·  ${enabled ? 'enabled on server' : 'disabled on server'}',
                    style: Fonts.body(10, palette.muted)),
              ),
            ],
          );
        }),
      ],
      if (reciters.isEmpty) _emptyRow(palette, 'No reciter rows returned.'),
      Padding(
        padding: const EdgeInsets.only(top: 10),
        child: Text(
            'The client player streams Quran.com audio under the recorded recitation rights decision '
            '(21 October 2026). These server reciter rows and RECITATION_ENABLED are a separate '
            'switch and can stay unverified/disabled.',
            style: Fonts.body(10, palette.muted)),
      ),
    ]);
  }

  Widget _premium(Palette palette, _AdminData data) {
    if (data.premiumError != null) return _section(palette, 'Premium tools', [_sectionError(palette, data.premiumError!)]);
    final premium = data.premium ?? <String, dynamic>{};
    final enabled = Api.optBool(premium, 'enabled');
    final features = Api.array(premium, 'features').whereType<Map>().take(10).toList();
    return _section(palette, 'Premium tools', [
      Row(
        children: [
          Expanded(child: Text('Premium features', style: Fonts.body(12, palette.ink))),
          Pill(enabled ? 'ENABLED' : 'DISABLED',
              textColor: enabled ? palette.green : palette.muted,
              fill: enabled ? palette.greenSoft : palette.paper,
              stroke: enabled ? palette.green : palette.line),
        ],
      ),
      for (var i = 0; i < features.length; i++) ...[
        if (i > 0) const Hairline(),
        Builder(builder: (context) {
          final feature = Map<String, dynamic>.from(features[i]);
          return Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Expanded(
                    child: Text(Api.optString(feature, 'name', Api.optString(feature, 'key', 'Feature')),
                        style: Fonts.body(12, palette.ink)),
                  ),
                  Pill(Api.optBool(feature, 'enabled') ? 'ON' : 'OFF',
                      textColor: palette.muted, fill: palette.paper, stroke: palette.line),
                ],
              ),
              Padding(
                padding: const EdgeInsets.only(top: 2),
                child: Text(
                    '${Api.optString(feature, 'currency')} ${Api.optInt(feature, 'priceMinor') / 100.0} · '
                    '${Api.optString(feature, 'billing', 'one-off')}',
                    style: Fonts.body(10, palette.muted)),
              ),
            ],
          );
        }),
      ],
      if (features.isEmpty) _emptyRow(palette, 'No premium features returned.'),
    ]);
  }

  // ---------------------------------------------------------------- helpers
  Widget _kv(Palette palette, String key, String value) => Padding(
        padding: const EdgeInsets.symmetric(vertical: 4),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Expanded(child: Text(key, style: Fonts.body(12, palette.muted))),
            ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 240),
              child: Text(value.isEmpty ? '—' : value, textAlign: TextAlign.right, style: Fonts.body(12, palette.ink)),
            ),
          ],
        ),
      );

  Widget _sectionError(Palette palette, String message) =>
      Padding(padding: const EdgeInsets.only(top: 8), child: Text(message, style: Fonts.body(11, palette.danger)));

  Widget _emptyRow(Palette palette, String message) =>
      Padding(padding: const EdgeInsets.only(top: 8), child: Text(message, style: Fonts.body(11, palette.muted)));

  Color _statusColor(Palette palette, String status) {
    if (status == 'resolved') return palette.green;
    if (status == 'rejected') return palette.danger;
    if (status == 'open') return palette.goldDeep;
    return palette.muted;
  }

  Color _statusFill(Palette palette, String status) {
    if (status == 'resolved') return palette.greenSoft;
    if (status == 'open') return palette.goldSoft;
    return palette.paper;
  }

  Color _rightsColor(Palette palette, String rights) {
    if (rights == 'verified') return palette.green;
    if (rights == 'blocked') return palette.danger;
    return palette.goldDeep;
  }

  Color _rightsFill(Palette palette, String rights) {
    if (rights == 'verified') return palette.greenSoft;
    if (rights == 'blocked') return palette.paper;
    return palette.goldSoft;
  }

  /// Friendly, status-aware reading of Api errors; the token is never included.
  String _describeError(Object error) {
    final raw = error.toString();
    if (raw.contains('Online services are not configured in this build')) {
      return 'Online services are not configured in this build.';
    }
    if (raw.contains('Administrator authentication required') || raw.contains('(401)') || (error is ApiException && error.status == 401)) {
      return 'Token rejected (401)';
    }
    if (raw.contains('ADMIN_TOKEN is unset') || raw.contains('Administration is not configured')) {
      return 'Administration is not enabled on the server (ADMIN_TOKEN unset)';
    }
    try {
      final payload = jsonDecode(raw);
      if (payload is Map) {
        final message = payload['error']?.toString() ?? '';
        if (message.isNotEmpty) return _shorten(message);
      }
    } catch (_) {
      // The payload is not JSON; fall through to the raw message.
    }
    return _shorten(raw.isEmpty ? 'The request could not be completed.' : raw);
  }

  String _shorten(String value) {
    final trimmed = value.trim();
    return trimmed.length <= 160 ? trimmed : '${trimmed.substring(0, 157)}…';
  }

  String _formatTime(String iso) {
    if (iso.isEmpty) return '—';
    var value = iso.replaceAll('T', ' ');
    if (value.length > 19) value = value.substring(0, 19);
    return value;
  }
}
