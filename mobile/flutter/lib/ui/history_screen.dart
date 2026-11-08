import 'package:flutter/material.dart';
import 'package:intl/intl.dart';

import '../data/app_state.dart';
import 'theme.dart';
import 'widgets.dart';

/// Saved conversations: a simple list with a back arrow. Pops the selected
/// archive (a JSON map) as the route result.
class HistoryScreen extends StatefulWidget {
  const HistoryScreen({super.key});

  @override
  State<HistoryScreen> createState() => _HistoryScreenState();
}

class _HistoryScreenState extends State<HistoryScreen> {
  late List<dynamic> _conversations;

  @override
  void initState() {
    super.initState();
    _conversations = AppState.instance.prefs.conversations();
  }

  Future<void> _confirmClear() async {
    final ok = await confirm(context, 'Clear history?',
        'This removes the saved conversations on this device. Bookmark and notes are not affected.',
        positive: 'Clear', destructive: true);
    if (!ok || !mounted) return;
    AppState.instance.prefs.setConversations(<dynamic>[]);
    setState(() => _conversations = <dynamic>[]);
  }

  @override
  Widget build(BuildContext context) {
    final palette = Palette.of(context);
    return PageScaffold(
      child: Column(
        children: [
          RafiqBar(title: 'History', subtitle: 'Saved on this device', onBack: () => Navigator.pop(context)),
          Container(height: 1, color: palette.line),
          Expanded(
            child: ListView(
              padding: const EdgeInsets.fromLTRB(16, 14, 16, 24),
              children: _conversations.isEmpty ? _empty(palette) : _list(palette),
            ),
          ),
        ],
      ),
    );
  }

  List<Widget> _empty(Palette palette) => [
        Text('No saved conversations yet', style: Fonts.medium(17, palette.ink)),
        Padding(
          padding: const EdgeInsets.only(top: 6),
          child: Text(
              'Start a chat, then tap New chat. Your previous conversation will appear here '
              '(stored on this device only).',
              style: Fonts.body(13, palette.muted)),
        ),
      ];

  List<Widget> _list(Palette palette) {
    final cards = <Widget>[];
    for (final item in _conversations) {
      if (item is! Map) continue;
      final title = item['title']?.toString() ?? 'Conversation';
      final savedAt = item['savedAt'] is num ? (item['savedAt'] as num).toInt() : 0;
      final turns = item['turns'] is List ? (item['turns'] as List).length : 0;
      final when = savedAt <= 0 ? '' : DateFormat.yMMMd().format(DateTime.fromMillisecondsSinceEpoch(savedAt));
      cards.add(CardBox(
        margin: const EdgeInsets.only(top: 8),
        onTap: () => Navigator.pop(context, Map<String, dynamic>.from(item)),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(title, style: Fonts.medium(15, palette.ink)),
            Padding(
              padding: const EdgeInsets.only(top: 4),
              child: Text('$when${when.isEmpty ? '' : '  ·  '}$turns messages', style: Fonts.body(11, palette.muted)),
            ),
          ],
        ),
      ));
    }
    cards.add(Padding(
      padding: const EdgeInsets.only(top: 12),
      child: Row(children: [TextAction('Clear history', onTap: _confirmClear)]),
    ));
    return cards;
  }
}
