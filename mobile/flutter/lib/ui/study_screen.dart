import 'package:flutter/material.dart';
import 'package:share_plus/share_plus.dart';

import '../data/app_state.dart';
import '../data/study_store.dart';
import '../logic/logic.dart';
import 'reader_screen.dart';
import 'study_sections.dart';
import 'theme.dart';
import 'widgets.dart';

/// The study hub for device-local collections, personal notes, saved searches,
/// reading plans and plain-text/CSV exports. A collection's verse list is
/// rendered in place, so back returns to the hub. Port of `StudyActivity`.
class StudyScreen extends StatefulWidget {
  const StudyScreen({super.key});

  @override
  State<StudyScreen> createState() => _StudyScreenState();
}

class _StudyScreenState extends State<StudyScreen> {
  final ScrollController _scroll = ScrollController();
  Dataset? _dataset;
  QuranMeta? _meta;
  bool _loaded = false;
  String? _showingCollection;

  AppState get app => AppState.instance;
  StudyStore get store => app.store;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    try {
      _dataset = await app.assets.ready(app.prefs.language);
      _meta = await app.assets.meta();
    } catch (_) {
      // Local-only sections stay usable without the dataset.
    }
    if (mounted) setState(() => _loaded = true);
  }

  @override
  void dispose() {
    _scroll.dispose();
    super.dispose();
  }

  void _renderHub() {
    setState(() => _showingCollection = null);
    _scrollToTop();
  }

  void _scrollToTop() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (_scroll.hasClients) _scroll.jumpTo(0);
    });
  }

  String referenceLabel(String key) {
    final chapterId = chapterIdOf(key);
    final chapter = _dataset == null || chapterId <= 0 ? null : _dataset!.chapter(chapterId);
    return chapter == null ? key : '${chapter.nameEnglish} $key';
  }

  static int chapterIdOf(String key) => int.tryParse(key.split(':').first) ?? 0;

  Future<void> openReader(int chapterId, String? verseKey) async {
    if (chapterId <= 0) {
      toast(context, 'That reference could not be opened.');
      return;
    }
    await Navigator.push(
        context, MaterialPageRoute<void>(builder: (_) => ReaderScreen(chapterId: chapterId, verseKey: verseKey)));
    // Reading progress may have changed in the reader; refresh the hub.
    if (mounted) setState(() {});
  }

  void share(String subject, String body) {
    if (body.trim().isEmpty) {
      toast(context, 'There is nothing to export yet. Save a verse or write a note first.');
      return;
    }
    SharePlus.instance.share(ShareParams(subject: subject, text: body));
  }

  void askSearch(String query) {
    app.pendingAsk.value = query;
    Navigator.of(context).popUntil((route) => route.isFirst);
  }

  // ------------------------------------------------------------------ build
  @override
  Widget build(BuildContext context) {
    final palette = Palette.of(context);
    final showingCollection = _showingCollection != null;
    return PopScope(
      canPop: !showingCollection,
      onPopInvokedWithResult: (didPop, _) {
        if (!didPop) _renderHub();
      },
      child: PageScaffold(
        child: Column(
          children: [
            RafiqBar(
              title: 'Study tools',
              subtitle: 'Collections · Notes · Plans',
              onBack: () => showingCollection ? _renderHub() : Navigator.pop(context),
              actions: const [ThemeToggle()],
            ),
            const GoldRule(),
            Expanded(
              child: ListView(
                controller: _scroll,
                padding: const EdgeInsets.fromLTRB(16, 18, 16, 28),
                children: !_loaded
                    ? [Text('Loading study data…', style: Fonts.body(13, palette.muted))]
                    : showingCollection
                        ? _collection(palette, _showingCollection!)
                        : _hub(palette),
              ),
            ),
          ],
        ),
      ),
    );
  }

  List<Widget> _hub(Palette palette) {
    final sections = StudySections(
      context: context,
      palette: palette,
      dataset: _dataset,
      meta: _meta,
      store: store,
      prefs: app.prefs,
      referenceLabel: referenceLabel,
      openReader: openReader,
      share: share,
      askSearch: askSearch,
      refresh: () => setState(() {}),
      showCollection: (name) {
        setState(() => _showingCollection = name);
        _scrollToTop();
      },
    );
    return [
      Text(
          'Collections, notes and plans are stored on this device only. The Quran text and '
          'translations always come from the bundled, validated dataset.',
          style: Fonts.body(12, palette.muted)),
      if (_dataset == null)
        Padding(
          padding: const EdgeInsets.only(top: 6),
          child: Text('The bundled Quran dataset could not be loaded, so verse references are shown as keys only.',
              style: Fonts.body(11, palette.danger)),
        ),
      sections.overview(),
      ...sections.collections(),
      ...sections.notes(),
      ...sections.searches(),
      ...sections.plans(),
      ...sections.exportSection(),
      Padding(
        padding: const EdgeInsets.only(top: 22),
        child: Text(
            'Reading the Quran is always free and offline. Exports leave the app only when you choose '
            'where to share them.',
            style: Fonts.body(10, palette.muted)),
      ),
    ];
  }

  List<Widget> _collection(Palette palette, String name) {
    final keys = store.bookmarkedKeys(name);
    return [
      Row(children: [StudySections.textAction(palette, '← All collections', _renderHub)]),
      Padding(
        padding: const EdgeInsets.symmetric(vertical: 2),
        child: Text(name, style: Fonts.display(22, palette.ink)),
      ),
      Text(
          '${keys.length}${keys.length == 1 ? ' saved verse' : ' saved verses'} · tap a verse to open it in the reader',
          style: Fonts.body(11, palette.muted)),
      if (keys.isEmpty)
        StudySections.card(
          palette,
          Text('Nothing saved in this collection yet. Open a verse in the reader and save it here.',
              style: Fonts.body(12, palette.muted)),
        )
      else
        StudySections.card(
          palette,
          Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              for (var i = 0; i < keys.length; i++) ...[
                if (i > 0) const Hairline(),
                _collectionVerseRow(palette, keys[i]),
              ],
            ],
          ),
        ),
    ];
  }

  Widget _collectionVerseRow(Palette palette, String key) {
    final dataset = _dataset;
    final verse = dataset?.verse(key);
    final preview = _previewTranslation(key);
    return InkWell(
      onTap: () => openReader(chapterIdOf(key), key),
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 6),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Expanded(child: Label(referenceLabel(key))),
                Text('›', style: Fonts.body(16, palette.gold)),
              ],
            ),
            if (verse != null)
              Padding(
                padding: const EdgeInsets.only(top: 6, bottom: 2),
                child: QuranText(verse.arabic, size: 20 * app.prefs.textScale, color: palette.ink, height: 1.2),
              ),
            if (preview.isNotEmpty)
              Padding(padding: const EdgeInsets.only(top: 4), child: Text(preview, style: Fonts.body(12, palette.muted))),
            if (verse != null && _meta != null)
              Padding(
                padding: const EdgeInsets.only(top: 4),
                child: Text(_meta!.indicator(verse.id), style: Fonts.body(10, palette.muted)),
              ),
          ],
        ),
      ),
    );
  }

  String _previewTranslation(String key) {
    final dataset = _dataset;
    if (dataset == null) return '';
    var text = dataset.translationText(app.prefs.language, key);
    if (text == null || text.isEmpty) text = dataset.translationText('en', key);
    if (text == null) return '';
    final clean = text.trim().replaceAll(RegExp(r'\s+'), ' ');
    return clean.length > 180 ? '${clean.substring(0, 177)}…' : clean;
  }
}
