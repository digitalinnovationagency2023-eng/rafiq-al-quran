import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:share_plus/share_plus.dart';

import '../data/app_state.dart';
import '../logic/logic.dart';
import 'player_screen.dart';
import 'reader_cards.dart';
import 'study_screen.dart';
import 'surah_screen.dart';
import 'theme.dart';
import 'widgets.dart';

/// Offline ayah reader: exact dataset Arabic beside an identified translation,
/// the juz/mushaf page position, per-verse actions (copy, share, bookmark
/// collections, notes, recitation), 40-verse paging, reading progress and the
/// source/licence line. Port of `ReaderActivity`.
class ReaderScreen extends StatefulWidget {
  final int chapterId;
  final String? verseKey;

  const ReaderScreen({super.key, required this.chapterId, this.verseKey});

  static const int pageStep = 40;

  @override
  State<ReaderScreen> createState() => _ReaderScreenState();
}

class _ReaderScreenState extends State<ReaderScreen> {
  final ScrollController _scroll = ScrollController();
  final Map<String, GlobalKey> _verseKeys = <String, GlobalKey>{};
  final GlobalKey _listKey = GlobalKey();
  Dataset? _dataset;
  QuranMeta? _meta;
  late int _chapterId;
  String? _startVerseKey;
  List<Verse> _verses = const [];
  int _fromIndex = 0;
  int _rendered = 0;
  String _info = 'Loading the mushaf…';
  String _lastRecordedKey = '';
  int _lastProgressAt = 0;
  Timer? _scrollDebounce;

  AppState get app => AppState.instance;

  @override
  void initState() {
    super.initState();
    _chapterId = widget.chapterId < 1 || widget.chapterId > 114 ? 1 : widget.chapterId;
    _startVerseKey = widget.verseKey;
    final store = app.store;
    if ((_startVerseKey == null || _startVerseKey!.isEmpty) && store.hasProgress && store.lastChapter == _chapterId) {
      _startVerseKey = store.lastVerseKey;
    }
    _scroll.addListener(_onScroll);
    _load();
  }

  Future<void> _load() async {
    final data = await app.assets.ready(app.prefs.language);
    final meta = await app.assets.meta();
    if (!mounted) return;
    setState(() {
      _dataset = data;
      _meta = meta;
      _showChapter();
    });
  }

  @override
  void dispose() {
    _recordProgress(true);
    _scrollDebounce?.cancel();
    _scroll.dispose();
    super.dispose();
  }

  // -------------------------------------------------------------- rendering
  void _showChapter() {
    final dataset = _dataset!;
    final chapter = dataset.chapter(_chapterId);
    if (chapter == null) {
      _info = 'Chapter not found.';
      return;
    }
    app.store.recordHistoryChapter(_chapterId);
    _verses = dataset.chapterVerses(_chapterId);
    _fromIndex = 0;
    final start = _startVerseKey;
    if (start != null && start.isNotEmpty) {
      final index = _verses.indexWhere((verse) => verse.key == start);
      if (index >= 0) {
        _fromIndex = index;
        _startVerseKey = null;
      }
    }
    _rendered = 0;
    _verseKeys.clear();
    _renderMore();
    final firstKey = _renderedKeys.isEmpty ? '' : _renderedKeys.first;
    if (firstKey.isNotEmpty) {
      _info = '${_indicatorFor(firstKey)}  ·  ${_fromIndex + 1} / ${_verses.length}';
      app.store.recordProgress(_chapterId, firstKey);
      _lastRecordedKey = firstKey;
    }
  }

  List<String> get _renderedKeys => [for (var i = _fromIndex; i < _fromIndex + _rendered; i++) _verses[i].key];

  void _renderMore() {
    final end = (_fromIndex + _rendered + ReaderScreen.pageStep).clamp(0, _verses.length);
    _rendered = end - _fromIndex;
    for (var i = _fromIndex; i < end; i++) {
      _verseKeys.putIfAbsent(_verses[i].key, GlobalKey.new);
    }
  }

  String _indicatorFor(String verseKey) {
    final verse = _dataset?.verse(verseKey);
    return verse == null || _meta == null ? '' : _meta!.indicator(verse.id);
  }

