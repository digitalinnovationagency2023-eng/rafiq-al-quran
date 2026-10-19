package com.qurancompanion.app.logic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Deterministic keyword + concept retrieval over the local dataset. */
public final class Retrieval {
    private Retrieval() {}

    private static final Set<String> ARABIC_STOP = new HashSet<>(Arrays.asList(
            "في","من","على","إلى","عن","أن","إن","ما","لا","هل","هو","هي","هم","كان","كانوا","هذا","هذه",
            "ذلك","التي","الذي","الذين","يا","أيها","ثم","قد","لقد","ولا","وما","ومن","وإلى","وعلى","أفلا",
            "أولم","أفلم","كذلك","بين","كل","بعض","غير","سوى","حيث","كيف","متى","أين","لماذا"));

    private static final Set<String> LATIN_STOP = new HashSet<>(Arrays.asList(
            "the","a","an","and","or","of","to","in","on","for","with","about","is","are","was","were","be","been",
            "being","what","which","who","whom","whose","how","why","when","where","do","does","did","can","could",
            "should","would","may","might","will","shall","i","you","he","she","it","we","they","me","my","your",
            "our","their","this","that","these","those","there","here","please","tell","show","give","find","say",
            "says","said","mention","mentions","mentioned","verse","verses","quran","qur'an","surah","sura","chapter"));

    public static final class Match {
        public final Verse verse;
        public final double score;
        public final double coverage;
        public final double confidence;
        public final int conceptHits;
        public final List<String> matched;

        Match(Verse verse, double score, double coverage, double confidence, int conceptHits, List<String> matched) {
            this.verse = verse;
            this.score = score;
            this.coverage = coverage;
            this.confidence = confidence;
            this.conceptHits = conceptHits;
            this.matched = matched;
        }
    }

    public static List<String> meaningfulTokens(String text, String language) {
        List<String> tokens = new ArrayList<>();
        if (ArabicText.isArabic(text)) {
            for (String token : ArabicText.tokens(text)) {
                if (token.length() > 1 && !ARABIC_STOP.contains(token)) tokens.add(token);
            }
            return tokens;
        }
        for (String token : Dataset.latinTokens(text)) {
            if (token.length() > 1 && !LATIN_STOP.contains(token)) tokens.add(token);
        }
        return tokens;
    }

    private static List<String> expandConcepts(Dataset dataset, List<String> tokens, String language) {
        List<String> arabicTerms = new ArrayList<>();
        Set<String> lowered = new HashSet<>();
        for (String token : tokens) lowered.add(token.toLowerCase());
        for (Concept concept : dataset.concepts) {
            boolean hit = false;
            for (List<String> vocabulary : concept.byLanguage) {
                for (String term : vocabulary) {
                    if (term.length() < 3) continue;
                    for (String word : lowered) {
                        if (word.equals(term) || word.contains(term) || term.contains(word)) {
                            hit = true;
                            break;
                        }
                    }
                    if (hit) break;
                }
                if (hit) break;
            }
            if (hit) {
                for (String term : concept.arabic) {
                    String normalized = ArabicText.normalize(term);
                    if (!normalized.isEmpty() && !arabicTerms.contains(normalized)) arabicTerms.add(normalized);
                }
            }
        }
        return arabicTerms;
    }

