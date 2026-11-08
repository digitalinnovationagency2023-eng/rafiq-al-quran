import 'package:flutter/material.dart';

import '../data/api.dart';
import '../data/app_state.dart';
import 'theme.dart';
import 'widgets.dart';

/// Feedback form posting to `/api/feedback` (category, description 5–4000
/// chars, optional verse reference and contact, diagnostics consent).
class ReportForm extends StatefulWidget {
  const ReportForm({super.key});

  static const List<String> keys = [
    'missing_verse', 'incorrect_reference', 'translation_display', 'search_language', //
    'audio_recitation', 'accessibility', 'bug', 'suggestion',
  ];
  static const List<String> labels = [
    'Potentially missing verses', 'Incorrect verse reference', 'Translation display', 'Search or language', //
    'Audio or recitation', 'Accessibility', 'Bug', 'Suggestion',
  ];

  @override
  State<ReportForm> createState() => _ReportFormState();
}

class _ReportFormState extends State<ReportForm> {
  final TextEditingController _description = TextEditingController();
  final TextEditingController _verse = TextEditingController();
  final TextEditingController _contact = TextEditingController();
  int _category = 0;
  bool _consent = false;
  bool _sending = false;
  String _status =
      'Reports are reviewed before anything is treated as a correction. Nothing is sent to WhatsApp or another third party automatically.';

  @override
  void dispose() {
    _description.dispose();
    _verse.dispose();
    _contact.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    final text = _description.text.trim();
    if (text.length < 5) {
      setState(() => _status = 'Please describe the issue in at least a few words.');
      return;
    }
    if (text.length > 4000) {
      setState(() => _status = 'Please keep the description under 4000 characters.');
      return;
    }
    setState(() {
      _sending = true;
      _status = 'Sending…';
    });
    final language = AppState.instance.prefs.language;
    final body = <String, dynamic>{
      'category': ReportForm.keys[_category],
      'description': text,
      'language': language,
      if (_verse.text.trim().isNotEmpty) 'verseReference': _verse.text.trim(),
      if (_contact.text.trim().isNotEmpty) 'contact': _contact.text.trim(),
      'consent': _consent,
      if (_consent) 'diagnostics': <String, dynamic>{'platform': 'ios', 'language': language},
    };
    try {
      final response = await Api.post('/api/feedback', body);
      if (!mounted) return;
      setState(() => _status = Api.optString(response, 'message', 'Thank you. Your report has been recorded for review.'));
    } catch (_) {
      if (!mounted) return;
      setState(() => _status = 'The report could not be sent. Check your connection and try again.');
    } finally {
      if (mounted) setState(() => _sending = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final palette = Palette.of(context);
    return Padding(
      padding: const EdgeInsets.only(top: 12),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          DropdownButtonFormField<int>(
            initialValue: _category,
            items: [
              for (var i = 0; i < ReportForm.labels.length; i++)
                DropdownMenuItem(value: i, child: Text(ReportForm.labels[i], style: Fonts.body(14, palette.ink))),
            ],
            onChanged: (value) => setState(() => _category = value ?? 0),
          ),
          const SizedBox(height: 10),
          RafiqField(controller: _description, hint: 'What happened?', minLines: 3, maxLines: 8),
          const SizedBox(height: 10),
          RafiqField(controller: _verse, hint: 'Verse reference (optional), e.g. 2:255'),
          const SizedBox(height: 10),
          RafiqField(controller: _contact, hint: 'Contact detail (optional)'),
          CheckboxListTile(
            value: _consent,
            onChanged: (value) => setState(() => _consent = value ?? false),
            contentPadding: EdgeInsets.zero,
            controlAffinity: ListTileControlAffinity.leading,
            activeColor: palette.green,
            title: Text('Include basic diagnostics (interface language and platform) with this report',
                style: Fonts.body(13.5, palette.ink)),
          ),
          Padding(
            padding: const EdgeInsets.only(top: 8),
            child: RafiqButton('Send report', primary: true, expand: true, onPressed: _sending ? null : _submit),
          ),
          Padding(padding: const EdgeInsets.only(top: 10), child: Text(_status, style: Fonts.body(11, palette.muted))),
        ],
      ),
    );
  }
}