  /// The first rendered verse whose bottom edge is below the top of the viewport.
  String _currentVisibleKey() {
    final listBox = _listKey.currentContext?.findRenderObject() as RenderBox?;
    if (listBox == null || !listBox.attached || !_scroll.hasClients) return _lastRecordedKey;
    final keys = _renderedKeys;
    if (keys.isEmpty) return '';
    var best = keys.first;
    for (final key in keys) {
      final box = _verseKeys[key]?.currentContext?.findRenderObject() as RenderBox?;
      if (box == null || !box.attached) continue;
      final top = box.localToGlobal(Offset.zero, ancestor: listBox).dy;
      final bottom = top + box.size.height;
      final listTop = listBox.localToGlobal(Offset.zero).dy;
      if (listTop + bottom > 20 + MediaQuery.paddingOf(context).top + 60) {
        best = key;
        break;
      }
    }
    return best;
  }

  void _onScroll() {
    final now = DateTime.now().millisecondsSinceEpoch;
    if (now - _lastProgressAt < 700) return;
    _lastProgressAt = now;
    _scrollDebounce?.cancel();
    _scrollDebounce = Timer(const Duration(milliseconds: 120), () {
      if (!mounted) return;
      final visible = _currentVisibleKey();
      if (visible.isNotEmpty) {
        final position = _renderedKeys.indexOf(visible) + _fromIndex + 1;
        setState(() => _info = '${_indicatorFor(visible)}  ·  $position / ${_verses.length}');
      }
      _recordProgress(false);
    });
  }

  void _recordProgress(bool force) {
    if (_dataset == null) return;
    final visible = _currentVisibleKey();
    if (visible.isEmpty || (!force && visible == _lastRecordedKey)) return;
    _lastRecordedKey = visible;
    app.store.recordProgress(_chapterId, visible);
  }

  void _rebuildKeepingPosition() {
    final key = _currentVisibleKey();
    setState(() {
      _startVerseKey = key;
      _showChapter();
    });
    WidgetsBinding.instance.addPostFrameCallback((_) => _scrollToKey(key));
  }

  void _scrollToKey(String key) {
    final context = _verseKeys[key]?.currentContext;
    if (context != null) Scrollable.ensureVisible(context, alignment: 0.02);
  }

  void _changeScale(double delta) {
    final value = (app.prefs.textScale + delta).clamp(0.85, 1.6);
    app.setTextScale(value);
    _rebuildKeepingPosition();
  }

  void _savePlace() {
    var key = _currentVisibleKey();
    if (key.isEmpty) key = _renderedKeys.isEmpty ? '' : _renderedKeys.first;
    if (key.isEmpty) {
      toast(context, 'Open a surah first.');
      return;
    }
    app.store.recordProgress(_chapterId, key);
    _lastRecordedKey = key;
    toast(context, 'Reading place saved · $key');
  }

  String _translationLine() {
    final dataset = _dataset!;
    final translation = dataset.translationMeta(app.prefs.language) ?? dataset.translationMeta('en');
    if (translation == null) return 'Translation source: dataset';
    return 'Translation: ${translation.title}'
        '${translation.author.isEmpty ? '' : ' — ${translation.author}'}'
        '  ·  licence ${translation.licenseStatus.toUpperCase()}';
  }

  String _shortChecksum() {
    final checksum = _dataset!.provenance().checksum;
    return checksum.length < 16 ? checksum : '${checksum.substring(0, 16)}…';
  }

  // ---------------------------------------------------------------- actions
  void _openPlayer(String verseKey) => Navigator.push(
      context, MaterialPageRoute<void>(builder: (_) => PlayerScreen(chapterId: _chapterId, verseKey: verseKey)));

  void _copyVerse(Verse verse, String translation) {
    final provenance = _dataset!.provenance();
    Clipboard.setData(ClipboardData(
        text: '${_referenceLabel(verse)}\n\n${verse.arabic}\n\n$translation'
            '\n\nSource: ${provenance.dataset} ${provenance.version}'));
    toast(context, 'Verse copied');
  }

  void _shareVerse(Verse verse, String translation) {
    final provenance = _dataset!.provenance();
    SharePlus.instance.share(ShareParams(
        subject: 'Share verse',
        text: '${_referenceLabel(verse)}\n\n${verse.arabic}\n\n$translation'
            '\n\nShared from Rafiq Al-Quran — Quran-only assistant.\nSource: '
            '${provenance.dataset} ${provenance.version}'));
  }

  String _referenceLabel(Verse verse) => '${verse.chapter.nameEnglish} ${verse.key}';

