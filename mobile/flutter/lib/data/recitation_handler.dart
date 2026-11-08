import 'package:audio_service/audio_service.dart';
import 'package:audio_session/audio_session.dart';
import 'package:flutter/foundation.dart';
import 'package:just_audio/just_audio.dart';

import '../logic/logic.dart';

/// A verified Quran.com CDN reciter: display name and path template.
class Reciter {
  final String name;
  final String template;

  const Reciter(this.name, this.template);

  /// Example: 2:255 Alafasy -> https://verses.quran.com/Alafasy/mp3/002255.mp3
  String url(int chapter, int verse) =>
      '${RecitationHandler.cdn}${template.replaceFirst('%03d%03d', '${_pad(chapter)}${_pad(verse)}')}';

  static String _pad(int value) => value.toString().padLeft(3, '0');

  /// Short chip label used on the Listen screen.
  String get shortName {
    if (name.startsWith('Mishary')) return 'Alafasy';
    if (name.startsWith('Abdul Basit')) return 'Basit';
    if (name.startsWith('Muhammad Siddiq')) return 'Siddiq';
    return name;
  }
}

/// Verse-by-verse Quran.com recitation, exposed to the lock screen and
/// CarPlay through `audio_service`. Port of the playback half of
/// `PlayerActivity.java`: audio streams from the public CDN over HTTPS, nothing
/// is stored, and at the end of a surah playback continues to the next one when
/// [continuous] is on (car mode or the continuousListen preference).
class RecitationHandler extends BaseAudioHandler with SeekHandler {
  static const String cdn = 'https://verses.quran.com/';

  static const List<Reciter> reciters = [
    Reciter('Mishary Rashid Alafasy', 'Alafasy/mp3/%03d%03d.mp3'),
    Reciter('Abdul Basit Abdus Samad (Murattal)', 'AbdulBaset/Murattal/mp3/%03d%03d.mp3'),
    Reciter('Muhammad Siddiq Al-Minshawi (Murattal)', 'Minshawi/Murattal/mp3/%03d%03d.mp3'),
  ];

  static late RecitationHandler instance;

  /// Registers the handler with the system once, before `runApp`.
  static Future<void> init() async {
    try {
      final session = await AudioSession.instance;
      await session.configure(const AudioSessionConfiguration.music());
    } catch (_) {
      // The session is a nicety on simulators and in tests.
    }
    try {
      instance = await AudioService.init(
        builder: RecitationHandler.new,
        config: const AudioServiceConfig(
          androidNotificationChannelId: 'com.rqmbooks.rafiqalquran.recitation',
          androidNotificationChannelName: 'Quran recitation',
          androidNotificationOngoing: true,
          androidStopForegroundOnPause: true,
        ),
      );
    } catch (_) {
      instance = RecitationHandler();
    }
  }

  final AudioPlayer _player = AudioPlayer();

  /// Screen state; the player screen listens to this.
  final ChangeNotifier changes = _Notifier();

  Dataset? dataset;
  Chapter? chapter;
  final List<Verse> verses = <Verse>[];
  int chapterId = 1;
  int index = 0;
  bool carMode = false;
  bool continuous = true;
  int reciterIndex = 0;

  /// Human-readable status shown under the reciter name.
  String status = 'Tap play to stream this verse from Quran.com.';
  bool statusIsError = false;
  bool buffering = false;
  int _loadGeneration = 0;

  RecitationHandler() {
    _player.playbackEventStream.listen(_broadcast, onError: (Object error, StackTrace stack) {
      _setStatus('Could not play this verse. Check your internet connection and try again.', error: true);
      buffering = false;
      _notify();
    });
    _player.processingStateStream.listen((state) {
      if (state == ProcessingState.completed) _onCompleted();
    });
  }

  Stream<Duration> get positionStream => _player.positionStream;
  Duration? get duration => _player.duration;
  bool get playing => _player.playing && _player.processingState != ProcessingState.completed;
  bool get prepared =>
      _player.processingState == ProcessingState.ready || _player.processingState == ProcessingState.buffering;

  Reciter get reciter => reciters[reciterIndex.clamp(0, reciters.length - 1)];
  Verse? get currentVerse => verses.isEmpty ? null : verses[index];

  void _notify() => (changes as _Notifier).ping();

  void _setStatus(String message, {bool error = false}) {
    status = message;
    statusIsError = error;
  }

  // ---------------------------------------------------------------- loading
  /// Loads a chapter and positions on [verseKey] (first verse when null).
  void loadChapter(Dataset data, int requestedChapter, String? verseKey, {bool? car, bool? continuousPlay}) {
    dataset = data;
    chapterId = requestedChapter < 1 || requestedChapter > 114 ? 1 : requestedChapter;
    if (car != null) carMode = car;
    if (continuousPlay != null) continuous = continuousPlay || carMode;
    chapter = data.chapter(chapterId) ?? data.chapter(1);
    verses
      ..clear()
      ..addAll(data.chapterVerses(chapter!.id));
    index = 0;
    if (verseKey != null && verseKey.isNotEmpty) {
      final position = verses.indexWhere((verse) => verse.key == verseKey);
      if (position >= 0) index = position;
    }
    _updateMediaItem();
    _notify();
  }

  void _updateMediaItem() {
    final verse = currentVerse;
    final name = chapter?.nameEnglish ?? 'Rafiq Al-Quran';
    mediaItem.add(MediaItem(
      id: verse == null ? 'rafiq' : reciter.url(verse.chapterId, verse.number),
      title: verse == null ? name : '$name ${verse.key}',
      album: 'Rafiq Al-Quran',
      artist: '${reciter.name} · ${AudioGate.attribution()}',
      duration: _player.duration,
    ));
  }

