import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../data/app_state.dart';
import '../../logic/logic.dart';
import '../player_screen.dart';
import '../theme.dart';
import '../widgets.dart';
import 'ai_flow.dart';
import 'main_controller.dart';

/// The user bubble.
class UserBubble extends StatelessWidget {
  final String text;

  const UserBubble(this.text, {super.key});

  @override
  Widget build(BuildContext context) {
    final palette = Palette.of(context);
    return Padding(
      padding: const EdgeInsets.only(top: 10, left: 52),
      child: Align(
        alignment: Alignment.centerRight,
        child: Container(
          padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
          decoration: BoxDecoration(color: palette.bubble, borderRadius: BorderRadius.circular(20)),
          child: Text(text, style: Fonts.body(15, palette.ink)),
        ),
      ),
    );
  }
}

/// Answer block: summary card, message, passage cards, notices, follow-ups.
class AnswerView extends StatelessWidget {
  final Answer answer;
  final MainController controller;

  const AnswerView({super.key, required this.answer, required this.controller});

  @override
  Widget build(BuildContext context) {
    final palette = Palette.of(context);
    if (answer.kind == 'conversation') return _conversation(palette);
    final summary = answer.summary;
    final hasSummary = summary != null && summary.trim().isNotEmpty;
    return Padding(
      padding: const EdgeInsets.only(top: 10),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          if (hasSummary) ...[
            SummaryCard(summary),
            Padding(padding: const EdgeInsets.only(top: 8), child: Text(answer.message, style: Fonts.body(12, palette.muted))),
          ] else
            SelectableText(answer.message, style: Fonts.body(15, palette.ink)),
          if (answer.kind == 'fallback') ...[
            for (final limit in answer.limits) Text('• $limit', style: Fonts.body(11, palette.muted)),
            _guidanceCard(palette),
          ] else ...[
            for (final passage in answer.passages) PassageCard(passage: passage, controller: controller),
            if (answer.translationNotice != null)
              Padding(
                padding: const EdgeInsets.only(top: 6),
                child: Text(answer.translationNotice!, style: Fonts.body(11, palette.muted)),
              ),
          ],
          NoticeView(answer),
          _followupRow(context, palette),
        ],
      ),
    );
  }

  Widget _conversation(Palette palette) => Padding(
        padding: const EdgeInsets.only(top: 10),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Label('Assistant'),
            Padding(
              padding: const EdgeInsets.symmetric(vertical: 4),
              child: SelectableText(answer.message, style: Fonts.body(15, palette.ink)),
            ),
            for (final line in answer.limits)
              Padding(padding: const EdgeInsets.only(top: 2), child: Text('• $line', style: Fonts.body(11, palette.muted))),
          ],
        ),
      );

  Widget _guidanceCard(Palette palette) => CardBox(
        margin: const EdgeInsets.only(top: 10),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const SectionLabel('Where to look next'),
            for (final line in Conversation.guidance())
              Padding(padding: const EdgeInsets.only(top: 3), child: Text('• $line', style: Fonts.body(11, palette.muted))),
          ],
        ),
      );

  Widget _followupRow(BuildContext context, Palette palette) => Padding(
        padding: const EdgeInsets.only(top: 4),
        child: Wrap(
          children: [
            TextAction('Copy', onTap: () {
              Clipboard.setData(ClipboardData(text: MainController.copyText(answer)));
              toast(context, 'Copied to clipboard');
            }),
            TextAction('Listen', onTap: () => controller.speakAnswer(answer)),
            if (answer.kind != 'fallback' && answer.passages.isNotEmpty)
              TextAction('More verses', onTap: () => controller.askMore(answer)),
            TextAction('New chat', onTap: controller.newConversation),
          ],
        ),
      );
}

/// "Quran-only answer" card with the ChatGPT-style reveal of the verified text.
class SummaryCard extends StatefulWidget {
  final String summary;

  const SummaryCard(this.summary, {super.key});

  @override
  State<SummaryCard> createState() => _SummaryCardState();
}

class _SummaryCardState extends State<SummaryCard> {
  int _shown = 0;
  Timer? _timer;

  @override
  void initState() {
    super.initState();
    final full = widget.summary;
    if (full.length > 240) {
      final step = full.length ~/ 70 < 14 ? 14 : full.length ~/ 70;
      _timer = Timer.periodic(const Duration(milliseconds: 24), (timer) {
        setState(() => _shown = (_shown + step).clamp(0, full.length));
        if (_shown >= full.length) timer.cancel();
      });
    } else {
      _shown = full.length;
    }
  }

  @override
  void dispose() {
    _timer?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final palette = Palette.of(context);
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.fromLTRB(14, 12, 14, 12),
      decoration: BoxDecoration(color: palette.greenSoft, borderRadius: BorderRadius.circular(16)),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('Quran-only answer', style: Fonts.medium(11, palette.green)),
          Padding(
            padding: const EdgeInsets.only(top: 6),
            child: SelectableText(widget.summary.substring(0, _shown), style: Fonts.body(15, palette.ink)),
          ),
          Padding(
            padding: const EdgeInsets.only(top: 8),
            child: Text(
                'Reasoned only from the verses below — not tafsir or a fatwa. Consult a qualified scholar for rulings.',
                style: Fonts.body(11, palette.muted)),
          ),
        ],
      ),
    );
  }
}

