import 'package:flutter/material.dart';

import '../data/app_state.dart';
import '../data/recitation_handler.dart';
import '../logic/logic.dart';
import 'player_screen.dart';
import 'surah_screen.dart';
import 'theme.dart';
import 'widgets.dart';

/// Listening home for Quran.com recitation: choose a reciter, resume the last
/// surah, or pick any of the 114 surahs. Port of `ListenActivity`.
class ListenScreen extends StatefulWidget {
  const ListenScreen({super.key});

  @override
  State<ListenScreen> createState() => _ListenScreenState();
}

class _ListenScreenState extends State<ListenScreen> {
  final TextEditingController _search = TextEditingController();
  Dataset? _dataset;
  String? _error;
  List<int> _visible = const [];

  @override
  void initState() {
    super.initState();
    AppState.instance.assets.dataset().then((data) {
      if (!mounted) return;
      setState(() {
        _dataset = data;
        _refresh();
      });
    }).catchError((Object error) {
      if (mounted) setState(() => _error = error.toString());
    });
  }

  @override
  void dispose() {
    _search.dispose();
    super.dispose();
  }

  void _refresh() => _visible = SurahSearch.filter(_dataset, _search.text);

  void _openPlayer(int chapterId, String? verseKey, {bool carMode = false}) {
    Navigator.push(context,
        MaterialPageRoute<void>(builder: (_) => PlayerScreen(chapterId: chapterId, verseKey: verseKey, carMode: carMode)));
  }

