// Interface copy. English and Arabic are maintained here. Other languages use
// the framework (language list, direction, number formats) but show English
// interface chrome until their copy is professionally reviewed, exactly like the
// legal notices. This is stated in the interface, never hidden.

export const LANGUAGES = [
  { code: "ar", name: "Arabic", nativeName: "العربية", direction: "rtl" },
  { code: "en", name: "English", nativeName: "English", direction: "ltr" },
  { code: "ur", name: "Urdu", nativeName: "اردو", direction: "rtl" },
  { code: "id", name: "Indonesian", nativeName: "Bahasa Indonesia", direction: "ltr" },
  { code: "bn", name: "Bengali", nativeName: "বাংলা", direction: "ltr" },
  { code: "tr", name: "Turkish", nativeName: "Türkçe", direction: "ltr" },
  { code: "fa", name: "Persian", nativeName: "فارسی", direction: "rtl" },
  { code: "ms", name: "Malay", nativeName: "Bahasa Melayu", direction: "ltr" },
  { code: "sw", name: "Swahili", nativeName: "Kiswahili", direction: "ltr" },
  { code: "fr", name: "French", nativeName: "Français", direction: "ltr" },
];

export const languageMeta = (code) => LANGUAGES.find((entry) => entry.code === code) || LANGUAGES[1];

const EN = {
  appName: "Quran Companion",
  tagline: "Find relevant verses, respectfully. Quranic text and identified translations only.",
  chat: "Ask",
  sources: "Sources",
  donate: "Support",
  premium: "Tools",
  about: "About",
  privacy: "Privacy",
  report: "Report an issue",
  history: "History",
  newConversation: "New conversation",
  composerPlaceholder: "Ask about a topic, or give a reference such as 2:255…",
  send: "Send",
  listening: "Listening…",
  micIdle: "Voice input",
  stopListening: "Stop listening",
  transcriptEditable: "You can edit the transcript before sending.",
  speakAnswer: "Listen to the translation and summary",
  stopSpeaking: "Stop playback",
  voiceDisabled: "Voice features are disabled in settings",
  voiceSettings: "Voice",
  enableVoice: "Enable voice features",
  language: "Language",
  textSize: "Text size",
  searchRunning: "Searching the configured Quran dataset…",
  emptyTitle: "Ask a question, or open a reference",
  emptyBody:
    "Answers show the Arabic verse, an identified translation, the surah and verse reference, and the source of the dataset. The assistant never writes religious text of its own.",
  error: "Something went wrong. Please try again.",
  offline: "You appear to be offline. The last loaded content stays visible.",
  notFoundVerse: "That reference was not found in the dataset.",
  noticeLabel: "Required notice",
  fallbackLabel: "No confident match",
  sourceAndLicence: "Source & licence",
  arabicLabel: "Quranic Arabic",
  translationLabel: "Translation",
  reference: "Reference",
  showSource: "Source details",
  close: "Close",
  loading: "Loading…",
  moreVerses: "More verses",
  recitation: "Recitation",
  recitationDisabled:
    "Recitation playback is disabled in this deployment until the recording rights of the reciter are verified and documented.",
  ttsDisclosure:
    "Spoken answers use your device's speech synthesis for the translation and summary only. It is not a Quranic recitation.",
  sttDisclosure:
    "Voice input uses your browser's speech recognition. Audio may be processed by your browser or platform provider; it never becomes part of the Quranic dataset.",
  interfacePending: "Interface text is in English until this language's copy is reviewed.",
  donationTitle: "Support the app",
  donationIntro:
    "Help keep this free Quran-focused application reliable and improving. Voluntary contributions may support maintenance, accessibility, extra language support, better search and verified recitation access.",
  donationFree: "Donating is entirely optional and never affects access to the core features.",
  donationAmount: "Amount",
  donationCustom: "Custom amount",
  donationContinue: "Continue to secure checkout",
  donationDisabled:
    "Donation collection is not enabled in this deployment. The amount can be validated here, but no payment is requested.",
  donationCompliance:
    "Live collection requires the operator's fundraising and consumer-protection review to be recorded. No religious reward is promised, and no charitable or tax-deductible status is claimed.",
  donationRecurring: "Make this a monthly donation",
  donationReceipt: "A receipt is issued by the payment provider after a successful payment.",
  premiumTitle: "Optional tools",
  premiumIntro:
    "These are optional conveniences. Core reading, verse lookup and the conversational experience are free and never depend on payment.",
  premiumDisabled: "Paid tools are not enabled in this deployment.",
  premiumCore: "Nothing here changes the Quranic text, its translation sources, or the accuracy of an answer.",
  feedbackTitle: "Report an issue",
  feedbackIntro:
    "Tell us about a missing verse, a wrong reference, a translation display problem, an accessibility issue or a bug. Reports are reviewed before anything is treated as a correction.",
  feedbackCategory: "Category",
  feedbackDescription: "What happened?",
  feedbackVerse: "Verse reference (optional)",
  feedbackContact: "Contact detail (optional)",
  feedbackConsent: "Include the question text and basic diagnostics with this report",
  feedbackConsentNote:
    "Without consent, the report is stored without conversation text or diagnostics. Nothing is sent to a third party automatically.",
  feedbackSubmit: "Send report",
  feedbackSent: "Thank you. Your report has been recorded for review.",
  feedbackWhatsApp: "You may also contact us on WhatsApp yourself. Nothing is sent automatically.",
  privacyTitle: "Privacy",
  isolationNote: "This application keeps its own database and never reads RQM Books accounting data.",
  admin: "Administration",
  adminToken: "Administrator token",
  adminSummary: "Summary",
  adminFeedback: "Feedback",
  adminTranslations: "Translations",
};

