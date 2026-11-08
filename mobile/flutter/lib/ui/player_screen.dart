import 'dart:async';

import 'package:flutter/material.dart';

import '../data/app_state.dart';
import '../data/recitation_handler.dart';
import '../logic/logic.dart';
import 'theme.dart';
import 'widgets.dart';

/// Verse-by-verse Quran.com recitation player. Audio keeps playing when the
/// screen locks (background audio through `audio_service`); it is released when
/// this screen is closed. Port of `PlayerActivity`.
class PlayerScreen extends StatefulWidget {
  final int chapterId;
  final String? verseKey;
  final bool carMode;

  const PlayerScreen({super.key, required this.chapterId, this.verseKey, this.carMode = false});

  @override
  State<PlayerScreen> createState() => _PlayerScreenState();
}

class _PlayerScreenState extends State<PlayerScreen> {
  final RecitationHandler _handler = RecitationHandler.instance;
  final ScrollController _scroll = ScrollController();
  QuranMeta? _meta;
  String? _loadError;
  bool _ready = false;
  double? _dragValue;
  StreamSubscription<Duration>? _positionSub;
  Duration _position = Duration.zero;
  int _lastChapter = 0;
  int _lastIndex = -1;

  @override
  void initState() {
    super.initState();
    _handler.changes.addListener(_onHandlerChange);
    _positionSub = _handler.positionStream.listen((position) {
      if (mounted && _dragValue == null) setState(() => _position = position);
    });
    _load();
  }

  Future<void> _load() async {
    final app = AppState.instance;
    try {
      final data = await app.assets.ready(app.prefs.language);
      _meta = await app.assets.meta();
      _handler
        ..reciterIndex = app.prefs.reciterIndex
        ..loadChapter(data, widget.chapterId, widget.verseKey,
            car: widget.carMode, continuousPlay: app.prefs.continuousListen);
      _handler.status = 'Tap play to stream this verse from Quran.com.';
      _handler.statusIsError = false;
      _lastChapter = _handler.chapterId;
      _lastIndex = _handler.index;
      if (mounted) setState(() => _ready = true);
    } catch (error) {
      if (mounted) setState(() => _loadError = error.toString());
    }
  }

  void _onHandlerChange() {
    if (!mounted) return;
    if (_handler.chapterId != _lastChapter || _handler.index != _lastIndex) {
      _lastChapter = _handler.chapterId;
      _lastIndex = _handler.index;
      _position = Duration.zero;
      if (_scroll.hasClients) _scroll.jumpTo(0);
    }
    setState(() {});
  }

  @override
  void dispose() {
    _handler.changes.removeListener(_onHandlerChange);
    _positionSub?.cancel();
    _handler.release();
    _scroll.dispose();
    super.dispose();
  }

  Future<void> _showReciterDialog() async {
    final choice = await chooseOne(context, 'Reciter', [for (final r in RecitationHandler.reciters) r.name],
        selected: _handler.reciterIndex);
    if (choice == null) return;
    AppState.instance.prefs.reciterIndex = choice;
    await _handler.setReciter(choice);
  }

  @override
  Widget build(BuildContext context) {
    final palette = Palette.of(context);
    return PageScaffold(
      child: Column(
        children: [
          RafiqBar(
              title: 'Listen',
              subtitle: 'Quran.com  ·  recitation',
              onBack: () => Navigator.pop(context),
              actions: const [ThemeToggle()]),
          const GoldRule(),
          Expanded(
            child: ListView(
              controller: _scroll,
              padding: const EdgeInsets.fromLTRB(16, 18, 16, 28),
              children: _loadError != null
                  ? [_loadErrorCard(palette)]
                  : !_ready
                      ? [Text('Loading the verse…', style: Fonts.body(13, palette.muted))]
                      : _content(palette),
            ),
          ),
        ],
      ),
    );
  }

