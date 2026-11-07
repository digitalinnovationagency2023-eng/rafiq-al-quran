import 'dart:math' as math;

import 'arabic_text.dart';
import 'concept.dart';
import 'dataset.dart';
import 'roman.dart';
import 'verse.dart';

/// Deterministic keyword + concept retrieval over the local dataset.
class Retrieval {
  Retrieval._();

  static const Set<String> _arabicStop = {
    'في', 'من', 'على', 'إلى', 'عن', 'أن', 'إن', 'ما', 'لا', 'هل', 'هو', 'هي', 'هم', 'كان', 'كانوا', 'هذا', 'هذه', //
    'ذلك', 'التي', 'الذي', 'الذين', 'يا', 'أيها', 'ثم', 'قد', 'لقد', 'ولا', 'وما', 'ومن', 'وإلى', 'وعلى', 'أفلا', //
    'أولم', 'أفلم', 'كذلك', 'بين', 'كل', 'بعض', 'غير', 'سوى', 'حيث', 'كيف', 'متى', 'أين', 'لماذا',
  };

  static const Set<String> _latinStop = {
    'the', 'a', 'an', 'and', 'or', 'of', 'to', 'in', 'on', 'for', 'with', 'about', 'is', 'are', 'was', 'were', 'be', 'been', //
    'being', 'what', 'which', 'who', 'whom', 'whose', 'how', 'why', 'when', 'where', 'do', 'does', 'did', 'can', 'could', //
    'should', 'would', 'may', 'might', 'will', 'shall', 'i', 'you', 'he', 'she', 'it', 'we', 'they', 'me', 'my', 'your', //
    'our', 'their', 'this', 'that', 'these', 'those', 'there', 'here', 'please', 'tell', 'show', 'give', 'find', 'say', //
    'says', 'said', 'mention', 'mentions', 'mentioned', 'verse', 'verses', 'quran', "qur'an", 'surah', 'sura', 'chapter', //
    'aur', 'kya', 'kia', 'ka', 'ki', 'ke', 'mein', 'hai', 'hain', 'ko', 'se', 'par', 'yeh', 'woh', 'wo', 'kuch', 'bhi', //
    'nahi', 'nahin', 'hota', 'hoti', 'kaise', 'kaisay', 'kion', 'kyon', 'kab', 'kahan', 'kon', 'kaun', 'aap', 'tum', //
    'hum', 'main', 'mera', 'meri', 'mere', 'apna', 'apni', 'liye', 'liyay', 'bare', 'baray', 'baare', 'mutalliq', 'batao', //
    'bata', 'bataye', 'batain', 'bataein', 'hazrat', 'hadrat', 'hz', 'prophet', 'prophets', 'nabi', 'paighambar', //
    'peer', 'pir', 'saint', 'imam', 'maulana', 'moulana', 'sheikh', 'shaikh', 'alaihi', 'alaihis', 'salam', 'rahimahullah',
  };

  static List<String> meaningfulTokens(String? text, String language) {
    final tokens = <String>[];
    final value = text ?? '';
    var arabic = false;
    var latin = false;
    for (final c in value.codeUnits) {
      if (c >= 0x0600 && c <= 0x06FF) {
        arabic = true;
      } else if (c < 128 && ((c >= 0x41 && c <= 0x5A) || (c >= 0x61 && c <= 0x7A))) {
        latin = true;
      }
    }
    if (arabic) {
      for (final token in ArabicText.tokens(value)) {
        if (token.length > 1 && !_arabicStop.contains(token)) tokens.add(token);
      }
    }
    if (latin || !arabic) {
      for (final token in Dataset.latinTokens(value)) {
        if (token.length > 1 && !_latinStop.contains(token) && !Roman.isHonorific(token)) tokens.add(token);
      }
    }
    return tokens;
  }

  /// Arabic search terms for a named figure found in the question (exact alias match).
  static List<String> _expandEntityTerms(Dataset dataset, String question) {
    final terms = <String>[];
    final Concept? entity = dataset.findEntity(question);
    if (entity == null) return terms;
    for (final term in entity.arabic) {
      final normalized = ArabicText.normalize(term);
      if (normalized.isNotEmpty && !terms.contains(normalized)) terms.add(normalized);
    }
    return terms;
  }

  static List<String> _expandConcepts(Dataset dataset, List<String> tokens) {
    final arabicTerms = <String>[];
    final lowered = <String>{for (final token in tokens) token.toLowerCase()};
    for (final concept in dataset.concepts) {
      var hit = false;
      for (final vocabulary in concept.byLanguage) {
        for (final term in vocabulary) {
          if (term.length < 3) continue;
          for (final word in lowered) {
            var matched = word == term || word.contains(term) || term.contains(word);
            if (!matched && Roman.isAscii(word) && Roman.isAscii(term)) {
              // Romanised spellings and small typos: sabrr, yaqubb, pateince.
              matched = Roman.sameRoman(word, term) || Roman.fuzzyMatch(word, term);
            }
            if (matched) {
              hit = true;
              break;
            }
          }
          if (hit) break;
        }
        if (hit) break;
      }
      if (hit) {
        for (final term in concept.arabic) {
          final normalized = ArabicText.normalize(term);
          if (normalized.isNotEmpty && !arabicTerms.contains(normalized)) arabicTerms.add(normalized);
        }
      }
    }
    return arabicTerms;
  }

