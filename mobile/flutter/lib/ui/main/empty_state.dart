import 'package:flutter/material.dart';

import '../../data/app_state.dart';
import '../../logic/logic.dart';
import '../listen_screen.dart';
import '../reader_screen.dart';
import '../study_screen.dart';
import '../surah_screen.dart';
import '../theme.dart';
import '../widgets.dart';

/// The chat home before the first message: quick strip, illustrated hero,
/// "Dua for knowledge" card (phones), suggestions (tablets) and the
/// "Continue reading" card when a reading position exists.
class EmptyState extends StatelessWidget {
  final Dataset? dataset;
  final QuranMeta? meta;
  final VoidCallback onAsk;
  final void Function(String question) onSuggestion;

  const EmptyState({super.key, required this.dataset, required this.meta, required this.onAsk, required this.onSuggestion});

  @override
  Widget build(BuildContext context) {
    final palette = Palette.of(context);
    final tablet = MediaQuery.sizeOf(context).width >= 600;
    final store = AppState.instance.store;
    return Padding(
      padding: EdgeInsets.symmetric(vertical: tablet ? 16 : 8),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          _quickStrip(context, palette, tablet),
          _hero(palette, tablet),
          if (!tablet) _duaCard(context, palette),
          if (tablet) ..._suggestions(palette),
          if (store.hasProgress && dataset != null) _continueReading(context, palette, store),
        ],
      ),
    );
  }

  Widget _quickStrip(BuildContext context, Palette palette, bool tablet) {
    final glyphs = ['✦', '▤', '♫', '✎'];
    final labels = ['Ask', 'Read', 'Listen', 'Study'];
    final accents = [palette.coral, palette.teal, palette.purple, palette.amber];
    final actions = <VoidCallback>[
      onAsk,
      () => Navigator.push(context, MaterialPageRoute<void>(builder: (_) => const SurahScreen())),
      () => Navigator.push(context, MaterialPageRoute<void>(builder: (_) => const ListenScreen())),
      () => Navigator.push(context, MaterialPageRoute<void>(builder: (_) => const StudyScreen())),
    ];
    final iconSize = tablet ? 46.0 : 42.0;
    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: Row(
        children: [
          for (var i = 0; i < glyphs.length; i++)
            Expanded(
              child: GestureDetector(
                onTap: actions[i],
                behavior: HitTestBehavior.opaque,
                child: Column(
                  children: [
                    Container(
                      width: iconSize,
                      height: iconSize,
                      alignment: Alignment.center,
                      decoration: BoxDecoration(color: accents[i], shape: BoxShape.circle),
                      child: Text(glyphs[i], style: Fonts.body(tablet ? 20 : 18, Colors.white, height: 1.1)),
                    ),
                    Padding(
                      padding: const EdgeInsets.only(top: 5, bottom: 2),
                      child: Text(labels[i], maxLines: 1, style: Fonts.medium(tablet ? 13 : 11.5, palette.ink, height: 1.2)),
                    ),
                  ],
                ),
              ),
            ),
        ],
      ),
    );
  }

  Widget _hero(Palette palette, bool tablet) => Container(
        height: tablet ? 300 : 150,
        margin: const EdgeInsets.only(bottom: 14),
        clipBehavior: Clip.antiAlias,
        decoration: BoxDecoration(gradient: Palette.heroGradient, borderRadius: BorderRadius.circular(26)),
        child: Stack(
          fit: StackFit.expand,
          children: [
            Opacity(opacity: 0.92, child: Image.asset(Palette.heroArt, fit: BoxFit.cover)),
            Positioned(
              top: 2,
              right: 2,
              child: Opacity(
                opacity: 0.22,
                child: Image.asset(Palette.calligraphy, width: 190, height: 135, fit: BoxFit.contain),
              ),
            ),
            Padding(
              padding: const EdgeInsets.fromLTRB(18, 14, 18, 14),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                mainAxisSize: MainAxisSize.min,
                children: [
                  Text('ASSALAMU ALAYKUM', style: Fonts.medium(11.5, palette.sun, letterSpacing: 1.84, height: 1.2)),
                  Padding(
                    padding: const EdgeInsets.only(top: 6),
                    child: Text('How can I help?', style: Fonts.display(tablet ? 30 : 26, Colors.white)),
                  ),
                  if (tablet)
                    Padding(
                      padding: const EdgeInsets.only(top: 6),
                      child: Text('Ask, read or listen.', style: Fonts.body(16, Palette.heroText, height: 1.35)),
                    ),
                  Padding(
                    padding: const EdgeInsets.only(top: 8),
                    child: Text('✦   ✧   ✦', style: Fonts.body(18, palette.sun, letterSpacing: 3.6, height: 1.1)),
                  ),
                ],
              ),
            ),
          ],
        ),
      );

  Widget _duaCard(BuildContext context, Palette palette) => CardBox(
        color: palette.goldSoft,
        border: palette.purple.withAlpha(90),
        padding: const EdgeInsets.fromLTRB(18, 12, 18, 12),
        onTap: () => Navigator.push(
            context, MaterialPageRoute<void>(builder: (_) => const ReaderScreen(chapterId: 20, verseKey: '20:114'))),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Expanded(child: Label('DUA FOR KNOWLEDGE', color: palette.purple)),
                Text('20:114', style: Fonts.body(11.5, palette.muted)),
              ],
            ),
            Padding(
              padding: const EdgeInsets.only(top: 4),
              child: QuranText(
                  'وَقُل رَّبِّ زِدنِي عِلمًا',
                  size: 23,
                  color: palette.ink),
            ),
          ],
        ),
      );

  List<Widget> _suggestions(Palette palette) {
    const suggestions = [
      ['Who is Hazrat Essa?', 'Prophets and their stories'],
      ['What does the Qur’an say about sabr and shukr?', 'Patience and gratitude · Roman Urdu works'],
      ['Show me 2:255', 'Open a verse by reference'],
    ];
    return [
      Padding(
        padding: const EdgeInsets.only(top: 16, bottom: 2),
        child: Label('TRY ASKING', color: palette.goldDeep),
      ),
      for (final suggestion in suggestions)
        Padding(
          padding: const EdgeInsets.only(top: 8),
          child: Material(
            color: palette.card,
            elevation: 1,
            borderRadius: BorderRadius.circular(20),
            child: InkWell(
              onTap: () => onSuggestion(suggestion[0]),
              borderRadius: BorderRadius.circular(20),
              child: Container(
                padding: const EdgeInsets.fromLTRB(18, 16, 14, 16),
                decoration: BoxDecoration(borderRadius: BorderRadius.circular(20), border: Border.all(color: palette.line)),
                child: Row(
                  children: [
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(suggestion[0], style: Fonts.display(13.5, palette.ink)),
                          Padding(padding: const EdgeInsets.only(top: 3), child: Text(suggestion[1], style: Fonts.body(13, palette.muted))),
                        ],
                      ),
                    ),
                    Padding(padding: const EdgeInsets.only(left: 8), child: Text('→', style: Fonts.body(22, palette.goldDeep))),
                  ],
                ),
              ),
            ),
          ),
        ),
    ];
  }

  Widget _continueReading(BuildContext context, Palette palette, store) {
    final chapter = dataset!.chapter(store.lastChapter);
    final String key = store.lastVerseKey;
    final last = key.isEmpty ? null : dataset!.verse(key);
    final labelText = chapter == null ? (key.isEmpty ? 'Saved reading position' : key) : '${chapter.nameEnglish}  ·  $key';
    final metaLine = StringBuffer();
    if (last != null) {
      if (meta != null) metaLine.write('${meta!.indicator(last.id)}  ·  ');
      final percent = (last.id * 100 / 6236).round();
      metaLine.write('about ${percent < 1 ? 1 : percent}% of the Qur’an read');
    } else {
      metaLine.write('Saved on this device');
    }
    return CardBox(
      margin: const EdgeInsets.only(top: 18),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Label('CONTINUE READING', color: palette.goldDeep),
          Padding(padding: const EdgeInsets.only(top: 4, bottom: 2), child: Text(labelText, style: Fonts.display(21, palette.ink))),
          Text(metaLine.toString(), style: Fonts.body(13, palette.muted)),
          Padding(
            padding: const EdgeInsets.only(top: 12),
            child: RafiqButton('Resume reading →',
                primary: true,
                expand: true,
                onPressed: () => Navigator.push(
                    context,
                    MaterialPageRoute<void>(
                        builder: (_) => ReaderScreen(chapterId: store.lastChapter, verseKey: store.lastVerseKey)))),
          ),
        ],
      ),
    );
  }
}