const AR = {
  appName: "رفيق القرآن",
  tagline: "ابحث عن الآيات ذات الصلة باحترام. نص القرآن والترجمات المحددة فقط.",
  chat: "اسأل",
  sources: "المصادر",
  donate: "الدعم",
  premium: "أدوات",
  about: "عن التطبيق",
  privacy: "الخصوصية",
  report: "أبلغ عن مشكلة",
  history: "السجل",
  newConversation: "محادثة جديدة",
  composerPlaceholder: "اسأل عن موضوع أو اذكر مرجعًا مثل 2:255…",
  send: "إرسال",
  listening: "يتم الاستماع…",
  micIdle: "إدخال صوتي",
  stopListening: "إيقاف الاستماع",
  transcriptEditable: "يمكنك تعديل النص قبل الإرسال.",
  speakAnswer: "استمع إلى الترجمة والملخص",
  stopSpeaking: "إيقاف التشغيل",
  voiceDisabled: "الميزات الصوتية معطلة في الإعدادات",
  voiceSettings: "الصوت",
  enableVoice: "تفعيل الميزات الصوتية",
  language: "اللغة",
  textSize: "حجم النص",
  searchRunning: "جارٍ البحث في مجموعة القرآن المحددة…",
  emptyTitle: "اطرح سؤالًا أو افتح مرجعًا",
  emptyBody:
    "تعرض الإجابة الآية بالعربية وترجمة محددة واسم السورة ورقم الآية ومصدر المجموعة. لا يكتب المساعد نصًا دينيًا من عنده.",
  error: "حدث خطأ. يُرجى المحاولة مرة أخرى.",
  offline: "يبدو أنك غير متصل بالإنترنت. سيبقى المحتوى المحمّل ظاهرًا.",
  notFoundVerse: "لم يتم العثور على هذا المرجع في مجموعة البيانات.",
  noticeLabel: "التنبيه المطلوب",
  fallbackLabel: "لا توجد مطابقة موثوقة",
  sourceAndLicence: "المصدر والترخيص",
  arabicLabel: "النص القرآني",
  translationLabel: "الترجمة",
  reference: "المرجع",
  showSource: "تفاصيل المصدر",
  close: "إغلاق",
  loading: "جارٍ التحميل…",
  moreVerses: "المزيد من الآيات",
  recitation: "التلاوة",
  recitationDisabled:
    "تشغيل التلاوة معطل في هذا النشر حتى يتم التحقق من حقوق التسجيلات وتوثيقها.",
  ttsDisclosure:
    "الإجابات الصوتية تستخدم تحويل النص إلى كلام في جهازك للترجمة والملخص فقط، وليست تلاوة قرآنية.",
  sttDisclosure:
    "الإدخال الصوتي يستخدم التعرف على الكلام في متصفحك. قد تتم معالجة الصوت من قبل مزود المتصفح أو النظام، ولا يصبح أبدًا جزءًا من مجموعة النص القرآني.",
  interfacePending: "نص الواجهة بالإنجليزية حتى تتم مراجعة ترجمته.",
  donationTitle: "ادعم التطبيق",
  donationIntro:
    "ساعد في استمرار هذا التطبيق المجاني وتحسينه. المساهمات الطوعية قد تدعم الصيانة وإمكانية الوصول ودعم لغات إضافية وتحسين البحث والوصول إلى تلاوات موثقة.",
  donationFree: "التبرع اختياري بالكامل ولا يؤثر على الوصول إلى الميزات الأساسية.",
  donationAmount: "المبلغ",
  donationCustom: "مبلغ مخصص",
  donationContinue: "المتابعة إلى الدفع الآمن",
  donationDisabled: "تحصيل التبرعات غير مفعّل في هذا النشر؛ يمكن التحقق من المبلغ فقط دون طلب أي دفعة.",
  donationCompliance:
    "يتطلب التحصيل المباشر توثيق مراجعة الجهة المشغلة للجمع وحماية المستهلك. لا يُوعد بأجر ديني، ولا يُدّعى وضع خيري أو إعفاء ضريبي.",
  donationRecurring: "اجعل هذا التبرع شهريًا",
  donationReceipt: "يصدر مزود الدفع إيصالًا بعد نجاح الدفع.",
  premiumTitle: "أدوات اختيارية",
  premiumIntro: "هذه أدوات اختيارية للراحة. القراءة والبحث والتجربة الحوارية الأساسية مجانية دائمًا.",
  premiumDisabled: "الأدوات المدفوعة غير مفعّلة في هذا النشر.",
  premiumCore: "لا يغيّر أي شيء هنا نص القرآن أو مصادر ترجماته أو دقة الإجابة.",
  feedbackTitle: "أبلغ عن مشكلة",
  feedbackIntro:
    "أخبرنا عن آية مفقودة أو مرجع غير صحيح أو مشكلة عرض ترجمة أو مشكلة وصول أو خطأ برمجي. تُراجع البلاغات قبل اعتبارها تصحيحًا.",
  feedbackCategory: "الفئة",
  feedbackDescription: "ماذا حدث؟",
  feedbackVerse: "مرجع الآية (اختياري)",
  feedbackContact: "بيانات التواصل (اختياري)",
  feedbackConsent: "أرفق نص السؤال وتشخيصات أساسية مع هذا البلاغ",
  feedbackConsentNote:
    "بدون الموافقة يُحفظ البلاغ دون نص المحادثة أو التشخيصات، ولا يُرسل شيء إلى طرف ثالث تلقائيًا.",
  feedbackSubmit: "إرسال البلاغ",
  feedbackSent: "شكرًا لك. تم تسجيل بلاغك للمراجعة.",
  feedbackWhatsApp: "يمكنك أيضًا التواصل معنا عبر واتساب بنفسك. لا يُرسل شيء تلقائيًا.",
  privacyTitle: "الخصوصية",
  isolationNote: "يحتفظ هذا التطبيق بقاعدة بياناته الخاصة ولا يقرأ أبدًا بيانات محاسبة RQM Books.",
  admin: "الإدارة",
  adminToken: "رمز المسؤول",
  adminSummary: "الملخص",
  adminFeedback: "البلاغات",
  adminTranslations: "الترجمات",
};

const DICTIONARIES = { en: EN, ar: AR };

export const translate = (language, key) => {
  const dictionary = DICTIONARIES[language] || EN;
  return dictionary[key] ?? EN[key] ?? key;
};

export const isUiTranslated = (language) => Boolean(DICTIONARIES[language]);

export const formatDate = (language, value) => {
  if (!value) return "";
  try {
    return new Intl.DateTimeFormat(language === "ar" ? "ar-AE" : language, {
      day: "2-digit",
      month: "short",
      hour: "2-digit",
      minute: "2-digit",
    }).format(new Date(value));
  } catch {
    return new Date(value).toISOString();
  }
};
