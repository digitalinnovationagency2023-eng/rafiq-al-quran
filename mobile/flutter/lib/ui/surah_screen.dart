import 'package:flutter/material.dart';
import 'package:share_plus/share_plus.dart';

import '../data/app_state.dart';
import '../logic/logic.dart';
import 'player_screen.dart';
import 'reader_screen.dart';
import 'theme.dart';
import 'widgets.dart';

enum _Pane { list, bookmarks, history }

/// Offline surah list (114 chapters) plus the device-local Bookmarks and
/// Reading history panes. Tapping any row opens the reader. Port of `SurahActivity`.
class SurahScreen extends StatefulWidget {
  const SurahScreen({super.key});

  @override
  State<SurahScreen> createState() => _SurahScreenState();
}

class _SurahScreenState extends State<SurahScreen> {
  final TextEditingController _search = TextEditingController();
  Dataset? _dataset;
  _Pane _pane = _Pane.list;
  List<int> _visible = const [];

  @override
  void initState() {
    super.initState();
    AppState.instance.assets.ready(AppState.instance.prefs.language).then((data) {
      if (!mounted) return;
      setState(() {
        _dataset = data;
        _applyFilter();
      });
    });
  }

  @override
  void dispose() {
    _search.dispose();
    super.dispose();
  }

  void _applyFilter() => _visible = SurahSearch.filter(_dataset, _search.text);

  void _showPane(_Pane pane) {
    if (_dataset == null && pane != _Pane.list) {
      toast(context, 'The dataset is still loading.');
      return;
    }
    setState(() => _pane = pane);
  }

  Future<void> _openReader(int chapterId, String? verseKey) async {
    await Navigator.push(
        context, MaterialPageRoute<void>(builder: (_) => ReaderScreen(chapterId: chapterId, verseKey: verseKey)));
    if (mounted) setState(() {});
  }

  void _openPlayer(int chapterId) => Navigator.push(
      context, MaterialPageRoute<void>(builder: (_) => PlayerScreen(chapterId: chapterId)));

  String _paneTitle() {
    switch (_pane) {
      case _Pane.list:
        return 'THE 114 SURAHS';
      case _Pane.bookmarks:
        return 'BOOKMARKS';
      case _Pane.history:
        return 'READING HISTORY';
    }
  }

  String _paneSubtitle() {
    final store = AppState.instance.store;
    switch (_pane) {
      case _Pane.list:
        return _dataset == null
            ? 'Tap a surah to read'
            : '${_visible.length} of 114 surahs · Mushaf order · search English / Arabic / Roman';
      case _Pane.bookmarks:
        return '${store.bookmarkTotal()} saved verses across ${store.collections().length} collections';
      case _Pane.history:
        return 'Most recent chapters you opened';
    }
  }