  Widget _loadErrorCard(Palette palette) => CardBox(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('The bundled dataset could not be loaded', style: Fonts.display(15, palette.ink)),
            Padding(padding: const EdgeInsets.only(top: 8), child: Text(_loadError!, style: Fonts.body(12, palette.muted))),
          ],
        ),
      );

  List<Widget> _content(Palette palette) {
    final chapter = _handler.chapter!;
    return [
      Row(
        children: [
          Expanded(child: Text(chapter.nameEnglish, style: Fonts.display(28, palette.ink))),
          QuranText(chapter.nameArabic, size: 22, color: palette.goldDeep, height: 1.3),
        ],
      ),
      Padding(
        padding: const EdgeInsets.only(top: 4, bottom: 10),
        child: Text('Recitation · ${chapter.verseCount} verses in this surah', style: Fonts.body(13.5, palette.muted)),
      ),
      if (_handler.carMode) _carCard(palette),
      _verseCard(palette),
      Padding(
        padding: const EdgeInsets.only(top: 12, bottom: 4),
        child: Row(
          children: [
            RafiqButton('← Previous verse', onPressed: _handler.index > 0 ? () => _handler.moveVerse(-1) : null),
            Expanded(
              child: Text('Verse ${_handler.index + 1} of ${_handler.verses.length}',
                  textAlign: TextAlign.center, style: Fonts.body(11, palette.muted)),
            ),
            RafiqButton('Next verse →',
                onPressed: _handler.index < _handler.verses.length - 1 ? () => _handler.moveVerse(1) : null),
          ],
        ),
      ),
      _playerCard(palette),
      _sourceCard(palette),
      Padding(
        padding: const EdgeInsets.only(top: 20),
        child: Text(
            "Audio streams from Quran.com's public CDN over HTTPS and is not stored on this device. "
            'Recitation rights review recorded 2026-10-21. The app never modifies or generates '
            'recitation content.',
            style: Fonts.body(11, palette.muted)),
      ),
    ];
  }

  Widget _verseCard(Palette palette) {
    final verse = _handler.currentVerse;
    if (verse == null) return Text('This surah has no verses in the dataset.', style: Fonts.body(13, palette.muted));
    final app = AppState.instance;
    final language = app.prefs.language;
    final dataset = _handler.dataset!;
    var translation = dataset.translationText(language, verse.key);
    var fallback = false;
    if (translation == null || translation.isEmpty) {
      translation = dataset.translationText('en', verse.key);
      fallback = translation != null && translation.isNotEmpty;
    }
    final missing = translation == null || translation.isEmpty;
    final scale = app.prefs.textScale;
    return CardBox(
      padding: const EdgeInsets.fromLTRB(16, 14, 16, 16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(child: Label('${_handler.chapter!.nameEnglish} ${verse.key}', color: palette.goldDeep)),
              Pill('STREAMED', textColor: palette.green, fill: palette.greenSoft, stroke: palette.green),
            ],
          ),
          Padding(
            padding: const EdgeInsets.symmetric(vertical: 14),
            child: QuranText(verse.arabic, size: 30 * scale, color: palette.ink),
          ),
          const Hairline(),
          const Label('Translation'),
          Padding(
            padding: const EdgeInsets.only(top: 8, bottom: 4),
            child: Text(missing ? 'A reviewed translation is not available for this verse in this build.' : translation,
                style: Fonts.body(17 * scale, missing ? palette.muted : palette.ink)),
          ),
          if (fallback)
            Text(
                'The English translation is shown because no reviewed translation is loaded for $language in this build.',
                style: Fonts.body(11, palette.muted)),
          if (_meta != null)
            Padding(
              padding: const EdgeInsets.only(top: 12),
              child: Text(_meta!.indicator(verse.id), style: Fonts.body(11, palette.muted)),
            ),
        ],
      ),
    );
  }

  Widget _carCard(Palette palette) => CardBox(
        margin: const EdgeInsets.only(top: 4, bottom: 10),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Label('CAR MODE · CONTINUOUS'),
            Padding(
              padding: const EdgeInsets.symmetric(vertical: 4),
              child: Text('The whole journey, surah after surah.', style: Fonts.body(18, palette.ink, weight: FontWeight.bold)),
            ),
            Padding(
              padding: const EdgeInsets.only(bottom: 8),
              child: Text(
                  'Audio follows the phone’s audio route, so a paired car system (CarPlay or Bluetooth) plays it automatically '
                  'and its controls skip between surahs.',
                  style: Fonts.body(12, palette.muted)),
            ),
            Text(
                "Audio uses the phone's media route, so a paired car Bluetooth system plays it automatically. "
                'Keep this screen open or lock the phone; playback continues through the surah and then the next surah in mushaf order.',
                style: Fonts.body(11.5, palette.muted)),
            Padding(
              padding: const EdgeInsets.only(top: 8),
              child: Row(children: [RafiqButton('Next surah →', onPressed: _handler.playNextChapter)]),
            ),
          ],
        ),
      );

  Widget _playerCard(Palette palette) {
    final duration = _handler.duration ?? Duration.zero;
    final total = duration.inMilliseconds <= 0 ? 1 : duration.inMilliseconds;
    final sliderValue = _dragValue ?? (_position.inMilliseconds.clamp(0, total) * 1000 / total);
    final shownPosition = _dragValue == null ? _position : Duration(milliseconds: (total * _dragValue! / 1000).round());
    final playing = _handler.playing;
    return CardBox(
      margin: const EdgeInsets.only(top: 14),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Label('Reciter'),
          InkWell(
            onTap: _showReciterDialog,
            child: Padding(
              padding: const EdgeInsets.only(top: 6, bottom: 4),
              child: Row(
                children: [
                  Expanded(child: Text(_handler.reciter.name, style: Fonts.body(15, palette.ink, weight: FontWeight.bold))),
                  GoldAction('Change', onTap: _showReciterDialog),
                ],
              ),
            ),
          ),
          Text(AudioGate.attribution(), style: Fonts.body(11, palette.muted)),
          Padding(
            padding: const EdgeInsets.only(top: 10),
            child: Text(_handler.status, style: Fonts.body(12, _handler.statusIsError ? palette.danger : palette.muted)),
          ),
          Padding(
            padding: const EdgeInsets.only(top: 10),
            child: Row(
              children: [
                Expanded(
                  child: SliderTheme(
                    data: SliderTheme.of(context).copyWith(
                        activeTrackColor: palette.green,
                        inactiveTrackColor: palette.line,
                        thumbColor: palette.green,
                        trackHeight: 3),
                    child: Slider(
                      value: sliderValue.clamp(0, 1000).toDouble(),
                      max: 1000,
                      onChanged: (value) => setState(() => _dragValue = value),
                      onChangeEnd: (value) {
                        _dragValue = null;
                        if (_handler.prepared) _handler.seek(Duration(milliseconds: (total * value / 1000).round()));
                      },
                    ),
                  ),
                ),
                Padding(
                  padding: const EdgeInsets.only(left: 10),
                  child: Text(
                      '${RecitationHandler.formatTime(shownPosition)} / ${RecitationHandler.formatTime(duration)}',
                      style: Fonts.body(11, palette.muted)),
                ),
              ],
            ),
          ),
          Padding(
            padding: const EdgeInsets.only(top: 10),
            child: Wrap(
              spacing: 8,
              runSpacing: 8,
              children: [
                RafiqButton(
                    _handler.buffering ? '… Buffering' : (playing ? '⏸ Pause' : '▶ Play'),
                    primary: true,
                    onPressed: _handler.buffering ? null : _handler.togglePlay),
                RafiqButton('↺ Replay', onPressed: _handler.replay),
                RafiqButton('⏹ Stop', onPressed: _handler.stop),
              ],
            ),
          ),
          Padding(
            padding: const EdgeInsets.only(top: 10),
            child: Text(
                'Playback needs an internet connection. Audio is streamed verse by verse from '
                'Quran.com and is never saved on this device.',
                style: Fonts.body(11, palette.muted)),
          ),
        ],
      ),
    );
  }

  Widget _sourceCard(Palette palette) => CardBox(
        margin: const EdgeInsets.only(top: 14),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Label('Source & rights'),
            _kv(palette, 'Audio source', 'Quran.com'),
            _kv(palette, 'Reciter', _handler.reciter.name),
            _kv(palette, 'Rights status', 'Review recorded 2026-10-21'),
            Padding(
              padding: const EdgeInsets.only(top: 10),
              child: Text(
                  "Verse audio is streamed from Quran.com's public CDN. The Arabic text and translation on "
                  "this screen still come from the app's validated dataset, not from the audio service.",
                  style: Fonts.body(11, palette.muted)),
            ),
          ],
        ),
      );

  Widget _kv(Palette palette, String key, String value) => Padding(
        padding: const EdgeInsets.symmetric(vertical: 5),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Expanded(child: Text(key, style: Fonts.body(12, palette.muted))),
            Flexible(child: Text(value, textAlign: TextAlign.right, style: Fonts.body(12, palette.ink))),
          ],
        ),
      );
}
