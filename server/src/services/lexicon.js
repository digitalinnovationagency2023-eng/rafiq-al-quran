// A curated editorial lexicon that maps everyday topic words in the supported
// languages to Arabic terms used in the Quran. It is a search aid only: it never
// produces religious text, and every verse it helps find is still read from the
// validated dataset and displayed verbatim.
//
// The list is intentionally small and reviewable. Operators can extend it in a
// future dataset release without touching Quranic text.

export const CONCEPTS = [
  { key: "mercy", arabic: ["رحم", "رحمة", "غفر", "عفو"], en: ["mercy", "merciful", "compassion", "forgiveness", "forgive"], ur: ["رحمت", "رحم", "معافی"], id: ["rahmat", "kasih", "ampun"], bn: ["রহমত", "দয়া", "ক্ষমা"], tr: ["merhamet", "bağışlama", "af"], fa: ["رحمت", "بخشش", "آمرزش"], ms: ["rahmat", "kasih", "ampun"], sw: ["rehema", "msamaha"], fr: ["miséricorde", "pardon", "clémence"] },
  { key: "patience", arabic: ["صبر", "صابر"], en: ["patience", "patient", "endure", "perseverance"], ur: ["صبر", "صبرکرنے"], id: ["sabar", "kesabaran"], bn: ["ধৈর্য", "সবর"], tr: ["sabır", "sabretmek"], fa: ["صبر", "شکیبایی"], ms: ["sabar", "kesabaran"], sw: ["subira", "uvumilivu"], fr: ["patience", "endurance"] },
  { key: "prayer", arabic: ["صلو", "صلاة", "صلات"], en: ["prayer", "pray", "salah", "worship"], ur: ["نماز", "دعا"], id: ["shalat", "salat", "doa"], bn: ["নামাজ", "সালাত", "দোয়া"], tr: ["namaz", "dua", "salat"], fa: ["نماز", "دعا"], ms: ["solat", "sembahyang", "doa"], sw: ["swala", "sala", "dua"], fr: ["prière", "prier", "salat"] },
  { key: "charity", arabic: ["صدق", "زكو", "أنفق", "خير"], en: ["charity", "give", "spend", "almsgiving", "zakat"], ur: ["صدقہ", "زکوٰۃ", "خرچ"], id: ["sedekah", "zakat", "infak"], bn: ["দান", "যাকাত", "সদকা"], tr: ["sadaka", "zekat", "infak"], fa: ["صدقه", "زکات", "انفاق"], ms: ["sedekah", "zakat", "infaq"], sw: ["sadaka", "zaka", "toa"], fr: ["aumône", "charité", "zakat"] },
  { key: "knowledge", arabic: ["علم", "يعلم", "حكم"], en: ["knowledge", "know", "learn", "wisdom"], ur: ["علم", "جاننا", "حکمت"], id: ["ilmu", "pengetahuan", "hikmah"], bn: ["জ্ঞান", "ইলম", "হিকমত"], tr: ["ilim", "bilgi", "hikmet"], fa: ["علم", "دانش", "حکمت"], ms: ["ilmu", "pengetahuan", "hikmah"], sw: ["elimu", "maarifa", "hekima"], fr: ["savoir", "connaissance", "sagesse"] },
  { key: "justice", arabic: ["عدل", "قسط", "ظلم"], en: ["justice", "fair", "oppression", "wrongdoing"], ur: ["انصاف", "عدل", "ظلم"], id: ["keadilan", "adil", "zalim"], bn: ["ন্যায়", "ইনসাফ", "জুলুম"], tr: ["adalet", "adil", "zulüm"], fa: ["عدالت", "داد", "ظلم"], ms: ["keadilan", "adil", "zalim"], sw: ["haki", "adili", "dhuluma"], fr: ["justice", "équité", "injustice"] },
  { key: "paradise", arabic: ["جنة", "جنات", "فردوس"], en: ["paradise", "garden", "heaven", "jannah"], ur: ["جنت", "باغ"], id: ["surga", "taman"], bn: ["জান্নাত", "বাগান"], tr: ["cennet", "bahçe"], fa: ["بهشت", "باغ"], ms: ["syurga", "taman"], sw: ["peponi", "bustani"], fr: ["paradis", "jardin"] },
  { key: "hellfire", arabic: ["نار", "جهنم", "عذاب"], en: ["hell", "fire", "punishment", "hellfire"], ur: ["جہنم", "آگ", "عذاب"], id: ["neraka", "api", "azab"], bn: ["জাহান্নাম", "আগুন", "আজাব"], tr: ["cehennem", "ateş", "azap"], fa: ["جهنم", "آتش", "عذاب"], ms: ["neraka", "api", "azab"], sw: ["motoni", "moto", "adhabu"], fr: ["enfer", "feu", "châtiment"] },
  { key: "parents", arabic: ["والد", "والدين", "أم", "أب"], en: ["parents", "mother", "father", "family"], ur: ["والدین", "ماں", "باپ"], id: ["orang tua", "ibu", "ayah"], bn: ["পিতা-মাতা", "মা", "বাবা"], tr: ["anne", "baba", "ebeveyn"], fa: ["پدر", "مادر", "والدین"], ms: ["ibu bapa", "ibu", "ayah"], sw: ["wazazi", "mama", "baba"], fr: ["parents", "mère", "père"] },
  { key: "marriage", arabic: ["نكح", "زوج", "أزواج"], en: ["marriage", "spouse", "wife", "husband", "marry"], ur: ["نکاح", "شادی", "بیوی", "شوہر"], id: ["pernikahan", "pasangan", "istri", "suami"], bn: ["বিবাহ", "স্ত্রী", "স্বামী"], tr: ["evlilik", "eş", "karı", "koca"], fa: ["ازدواج", "همسر", "زن", "شوهر"], ms: ["perkahwinan", "pasangan", "isteri", "suami"], sw: ["ndoa", "mke", "mume"], fr: ["mariage", "épouse", "époux"] },
  { key: "wealth", arabic: ["مال", "أموال", "رزق", "غنى"], en: ["wealth", "money", "provision", "rich", "poverty"], ur: ["مال", "دولت", "رزق", "غربت"], id: ["harta", "kekayaan", "rezeki", "miskin"], bn: ["সম্পদ", "ধন", "রিজিক", "দারিদ্র্য"], tr: ["mal", "servet", "rızık", "fakirlik"], fa: ["مال", "ثروت", "رزق", "فقر"], ms: ["harta", "kekayaan", "rezeki", "miskin"], sw: ["mali", "utajiri", "riziki", "umaskini"], fr: ["richesse", "argent", "provision", "pauvreté"] },
  { key: "death", arabic: ["موت", "يموت", "أجل"], en: ["death", "die", "hereafter", "soul"], ur: ["موت", "مرنا", "آخرت"], id: ["kematian", "mati", "akhirat"], bn: ["মৃত্যু", "মরা", "আখিরাত"], tr: ["ölüm", "ölmek", "ahiret"], fa: ["مرگ", "مردن", "آخرت"], ms: ["kematian", "mati", "akhirat"], sw: ["kifo", "kufa", "akhira"], fr: ["mort", "mourir", "au-delà"] },
  { key: "patience_hardship", arabic: ["بلو", "فتن", "ضيق", "كرب"], en: ["hardship", "trial", "difficulty", "suffering", "test"], ur: ["مصیبت", "آزمائش", "پریشانی"], id: ["cobaan", "kesulitan", "ujian"], bn: ["বিপদ", "পরীক্ষা", "কষ্ট"], tr: ["sıkıntı", "imtihan", "zorluk"], fa: ["سختی", "آزمایش", "مصیبت"], ms: ["ujian", "kesusahan", "cubaan"], sw: ["shida", "mtihani", "huzuni"], fr: ["épreuve", "difficulté", "souffrance"] },
  { key: "guidance", arabic: ["هدي", "هدى", "رشد", "نور"], en: ["guidance", "guide", "light", "straight path"], ur: ["ہدایت", "نور", "سیدھا راستہ"], id: ["petunjuk", "hidayah", "cahaya"], bn: ["হেদায়েত", "পথ", "আলো"], tr: ["hidayet", "rehberlik", "nur"], fa: ["هدایت", "نور", "راه راست"], ms: ["petunjuk", "hidayah", "cahaya"], sw: ["uongozi", "mwongozo", "nuru"], fr: ["guidance", "lumière", "droit chemin"] },
  { key: "sin", arabic: ["ذنب", "سيئ", "خطيئ", "توب"], en: ["sin", "wrong", "repentance", "mistake"], ur: ["گناہ", "توبہ", "خطا"], id: ["dosa", "taubat", "kesalahan"], bn: ["পাপ", "তাওবা", "ভুল"], tr: ["günah", "tövbe", "hata"], fa: ["گناه", "توبه", "خطا"], ms: ["dosa", "taubat", "kesilapan"], sw: ["dhambi", "tubu", "kosa"], fr: ["péché", "repentir", "faute"] },
  { key: "creation", arabic: ["خلق", "سماو", "أرض", "خلق"], en: ["creation", "create", "heavens", "earth"], ur: ["پیدائش", "آسمان", "زمین"], id: ["penciptaan", "langit", "bumi"], bn: ["সৃষ্টি", "আকাশ", "পৃথিবী"], tr: ["yaratılış", "gökler", "yer"], fa: ["آفرینش", "آسمان", "زمین"], ms: ["penciptaan", "langit", "bumi"], sw: ["uumbaji", "mbingu", "ardhi"], fr: ["création", "cieux", "terre"] },
];

/** Expand a user's words into Arabic search terms using the lexicon. */
export const expandTerms = (words) => {
  const extra = new Set();
  const lowered = words.map((word) => String(word || "").toLowerCase());
  for (const concept of CONCEPTS) {
    const vocabulary = [concept.en, concept.ur, concept.id, concept.bn, concept.tr, concept.fa, concept.ms, concept.sw, concept.fr].flat();
    const hit = lowered.some((word) =>
      vocabulary.some((term) => {
        const normalizedTerm = term.toLowerCase();
        return normalizedTerm.length > 2 && (word === normalizedTerm || word.includes(normalizedTerm) || normalizedTerm.includes(word));
      })
    );
    if (hit) concept.arabic.forEach((term) => extra.add(term));
  }
  return [...extra];
};
