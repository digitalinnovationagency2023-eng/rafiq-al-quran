import 'arabic_text.dart';
import 'chapter.dart';
import 'dataset.dart';
import 'roman.dart';

/// Search the 114 surahs by English, Arabic or romanised name.
class SurahSearch {
  SurahSearch._();

  static const Map<int, List<String>> _aliases = {
    1: ['fatiha', 'fatihah', 'opening'],
    2: ['baqarah', 'baqara', 'cow'],
    3: ['imran', 'family of imran'],
    4: ['nisa', 'women'],
    5: ['maidah', 'maida', 'table'],
    6: ['anam', 'cattle'],
    7: ['araf', 'heights'],
    8: ['anfal', 'spoils'],
    9: ['tawbah', 'tauba', 'repentance'],
    10: ['yunus', 'jonah'],
    11: ['hud'],
    12: ['yusuf', 'joseph'],
    13: ['rad', 'thunder'],
    14: ['ibrahim', 'abraham'],
    15: ['hijr'],
    16: ['nahl', 'bee'],
    17: ['isra', 'night journey'],
    18: ['kahf', 'cave'],
    19: ['maryam', 'mary'],
    20: ['taha', 'ta ha'],
    21: ['anbiya', 'prophets'],
    22: ['hajj', 'pilgrimage'],
    23: ['muminun', 'believers'],
    24: ['nur', 'light'],
    25: ['furqan', 'criterion'],
    26: ['shuara', 'poets'],
    27: ['naml', 'ant'],
    28: ['qasas', 'stories'],
    29: ['ankabut', 'spider'],
    30: ['rum', 'romans'],
    31: ['luqman'],
    32: ['sajdah', 'prostration'],
    33: ['ahzab', 'confederates'],
    34: ['saba', 'sheba'],
    35: ['fatir', 'originator'],
    36: ['yasin', 'ya sin'],
    37: ['saffat', 'ranged'],
    38: ['sad'],
    39: ['zumar', 'groups'],
    40: ['ghafir', 'forgiver'],
    41: ['fussilat', 'explained'],
    42: ['shura', 'consultation'],
    43: ['zukhruf', 'gold'],
    44: ['dukhan', 'smoke'],
    45: ['jathiyah', 'kneeling'],
    46: ['ahqaf', 'dunes'],
    47: ['muhammad'],
    48: ['fath', 'victory'],
    49: ['hujurat', 'rooms'],
    50: ['qaf'],
    51: ['dhariyat', 'winds'],
    52: ['tur', 'mountain'],
    53: ['najm', 'star'],
    54: ['qamar', 'moon'],
    55: ['rahman', 'most gracious'],
    56: ['waqiah', 'event'],
    57: ['hadid', 'iron'],
    58: ['mujadilah', 'dispute'],
    59: ['hashr', 'exile'],
    60: ['mumtahanah', 'tested'],
    61: ['saff', 'ranks'],
    62: ['jumuah', 'friday'],
    63: ['munafiqun', 'hypocrites'],
    64: ['taghabun', 'mutual loss'],
    65: ['talaq', 'divorce'],
    66: ['tahrim', 'prohibition'],
    67: ['mulk', 'kingdom'],
    68: ['qalam', 'pen'],
    69: ['haqqah', 'reality'],
    70: ['maarij', 'ascending'],
    71: ['nuh', 'noah'],
    72: ['jinn'],
    73: ['muzzammil', 'enfolded'],
    74: ['muddaththir', 'cloaked'],
    75: ['qiyamah', 'resurrection'],
    76: ['insan', 'man'],
    77: ['mursalat', 'sent forth'],
    78: ['naba', 'news'],
    79: ['naziat', 'those who drag'],
    80: ['abasa', 'frowned'],
    81: ['takwir', 'folding up'],
    82: ['infitar', 'cleaving'],
    83: ['mutaffifin', 'defrauding'],
    84: ['inshiqaq', 'splitting'],
    85: ['buruj', 'constellations'],
    86: ['tariq', 'night star'],
    87: ['ala', 'most high'],
    88: ['ghashiyah', 'overwhelming'],
    89: ['fajr', 'dawn'],
    90: ['balad', 'city'],
    91: ['shams', 'sun'],
    92: ['layl', 'night'],
    93: ['duha', 'morning'],
    94: ['sharh', 'expansion'],
    95: ['tin', 'fig'],
    96: ['alaq', 'clot'],
    97: ['qadr', 'power'],
    98: ['bayyinah', 'clear evidence'],
    99: ['zalzalah', 'earthquake'],
    100: ['adiyat', 'chargers'],
    101: ['qariah', 'calamity'],
    102: ['takathur', 'rivalry'],
    103: ['asr', 'time'],
    104: ['humazah', 'slanderer'],
    105: ['fil', 'elephant'],
    106: ['quraysh', 'quraish'],
    107: ['maun', 'small kindness'],
    108: ['kawthar', 'abundance'],
    109: ['kafirun', 'disbelievers'],
    110: ['nasr', 'help'],
    111: ['masad', 'palm fibre'],
    112: ['ikhlas', 'sincerity'],
    113: ['falaq', 'daybreak'],
    114: ['nas', 'mankind'],
  };

