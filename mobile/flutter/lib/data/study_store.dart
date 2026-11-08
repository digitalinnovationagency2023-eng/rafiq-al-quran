import 'dart:convert';

import 'package:shared_preferences/shared_preferences.dart';

import '../logic/study_data.dart';

/// A reading plan (port of `StudyStore.Plan`).
class Plan {
  final String id;
  final String name;
  final String preset;
  final int days;
  final List<int> chapters;
  final List<int> done;
  final int createdAt;

  const Plan(this.id, this.name, this.preset, this.days, this.chapters, this.done, this.createdAt);

  int percent() => StudyData.percent(done.length, chapters.length);
  String progressText() => StudyData.formatProgress(done.length, chapters.length);
  bool get isComplete => chapters.isNotEmpty && done.length >= chapters.length;
  int nextChapter() => StudyData.nextChapter(chapters, done);
}

/// Device-local study data: bookmark collections, personal notes, saved
/// searches, reading plans and the last reading position. Same keys as the
/// Android `rafiq_study` file. Everything stays on the device.
class StudyStore {
  static const _keyCollections = 'collections';
  static const _keyBookmarks = 'bookmarks';
  static const _keyNotes = 'notes';
  static const _keySearches = 'searches';
  static const _keyPlans = 'plans';
  static const _keyLastVerse = 'lastVerse';
  static const _keyLastChapter = 'lastChapter';
  static const _keyLastAt = 'lastAt';
  static const _keyHistory = 'historyChapters';

  final SharedPreferences _prefs;

  StudyStore(this._prefs);

  int _now() => DateTime.now().millisecondsSinceEpoch;

  Map<String, dynamic> _object(String key) {
    try {
      final decoded = jsonDecode(_prefs.getString(key) ?? '{}');
      return decoded is Map ? Map<String, dynamic>.from(decoded) : <String, dynamic>{};
    } catch (_) {
      return <String, dynamic>{};
    }
  }

  List<dynamic> _array(String key) {
    try {
      final decoded = jsonDecode(_prefs.getString(key) ?? '[]');
      return decoded is List ? decoded : <dynamic>[];
    } catch (_) {
      return <dynamic>[];
    }
  }

  void _put(String key, Object value) => _prefs.setString(key, jsonEncode(value));

  // ------------------------------------------------------------ collections
  List<String> collections() {
    final names = <String>[];
    for (final item in _array(_keyCollections)) {
      final name = item is String ? item : '';
      if (name.isNotEmpty && !names.contains(name)) names.add(name);
    }
    if (!_containsIgnoreCase(names, StudyData.defaultCollection)) {
      names.insert(0, StudyData.defaultCollection);
      _saveCollections(names);
    }
    return names;
  }

  String addCollection(String? raw) {
    final names = collections();
    final canonical = StudyData.addCollection(names, raw);
    _saveCollections(names);
    return canonical;
  }

  void removeCollection(String? raw) {
    final names = collections();
    final name = StudyData.sanitizeCollectionName(raw);
    if (name.toLowerCase() == StudyData.defaultCollection.toLowerCase()) return;
    names.removeWhere((existing) => existing.toLowerCase() == name.toLowerCase());
    _saveCollections(names);
    // Reassign the collection's bookmarks to the default collection.
    final bookmarks = _bookmarks();
    for (final key in bookmarks.keys.toList()) {
      if ((bookmarks[key]?.toString() ?? StudyData.defaultCollection) == name) {
        bookmarks[key] = StudyData.defaultCollection;
      }
    }
    _put(_keyBookmarks, bookmarks);
  }

  void _saveCollections(List<String> names) => _put(_keyCollections, names);

  static bool _containsIgnoreCase(List<String> values, String needle) =>
      values.any((value) => value.toLowerCase() == needle.toLowerCase());

  // -------------------------------------------------------------- bookmarks
  Map<String, dynamic> _bookmarks() => _object(_keyBookmarks);

  bool isBookmarked(String verseKey) => _bookmarks().containsKey(verseKey);

  String? collectionOf(String verseKey) {
    final object = _bookmarks();
    return object.containsKey(verseKey) ? (object[verseKey]?.toString() ?? StudyData.defaultCollection) : null;
  }

  void bookmark(String verseKey, String? collection) {
    final object = _bookmarks();
    var name = StudyData.sanitizeCollectionName(collection);
    if (!_containsIgnoreCase(collections(), name)) {
      addCollection(name);
      name = StudyData.sanitizeCollectionName(name);
    }
    object[verseKey] = name;
    _put(_keyBookmarks, object);
  }

  void removeBookmark(String verseKey) {
    final object = _bookmarks();
    object.remove(verseKey);
    _put(_keyBookmarks, object);
  }

  /// Toggles the default collection. Returns true when the verse is now saved.
  bool toggleBookmark(String verseKey) {
    if (isBookmarked(verseKey)) {
      removeBookmark(verseKey);
      return false;
    }
    bookmark(verseKey, StudyData.defaultCollection);
    return true;
  }

  List<String> bookmarkedKeys(String? collection) {
    final keys = <String>[];
    final object = _bookmarks();
    for (final key in object.keys) {
      final value = object[key]?.toString() ?? StudyData.defaultCollection;
      if (collection == null || value.toLowerCase() == collection.toLowerCase()) keys.add(key);
    }
    keys.sort(StudyData.compareVerseKeys);
    return keys;
  }

  int bookmarkCount(String collection) => bookmarkedKeys(collection).length;

  int bookmarkTotal() => bookmarkedKeys(null).length;

  // ------------------------------------------------------------------ notes
  String note(String verseKey) {
    final entry = _object(_keyNotes)[verseKey];
    return entry is Map ? (entry['text']?.toString() ?? '') : '';
  }