  String verseUrl() {
    final verse = currentVerse!;
    return reciter.url(verse.chapterId, verse.number);
  }

  // --------------------------------------------------------------- controls
  Future<void> prepareAndPlay() async {
    if (verses.isEmpty) return;
    final generation = ++_loadGeneration;
    buffering = true;
    _setStatus('Buffering from Quran.com…');
    _updateMediaItem();
    _notify();
    try {
      await _player.setUrl(verseUrl());
      if (generation != _loadGeneration) return;
      buffering = false;
      _setStatus('Playing — ${AudioGate.attribution()}');
      _updateMediaItem();
      _notify();
      await _player.play();
    } catch (error) {
      if (generation != _loadGeneration) return;
      buffering = false;
      _setStatus('Could not play this verse. Check your internet connection and try again.', error: true);
      _notify();
    }
  }

  @override
  Future<void> play() async {
    if (prepared && !playing) {
      _setStatus('Playing — ${AudioGate.attribution()}');
      _notify();
      await _player.play();
      return;
    }
    await prepareAndPlay();
  }

  @override
  Future<void> pause() async {
    await _player.pause();
    _setStatus('Paused.');
    _notify();
  }

  Future<void> togglePlay() async {
    if (playing) {
      await pause();
    } else {
      await play();
    }
  }

  Future<void> replay() async {
    if (prepared) {
      try {
        await _player.seek(Duration.zero);
        _setStatus('Replaying — ${AudioGate.attribution()}');
        _notify();
        await _player.play();
        return;
      } catch (_) {
        // fall through to a fresh stream
      }
    }
    await prepareAndPlay();
  }

  @override
  Future<void> stop() async {
    _loadGeneration++;
    buffering = false;
    try {
      await _player.stop();
    } catch (_) {
      // already stopped
    }
    _setStatus('Stopped.');
    _notify();
    await super.stop();
  }

  /// Pauses without resetting the status (used when the screen moves verses).
  Future<void> _halt() async {
    _loadGeneration++;
    buffering = false;
    try {
      if (_player.playing) await _player.pause();
    } catch (_) {
      // best effort
    }
  }

  @override
  Future<void> seek(Duration position) => _player.seek(position);

  /// Previous / next verse inside the surah.
  Future<void> moveVerse(int delta) async {
    final target = index + delta;
    if (target < 0 || target >= verses.length) return;
    await _halt();
    index = target;
    _setStatus('Ready to play ${chapter!.nameEnglish} ${verses[index].key}.');
    _updateMediaItem();
    _notify();
  }

  /// Next surah in mushaf order; media-button "next" in car mode.
  @override
  Future<void> skipToNext() => playNextChapter();

  Future<void> playNextChapter() async {
    final data = dataset;
    if (data == null) return;
    if (chapterId >= 114) {
      await stop();
      _setStatus('Completed the recitation through Surah An-Nas.');
      _notify();
      return;
    }
    await _halt();
    loadChapter(data, chapterId + 1, null);
    await prepareAndPlay();
  }

  @override
  Future<void> skipToPrevious() => previousSurah();

  Future<void> previousSurah() async {
    final data = dataset;
    if (data == null) return;
    if (chapterId <= 1) {
      await moveVerse(-1);
      return;
    }
    await _halt();
    loadChapter(data, chapterId - 1, null);
  }

  Future<void> setReciter(int value) async {
    reciterIndex = value.clamp(0, reciters.length - 1);
    await _halt();
    _setStatus('Reciter set to ${reciter.name}. Tap play to listen.');
    _updateMediaItem();
    _notify();
  }

  /// Releases the stream when the player screen closes (Android onDestroy).
  Future<void> release() async {
    _loadGeneration++;
    buffering = false;
    try {
      await _player.stop();
    } catch (_) {
      // already released
    }
    _setStatus('Tap play to stream this verse from Quran.com.');
    _notify();
  }

  // ------------------------------------------------------------- callbacks
  Future<void> _onCompleted() async {
    if (index < verses.length - 1) {
      index++;
      _notify();
      await prepareAndPlay();
    } else if (continuous && chapterId < 114) {
      await playNextChapter();
    } else {
      await _player.stop();
      _setStatus(continuous ? 'Completed the recitation through Surah An-Nas.' : 'Finished reciting this surah.');
      _notify();
    }
  }

  void _broadcast(PlaybackEvent event) {
    final isPlaying = _player.playing;
    playbackState.add(playbackState.value.copyWith(
      controls: [
        MediaControl.skipToPrevious,
        if (isPlaying) MediaControl.pause else MediaControl.play,
        MediaControl.stop,
        MediaControl.skipToNext,
      ],
      systemActions: const {MediaAction.seek, MediaAction.seekForward, MediaAction.seekBackward},
      androidCompactActionIndices: const [0, 1, 3],
      processingState: const {
        ProcessingState.idle: AudioProcessingState.idle,
        ProcessingState.loading: AudioProcessingState.loading,
        ProcessingState.buffering: AudioProcessingState.buffering,
        ProcessingState.ready: AudioProcessingState.ready,
        ProcessingState.completed: AudioProcessingState.completed,
      }[_player.processingState]!,
      playing: isPlaying,
      updatePosition: _player.position,
      bufferedPosition: _player.bufferedPosition,
      speed: _player.speed,
      queueIndex: event.currentIndex,
    ));
    _notify();
  }

  static String formatTime(Duration? value) {
    if (value == null || value.inMilliseconds <= 0) return '0:00';
    final totalSeconds = value.inSeconds;
    final minutes = totalSeconds ~/ 60;
    final seconds = totalSeconds % 60;
    return '$minutes:${seconds.toString().padLeft(2, '0')}';
  }
}

class _Notifier extends ChangeNotifier {
  void ping() => notifyListeners();
}
