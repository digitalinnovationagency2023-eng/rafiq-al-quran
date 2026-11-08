import 'package:flutter/material.dart';

import '../data/prefs.dart';
import '../data/study_store.dart';
import '../logic/logic.dart';
import 'theme.dart';
import 'widgets.dart';

/// Verse renderers for the reader: the continuous book block and the card
/// view (stacked or side by side). Port of `ReaderActivity.verseCard` and
/// `bookVerseBlock`.
class ReaderCards {
  final Palette palette;
  final Dataset dataset;
  final QuranMeta meta;
  final Prefs prefs;
  final StudyStore store;
  final void Function(Verse verse, String translation) onCopy;
  final void Function(Verse verse, String translation) onShare;
  final void Function(String verseKey, bool bookmarked) onBookmark;
  final void Function(String verseKey) onNote;
  final void Function(String verseKey) onListen;

  const ReaderCards({
    required this.palette,
    required this.dataset,
    required this.meta,
    required this.prefs,
    required this.store,
    required this.onCopy,
    required this.onShare,
    required this.onBookmark,
    required this.onNote,
    required this.onListen,
  });

  Widget verseCard(Verse verse) => prefs.bookMode ? _bookVerseBlock(verse) : _cardVerse(verse);

  (String, bool) _translationFor(Verse verse) {
    var translation = dataset.translationText(prefs.language, verse.key);
    var fallback = false;
    if (translation == null || translation.isEmpty) {
      translation = dataset.translationText('en', verse.key);
      fallback = true;
    }
    return (translation ?? '', fallback);
  }

  Widget _cardVerse(Verse verse) {
    final scale = prefs.textScale;
    final (translation, fallback) = _translationFor(verse);
    final note = store.note(verse.key);
    final bookmarked = store.isBookmarked(verse.key);
    final arabic = Padding(
      padding: const EdgeInsets.only(top: 12, bottom: 10),
      child: QuranText(verse.arabic, size: 29 * scale, color: palette.ink),
    );
    return Container(
      margin: const EdgeInsets.only(top: 10),
      padding: const EdgeInsets.fromLTRB(14, 12, 14, 10),
      decoration: BoxDecoration(
          color: palette.card, borderRadius: BorderRadius.circular(22), border: Border.all(color: palette.line)),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(
                child: Text('${verse.chapter.nameEnglish}  ${verse.key}',
                    style: Fonts.body(12, palette.goldDeep, weight: FontWeight.bold)),
              ),
              Pill('PAGE ${meta.page(verse.id)}  ·  JUZ ${meta.juz(verse.id)}',
                  textColor: palette.muted, fill: palette.goldSoft, stroke: palette.line),
            ],
          ),
          if (prefs.sideBySide && prefs.translationVisible && translation.isNotEmpty)
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Expanded(child: arabic),
                const SizedBox(width: 8),
                Expanded(
                  child: Container(
                    margin: const EdgeInsets.only(top: 12),
                    padding: const EdgeInsets.fromLTRB(10, 8, 10, 8),
                    decoration: BoxDecoration(
                        color: palette.goldSoft, borderRadius: BorderRadius.circular(6), border: Border.all(color: palette.line)),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        const Label('Translation'),
                        Text(translation, style: Fonts.body(15 * scale, palette.ink)),
                      ],
                    ),
                  ),
                ),
              ],
            )
          else ...[
            arabic,
            if (prefs.translationVisible && translation.isNotEmpty) ...[
              const Hairline(),
              Text(translation, style: Fonts.body(17 * scale, palette.muted)),
              if (fallback)
                Text('English translation shown for this interface language.', style: Fonts.body(10, palette.muted)),
            ],
          ],
          if (note.isNotEmpty)
            GestureDetector(
              onTap: () => onNote(verse.key),
              child: Container(
                margin: const EdgeInsets.only(top: 8),
                width: double.infinity,
                padding: const EdgeInsets.fromLTRB(10, 6, 10, 6),
                decoration: BoxDecoration(
                    color: palette.goldSoft, borderRadius: BorderRadius.circular(6), border: Border.all(color: palette.line)),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [const Label('Personal note'), Text(note, style: Fonts.body(12, palette.ink))],
                ),
              ),
            ),
          Padding(
            padding: const EdgeInsets.only(top: 6),
            child: Wrap(
              children: [
                GoldAction('Copy', onTap: () => onCopy(verse, translation)),
                GoldAction('Share', onTap: () => onShare(verse, translation)),
                GoldAction(bookmarked ? 'Saved ✓' : 'Save', onTap: () => onBookmark(verse.key, bookmarked)),
                GoldAction(note.isEmpty ? 'Note' : 'Edit note', onTap: () => onNote(verse.key)),
                GoldAction('Listen', onTap: () => onListen(verse.key)),
              ],
            ),
          ),
        ],
      ),
    );
  }

  /// Continuous mushaf-style block: big Arabic, translation directly under each verse.
  Widget _bookVerseBlock(Verse verse) {
    final scale = prefs.textScale;
    final (translation, _) = _translationFor(verse);
    final bookmarked = store.isBookmarked(verse.key);
    final marked = '${verse.arabic}  ۝${toArabicDigits(verse.number)}';
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('${verse.chapter.nameEnglish} · ${verse.key}', style: Fonts.body(10.5, palette.muted, letterSpacing: 0.4)),
          Padding(
            padding: const EdgeInsets.only(top: 7, bottom: 5),
            child: QuranText(marked, size: 32 * scale, color: palette.ink, height: 1.6),
          ),
          if (prefs.translationVisible && translation.isNotEmpty)
            Padding(
              padding: const EdgeInsets.symmetric(vertical: 2),
              child: Text(translation, style: Fonts.body(15.5 * scale, palette.muted, height: 1.3)),
            ),
          Padding(
            padding: const EdgeInsets.only(top: 3),
            child: Wrap(
              children: [
                GoldAction(bookmarked ? 'Saved ✓' : 'Save', onTap: () => onBookmark(verse.key, bookmarked)),
                GoldAction('Note', onTap: () => onNote(verse.key)),
                GoldAction('Listen', onTap: () => onListen(verse.key)),
              ],
            ),
          ),
          const Hairline(),
        ],
      ),
    );
  }

  /// Arabic-Indic digits for the end-of-ayah marker.
  static String toArabicDigits(int number) {
    final digits = number.toString();
    final builder = StringBuffer();
    for (final c in digits.codeUnits) {
      builder.writeCharCode(0x0660 + (c - 0x30));
    }
    return builder.toString();
  }
}