  static List<Match> search(Dataset dataset, String question, String language, int limit,
      List<String>? extraTerms, Set<String>? excludeKeys) {
    final questionTokens = <String>[...meaningfulTokens(question, language).toSet()];
    if (extraTerms != null) {
      questionTokens.addAll(meaningfulTokens(extraTerms.join(' '), language).toSet());
    }
    if (questionTokens.isEmpty) return <Match>[];

    final conceptTerms = _expandConcepts(dataset, questionTokens);
    final arabicQuery = ArabicText.isArabic(question);
    final questionSet = questionTokens.toSet();

    final scores = <int, double>{};
    final matchedQuery = <int, Set<String>>{};
    final matchedConcept = <int, Set<String>>{};

    final total = dataset.verses.length;
    for (final token in questionTokens) {
      final normalized = ArabicText.normalize(token);
      if (normalized.isEmpty) continue;
      for (final form in ArabicText.searchForms(normalized)) {
        final bucket = dataset.arabicIndex[form];
        if (bucket == null) continue;
        final weight = _idf(bucket.length, total) * (arabicQuery ? 1.3 : 1.0);
        for (final index in bucket) {
          scores[index] = (scores[index] ?? 0) + weight;
          (matchedQuery[index] ??= <String>{}).add(token);
        }
      }
      for (final targetLanguage in [language, 'en']) {
        for (final form in Dataset.latinForms(token)) {
          final bucket = dataset.translationIndex['$targetLanguage|$form'];
          if (bucket == null) continue;
          final weight = _idf(bucket.length, total) * (targetLanguage == language ? 1.0 : 0.6);
          for (final index in bucket) {
            scores[index] = (scores[index] ?? 0) + weight;
            (matchedQuery[index] ??= <String>{}).add(token);
          }
        }
      }
    }

    for (final term in conceptTerms) {
      for (final form in ArabicText.searchForms(term)) {
        final bucket = dataset.arabicIndex[form];
        if (bucket == null) continue;
        final weight = _idf(bucket.length, total) * 0.9;
        for (final index in bucket) {
          scores[index] = (scores[index] ?? 0) + weight;
          (matchedConcept[index] ??= <String>{}).add(term);
        }
      }
    }

    // Named figures (Essa/Isa/Jesus, Yaqoob/Jacob, ...) get a stronger boost: the
    // name itself usually carries the whole question.
    for (final term in _expandEntityTerms(dataset, question)) {
      for (final form in ArabicText.searchForms(term)) {
        final bucket = dataset.arabicIndex[form];
        if (bucket == null) continue;
        final weight = _idf(bucket.length, total) * 1.6;
        for (final index in bucket) {
          scores[index] = (scores[index] ?? 0) + weight;
          (matchedConcept[index] ??= <String>{}).add(term);
        }
      }
    }

    final normalizedQuestion = ArabicText.normalize(question);
    if (normalizedQuestion.length > 6) {
      for (var index = 0; index < dataset.verses.length; index++) {
        final verse = dataset.verses[index];
        if (verse.normalized.contains(normalizedQuestion)) {
          scores[index] = (scores[index] ?? 0) + 4.0;
          (matchedQuery[index] ??= <String>{}).add('phrase');
        }
      }
    }

    final order = scores.keys.toList()..sort((left, right) => scores[right]!.compareTo(scores[left]!));

    var bestScore = 0.0;
    final candidates = <_Candidate>[];
    for (final index in order) {
      final verse = dataset.verses[index];
      if (excludeKeys != null && excludeKeys.contains(verse.key)) continue;
      final direct = matchedQuery[index];
      final concept = matchedConcept[index];
      var directHits = 0;
      if (direct != null) {
        for (final token in direct) {
          if (questionSet.contains(token)) directHits++;
        }
      }
      final coverage = questionTokens.isEmpty ? 0.0 : directHits / questionTokens.length;
      final conceptHits = concept == null ? 0 : concept.length;
      if (coverage <= 0 && conceptHits == 0) continue;
      final score = scores[index]!;
      bestScore = math.max(bestScore, score);
      candidates.add(_Candidate(verse, score, coverage, conceptHits));
      if (candidates.length >= math.max(limit * 4, 24)) break;
    }
    if (candidates.isEmpty) return <Match>[];

    final best = bestScore;
    final results = <Match>[];
    for (final candidate in candidates) {
      final relevance = math.min(1.0, candidate.score / math.max(best, 1.0));
      final conceptCredit = candidate.conceptHits > 0 && candidate.coverage == 0 ? 0.5 : 0.0;
      final confidence = 0.5 * math.min(1.0, candidate.coverage + conceptCredit) +
          0.3 * relevance +
          0.2 * math.min(1.0, (candidate.coverage + candidate.conceptHits) / 2.0);
      results.add(Match(candidate.verse, candidate.score, candidate.coverage, confidence, candidate.conceptHits,
          const <String>[]));
    }
    results.sort((left, right) => right.confidence.compareTo(left.confidence));
    return results.length > limit ? results.sublist(0, limit) : results;
  }

  static double _idf(int frequency, int total) => math.log(1.0 + total / (1.0 + frequency));
}

class Match {
  final Verse verse;
  final double score;
  final double coverage;
  final double confidence;
  final int conceptHits;
  final List<String> matched;

  const Match(this.verse, this.score, this.coverage, this.confidence, this.conceptHits, this.matched);
}

class _Candidate {
  final Verse verse;
  final double score;
  final double coverage;
  final int conceptHits;

  const _Candidate(this.verse, this.score, this.coverage, this.conceptHits);
}