    public static List<Match> search(Dataset dataset, String question, String language, int limit,
                                     List<String> extraTerms, Set<String> excludeKeys) {
        List<String> questionTokens = new ArrayList<>(new LinkedHashSet<>(meaningfulTokens(question, language)));
        if (extraTerms != null) questionTokens.addAll(new LinkedHashSet<>(meaningfulTokens(join(extraTerms), language)));
        if (questionTokens.isEmpty()) return new ArrayList<>();

        List<String> conceptTerms = expandConcepts(dataset, questionTokens, language);
        boolean arabicQuery = ArabicText.isArabic(question);
        Set<String> questionSet = new HashSet<>(questionTokens);

        Map<Integer, Double> scores = new HashMap<>();
        Map<Integer, Set<String>> matchedQuery = new HashMap<>();
        Map<Integer, Set<String>> matchedConcept = new HashMap<>();

        int total = dataset.verses.size();
        for (String token : questionTokens) {
            String normalized = ArabicText.normalize(token);
            if (normalized.isEmpty()) continue;
            for (String form : ArabicText.searchForms(normalized)) {
                List<Integer> bucket = dataset.arabicIndex.get(form);
                if (bucket == null) continue;
                double weight = idf(bucket.size(), total) * (arabicQuery ? 1.3 : 1.0);
                for (int index : bucket) {
                    scores.merge(index, weight, Double::sum);
                    matchedQuery.computeIfAbsent(index, key -> new HashSet<>()).add(token);
                }
            }
            for (String targetLanguage : new String[]{language, "en"}) {
                for (String form : Dataset.latinForms(token)) {
                    List<Integer> bucket = dataset.translationIndex.get(targetLanguage + "|" + form);
                    if (bucket == null) continue;
                    double weight = idf(bucket.size(), total) * (targetLanguage.equals(language) ? 1.0 : 0.6);
                    for (int index : bucket) {
                        scores.merge(index, weight, Double::sum);
                        matchedQuery.computeIfAbsent(index, key -> new HashSet<>()).add(token);
                    }
                }
            }
        }

        for (String term : conceptTerms) {
            for (String form : ArabicText.searchForms(term)) {
                List<Integer> bucket = dataset.arabicIndex.get(form);
                if (bucket == null) continue;
                double weight = idf(bucket.size(), total) * 0.9;
                for (int index : bucket) {
                    scores.merge(index, weight, Double::sum);
                    matchedConcept.computeIfAbsent(index, key -> new HashSet<>()).add(term);
                }
            }
        }

        String normalizedQuestion = ArabicText.normalize(question);
        if (normalizedQuestion.length() > 6) {
            for (int index = 0; index < dataset.verses.size(); index++) {
                Verse verse = dataset.verses.get(index);
                if (verse.normalized.contains(normalizedQuestion)) {
                    scores.merge(index, 4.0, Double::sum);
                    matchedQuery.computeIfAbsent(index, key -> new HashSet<>()).add("phrase");
                }
            }
        }

        List<int[]> order = new ArrayList<>();
        for (Integer index : scores.keySet()) order.add(new int[]{index});
        order.sort((left, right) -> Double.compare(scores.get(right[0]), scores.get(left[0])));

        double bestScore = 0;
        List<Candidate> candidates = new ArrayList<>();
        for (int[] holder : order) {
            int index = holder[0];
            Verse verse = dataset.verses.get(index);
            if (excludeKeys != null && excludeKeys.contains(verse.key)) continue;
            Set<String> direct = matchedQuery.get(index);
            Set<String> concept = matchedConcept.get(index);
            int directHits = 0;
            if (direct != null) for (String token : direct) if (questionSet.contains(token)) directHits++;
            double coverage = questionTokens.isEmpty() ? 0 : (double) directHits / questionTokens.size();
            int conceptHits = concept == null ? 0 : concept.size();
            if (coverage <= 0 && conceptHits == 0) continue;
            double score = scores.get(index);
            bestScore = Math.max(bestScore, score);
            candidates.add(new Candidate(verse, score, coverage, conceptHits));
            if (candidates.size() >= Math.max(limit * 4, 24)) break;
        }
        if (candidates.isEmpty()) return new ArrayList<>();

        final double best = bestScore;
        List<Match> results = new ArrayList<>();
        for (Candidate candidate : candidates) {
            double relevance = Math.min(1.0, candidate.score / Math.max(best, 1.0));
            double conceptCredit = candidate.conceptHits > 0 && candidate.coverage == 0 ? 0.5 : 0;
            double confidence = 0.5 * Math.min(1.0, candidate.coverage + conceptCredit)
                    + 0.3 * relevance
                    + 0.2 * Math.min(1.0, (candidate.coverage + candidate.conceptHits) / 2.0);
            results.add(new Match(candidate.verse, candidate.score, candidate.coverage, confidence,
                    candidate.conceptHits, new ArrayList<>()));
        }
        results.sort((left, right) -> Double.compare(right.confidence, left.confidence));
        return results.size() > limit ? new ArrayList<>(results.subList(0, limit)) : results;
    }

    private static String join(List<String> values) {
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (builder.length() > 0) builder.append(' ');
            builder.append(value);
        }
        return builder.toString();
    }

    private static double idf(int frequency, int total) {
        return Math.log(1.0 + (double) total / (1.0 + frequency));
    }

    private static final class Candidate {
        final Verse verse;
        final double score;
        final double coverage;
        final int conceptHits;

        Candidate(Verse verse, double score, double coverage, int conceptHits) {
            this.verse = verse;
            this.score = score;
            this.coverage = coverage;
            this.conceptHits = conceptHits;
        }
    }
}
