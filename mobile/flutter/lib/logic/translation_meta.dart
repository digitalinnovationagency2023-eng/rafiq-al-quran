class TranslationMeta {
  final String language;
  final String title;
  final String author;
  final String direction;
  final String licenseStatus;

  const TranslationMeta(this.language, this.title, this.author, this.direction, this.licenseStatus);

  static TranslationMeta parse(String line) {
    final parts = line.split('\t');
    return TranslationMeta(
      parts[0],
      parts[1],
      parts.length > 2 ? parts[2] : '',
      parts.length > 3 ? parts[3] : 'ltr',
      parts.length > 4 ? parts[4] : 'unverified',
    );
  }
}