  @override
  Widget build(BuildContext context) {
    final palette = Palette.of(context);
    final store = AppState.instance.store;
    final prefs = AppState.instance.prefs;
    return PageScaffold(
      child: Column(
        children: [
          RafiqBar(
              title: 'Listen',
              subtitle: 'Quran.com  ·  streamed',
              onBack: () => Navigator.pop(context),
              actions: const [ThemeToggle()]),
          const GoldRule(),
          Expanded(
            child: ListView(
              padding: const EdgeInsets.fromLTRB(20, 22, 20, 20),
              children: [
                _hero(palette),
                CardBox(
                  margin: const EdgeInsets.only(top: 14),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Label('RECITER', color: palette.goldDeep),
                      Padding(
                        padding: const EdgeInsets.only(top: 8),
                        child: Wrap(spacing: 8, runSpacing: 8, children: _reciterChips(palette, prefs)),
                      ),
                    ],
                  ),
                ),
                if (store.hasProgress)
                  CardBox(
                    margin: const EdgeInsets.only(top: 14),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Label('CONTINUE LISTENING', color: palette.goldDeep),
                        Padding(
                          padding: const EdgeInsets.only(top: 4, bottom: 10),
                          child: Text('Your last surah is waiting.', style: Fonts.display(20, palette.ink)),
                        ),
                        RafiqButton('Open the player →',
                            primary: true, onPressed: () => _openPlayer(store.lastChapter, store.lastVerseKey)),
                      ],
                    ),
                  ),
                Padding(
                  padding: const EdgeInsets.symmetric(vertical: 4),
                  child: SearchField(controller: _search, hint: 'Search surah', onChanged: (_) => setState(_refresh)),
                ),
                Padding(
                  padding: const EdgeInsets.only(top: 16, bottom: 4),
                  child: Row(
                    children: [
                      TextAction('▤ Browse & read',
                          onTap: () => Navigator.push(
                              context, MaterialPageRoute<void>(builder: (_) => const SurahScreen()))),
                      const Spacer(),
                      TextAction('♫ Car mode · continuous',
                          onTap: () => _openPlayer(store.hasProgress ? store.lastChapter : 1,
                              store.hasProgress ? store.lastVerseKey : null,
                              carMode: true)),
                    ],
                  ),
                ),
                Padding(
                  padding: const EdgeInsets.only(top: 18, bottom: 8),
                  child: Label('MUSHAF ORDER · 1 → 114', color: palette.goldDeep),
                ),
                if (_error != null)
                  Padding(
                    padding: const EdgeInsets.only(top: 12),
                    child: Text('The Quran dataset could not be loaded: $_error', style: Fonts.body(14, palette.danger)),
                  ),
                if (_dataset != null) for (final id in _visible) _chapterRow(palette, _dataset!.chapter(id)!),
                Padding(
                  padding: const EdgeInsets.only(top: 6),
                  child: Text(
                      "Audio streams from Quran.com's public CDN. Source and rights review are recorded in the app. "
                      'No audio is downloaded or stored on this device.',
                      style: Fonts.body(11.5, palette.muted)),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _hero(Palette palette) => Container(
        decoration: BoxDecoration(gradient: Palette.heroGradient, borderRadius: BorderRadius.circular(26)),
        padding: const EdgeInsets.fromLTRB(22, 20, 22, 18),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('QURAN.COM RECITATION', style: Fonts.medium(11, palette.sun, letterSpacing: 1.76, height: 1.2)),
            Padding(
              padding: const EdgeInsets.only(top: 6),
              child: Text('Listen anywhere.', style: Fonts.display(28, Colors.white)),
            ),
            Padding(
              padding: const EdgeInsets.only(top: 5, bottom: 6),
              child: Text('Choose a reciter, then a surah.', style: Fonts.body(15, Palette.heroText)),
            ),
            Text('✦   ✧   ✦', style: Fonts.body(15, palette.sun, letterSpacing: 3)),
          ],
        ),
      );

  List<Widget> _reciterChips(Palette palette, prefs) {
    final selected = prefs.reciterIndex.clamp(0, RecitationHandler.reciters.length - 1);
    final accents = [palette.teal, palette.purple, palette.amber];
    return [
      for (var index = 0; index < RecitationHandler.reciters.length; index++)
        GestureDetector(
          onTap: () => setState(() => prefs.reciterIndex = index),
          child: Container(
            padding: const EdgeInsets.symmetric(horizontal: 9, vertical: 8),
            decoration: BoxDecoration(
              color: index == selected ? accents[index % 3] : accents[index % 3].withAlpha(30),
              borderRadius: BorderRadius.circular(22),
              border: index == selected ? null : Border.all(color: accents[index % 3].withAlpha(120)),
            ),
            child: Text(RecitationHandler.reciters[index].shortName,
                maxLines: 1, style: Fonts.medium(12, index == selected ? palette.onBar : palette.ink, height: 1.2)),
          ),
        ),
    ];
  }

  Widget _chapterRow(Palette palette, Chapter chapter) {
    final accents = [palette.coral, palette.teal, palette.purple, palette.amber];
    final accent = accents[(chapter.id - 1) % accents.length];
    return InkWell(
      onTap: () => _openPlayer(chapter.id, null),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 9, vertical: 13),
        child: Row(
          children: [
            Container(
              width: 42,
              height: 42,
              alignment: Alignment.center,
              decoration: BoxDecoration(
                  color: accent.withAlpha(30),
                  borderRadius: BorderRadius.circular(22),
                  border: Border.all(color: accent.withAlpha(100))),
              child: Text('${chapter.id}', style: Fonts.display(14, accent)),
            ),
            const SizedBox(width: 14),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(chapter.nameEnglish, style: Fonts.display(18.5, palette.ink)),
                  QuranText(chapter.nameArabic, size: 18, color: accent, height: 1.3),
                  Padding(
                    padding: const EdgeInsets.only(top: 3),
                    child: Text(
                        '${chapter.verseCount} verses  ·  ${chapter.revelation.isEmpty ? 'Qur’an' : chapter.revelation}',
                        style: Fonts.body(12.5, palette.muted)),
                  ),
                ],
              ),
            ),
            CircleGlyph('▶',
                fill: accent.withAlpha(34),
                glyphColor: accent,
                size: 42,
                semanticLabel: 'Listen to ${chapter.nameEnglish}',
                onTap: () => _openPlayer(chapter.id, null)),
          ],
        ),
      ),
    );
  }
}
