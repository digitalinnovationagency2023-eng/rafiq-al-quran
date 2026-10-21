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

  // --- Topics requested for advisory, multi-verse answers -------------------
  { key: "theft", arabic: ["سرق", "سارق", "سرقة"], en: ["theft", "steal", "stealing", "stolen", "thief", "rob", "robbery"], ur: ["چوری", "سرقہ"], id: ["mencuri", "pencurian", "maling"], bn: ["চুরি", "চোর"], tr: ["hırsızlık", "çalmak", "hırsız"], fa: ["دزدی", "سرقت", "دزد"], ms: ["mencuri", "kecurian"], sw: ["wizi", "kuiba", "mwizi"], fr: ["vol", "voler", "voleur"] },
  { key: "law", arabic: ["حكم", "شرع", "حد", "حدود"], en: ["law", "ruling", "rule", "legal", "commandment", "prescribe", "ordain", "statute"], ur: ["قانون", "حکم", "شرع"], id: ["hukum", "syariat", "aturan"], bn: ["আইন", "বিধান", "হুকুম"], tr: ["hukuk", "yasa", "emir", "hüküm"], fa: ["قانون", "حکم", "شرع"], ms: ["hukum", "undang-undang"], sw: ["sheria", "amri", "kanuni"], fr: ["loi", "règle", "jugement", "décret"] },
  { key: "punishment", arabic: ["عاقب", "عقاب", "جزاء", "عذاب"], en: ["punishment", "punish", "penalty", "retribution", "sentence", "consequence"], ur: ["سزا", "عذاب"], id: ["hukuman", "azab"], bn: ["শাস্তি", "আযাব"], tr: ["ceza", "azap", "yaptırım"], fa: ["مجازات", "عذاب", "کیفر"], ms: ["hukuman", "azab"], sw: ["adhabu", "malipo"], fr: ["châtiment", "punition", "peine"] },

  // --- Named figures: transliterations, English/Biblical names and the Arabic
  //     terms the Quran uses. These are search aids only; the answer always
  //     displays the verses from the validated dataset.
  { key: "entity-isa", arabic: ["عيسى", "المسيح", "ابن مريم"], en: ["isa", "jesus", "christ", "masih", "messiah", "essa", "esa", "eisa", "iisa"], ur: ["عیسی", "عیسیٰ", "یسوع", "مسیحا"], id: ["isa", "yesus", "almasih"], bn: ["ঈসা", "যীশু", "মসীহ"], tr: ["isa", "isa aleyhisselam", "mesih"], fa: ["عیسی", "مسیحا"], ms: ["isa", "yesus", "almasih"], sw: ["isa", "yesu", "masihi"], fr: ["jésus", "messie"] },
  { key: "entity-yaqub", arabic: ["يعقوب"], en: ["yaqub", "yaqoob", "yaqoub", "jacob", "israel", "israil"], ur: ["یعقوب"], id: ["yakub", "yaqub"], bn: ["ইয়াকুব"], tr: ["yakup"], fa: ["یعقوب"], ms: ["yakub"], sw: ["yakobo"], fr: ["jacob"] },
  { key: "entity-musa", arabic: ["موسى"], en: ["musa", "moosa", "moses", "moises"], ur: ["موسیٰ", "موسی"], id: ["musa"], bn: ["মুসা"], tr: ["musa"], fa: ["موسی"], ms: ["musa"], sw: ["musa"], fr: ["moïse"] },
  { key: "entity-ibrahim", arabic: ["إبراهيم"], en: ["ibrahim", "ibraheem", "abraham"], ur: ["ابراہیم"], id: ["ibrahim"], bn: ["ইব্রাহিম"], tr: ["ibrahim"], fa: ["ابراهیم"], ms: ["ibrahim"], sw: ["ibrahimu"], fr: ["abraham"] },
  { key: "entity-maryam", arabic: ["مريم"], en: ["maryam", "mary", "miriam"], ur: ["مریم"], id: ["maryam"], bn: ["মারইয়াম"], tr: ["meryem"], fa: ["مریم"], ms: ["maryam"], sw: ["mariamu"], fr: ["marie"] },
  { key: "entity-nuh", arabic: ["نوح"], en: ["nuh", "nooh", "noah"], ur: ["نوح"], id: ["nuh"], bn: ["নূহ"], tr: ["nuh"], fa: ["نوح"], ms: ["nuh"], sw: ["nuhu"], fr: ["noé"] },
  { key: "entity-adam", arabic: ["آدم"], en: ["adam"], ur: ["آدم"], id: ["adam"], bn: ["আদম"], tr: ["adem"], fa: ["آدم"], ms: ["adam"], sw: ["adam"], fr: ["adam"] },
  { key: "entity-dawud", arabic: ["داود"], en: ["dawud", "dawood", "david"], ur: ["داؤد"], id: ["daud"], bn: ["দাউদ"], tr: ["davut"], fa: ["داوود"], ms: ["daud"], sw: ["daudi"], fr: ["david"] },
  { key: "entity-sulaiman", arabic: ["سليمان"], en: ["sulaiman", "suleiman", "solomon"], ur: ["سلیمان"], id: ["sulaiman"], bn: ["সুলাইমান"], tr: ["süleyman"], fa: ["سلیمان"], ms: ["sulaiman"], sw: ["sulaimani"], fr: ["salomon"] },
  { key: "entity-yusuf", arabic: ["يوسف"], en: ["yusuf", "yousuf", "joseph"], ur: ["یوسف"], id: ["yusuf"], bn: ["ইউসুফ"], tr: ["yusuf"], fa: ["یوسف"], ms: ["yusuf"], sw: ["yusufu"], fr: ["joseph"] },
  { key: "entity-yunus", arabic: ["يونس"], en: ["yunus", "younus", "jonah"], ur: ["یونس"], id: ["yunus"], bn: ["ইউনুস"], tr: ["yunus"], fa: ["یونس"], ms: ["yunus"], sw: ["yunusa"], fr: ["jonas"] },
  { key: "entity-ayyub", arabic: ["أيوب"], en: ["ayyub", "ayub", "job"], ur: ["ایوب"], id: ["ayyub"], bn: ["আইয়ুব"], tr: ["eyyüp"], fa: ["ایوب"], ms: ["ayyub"], sw: ["ayubu"], fr: ["job"] },
  { key: "entity-harun", arabic: ["هارون"], en: ["harun", "haroon", "aaron"], ur: ["ہارون"], id: ["harun"], bn: ["হারুন"], tr: ["harun"], fa: ["هارون"], ms: ["harun"], sw: ["haruni"], fr: ["aaron"] },
  { key: "entity-zakariya", arabic: ["زكريا"], en: ["zakariya", "zakariyya", "zechariah"], ur: ["زکریا"], id: ["zakaria"], bn: ["জাকারিয়া"], tr: ["zekeriya"], fa: ["زکریا"], ms: ["zakaria"], sw: ["zakaria"], fr: ["zacharie"] },
  { key: "entity-yahya", arabic: ["يحيى"], en: ["yahya", "yehya", "john"], ur: ["یحییٰ"], id: ["yahya"], bn: ["ইয়াহইয়া"], tr: ["yahya"], fa: ["یحیی"], ms: ["yahya"], sw: ["yohana"], fr: ["jean"] },
  { key: "entity-ishaq", arabic: ["إسحاق"], en: ["ishaq", "ishaaq", "isaac"], ur: ["اسحاق"], id: ["ishak"], bn: ["ইসহাক"], tr: ["ishak"], fa: ["اسحاق"], ms: ["ishak"], sw: ["isaka"], fr: ["isaac"] },
  { key: "entity-ismail", arabic: ["إسماعيل"], en: ["ismail", "ismaeel", "ishmael"], ur: ["اسماعیل"], id: ["ismail"], bn: ["ইসমাইল"], tr: ["ismail"], fa: ["اسماعیل"], ms: ["ismail"], sw: ["ismaili"], fr: ["ismaël"] },
  { key: "entity-lut", arabic: ["لوط"], en: ["lut", "lot"], ur: ["لوط"], id: ["lut"], bn: ["লুত"], tr: ["lut"], fa: ["لوط"], ms: ["lut"], sw: ["lutu"], fr: ["lot"] },
  { key: "entity-hud", arabic: ["هود"], en: ["hud"], ur: ["ہود"], id: ["hud"], bn: ["হুদ"], tr: ["hud"], fa: ["هود"], ms: ["hud"], sw: ["hudi"], fr: ["hûd"] },
  { key: "entity-salih", arabic: ["صالح"], en: ["salih", "saleh"], ur: ["صالح"], id: ["salih"], bn: ["সালিহ"], tr: ["salih"], fa: ["صالح"], ms: ["salih"], sw: ["salih"], fr: ["sâlih"] },
  { key: "entity-shuayb", arabic: ["شعيب"], en: ["shuayb", "shuaib", "jethro"], ur: ["شعیب"], id: ["syuaib"], bn: ["শুআইব"], tr: ["şuayb"], fa: ["شعیب"], ms: ["syuaib"], sw: ["shuaibu"], fr: ["chuayb"] },
  { key: "entity-idris", arabic: ["إدريس"], en: ["idris", "enoch"], ur: ["ادریس"], id: ["idris"], bn: ["ইদ্রিস"], tr: ["idris"], fa: ["ادریس"], ms: ["idris"], sw: ["idrisi"], fr: ["idris"] },
  { key: "entity-ilyas", arabic: ["إلياس"], en: ["ilyas", "elijah"], ur: ["الیاس"], id: ["ilyas"], bn: ["ইলিয়াস"], tr: ["ilyas"], fa: ["الیاس"], ms: ["ilyas"], sw: ["eliya"], fr: ["élie"] },
  { key: "entity-alyasa", arabic: ["اليسع"], en: ["alyasa", "elisha"], ur: ["الیسع"], id: ["alyasa"], bn: ["আল-ইয়াসা"], tr: ["elyesa"], fa: ["الیسع"], ms: ["alyasa"], sw: ["alyasa"], fr: ["élysée"] },
  { key: "entity-dhulkifl", arabic: ["ذو الكفل"], en: ["dhulkifl", "dhul kifl", "dhul-kifl"], ur: ["ذوالکفل"], id: ["zulkifli", "dzulkifli"], bn: ["যুলকিফল"], tr: ["zülkifl"], fa: ["ذوالکفل"], ms: ["zulkifli"], sw: ["dhulkifli"], fr: ["dhul-kifl"] },
  { key: "entity-luqman", arabic: ["لقمان"], en: ["luqman", "luqmaan"], ur: ["لقمان"], id: ["luqman"], bn: ["লুকমান"], tr: ["lokman"], fa: ["لقمان"], ms: ["luqman"], sw: ["luqmani"], fr: ["louqman"] },
  { key: "entity-dhulqarnayn", arabic: ["ذو القرنين"], en: ["dhulqarnayn", "dhul qarnayn", "dhul-qarnayn"], ur: ["ذوالقرنین"], id: ["zulkarnain"], bn: ["যুলকারনাইন"], tr: ["zülkarneyn"], fa: ["ذوالقرنین"], ms: ["zulkarnain"], sw: ["dhulqarnaini"], fr: ["dhul-qarnayn"] },
  { key: "entity-firaun", arabic: ["فرعون"], en: ["firaun", "pharaoh", "pharoah"], ur: ["فرعون"], id: ["firaun"], bn: ["ফিরআউন"], tr: ["firavun"], fa: ["فرعون"], ms: ["firaun"], sw: ["firauni"], fr: ["pharaon"] },
  { key: "entity-jibrail", arabic: ["جبريل", "روح القدس"], en: ["jibrail", "jibreel", "gabriel"], ur: ["جبرائیل"], id: ["jibril"], bn: ["জিবরীল"], tr: ["cibril"], fa: ["جبرئیل"], ms: ["jibril"], sw: ["jibrili"], fr: ["gabriel"] },
  { key: "entity-mikail", arabic: ["ميكائيل"], en: ["mikail", "michael"], ur: ["میکائیل"], id: ["mikail"], bn: ["মিকাইল"], tr: ["mikail"], fa: ["میکائیل"], ms: ["mikail"], sw: ["mikaili"], fr: ["michel"] },
  { key: "entity-iblis", arabic: ["إبليس", "الشيطان"], en: ["iblis", "shaitan", "satan", "devil"], ur: ["ابلیس", "شیطان"], id: ["iblis", "syaitan"], bn: ["ইবলিস", "শয়তান"], tr: ["iblis", "şeytan"], fa: ["ابلیس", "شیطان"], ms: ["iblis", "syaitan"], sw: ["iblisi", "shetani"], fr: ["iblis", "satan"] },
  { key: "entity-muhammad", arabic: ["محمد", "أحمد"], en: ["muhammad", "mohammad", "muhammed", "mohamed", "ahmed"], ur: ["محمد"], id: ["muhammad"], bn: ["মুহাম্মদ"], tr: ["muhammed"], fa: ["محمد"], ms: ["muhammad"], sw: ["muhammad"], fr: ["mohamed"] },
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