  Future<void> _bookmarkDialog(String verseKey, bool bookmarked) async {
    final store = app.store;
    final collections = store.collections();
    final options = [...collections, '+ New collection…', if (bookmarked) 'Remove bookmark'];
    final which = await chooseOne(
        context, bookmarked ? 'Saved in ${store.collectionOf(verseKey)}' : 'Save verse', options);
    if (which == null || !mounted) return;
    if (which == collections.length) {
      await _newCollectionForVerse(verseKey);
    } else if (bookmarked && which == options.length - 1) {
      store.removeBookmark(verseKey);
      toast(context, 'Bookmark removed');
      _rebuildKeepingPosition();
    } else {
      store.bookmark(verseKey, collections[which]);
      toast(context, 'Saved in ${collections[which]}');
      _rebuildKeepingPosition();
    }
  }

  Future<void> _newCollectionForVerse(String verseKey) async {
    final name = await promptText(context, 'New collection', hint: 'Collection name', positive: 'Create');
    if (name == null || !mounted) return;
    final canonical = app.store.addCollection(name);
    app.store.bookmark(verseKey, canonical);
    toast(context, 'Saved in $canonical');
    _rebuildKeepingPosition();
  }

  Future<void> _editNote(String verseKey) async {
    final result = await promptText(context, 'Note on $verseKey',
        message: 'Only for you. Never sent anywhere.',
        hint: 'Your personal note (stored on this device)',
        initial: app.store.note(verseKey),
        multiline: true,
        neutral: 'Delete');
    if (result == null || !mounted) return;
    if (result.startsWith('\u0000')) {
      app.store.setNote(verseKey, '');
      toast(context, 'Note removed');
    } else {
      app.store.setNote(verseKey, result);
      toast(context, 'Note saved');
    }
    _rebuildKeepingPosition();
  }

