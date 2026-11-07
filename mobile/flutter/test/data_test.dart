// Data-layer checks: history codec, archives, study store, prefs defaults,
// attachments and API helpers. These run without a device.
import 'dart:convert';
import 'dart:typed_data';

import 'package:archive/archive.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:rafiq_al_quran/data/api.dart';
import 'package:rafiq_al_quran/data/attachments.dart';
import 'package:rafiq_al_quran/data/history_codec.dart';
import 'package:rafiq_al_quran/data/prefs.dart';
import 'package:rafiq_al_quran/data/study_store.dart';
import 'package:rafiq_al_quran/logic/logic.dart';
import 'package:shared_preferences/shared_preferences.dart';

Answer _sampleAnswer() {
  const chapter = Chapter(2, 'البقرة', 'Al-Baqarah', 286, 'Medinan');
  const meta = TranslationMeta('en', 'Saheeh International', 'Saheeh International', 'ltr', 'unverified');
  final provenance = Provenance()
    ..dataset = 'quran-uthmani'
    ..version = 'quran.com-api-v4'
    ..sourceUrl = 'https://api.quran.com'
    ..checksum = 'abc';
  final passage = Passage('2:255', chapter, 'ٱللَّهُ لَآ إِلَٰهَ إِلَّا هُوَ', 'Allah - there is no deity except Him', 'en', meta,
      false, provenance);
  return Answer('answer', 'en', 'I found one passage that refers to this:', [passage], 1.0, Notices.noticeEn, true,
      null, const ['limit'], true, const ['2:255'], 'summary text');
}

