// Port of mobile/android/tools/LogicTest.java: the pure app logic verified
// against the real bundled assets (assets/data, produced by tool/sync_assets.mjs).
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:rafiq_al_quran/logic/logic.dart';

List<String> _read(String name) {
  final file = File('assets/data/$name');
  if (!file.existsSync()) {
    throw StateError('Missing $name. Run `node mobile/flutter/tool/sync_assets.mjs` first.');
  }
  return DatasetLoader.readLines(file.readAsStringSync());
}

void main() {
  late Dataset dataset;
  late QuranMeta meta;
  late Answer english;
  late Answer arabic;

  setUpAll(() {
    final translations = <String>[];
    for (final code in Language.codes()) {
      final file = File('assets/data/translations.$code.tsv');
      if (file.existsSync()) translations.addAll(DatasetLoader.readLines(file.readAsStringSync()));
    }
    dataset = DatasetLoader.load(
      _read('chapters.tsv'),
      _read('quran.tsv'),
      translations,
      _read('translation-meta.tsv'),
      _read('lexicon.tsv'),
      _read('provenance.tsv'),
    );
    meta = QuranMeta.parse(_read('quran-meta.tsv'));
    english = AnswerComposer.answer(dataset, 'What does the Quran say about patience?', 'en');
    arabic = AnswerComposer.answer(dataset, 'ماذا يقول القرآن عن الصبر', 'ar');
  });

  group('dataset', () {
    test('has 114 chapters', () => expect(dataset.chapterCount(), 114));
    test('has 6236 verses', () => expect(dataset.verseCount(), 6236));
    test('has 9 translations', () => expect(dataset.translationCount(), 9));
    test('provenance checksum is recorded', () => expect(dataset.provenance().checksum.length, 64));
    test('first verse text is the Uthmani basmala',
        () => expect(dataset.verse('1:1')!.arabic.startsWith('بِسْمِ'), isTrue));
    test('2:255 exists', () => expect(dataset.verse('2:255'), isNotNull));
    test('every language has translation rows', () {
      for (final code in Language.codes()) {
        if (code == 'ar') continue;
        expect(dataset.hasTranslationLanguage(code), isTrue, reason: code);
      }
    });
  });

  group('answers', () {
    test('english topic question is answered', () {
      expect(english.kind, 'answer');
      expect(english.passages, isNotEmpty);
    });
    test('english answer carries the exact notice', () => expect(english.notice, Notices.noticeEn));
    test('english answer keeps translation separate from Arabic', () {
      expect(english.passages.first.translationText.length, greaterThan(10));
      expect(english.passages.first.translationText, isNot(english.passages.first.arabic));
    });
    test('every passage exists in the dataset with identical Arabic', () {
      for (final passage in english.passages) {
        final verse = dataset.verse(passage.verseKey);
        expect(verse, isNotNull, reason: passage.verseKey);
        expect(verse!.arabic, passage.arabic);
      }
    });
    test('arabic topic question is answered', () {
      expect(arabic.kind, 'answer');
      expect(arabic.passages, isNotEmpty);
    });
    test('arabic answer shows the exact Arabic notice', () => expect(arabic.notice, Notices.noticeAr));
    test('arabic interface falls back to the English translation, labelled', () {
      expect(arabic.passages.first.fallbackToEnglish, isTrue);
      expect(arabic.passages.first.translationLanguage, 'en');
    });
    test('explicit reference shows exactly that verse', () {
      final reference = AnswerComposer.answer(dataset, 'Show me 2:255', 'en');
      expect(reference.passages.first.verseKey, '2:255');
    });
    test('reference separators 2.255 and 2-255 are accepted', () {
      expect(AnswerComposer.parseVerseReference('open 2.255 please'), '2:255');
      expect(AnswerComposer.parseVerseReference('2-255'), '2:255');
      expect(AnswerComposer.parseVerseReference('surah 115:1'), isNull);
    });
    test('surah name request shows the opening passages', () {
      final answer = AnswerComposer.answer(dataset, 'Read Al-Fatihah to me', 'en');
      expect(answer.passages.length, 3);
      expect(answer.passages.first.verseKey, '1:1');
    });
    test('unrelated question returns the fallback', () {
      final nonsense = AnswerComposer.answer(dataset, 'quantum tunnelling in superconductors', 'en');
      expect(nonsense.kind, 'fallback');
      expect(nonsense.passages, isEmpty);
      expect(nonsense.message, Notices.fallbackEn);
      expect(nonsense.message.toLowerCase().contains('does not mention'), isFalse);
    });
    test('more requests do not repeat shown verses', () {
      final more = AnswerComposer.answer(dataset, 'What does the Quran say about patience?', 'en', english.shownVerseKeys);
      expect(more.passages.any((p) => english.shownVerseKeys.contains(p.verseKey)), isFalse);
    });
  });

  group('mushaf metadata', () {
    test('covers all 6236 verses', () => expect(meta.verseCount(), 6236));
    test('1:1 is juz 1 page 1', () {
      expect(meta.juz(dataset.verse('1:1')!.id), 1);
      expect(meta.page(dataset.verse('1:1')!.id), 1);
    });
    test('2:255 is juz 3 page 42', () {
      expect(meta.juz(dataset.verse('2:255')!.id), 3);
      expect(meta.page(dataset.verse('2:255')!.id), 42);
    });
    test('114:6 is juz 30 page 604', () {
      expect(meta.juz(dataset.verse('114:6')!.id), 30);
      expect(meta.page(dataset.verse('114:6')!.id), 604);
    });
    test('juz indicator text is exact', () => expect(meta.indicator(dataset.verse('2:255')!.id), 'Juz 3  ·  Page 42'));
    test('chapter indicator spans pages', () => expect(meta.chapterIndicator(dataset, 1), 'Juz 1  ·  Page 1'));
    test('page numbers never regress',
        () => expect(meta.page(dataset.verse('1:1')!.id) <= meta.page(dataset.verse('114:6')!.id), isTrue));
  });

  group('study tools', () {
    test('collection names are sanitized', () => expect(StudyData.sanitizeCollectionName('  My | favourites\n'), 'My favourites'));
    test('empty collection falls back to General', () => expect(StudyData.sanitizeCollectionName('   '), StudyData.defaultCollection));
    test('collections deduplicate case-insensitively', () {
      final collections = StudyData.defaultCollections();
      StudyData.addCollection(collections, 'Reflections');
      StudyData.addCollection(collections, 'reflections');
      expect(collections.length, 2);
      expect(collections, contains('Reflections'));
    });
    test('collection names are bounded',
        () => expect(StudyData.sanitizeCollectionName('x' * 80).length, StudyData.maxCollectionLength));
    test('progress percentage rounds to nearest', () {
      expect(StudyData.percent(1, 3), 33);
      expect(StudyData.percent(2, 3), 67);
    });
    test('progress text is exact', () => expect(StudyData.formatProgress(3, 38), '3 of 38  ·  8%'));
    test('notes are trimmed and bounded', () {
      expect(StudyData.sanitizeNote('  note  '), 'note');
      expect(StudyData.sanitizeNote('x' * 3000).length, StudyData.maxNoteLength);
    });
    test('csv quoting follows RFC 4180',
        () => expect(StudyData.csv(['a,b', 'say "hi"', 'plain']), '"a,b","say ""hi""",plain'));
    test('juz 30 preset is surahs 78-114', () {
      final juz30 = StudyData.presetChapters(StudyData.presetJuz30);
      expect(juz30.length, 37);
      expect(juz30.first, 78);
      expect(juz30.last, 114);
    });
    test('full preset is all 114 surahs', () => expect(StudyData.presetChapters(StudyData.presetFull).length, 114));
    test('verse keys sort numerically', () => expect(StudyData.compareVerseKeys('2:9', '2:10'), lessThan(0)));
    test('verse key validation rejects loose input', () {
      expect(StudyData.validVerseKey('2'), isFalse);
      expect(StudyData.validVerseKey('114:6'), isTrue);
    });
    test('next chapter skips completed ones', () => expect(StudyData.nextChapter([1, 2, 3], [1]), 2));
    test('next chapter returns 0 when the plan is complete', () => expect(StudyData.nextChapter([1, 2], [1, 2]), 0));
    test('plan chapters round-trip through the CSV encoding', () {
      expect(StudyData.joinChapters([2, 5, 114]), '2,5,114');
      expect(StudyData.parseChapters('2,5,114'), [2, 5, 114]);
    });
  });

  group('recitation', () {
    test('attribution names Quran.com', () {
      expect(AudioGate.attribution(), contains('Quran.com'));
      expect(AudioGate.rightsDecision, contains('rights review recorded'));
    });
  });

  group('conversation layer', () {
    test('greeting gets a conversational reply', () {
      final salute = Conversation.classify('Hello!');
      expect(salute?.kind, ConversationKind.greeting);
      expect(salute!.text.toLowerCase(), contains('quran'));
    });
    test('Islamic greeting is recognised', () => expect(Conversation.classify('Assalamu alaikum')?.kind, ConversationKind.salam));
    test('thanks gets a short reply', () => expect(Conversation.classify('Thank you so much')?.kind, ConversationKind.thanks));
    test('ruling request refuses to interpret and points to scholars', () {
      final ruling = Conversation.classify('Is it haram to listen to music? Give me a fatwa');
      expect(ruling?.kind, ConversationKind.rulingRequest);
      expect(ruling!.text.toLowerCase(), contains('scholar'));
    });
    test('off-topic question is handled gracefully',
        () => expect(Conversation.classify('What is the weather today?')?.kind, ConversationKind.offtopic));
    test('advice questions are routed to Quran retrieval',
        () => expect(Conversation.classify('What does the Quran say about patience?'), isNull));
    test('feature help answers bookmarks', () {
      final help = Conversation.classify('How do I bookmark a verse?');
      expect(help?.kind, ConversationKind.help);
      expect(help!.text.toLowerCase(), contains('save'));
    });
    test('feature help answers recitation', () {
      final help = Conversation.classify('How do I listen to recitation?');
      expect(help?.kind, ConversationKind.help);
      expect(help!.text.toLowerCase(), contains('quran.com'));
    });
    test('feature help answers language',
        () => expect(Conversation.classify('Where can I change the language?')?.kind, ConversationKind.help));
    test('feature help answers AI', () {
      final help = Conversation.classify('How does the AI work?');
      expect(help?.kind, ConversationKind.help);
      expect(help!.text.toLowerCase(), contains('never'));
    });
    test('feature help offers an overview', () => expect(Conversation.classify('help')?.kind, ConversationKind.help));
    test('retrieval questions still bypass feature help',
        () => expect(Conversation.classify('What does the Quran say about mercy?'), isNull));
    test('overlong messages are not treated as small talk', () => expect(Conversation.classify('x' * 800), isNull));
    test('identity reply says native app, never Android', () {
      final identity = Conversation.classify('Who are you?');
      expect(identity?.kind, ConversationKind.identity);
      expect(identity!.text, contains('native app'));
      expect(identity.text.toLowerCase(), isNot(contains('android')));
    });
    test('"hi" does not match inside "thing"', () => expect(Conversation.classify('the thing about patience'), isNull));
    test('guidance names scholars, Hadith and Fiqh study', () {
      expect(Conversation.guidance().any((line) => line.toLowerCase().contains('scholar')), isTrue);
      expect(Conversation.guidance().any((line) => line.toLowerCase().contains('hadith')), isTrue);
    });
    test('guidance disclaims responsibility',
        () => expect(Conversation.guidance().any((line) => line.toLowerCase().contains('no responsibility')), isTrue));
    test('attachment fallback mentions the AI service',
        () => expect(Conversation.attachmentUnavailable().toLowerCase(), contains('ai service')));
    test('AI consent text says verses never come from the AI',
        () => expect(Conversation.aiConsentMessage(), contains('never supplies Quran text')));
  });

  group('voice languages', () {
    test('locale mapping covers all ten interface languages', () {
      expect(VoiceLanguage.localeTag('en'), 'en-US');
      expect(VoiceLanguage.localeTag('ar'), 'ar-SA');
      expect(VoiceLanguage.localeTag('ur'), 'ur-PK');
      expect(VoiceLanguage.localeTag('bn'), 'bn-BD');
      expect(VoiceLanguage.localeTag('fa'), 'fa-IR');
      expect(VoiceLanguage.localeTag('sw'), 'sw-KE');
    });
    test('every voice locale starts with its language code', () {
      for (final code in Language.codes()) {
        expect(VoiceLanguage.localeTag(code).startsWith('$code-'), isTrue, reason: code);
      }
    });
    test('unknown voice language falls back to en-US', () => expect(VoiceLanguage.localeTag('zz'), 'en-US'));
    test('english answer is voiced and spoken in English', () {
      expect(VoiceLanguage.spokenLanguage(english), 'en');
      expect(VoiceLanguage.spokenText(english, 4000), contains(english.passages.first.translationText));
    });
    test('arabic answer speaks the English translation (no Arabic translation rows)',
        () => expect(VoiceLanguage.spokenLanguage(arabic), 'en'));
    test('arabic answer does not speak the Arabic connective with an English voice',
        () => expect(VoiceLanguage.spokenText(arabic, 4000), isNot(contains(Notices.connectiveFound('ar', 1)))));
    test('arabic fallback is spoken in Arabic', () => expect(VoiceLanguage.spokenLanguage(Answer.fallback('ar', [])), 'ar'));
    test('english fallback is spoken in English', () => expect(VoiceLanguage.spokenLanguage(Answer.fallback('ur', [])), 'en'));
    test('conversation replies are spoken in English', () {
      const reply = Answer('conversation', 'ar', 'Hello!', [], 0, '', false, null, [], false, []);
      expect(VoiceLanguage.spokenLanguage(reply), 'en');
    });
    test('urdu answer speaks the Urdu translation and omits English labels', () {
      final urdu = AnswerComposer.answer(dataset, 'Show me 2:255', 'ur');
      expect(VoiceLanguage.spokenLanguage(urdu), 'ur');
      final spoken = VoiceLanguage.spokenText(urdu, 4000);
      expect(spoken, isNot(contains('Al-Baqarah')));
      expect(spoken, isNot(contains('I found')));
      expect(VoiceLanguage.spokenText(urdu, 20).length, lessThanOrEqualTo(20));
    });
  });

  group('named figures and topics', () {
    test('Hazrat Essa resolves to Isa/Jesus and returns several verses', () {
      final essa = AnswerComposer.answer(dataset, 'Who is Hazrat Essa?', 'en');
      expect(essa.kind, 'answer');
      expect(essa.passages.length, greaterThanOrEqualTo(3));
      expect(essa.message.contains('Isa') || essa.message.contains('Jesus'), isTrue);
    });
    test('Yaqoob resolves to Jacob and returns verses', () {
      final jacob = AnswerComposer.answer(dataset, 'Who is Yaqoob?', 'en');
      expect(jacob.kind, 'answer');
      expect(jacob.passages, isNotEmpty);
      expect(jacob.message.contains('Yaqub') || jacob.message.contains('Jacob'), isTrue);
    });
    test('theft/law question includes 5:38', () {
      final theft = AnswerComposer.answer(dataset, 'What is the law about theft?', 'en');
      expect(theft.kind, 'answer');
      expect(theft.passages.any((p) => p.verseKey == '5:38'), isTrue);
    });
    test('Arabic name question (عيسى) is answered', () {
      final answer = AnswerComposer.answer(dataset, 'من هو عيسى', 'ar');
      expect(answer.kind, 'answer');
      expect(answer.passages, isNotEmpty);
    });
    test('honorifics alone are not a named figure', () => expect(dataset.findEntity('Who is the prophet?'), isNull));
    test('ordinary topic questions are not treated as a named figure',
        () => expect(dataset.findEntity('What does the Quran say about patience?'), isNull));
    test('entity alias list is loaded from the bundled lexicon', () {
      expect(dataset.findEntity('Tell me about Jesus'), isNotNull);
      expect(dataset.findEntity('hazrat essa'), isNotNull);
    });
  });

  group('romanised, fuzzy and mixed input', () {
    for (final entry in {
      'hazrath eesa': 'Isa',
      'yaqubb': 'Yaqub',
      'moosa': 'Musa',
      'what quran says about sabr aur shukr': 'mixed English/Roman-Urdu',
      'ما معنى sabr': 'mixed Arabic + Roman',
      'namaz kya hai': 'prayer',
      'roza ke baray mein': 'fasting',
      'pateince': 'one-letter typo',
    }.entries) {
      test('"${entry.key}" (${entry.value}) is answered', () {
        final answer = AnswerComposer.answer(dataset, entry.key, 'en');
        expect(answer.kind, 'answer', reason: entry.key);
        expect(answer.passages, isNotEmpty);
      });
    }
    test('romanised entity lookup handles honorifics and spelling', () {
      expect(dataset.findEntity('Hazrath Eesa'), isNotNull);
      expect(dataset.findEntity('hazrat yakoob'), isNotNull);
      expect(dataset.findEntity('random english sentence'), isNull);
    });
    test('Roman canonical forms collapse spelling variants', () {
      expect(Roman.sameRoman('Eesa', 'isa'), isTrue);
      expect(Roman.sameRoman('Moosa', 'musa'), isTrue);
      expect(Roman.fuzzyMatch('pateince', 'patience'), isTrue);
      expect(Roman.isHonorific('Hazrat'), isTrue);
    });
  });

  group('index facts', () {
    test('named prophet count is 25 with 4:164/40:78', () {
      final result = Facts.match(dataset, 'How many prophets are mentioned in the Quran?');
      expect(result, isNotNull);
      expect(result!.message, contains('25'));
      expect(result.message, contains('4:164'));
      expect(result.verseKeys, containsAll(['4:164', '40:78']));
      final factAnswer = AnswerComposer.factAnswer(dataset, 'en', result.message, result.verseKeys);
      expect(factAnswer.passages.length, 2);
      expect(factAnswer.passages.first.verseKey, '4:164');
    });
    test('list-of-prophets question resolves',
        () => expect(Facts.match(dataset, 'List the names of the prophets in the Quran')?.message, contains('Muhammad')));
    test('114 surahs from the validated dataset',
        () => expect(Facts.match(dataset, 'How many surahs are in the Quran?')?.message, contains('114')));
    test('6236 verses from the validated dataset',
        () => expect(Facts.match(dataset, 'How many verses are in the Quran?')?.message, contains('6236')));
    test('named mentions of Musa are counted', () {
      final result = Facts.match(dataset, 'How many times is Musa mentioned in the Quran?');
      expect(result, isNotNull);
      expect(result!.message, contains('Musa'));
      expect(result.verseKeys, isNotEmpty);
    });
    test('unrelated question is not a fact', () => expect(Facts.match(dataset, 'patience'), isNull));
  });

  group('surah search', () {
    test('matches by number, Arabic, English and alias', () {
      expect(SurahSearch.filter(dataset, '').length, 114);
      expect(SurahSearch.filter(dataset, '2'), [2]);
      expect(SurahSearch.filter(dataset, 'baqara'), contains(2));
      expect(SurahSearch.filter(dataset, 'cow'), contains(2));
      expect(SurahSearch.filter(dataset, 'الفاتحة'), contains(1));
      expect(SurahSearch.filter(dataset, 'Surah Yaseen'), contains(36));
      expect(SurahSearch.filter(dataset, 'yousuf'), contains(12));
    });
    test('nonsense matches nothing', () => expect(SurahSearch.filter(dataset, 'zzzzqqq'), isEmpty));
  });

  group('languages', () {
    test('ten languages with RTL flags', () {
      expect(Language.all.length, 10);
      expect(Language.byCode('ar').rtl, isTrue);
      expect(Language.byCode('ur').rtl, isTrue);
      expect(Language.byCode('fa').rtl, isTrue);
      expect(Language.byCode('en').rtl, isFalse);
      expect(Language.byCode('zz').code, 'en');
    });
  });
}