  // ------------------------------------------------------------------ build
  @override
  Widget build(BuildContext context) {
    final palette = Palette.of(context);
    return PageScaffold(
      child: Column(
        children: [
          RafiqBar(
            title: 'Reader',
            subtitle: 'Offline  ·  source-tagged',
            onBack: () => Navigator.pop(context),
            actions: [
              const ThemeToggle(),
              GoldAction('Study',
                  onTap: () => Navigator.push(context, MaterialPageRoute<void>(builder: (_) => const StudyScreen()))),
            ],
          ),
          const GoldRule(),
          Container(
            width: double.infinity,
            color: palette.goldSoft,
            padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 6),
            child: Text(_info, style: Fonts.body(11, palette.muted)),
          ),
          Expanded(
            child: _dataset == null
                ? const SizedBox.shrink()
                : SingleChildScrollView(
                    controller: _scroll,
                    padding: const EdgeInsets.fromLTRB(12, 10, 12, 24),
                    child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: _chapterContent(palette)),
                  ),
          ),
        ],
      ),
    );
  }

  List<Widget> _chapterContent(Palette palette) {
    final dataset = _dataset!;
    final chapter = dataset.chapter(_chapterId)!;
    final prefs = app.prefs;
    final cards = ReaderCards(
      palette: palette,
      dataset: dataset,
      meta: _meta!,
      prefs: prefs,
      store: app.store,
      onCopy: _copyVerse,
      onShare: _shareVerse,
      onBookmark: _bookmarkDialog,
      onNote: _editNote,
      onListen: _openPlayer,
    );
    final verseWidgets = <Widget>[
      for (var i = _fromIndex; i < _fromIndex + _rendered; i++)
        KeyedSubtree(key: _verseKeys[_verses[i].key], child: cards.verseCard(_verses[i])),
    ];
    return [
      CardBox(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(chapter.nameEnglish, style: Fonts.display(28, palette.ink)),
            QuranText(chapter.nameArabic, size: 24, color: palette.goldDeep),
            Padding(
              padding: const EdgeInsets.only(top: 6),
              child: Text(
                  '${chapter.verseCount} verses  ·  ${chapter.revelation}  ·  ${_meta!.chapterIndicator(dataset, _chapterId)}',
                  style: Fonts.body(13, palette.muted)),
            ),
            Padding(padding: const EdgeInsets.only(top: 6), child: Text(_translationLine(), style: Fonts.body(11.5, palette.muted))),
          ],
        ),
      ),
      _readerControls(palette),
      if (_fromIndex > 0)
        Padding(
          padding: const EdgeInsets.only(top: 8),
          child: RafiqButton('↑ Begin at the first verse', expand: true, onPressed: () {
            setState(() {
              _startVerseKey = null;
              _showChapter();
            });
            _scroll.jumpTo(0);
          }),
        ),
      Padding(
        padding: const EdgeInsets.only(top: 10),
        child: prefs.bookMode
            ? Container(
                key: _listKey,
                padding: const EdgeInsets.fromLTRB(18, 14, 18, 14),
                decoration: BoxDecoration(
                    color: palette.card, borderRadius: BorderRadius.circular(22), border: Border.all(color: palette.line)),
                child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: verseWidgets),
              )
            : Column(key: _listKey, crossAxisAlignment: CrossAxisAlignment.stretch, children: verseWidgets),
      ),
      ..._footer(palette, chapter),
    ];
  }

  Widget _readerControls(Palette palette) {
    final prefs = app.prefs;
    return CardBox(
      margin: const EdgeInsets.only(top: 8),
      padding: const EdgeInsets.fromLTRB(12, 8, 12, 8),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              GoldAction('A−', onTap: () => _changeScale(-0.1)),
              GoldAction('A+', onTap: () => _changeScale(0.1)),
              Text('Text size ${(prefs.textScale * 100).round()}%', style: Fonts.body(11, palette.muted)),
            ],
          ),
          Wrap(
            children: [
              GoldAction(prefs.translationVisible ? 'Translation on' : 'Translation off', onTap: () {
                prefs.translationVisible = !prefs.translationVisible;
                _rebuildKeepingPosition();
              }),
              GoldAction(prefs.bookMode ? 'Book view' : 'Card view', onTap: () {
                prefs.bookMode = !prefs.bookMode;
                _rebuildKeepingPosition();
              }),
              if (!prefs.bookMode)
                GoldAction(prefs.sideBySide ? 'Side by side' : 'Stacked', onTap: () {
                  prefs.sideBySide = !prefs.sideBySide;
                  _rebuildKeepingPosition();
                }),
            ],
          ),
          Wrap(
            children: [
              GoldAction('Save my place', onTap: _savePlace),
              GoldAction('Search surah',
                  onTap: () => Navigator.push(context, MaterialPageRoute<void>(builder: (_) => const SurahScreen()))),
            ],
          ),
        ],
      ),
    );
  }

  List<Widget> _footer(Palette palette, Chapter chapter) {
    final dataset = _dataset!;
    if (_fromIndex + _rendered < _verses.length) {
      final remaining = _verses.length - (_fromIndex + _rendered);
      return [
        Padding(
          padding: const EdgeInsets.fromLTRB(2, 12, 2, 0),
          child: RafiqButton(
              'Show the next ${remaining < ReaderScreen.pageStep ? remaining : ReaderScreen.pageStep} verses',
              expand: true,
              onPressed: () => setState(_renderMore)),
        ),
      ];
    }
    final store = app.store;
    final planCount = store.plans().where((plan) => plan.chapters.contains(_chapterId) && !plan.done.contains(_chapterId)).length;
    final provenance = dataset.provenance();
    return [
      CardBox(
        margin: const EdgeInsets.only(top: 14),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const SectionLabel('End of surah'),
            Text(
                'You have reached the end of ${chapter.nameEnglish}. Source: ${provenance.dataset} ${provenance.version}.',
                style: Fonts.body(11, palette.muted)),
            if (planCount > 0)
              Padding(
                padding: const EdgeInsets.only(top: 10),
                child: RafiqButton(
                    'Mark ${chapter.nameEnglish} read in $planCount ${planCount == 1 ? 'plan' : 'plans'}',
                    primary: true, onPressed: () {
                  for (final plan in store.plans()) {
                    store.markChapterDone(plan.id, _chapterId);
                  }
                  toast(context, 'Reading plan progress updated.');
                  setState(() {});
                }),
              )
            else ...[
              Padding(
                padding: const EdgeInsets.only(top: 8),
                child: Text('Reading plans live in Study tools: pick a plan and this surah will count toward it.',
                    style: Fonts.body(11, palette.muted)),
              ),
              Padding(
                padding: const EdgeInsets.only(top: 8),
                child: RafiqButton('Open study tools',
                    onPressed: () => Navigator.push(context, MaterialPageRoute<void>(builder: (_) => const StudyScreen()))),
              ),
            ],
            Padding(
              padding: const EdgeInsets.only(top: 8),
              child: Text(
                  'Dataset: ${provenance.dataset} ${provenance.version}  ·  checksum ${_shortChecksum()}\n${_translationLine()}'
                  '\nThe Arabic text is the exact dataset text; the translation is a separate human translation and is not the Arabic text.',
                  style: Fonts.body(9, palette.muted)),
            ),
          ],
        ),
      ),
      Padding(
        padding: const EdgeInsets.only(top: 8),
        child: RafiqButton('Back to the surah list', expand: true, onPressed: () => Navigator.pop(context)),
      ),
    ];
  }
}