  void setNote(String verseKey, String? text) {
    final notes = _object(_keyNotes);
    final cleaned = StudyData.sanitizeNote(text);
    if (cleaned.isEmpty) {
      notes.remove(verseKey);
    } else {
      notes[verseKey] = <String, dynamic>{'text': cleaned, 'at': _now()};
    }
    _put(_keyNotes, notes);
  }

  List<String> noteKeys() => _object(_keyNotes).keys.toList()..sort(StudyData.compareVerseKeys);

  int noteCount() => noteKeys().length;

  // --------------------------------------------------------- saved searches
  void addSearch(String? query) {
    final cleaned = query?.trim() ?? '';
    if (cleaned.isEmpty) return;
    final queries = <String>[];
    for (final entry in _array(_keySearches)) {
      final value = entry is Map ? (entry['q']?.toString() ?? '') : '';
      if (value.isNotEmpty && value.toLowerCase() != cleaned.toLowerCase() && !queries.contains(value)) {
        queries.add(value);
      }
    }
    queries.insert(0, cleaned);
    while (queries.length > StudyData.maxSearches) {
      queries.removeLast();
    }
    final now = _now();
    _put(_keySearches, [for (final value in queries) <String, dynamic>{'q': value, 'at': now}]);
  }

  List<String> searches() {
    final queries = <String>[];
    for (final entry in _array(_keySearches)) {
      final value = entry is Map ? (entry['q']?.toString() ?? '') : '';
      if (value.isNotEmpty) queries.add(value);
    }
    return queries;
  }

  void clearSearches() => _prefs.setString(_keySearches, '[]');

  // ----------------------------------------------------------- reading plans
  List<Plan> plans() {
    final plans = <Plan>[];
    final array = _array(_keyPlans);
    for (var i = 0; i < array.length; i++) {
      final object = array[i];
      if (object is! Map) continue;
      try {
        final chapters = StudyData.parseChapters(object['chapters']?.toString() ?? '');
        final done = StudyData.parseChapters(object['done']?.toString() ?? '');
        plans.add(Plan(
          object['id']?.toString() ?? 'plan-$i',
          object['name']?.toString() ?? 'Reading plan',
          object['preset']?.toString() ?? StudyData.presetFull,
          _int(object['days'], 30),
          chapters,
          done,
          _int(object['createdAt'], 0),
        ));
      } catch (_) {
        // skip malformed plan
      }
    }
    return plans;
  }

  static int _int(Object? value, int fallback) {
    if (value is int) return value;
    if (value is num) return value.toInt();
    return int.tryParse(value?.toString() ?? '') ?? fallback;
  }

  Plan createPlan(String? name, String? preset, int days) {
    final chapters = StudyData.presetChapters(preset);
    final now = _now();
    final id = 'plan-$now';
    var cleaned = StudyData.sanitizeCollectionName(name);
    if (name == null || name.trim().isEmpty) cleaned = StudyData.presetTitle(preset);
    final safeDays = days < 1 ? 1 : days;
    final array = _array(_keyPlans);
    array.add(<String, dynamic>{
      'id': id,
      'name': cleaned,
      'preset': preset,
      'days': safeDays,
      'chapters': StudyData.joinChapters(chapters),
      'done': '',
      'createdAt': now,
    });
    _put(_keyPlans, array);
    return Plan(id, cleaned, preset ?? StudyData.presetFull, safeDays, chapters, <int>[], now);
  }

  void markChapterDone(String planId, int chapterId) {
    final array = _array(_keyPlans);
    for (final object in array) {
      if (object is! Map || object['id']?.toString() != planId) continue;
      final chapters = StudyData.parseChapters(object['chapters']?.toString() ?? '');
      if (!chapters.contains(chapterId)) return;
      final done = StudyData.parseChapters(object['done']?.toString() ?? '');
      if (!done.contains(chapterId)) done.add(chapterId);
      object['done'] = StudyData.joinChapters(done);
    }
    _put(_keyPlans, array);
  }

  void deletePlan(String planId) {
    final next = [
      for (final object in _array(_keyPlans))
        if (object is Map && object['id']?.toString() != planId) object
    ];
    _put(_keyPlans, next);
  }

  void clearPlans() => _prefs.setString(_keyPlans, '[]');

  // -------------------------------------------------------- reading position
  void recordProgress(int chapterId, String? verseKey) {
    _prefs.setInt(_keyLastChapter, chapterId);
    _prefs.setString(_keyLastVerse, verseKey ?? '');
    _prefs.setInt(_keyLastAt, _now());
  }

  bool get hasProgress => lastChapter > 0;

  int get lastChapter => _prefs.getInt(_keyLastChapter) ?? 0;

  String get lastVerseKey => _prefs.getString(_keyLastVerse) ?? '';

  int get lastAt => _prefs.getInt(_keyLastAt) ?? 0;

  // ------------------------------------------------------- reading history
  /// Most-recent-first list of chapter ids, capped at 30.
  void recordHistoryChapter(int chapterId) {
    final chapters = historyChapters();
    chapters.remove(chapterId);
    chapters.insert(0, chapterId);
    while (chapters.length > 30) {
      chapters.removeLast();
    }
    _put(_keyHistory, chapters);
  }

  List<int> historyChapters() {
    final chapters = <int>[];
    for (final item in _array(_keyHistory)) {
      final chapter = _int(item, 0);
      if (chapter >= 1 && chapter <= 114 && !chapters.contains(chapter)) chapters.add(chapter);
    }
    return chapters;
  }

  void clearHistory() => _prefs.setString(_keyHistory, '[]');
}