void main() {
  group('history codec', () {
    test('round-trips turns without the summary', () {
      final turns = [const Turn('user', 'Show me 2:255', null), Turn('assistant', null, _sampleAnswer())];
      final decoded = HistoryCodec.decode(HistoryCodec.encodeString(turns));
      expect(decoded.length, 2);
      expect(decoded[0].role, 'user');
      expect(decoded[0].question, 'Show me 2:255');
      final answer = decoded[1].answer!;
      expect(answer.kind, 'answer');
      expect(answer.passages.single.verseKey, '2:255');
      expect(answer.passages.single.referenceLabel, 'Al-Baqarah 2:255');
      expect(answer.passages.single.translationLicenseStatus, 'unverified');
      expect(answer.notice, Notices.noticeEn);
      expect(answer.shownVerseKeys, ['2:255']);
      expect(answer.summary, isNull);
    });
    test('malformed json decodes to an empty list', () {
      expect(HistoryCodec.decode('not json'), isEmpty);
      expect(HistoryCodec.decode(null), isEmpty);
    });
    test('archives keep one entry per conversation, newest first, max 20', () {
      var list = <dynamic>[];
      for (var i = 0; i < 25; i++) {
        list = HistoryCodec.archive(list, 'id-$i', [Turn('user', 'Question $i', null)], i);
      }
      expect(list.length, HistoryCodec.maxArchives);
      expect((list.first as Map)['title'], 'Question 24');
      list = HistoryCodec.archive(list, 'id-24', [const Turn('user', 'Replaced', null)], 99);
      expect(list.length, HistoryCodec.maxArchives);
      expect((list.first as Map)['title'], 'Replaced');
      expect(list.where((item) => (item as Map)['id'] == 'id-24').length, 1);
    });
    test('archive title skips attachment bubbles and is clipped', () {
      expect(HistoryCodec.archiveTitle([const Turn('user', '[Attachment: a.txt]', null)]), 'Attachment conversation');
      expect(HistoryCodec.archiveTitle([Turn('user', 'x' * 200, null)]).length, 90);
    });
    test('verified() drops passages whose Arabic no longer matches', () {
      final turns = [Turn('assistant', null, _sampleAnswer())];
      expect(HistoryCodec.verified(turns, null), isEmpty);
      final conversation = [const Turn('assistant', null, Answer('conversation', 'en', 'Hi', [], 0, '', false, null, [], false, []))];
      expect(HistoryCodec.verified(conversation, null).length, 1);
    });
  });

  group('prefs and study store', () {
    late SharedPreferences shared;
    setUp(() async {
      SharedPreferences.setMockInitialValues(<String, Object>{});
      shared = await SharedPreferences.getInstance();
    });

    test('defaults match the Android quran_prefs file', () {
      final prefs = Prefs(shared);
      expect(prefs.language, 'en');
      expect(prefs.textScale, 1.0);
      expect(prefs.voiceEnabled, isTrue);
      expect(prefs.handsFree, isFalse);
      expect(prefs.onboardingSeen, isFalse);
      expect(prefs.history, '[]');
      expect(prefs.conversations(), isEmpty);
      expect(prefs.theme, 'light');
      expect(prefs.sideBySide, isFalse);
      expect(prefs.bookMode, isTrue);
      expect(prefs.continuousListen, isTrue);
      expect(prefs.translationVisible, isTrue);
      expect(prefs.adminToken, '');
      expect(prefs.reciterIndex, 0);
      expect(prefs.aiAssistEnabled, isTrue);
      expect(prefs.aiConsentShown, isFalse);
      expect(prefs.aiCallsToday, 0);
      expect(prefs.aiDay, '');
      prefs.theme = 'weird';
      expect(prefs.theme, 'light');
    });

    test('collections always include General and dedupe case-insensitively', () {
      final store = StudyStore(shared);
      expect(store.collections(), ['General']);
      expect(store.addCollection('Reflections'), 'Reflections');
      expect(store.addCollection('reflections'), 'Reflections');
      expect(store.collections().length, 2);
      store.removeCollection('General');
      expect(store.collections(), contains('General'));
    });

    test('bookmarks move to General when their collection is deleted', () {
      final store = StudyStore(shared);
      store.bookmark('2:255', 'Favourites');
      store.bookmark('1:1', 'General');
      expect(store.bookmarkedKeys(null), ['1:1', '2:255']);
      expect(store.collectionOf('2:255'), 'Favourites');
      store.removeCollection('Favourites');
      expect(store.collectionOf('2:255'), 'General');
      expect(store.toggleBookmark('2:255'), isFalse);
      expect(store.bookmarkTotal(), 1);
    });

    test('notes, searches and history are bounded', () {
      final store = StudyStore(shared);
      store.setNote('2:255', '  hello  ');
      expect(store.note('2:255'), 'hello');
      store.setNote('2:255', '');
      expect(store.noteKeys(), isEmpty);
      for (var i = 0; i < 40; i++) {
        store.addSearch('query $i');
      }
      expect(store.searches().length, StudyData.maxSearches);
      expect(store.searches().first, 'query 39');
      store.addSearch('QUERY 39');
      expect(store.searches().where((q) => q.toLowerCase() == 'query 39').length, 1);
      for (var i = 1; i <= 35; i++) {
        store.recordHistoryChapter(i);
      }
      expect(store.historyChapters().length, 30);
      expect(store.historyChapters().first, 35);
    });

    test('plans track progress with the StudyData presets', () {
      final store = StudyStore(shared);
      final plan = store.createPlan('', StudyData.presetLast10, 0);
      expect(plan.name, StudyData.presetTitle(StudyData.presetLast10));
      expect(plan.days, 1);
      expect(plan.chapters.length, 10);
      store.markChapterDone(plan.id, 105);
      store.markChapterDone(plan.id, 3);
      final reloaded = store.plans().single;
      expect(reloaded.done, [105]);
      expect(reloaded.percent(), 10);
      expect(reloaded.nextChapter(), 106);
      store.deletePlan(plan.id);
      expect(store.plans(), isEmpty);
    });

    test('reading position is recorded', () {
      final store = StudyStore(shared);
      expect(store.hasProgress, isFalse);
      store.recordProgress(2, '2:255');
      expect(store.hasProgress, isTrue);
      expect(store.lastChapter, 2);
      expect(store.lastVerseKey, '2:255');
      expect(store.lastAt, greaterThan(0));
    });
  });

  group('attachments', () {
    test('reads text files on the device', () {
      final file = Attachments.read('notes.txt', Uint8List.fromList(utf8.encode('patience and mercy\r\nline two')));
      expect(file.readableLocally, isTrue);
      expect(file.text, 'patience and mercy\nline two');
      expect(file.summary(), contains('characters read'));
    });
    test('extracts paragraphs from a .docx archive', () {
      const xml = '<?xml version="1.0"?><w:document><w:body><w:p><w:r><w:t>Hello &amp; welcome</w:t></w:r></w:p>'
          '<w:p><w:r><w:t>Second</w:t><w:tab/><w:t>para</w:t></w:r></w:p></w:body></w:document>';
      final archive = Archive()..addFile(ArchiveFile('word/document.xml', xml.length, utf8.encode(xml)));
      final bytes = Uint8List.fromList(ZipEncoder().encode(archive));
      final file = Attachments.read('doc.docx', bytes);
      expect(file.readableLocally, isTrue);
      expect(file.text, 'Hello & welcome\nSecond para');
    });
    test('images are kept as bytes for the AI service', () {
      final file = Attachments.read('shot.png', Uint8List.fromList([1, 2, 3]));
      expect(file.image, isTrue);
      expect(file.readableLocally, isFalse);
      expect(file.mime, 'image/png');
      expect(file.summary(), contains('AI-assisted'));
    });
    test('unsupported types and oversize files report an error', () {
      expect(Attachments.read('a.pdf', Uint8List(10)).error, contains('not supported'));
      expect(Attachments.read('a.txt', Uint8List(Attachments.maxBytes + 1)).error, 'The file could not be read.');
      expect(Attachments.read(null, null).error, 'No file was selected.');
    });
  });

  group('api helpers', () {
    test('quota errors are detected from the message', () {
      expect(Api.isQuotaError(const ApiException(429, '{"error":"Too many"}')), isTrue);
      expect(Api.isQuotaError(const ApiException(503, 'Daily AI budget exhausted')), isTrue);
      expect(Api.isQuotaError(const ApiException(500, 'quota exceeded')), isTrue);
      expect(Api.isQuotaError(const ApiException(500, 'boom')), isFalse);
      expect(Api.isQuotaError(null), isFalse);
    });
    test('optional readers tolerate missing and mistyped values', () {
      final object = <String, dynamic>{'a': '5', 'b': 'true', 'c': 2.0, 'd': <String, dynamic>{'x': 1}};
      expect(Api.optInt(object, 'a'), 5);
      expect(Api.optInt(object, 'c'), 2);
      expect(Api.optInt(object, 'zz', 7), 7);
      expect(Api.optBool(object, 'b'), isTrue);
      expect(Api.optString(object, 'zz', 'dash'), 'dash');
      expect(Api.optObject(object, 'd')!['x'], 1);
      expect(Api.array(object, 'nope'), isEmpty);
    });
    test('base URL matches the Android strings.xml', () => expect(Api.baseUrl, 'https://rqmbooks.com/rafiq-api'));
  });
}
