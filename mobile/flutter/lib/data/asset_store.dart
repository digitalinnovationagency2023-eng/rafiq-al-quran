import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import '../logic/logic.dart';

/// Loads the bundled dataset. The Arabic text is parsed once in a background
/// isolate; translations are loaded one language at a time so the first screen
/// appears quickly and memory stays small on a phone. Port of `Assets.java`.
class AssetStore {
  AssetStore._();

  static final AssetStore instance = AssetStore._();

  static const String dataPath = 'assets/data';

  Dataset? _dataset;
  QuranMeta? _meta;
  Future<Dataset>? _datasetFuture;
  Future<QuranMeta>? _metaFuture;
  final Set<String> _loadedLanguages = <String>{};
  final Map<String, Future<void>> _languageFutures = <String, Future<void>>{};

  /// The dataset when it is already in memory, otherwise null.
  Dataset? get datasetOrNull => _dataset;
  QuranMeta? get metaOrNull => _meta;

  bool isLanguageLoaded(String language) => _loadedLanguages.contains(language);

  /// Parses everything except the translations (once).
  Future<Dataset> dataset() {
    final existing = _dataset;
    if (existing != null) return Future<Dataset>.value(existing);
    return _datasetFuture ??= _loadDataset();
  }

  Future<Dataset> _loadDataset() async {
    try {
      final files = await Future.wait<String>([
        rootBundle.loadString('$dataPath/chapters.tsv'),
        rootBundle.loadString('$dataPath/quran.tsv'),
        rootBundle.loadString('$dataPath/translation-meta.tsv'),
        rootBundle.loadString('$dataPath/lexicon.tsv'),
        rootBundle.loadString('$dataPath/provenance.tsv'),
      ]);
      final loaded = await compute(_buildDataset, files);
      _dataset = loaded;
      return loaded;
    } catch (error) {
      _datasetFuture = null;
      throw StateError('Bundled Quran dataset could not be loaded: $error');
    }
  }

  /// Loads one language's translation rows if they are not in memory yet.
  Future<void> ensureLanguage(String language) async {
    if (language == 'ar' || _loadedLanguages.contains(language)) return;
    final pending = _languageFutures[language];
    if (pending != null) return pending;
    final future = _loadLanguage(language);
    _languageFutures[language] = future;
    try {
      await future;
    } finally {
      _languageFutures.remove(language);
    }
  }

  Future<void> _loadLanguage(String language) async {
    final data = await dataset();
    try {
      final content = await rootBundle.loadString('$dataPath/translations.$language.tsv');
      final bundle = await compute(_buildBundle, <Object>[content, data.verseIndexByKey()]);
      data.mergeTranslationBundle(bundle);
      _loadedLanguages.add(language);
    } catch (error) {
      throw StateError('Translation for $language could not be loaded: $error');
    }
  }

  /// Structural juz/page/hizb metadata, loaded once.
  Future<QuranMeta> meta() {
    final existing = _meta;
    if (existing != null) return Future<QuranMeta>.value(existing);
    return _metaFuture ??= _loadMeta();
  }

  Future<QuranMeta> _loadMeta() async {
    try {
      final content = await rootBundle.loadString('$dataPath/quran-meta.tsv');
      final parsed = await compute(_parseMeta, content);
      _meta = parsed;
      return parsed;
    } catch (error) {
      _metaFuture = null;
      throw StateError('Mushaf page metadata could not be loaded: $error');
    }
  }

  /// Dataset plus the English rows and the interface language (the common
  /// start-up sequence of every screen).
  Future<Dataset> ready(String language) async {
    final data = await dataset();
    await ensureLanguage('en');
    if (language != 'en') {
      try {
        await ensureLanguage(language);
      } catch (_) {
        // English remains the fallback translation.
      }
    }
    return data;
  }
}

Dataset _buildDataset(List<String> files) => DatasetLoader.load(
      DatasetLoader.readLines(files[0]),
      DatasetLoader.readLines(files[1]),
      const <String>[],
      DatasetLoader.readLines(files[2]),
      DatasetLoader.readLines(files[3]),
      DatasetLoader.readLines(files[4]),
    );

TranslationBundle _buildBundle(List<Object> args) => Dataset.buildTranslationBundle(
      DatasetLoader.readLines(args[0] as String),
      (args[1] as Map).cast<String, int>(),
    );

QuranMeta _parseMeta(String content) => QuranMeta.parse(DatasetLoader.readLines(content));
