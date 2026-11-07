/// A reviewed lexicon entry: topic or `entity-*` figure with aliases per language.
class Concept {
  final String key;
  final List<String> arabic;

  /// Aliases in the order of [Language.all]: en, ur, id, bn, tr, fa, ms, sw, fr.
  final List<List<String>> byLanguage;

  const Concept(this.key, this.arabic, this.byLanguage);
}