  @override
  Widget build(BuildContext context) {
    final palette = Palette.of(context);
    return PopScope(
      canPop: _pane == _Pane.list,
      onPopInvokedWithResult: (didPop, _) {
        if (!didPop) _showPane(_Pane.list);
      },
      child: PageScaffold(
        child: Column(
          children: [
            RafiqBar(
              title: 'Surahs',
              subtitle: '114  ·  offline',
              onBack: () => Navigator.pop(context),
              actions: [
                GoldAction('☆', size: 22, semanticLabel: 'Bookmarks', onTap: () => _showPane(_Pane.bookmarks)),
                GoldAction('◷', size: 21, semanticLabel: 'Reading history', onTap: () => _showPane(_Pane.history)),
                const ThemeToggle(),
              ],
            ),
            const GoldRule(),
            Padding(
              padding: const EdgeInsets.fromLTRB(14, 10, 14, 6),
              child: Row(
                children: [
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(_paneTitle(), style: Fonts.body(11, palette.goldDeep, weight: FontWeight.bold, letterSpacing: 1.5)),
                        Text(_paneSubtitle(), style: Fonts.body(11, palette.muted)),
                      ],
                    ),
                  ),
                ],
              ),
            ),
            if (_pane == _Pane.list)
              Padding(
                padding: const EdgeInsets.fromLTRB(14, 4, 14, 4),
                child: SearchField(controller: _search, hint: 'Search surah', onChanged: (_) => setState(_applyFilter)),
              ),
            Expanded(child: _body(palette)),
          ],
        ),
      ),
    );
  }

  Widget _body(Palette palette) {
    if (_dataset == null) {
      return Padding(
        padding: const EdgeInsets.fromLTRB(16, 24, 16, 0),
        child: Align(
            alignment: Alignment.topLeft,
            child: Text('Loading the bundled Quran dataset…', style: Fonts.body(13, palette.muted))),
      );
    }
    switch (_pane) {
      case _Pane.list:
        return ListView.separated(
          itemCount: _visible.length,
          separatorBuilder: (_, _) => Container(height: 1, color: palette.line),
          itemBuilder: (context, index) => _chapterRow(palette, _dataset!.chapter(_visible[index])!),
        );
      case _Pane.bookmarks:
        return ListView(padding: const EdgeInsets.fromLTRB(14, 6, 14, 20), children: _bookmarks(palette));
      case _Pane.history:
        return ListView(padding: const EdgeInsets.fromLTRB(14, 6, 14, 20), children: _history(palette));
    }
  }

  Widget _chapterRow(Palette palette, Chapter chapter) => InkWell(
        onTap: () => _openReader(chapter.id, null),
        child: Padding(
          padding: const EdgeInsets.fromLTRB(14, 12, 12, 12),
          child: Row(
            children: [
              Container(
                width: 44,
                height: 44,
                alignment: Alignment.center,
                decoration: BoxDecoration(
                    color: palette.goldSoft, borderRadius: BorderRadius.circular(22), border: Border.all(color: palette.line)),
                child: Text('${chapter.id}', style: Fonts.display(15, palette.goldDeep)),
              ),
              const SizedBox(width: 14),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(chapter.nameEnglish, style: Fonts.display(18.5, palette.ink)),
                    QuranText(chapter.nameArabic, size: 18, color: palette.goldDeep, height: 1.3),
                    Padding(
                      padding: const EdgeInsets.only(top: 2),
                      child: Text(
                          '${chapter.verseCount} verses  ·  ${chapter.revelation.isEmpty ? 'Qur’an' : chapter.revelation}',
                          style: Fonts.body(11.5, palette.muted)),
                    ),
                  ],
                ),
              ),
              const SizedBox(width: 10),
              CircleGlyph('▶',
                  fill: palette.greenSoft,
                  glyphColor: palette.green,
                  size: 40,
                  semanticLabel: 'Listen to ${chapter.nameEnglish} recitation from Quran.com',
                  onTap: () => _openPlayer(chapter.id)),
            ],
          ),
        ),
      );

  // ------------------------------------------------------------- bookmarks
  List<Widget> _bookmarks(Palette palette) {
    final store = AppState.instance.store;
    final widgets = <Widget>[
      Row(children: [GoldAction('← Back to surahs', onTap: () => _showPane(_Pane.list))]),
    ];
    if (store.bookmarkTotal() == 0) {
      widgets.add(CardBox(
        margin: const EdgeInsets.only(top: 10),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const SectionLabel('Nothing saved yet'),
            Text('Open a surah, tap Save on any verse and it will appear here in the collection you choose.',
                style: Fonts.body(12, palette.muted)),
          ],
        ),
      ));
    }
    for (final collection in store.collections()) {
      final keys = store.bookmarkedKeys(collection);
      if (keys.isEmpty) continue;
      widgets.add(Padding(
        padding: const EdgeInsets.only(top: 16, bottom: 4),
        child: SectionLabel('$collection  ·  ${keys.length}'),
      ));
      for (final key in keys) {
        widgets.add(_verseRow(palette, key, collection));
      }
    }
    if (store.bookmarkTotal() > 0) {
      widgets.add(Padding(
        padding: const EdgeInsets.only(top: 16),
        child: RafiqButton('Export bookmarks (CSV) via share sheet', expand: true, onPressed: _shareBookmarksCsv),
      ));
    }
    return widgets;
  }

  Widget _verseRow(Palette palette, String key, String collection) {
    final dataset = _dataset!;
    final store = AppState.instance.store;
    final verse = dataset.verse(key);
    final reference = verse == null ? key : '${verse.chapter.nameEnglish}  ${verse.key}';
    var translation = dataset.translationText(AppState.instance.prefs.language, key);
    if (translation == null || translation.isEmpty) translation = dataset.translationText('en', key);
    final note = store.note(key);
    return CardBox(
      margin: const EdgeInsets.only(top: 6),
      onTap: () => _openVerse(key),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(reference, style: Fonts.body(13, palette.ink, weight: FontWeight.bold)),
          if (translation != null && translation.isNotEmpty)
            Padding(
              padding: const EdgeInsets.only(top: 3),
              child: Text(translation.length > 120 ? '${translation.substring(0, 120)}…' : translation,
                  style: Fonts.body(11, palette.muted)),
            ),
          if (note.isNotEmpty)
            Padding(padding: const EdgeInsets.only(top: 3), child: Text('Note: $note', style: Fonts.body(11, palette.goldDeep))),
          Padding(
            padding: const EdgeInsets.only(top: 4),
            child: Text(collection.toUpperCase(), style: Fonts.body(9, palette.goldDeep, letterSpacing: 1.1)),
          ),
        ],
      ),
    );
  }

  void _shareBookmarksCsv() {
    final store = AppState.instance.store;
    final buffer = StringBuffer()..writeln(StudyData.csvHeader(['Reference', 'Collection', 'Note']));
    for (final collection in store.collections()) {
      for (final key in store.bookmarkedKeys(collection)) {
        final verse = _dataset!.verse(key);
        final reference = verse == null ? key : '${verse.chapter.nameEnglish} ${verse.key}';
        buffer.writeln(StudyData.csv([reference, collection, store.note(key)]));
      }
    }
    SharePlus.instance.share(ShareParams(text: buffer.toString(), subject: 'Rafiq Al-Quran bookmarks (CSV)'));
  }

  // --------------------------------------------------------------- history
  List<Widget> _history(Palette palette) {
    final store = AppState.instance.store;
    final chapters = store.historyChapters();
    final widgets = <Widget>[
      Row(children: [GoldAction('← Back to surahs', onTap: () => _showPane(_Pane.list))]),
    ];
    if (chapters.isEmpty) {
      widgets.add(CardBox(
        margin: const EdgeInsets.only(top: 10),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const SectionLabel('No reading history yet'),
            Text('Chapters you open in the reader are listed here so you can continue where you left off.',
                style: Fonts.body(12, palette.muted)),
          ],
        ),
      ));
    }
    final lastKey = store.lastVerseKey;
    for (final chapterId in chapters) {
      final chapter = _dataset!.chapter(chapterId);
      if (chapter == null) continue;
      var detail = '${chapter.verseCount} verses  ·  ${chapter.revelation}';
      if (chapterId == store.lastChapter && lastKey.startsWith('$chapterId:')) {
        detail += '  ·  last position $lastKey';
      }
      widgets.add(CardBox(
        margin: const EdgeInsets.only(top: 6),
        onTap: () => _openReader(chapterId, null),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('${chapter.nameEnglish}  ·  ${chapter.nameArabic}', style: Fonts.display(14, palette.ink)),
            Padding(padding: const EdgeInsets.only(top: 3), child: Text(detail, style: Fonts.body(11, palette.muted))),
          ],
        ),
      ));
    }
    if (chapters.isNotEmpty) {
      widgets.add(Padding(
        padding: const EdgeInsets.only(top: 14),
        child: Row(
          children: [
            GoldAction('Clear reading history', onTap: () {
              store.clearHistory();
              toast(context, 'Reading history cleared');
              setState(() {});
            }),
          ],
        ),
      ));
    }
    return widgets;
  }

  void _openVerse(String key) {
    if (!key.contains(':')) return;
    final chapterId = int.tryParse(key.split(':').first);
    if (chapterId != null) _openReader(chapterId, key);
  }
}
