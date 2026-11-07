/// The exact required notices and the exact fallback wording. English and Arabic
/// are the approved languages; other languages show the English wording and are
/// marked as pending review in the interface. Copied verbatim from the Java app.
class Notices {
  Notices._();

  static const String noticeEn =
      'Notice: I am an AI system designed to help locate Quranic verses. This service uses only the Quran text and identified translations configured in the application. I may misunderstand a question, retrieve incomplete or irrelevant passages, or display a translation incorrectly. The passages shown are retrieved from the specified sources; they are not an independent religious ruling or authoritative interpretation. Please consult qualified scholars when religious interpretation is required.';

  static const String noticeAr =
      'تنبيه: أنا نظام ذكاء اصطناعي مخصص للمساعدة في الوصول إلى الآيات القرآنية. أعتمد في هذه الخدمة على نص القرآن الكريم والترجمات المحددة داخل التطبيق فقط. قد أخطئ في فهم السؤال أو تحديد الآيات ذات الصلة أو عرض الترجمة، وقد لا أتمكن من إظهار جميع الآيات المرتبطة بالسؤال. ما أعرضه هو نصوص مسترجعة من المصادر المحددة، وليس تفسيرًا أو فتوى أو حكمًا شرعيًا. يُرجى الرجوع إلى أهل العلم المؤهلين.';

  static const String fallbackEn =
      "I'm sorry, I couldn't identify sufficiently relevant verses through my available search system. This may reflect a limitation in how I understood your question or retrieved the passages; it does not establish that no relevant verses exist in the Quran. You can rephrase your question or consult qualified scholars. You can also report a possible omission through our feedback channel.";

  static const String fallbackAr =
      'أعتذر، لم أتمكن من تحديد الآيات ذات الصلة بسؤالك من خلال نظام البحث المتاح لي. قد يكون ذلك بسبب محدودية قدرتي على فهم السؤال أو استرجاع الآيات المناسبة، ولا يعني ذلك عدم وجود آيات ذات صلة في القرآن الكريم. يمكنك إعادة صياغة السؤال أو مراجعة أهل العلم المؤهلين، كما يمكنك إبلاغنا بأي ملاحظات عبر قناة التواصل المخصصة.';

  static String noticeFor(String language) => language == 'ar' ? noticeAr : noticeEn;

  static String fallbackFor(String language) => language == 'ar' ? fallbackAr : fallbackEn;

  static bool noticeVerified(String language) => language == 'ar' || language == 'en';

  static String connectiveFound(String language, int count) {
    if (language == 'ar') {
      return count == 1 ? 'وجدت آية واحدة تتعلق بهذا:' : 'وجدت $count آيات تتعلق بهذا:';
    }
    return count == 1 ? 'I found one passage that refers to this:' : 'I found $count passages that refer to this:';
  }

  /// Message for a question about a named figure (prophet/person).
  static String entityFound(String language, String name, int count) {
    if (language == 'ar') {
      return 'يذكر القرآن $name في ${count == 1 ? 'آية واحدة' : '$count آيات'}'
          '، وهي معروضة أدناه مع مراجعها. هذا استرجاع لنص القرآن فقط وليس تفسيرًا ولا فتوى.';
    }
    return 'The Quran mentions $name in ${count == 1 ? 'one passage' : '$count passages'}'
        ', shown below with their references. This is a Quran-only retrieval — not a tafsir or a ruling.';
  }

  static String translationPending(String language) => language == 'ar'
      ? 'لا تتوفر ترجمة مُراجعة لهذه اللغة بعد، لذلك تُعرض الترجمة الإنجليزية.'
      : 'A reviewed translation is not available for this language, so the English translation is shown.';
}