/// A verified passage: reference, Amiri Arabic, translation, source and licence line.
class PassageCard extends StatelessWidget {
  final Passage passage;
  final MainController controller;

  const PassageCard({super.key, required this.passage, required this.controller});

  @override
  Widget build(BuildContext context) {
    final palette = Palette.of(context);
    final scale = AppState.instance.prefs.textScale;
    final meta = passage.translationTitle == null
        ? passage.translationLanguage
        : '${passage.translationTitle}${passage.translationAuthor == null ? '' : ' — ${passage.translationAuthor}'}';
    return Container(
      margin: const EdgeInsets.only(top: 10),
      width: double.infinity,
      padding: const EdgeInsets.fromLTRB(14, 12, 14, 12),
      decoration: BoxDecoration(
          color: palette.card, borderRadius: BorderRadius.circular(22), border: Border.all(color: palette.line)),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(child: Text(passage.referenceLabel, style: Fonts.medium(11.5, palette.gold))),
              Text(passage.chapterNameArabic, style: Fonts.body(12, palette.muted)),
            ],
          ),
          Padding(
            padding: const EdgeInsets.symmetric(vertical: 10),
            child: QuranText(passage.arabic, size: 25 * scale, color: palette.ink, height: 1.5),
          ),
          const Hairline(),
          const Label('Translation'),
          Padding(padding: const EdgeInsets.only(top: 2, bottom: 4), child: Text(meta, style: Fonts.body(11, palette.muted))),
          SelectableText(passage.translationText, style: Fonts.body(15 * scale, palette.ink)),
          if (passage.fallbackToEnglish)
            Text(
                'A reviewed translation is not available for this interface language, so the English translation is shown.',
                style: Fonts.body(11, palette.muted)),
          Padding(
            padding: const EdgeInsets.only(top: 2),
            child: Wrap(
              children: [
                TextAction('Source', onTap: () => _showSourceDialog(context)),
                TextAction('Recitation — Quran.com', onTap: () {
                  final verse = controller.dataset?.verse(passage.verseKey);
                  if (verse != null) {
                    Navigator.push(context,
                        MaterialPageRoute<void>(builder: (_) => PlayerScreen(chapterId: verse.chapterId, verseKey: verse.key)));
                  }
                }),
              ],
            ),
          ),
          Padding(
            padding: const EdgeInsets.only(top: 4),
            child: Text(
                'Licence status: ${passage.translationLicenseStatus.toUpperCase()}  ·  dataset ${passage.datasetVersion}',
                style: Fonts.body(10, palette.muted)),
          ),
        ],
      ),
    );
  }

  void _showSourceDialog(BuildContext context) {
    final text = 'Verse: ${passage.referenceLabel}\n\n'
        'Dataset: ${passage.dataset} (${passage.datasetVersion})\n\n'
        'Checksum SHA-256:\n${passage.datasetChecksum}\n\n'
        'Source: ${passage.datasetSourceUrl.isEmpty ? 'recorded at import' : passage.datasetSourceUrl}\n\n'
        'Translation: ${passage.translationTitle ?? passage.translationLanguage}'
        '${passage.translationAuthor == null ? '' : ' — ${passage.translationAuthor}'}\n'
        'Translation language: ${passage.translationLanguage}\n'
        'Licence status: ${passage.translationLicenseStatus}\n\n'
        'The Arabic line is the exact dataset text. The translation is a separate human translation and is not the Arabic text.';
    showMessage(context, 'Source & licence', text);
  }
}

/// Collapsible "Required notice" line.
class NoticeView extends StatefulWidget {
  final Answer answer;

  const NoticeView(this.answer, {super.key});

  @override
  State<NoticeView> createState() => _NoticeViewState();
}

class _NoticeViewState extends State<NoticeView> {
  bool _open = false;

  @override
  Widget build(BuildContext context) {
    final palette = Palette.of(context);
    final suffix = widget.answer.noticeVerified ? '' : ' · translation pending review';
    return Padding(
      padding: const EdgeInsets.only(top: 10),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          GestureDetector(
            onTap: () => setState(() => _open = !_open),
            behavior: HitTestBehavior.opaque,
            child: Text('Required notice$suffix ${_open ? '▾' : '▸'}', style: Fonts.body(11, palette.muted)),
          ),
          if (_open)
            Padding(
              padding: const EdgeInsets.only(top: 4),
              child: SelectableText(widget.answer.notice,
                  textDirection: widget.answer.language == 'ar' ? TextDirection.rtl : TextDirection.ltr,
                  style: Fonts.body(11, palette.muted)),
            ),
        ],
      ),
    );
  }
}

/// "Daily AI limit reached" card shown once per quota-blocked turn.
class AiQuotaNotice extends StatelessWidget {
  const AiQuotaNotice({super.key});

  @override
  Widget build(BuildContext context) {
    final palette = Palette.of(context);
    return CardBox(
      margin: const EdgeInsets.only(top: 8),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('Daily AI limit reached', style: Fonts.body(14, palette.goldDeep, weight: FontWeight.bold)),
          Padding(
            padding: const EdgeInsets.only(top: 4),
            child: Text(
                'You have used your ${AiFlow.dailyLimit} free AI-assisted answers for today. '
                'Quran reading, search, bookmarks and the verified verse answers still work normally. '
                'Your AI quota resets tomorrow.',
                style: Fonts.body(12, palette.muted)),
          ),
        ],
      ),
    );
  }
}