  static final RegExp _nonDigits = RegExp(r'[^0-9]');
  static final RegExp _nonAlnumSpace = RegExp(r'[^a-z0-9 ]');
  static final RegExp _spaces = RegExp(r'\s+');

  static bool matches(Chapter? chapter, String? rawQuery) {
    if (chapter == null) return false;
    final query = rawQuery == null ? '' : rawQuery.trim();
    if (query.isEmpty) return true;
    final number = query.replaceAll(_nonDigits, '');
    if (number.isNotEmpty && query.length <= 4 && int.parse(number) == chapter.id) return true;

    final arabicQuery = ArabicText.normalize(query);
    if (arabicQuery.isNotEmpty && ArabicText.normalize(chapter.nameArabic).contains(arabicQuery)) return true;

    final english = Roman.canonical(_stripPrefixes(chapter.nameEnglish));
    final q = Roman.canonical(_stripPrefixes(query));
    if (q.isNotEmpty && (english.contains(q) || q.contains(english))) return true;

    final queryTokens = query.toLowerCase().replaceAll(_nonAlnumSpace, ' ').trim().split(_spaces);
    for (final token in queryTokens) {
      if (token.length < 3) continue;
      final canonical = Roman.canonical(token);
      if (canonical.isEmpty) continue;
      if (english.contains(canonical)) return true;
      final aliases = _aliases[chapter.id];
      if (aliases != null) {
        for (final alias in aliases) {
          final aliasCanonical = Roman.canonical(alias);
          if (canonical == aliasCanonical || Roman.fuzzyMatch(canonical, aliasCanonical)) return true;
        }
      }
    }
    return false;
  }

  static List<int> filter(Dataset? dataset, String? query) {
    final ids = <int>[];
    if (dataset == null) return ids;
    final value = query == null ? '' : query.trim();
    if (value.isEmpty) {
      for (var id = 1; id <= 114; id++) {
        ids.add(id);
      }
      return ids;
    }
    for (var id = 1; id <= 114; id++) {
      if (matches(dataset.chapter(id), value)) ids.add(id);
    }
    return ids;
  }

  static String _stripPrefixes(String? value) {
    var text = value == null ? '' : value.toLowerCase().trim();
    text = text
        .replaceFirst(RegExp(r'^surah\s+'), '')
        .replaceFirst(RegExp(r'^surat\s+'), '')
        .replaceFirst(RegExp(r'^chapter\s+'), '');
    text = text.replaceFirst(RegExp(r'^al[-\s]+'), '').replaceFirst(RegExp(r'^el[-\s]+'), '');
    return text;
  }
}
