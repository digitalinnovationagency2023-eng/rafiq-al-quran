package com.rqmbooks.rafiqalquran.logic;

/**
 * The exact required notices and the exact fallback wording. English and Arabic
 * are the approved languages; other languages show the English wording and are
 * marked as pending review in the interface.
 */
public final class Notices {
    private Notices() {}

    public static final String NOTICE_EN =
            "Notice: I am an AI system designed to help locate Quranic verses. This service uses only the Quran text and identified translations configured in the application. I may misunderstand a question, retrieve incomplete or irrelevant passages, or display a translation incorrectly. The passages shown are retrieved from the specified sources; they are not an independent religious ruling or authoritative interpretation. Please consult qualified scholars when religious interpretation is required.";

    public static final String NOTICE_AR =
            "تنبيه: أنا نظام ذكاء اصطناعي مخصص للمساعدة في الوصول إلى الآيات القرآنية. أعتمد في هذه الخدمة على نص القرآن الكريم والترجمات المحددة داخل التطبيق فقط. قد أخطئ في فهم السؤال أو تحديد الآيات ذات الصلة أو عرض الترجمة، وقد لا أتمكن من إظهار جميع الآيات المرتبطة بالسؤال. ما أعرضه هو نصوص مسترجعة من المصادر المحددة، وليس تفسيرًا أو فتوى أو حكمًا شرعيًا. يُرجى الرجوع إلى أهل العلم المؤهلين.";

    public static final String FALLBACK_EN =
            "I'm sorry, I couldn't identify sufficiently relevant verses through my available search system. This may reflect a limitation in how I understood your question or retrieved the passages; it does not establish that no relevant verses exist in the Quran. You can rephrase your question or consult qualified scholars. You can also report a possible omission through our feedback channel.";

    public static final String FALLBACK_AR =
            "أعتذر، لم أتمكن من تحديد الآيات ذات الصلة بسؤالك من خلال نظام البحث المتاح لي. قد يكون ذلك بسبب محدودية قدرتي على فهم السؤال أو استرجاع الآيات المناسبة، ولا يعني ذلك عدم وجود آيات ذات صلة في القرآن الكريم. يمكنك إعادة صياغة السؤال أو مراجعة أهل العلم المؤهلين، كما يمكنك إبلاغنا بأي ملاحظات عبر قناة التواصل المخصصة.";

    public static String noticeFor(String language) {
        return "ar".equals(language) ? NOTICE_AR : NOTICE_EN;
    }

    public static String fallbackFor(String language) {
        return "ar".equals(language) ? FALLBACK_AR : FALLBACK_EN;
    }

    public static boolean noticeVerified(String language) {
        return "ar".equals(language) || "en".equals(language);
    }

    public static String connectiveFound(String language, int count) {
        if ("ar".equals(language)) {
            return count == 1 ? "وجدت آية واحدة تتعلق بهذا:" : "وجدت " + count + " آيات تتعلق بهذا:";
        }
        return count == 1 ? "I found one passage that refers to this:" : "I found " + count + " passages that refer to this:";
    }

    public static String translationPending(String language) {
        return "ar".equals(language)
                ? "لا تتوفر ترجمة مُراجعة لهذه اللغة بعد، لذلك تُعرض الترجمة الإنجليزية."
                : "A reviewed translation is not available for this language, so the English translation is shown.";
    }
}
