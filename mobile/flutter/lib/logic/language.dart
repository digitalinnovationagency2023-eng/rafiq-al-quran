/// Interface languages.
class Language {
  final String code;
  final String englishName;
  final String nativeName;
  final bool rtl;

  const Language(this.code, this.englishName, this.nativeName, this.rtl);

  static const List<Language> all = [
    Language('ar', 'Arabic', 'العربية', true),
    Language('en', 'English', 'English', false),
    Language('ur', 'Urdu', 'اردو', true),
    Language('id', 'Indonesian', 'Bahasa Indonesia', false),
    Language('bn', 'Bengali', 'বাংলা', false),
    Language('tr', 'Turkish', 'Türkçe', false),
    Language('fa', 'Persian', 'فارسی', true),
    Language('ms', 'Malay', 'Bahasa Melayu', false),
    Language('sw', 'Swahili', 'Kiswahili', false),
    Language('fr', 'French', 'Français', false),
  ];

  static Language byCode(String? code) {
    for (final language in all) {
      if (language.code == code) return language;
    }
    return all[1];
  }

  static List<String> codes() => [for (final language in all) language.code];
}
