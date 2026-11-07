/// Small, deterministic conversational layer for the Quran-only assistant.
///
/// It answers greetings, thanks, questions about the app, and politely handles
/// out-of-scope small talk without ever offering religious content of its own.
/// Anything that asks for advice is deliberately NOT answered here: it returns
/// null so the caller routes it to verse retrieval. Requests for rulings, tafsir
/// or interpretation get a fixed "not an authority" reply plus a pointer to
/// qualified scholars.
///
/// No Quranic text, translation, Hadith or Fiqh ruling is ever produced by this
/// class; the only scripture shown by the app comes from the validated dataset.
enum ConversationKind {
  salam,
  greeting,
  thanks,
  farewell,
  identity,
  capabilities,
  howAreYou,
  offtopic,
  rulingRequest,
  attachmentHelp,
  help,
}

/// A conversational reply, ready to display.
class Reply {
  final ConversationKind kind;
  final String text;

  /// Optional short pointer shown under the reply.
  final String? hint;

  const Reply(this.kind, this.text, this.hint);

  /// Public factory so the UI can show an attachment/AI explanation card.
  static Reply of(ConversationKind kind, String text, String? hint) => Reply(kind, text, hint);
}

class Conversation {
  Conversation._();

  static const List<String> _salamWords = [
    'salam', 'salaam', 'assalam', 'assalamu', 'assalaamu', 'as-salamu', 'salamu alaikum', 'assalamu alaikum', //
    'السلام عليكم', 'السلام', 'سلام عليكم', 'سلام',
  ];
  static const List<String> _greetingWords = [
    'hi', 'hii', 'hello', 'hellow', 'hey', 'good morning', 'good afternoon', 'good evening', //
    'مرحبا', 'أهلا', 'اهلا', 'hola',
  ];
  static const List<String> _thanksWords = [
    'thank', 'thanks', 'thx', 'shukran', 'jazak', 'jazakallah', 'شكرا', 'جزاك', //
  ];
  static const List<String> _byeWords = ['bye', 'goodbye', 'see you', 'see ya', 'good night', 'مع السلامة'];
  static const List<String> _identityPhrases = [
    'who are you', 'what are you', 'your name', 'who made you', 'who created you', //
    'who built you', 'what is rafiq', 'what is this app', 'about this app', 'are you human', //
    'are you real', 'are you an ai', 'are you a robot',
  ];
  static const List<String> _capabilityPhrases = [
    'what can you do', 'how do you work', 'how does this work', 'what do you do', //
    'help me', 'how can you help', 'what can i ask', 'how to use', 'how do i use', //
    'features', 'what are your features', 'can you help',
  ];
  static const List<String> _howAreYouPhrases = [
    'how are you', 'how are you doing', 'how is it going', "how's it going", 'whats up', "what's up", //
  ];
  static const List<String> _rulingPhrases = [
    'fatwa', 'is it halal', 'is it haram', 'halal or haram', 'permissible', 'impermissible', //
    'islamic ruling', 'ruling on', 'sharia ruling', 'shariah ruling', 'madhhab', 'madhab', //
    'tafsir', 'tafseer', 'interpretation', 'interpret this', 'explain this verse', //
    'meaning of this verse', 'what does this verse mean', 'explain the verse', //
    'hadith', 'hadis', 'sunnah ruling', 'is it a sin', 'is it haram to', 'can i as a muslim', //
    'what is the punishment', 'which school of thought',
  ];
  static const List<String> _offtopicPhrases = [
    'weather', 'temperature', 'forecast', 'joke', 'funny', 'sing', 'song', 'music', //
    'football', 'cricket', 'soccer', 'basketball', 'match score', 'president', 'prime minister', //
    'election', 'politics', 'stock', 'bitcoin', 'crypto', 'price of', 'news', 'headline', //
    'movie', 'film', 'netflix', 'game', 'minecraft', 'fortnite', 'calculate', 'math', //
    'programming', 'python', 'javascript', 'write code', 'translate this', 'recipe', 'cook', //
    'diet', 'doctor', 'medicine', 'homework', 'essay', 'poem', 'date today', 'time now',
  ];

  /// Classifies a message. Returns a conversational reply, or null when the
  /// message should be handled by Quran verse retrieval (advice/topic questions).
  static Reply? classify(String? question) {
    if (question == null) return null;
    final text = _normalize(question);
    if (text.isEmpty) return null;
    if (text.length > 400) return null;
    if (_containsAny(text, _salamWords)) {
      return const Reply(
          ConversationKind.salam,
          "Wa alaykum as-salam wa rahmatullah. I'm Rafiq Al-Quran — a Quran-only assistant. "
          'I can find verses for a topic or a reference, and I never add my own interpretation.',
          'Try: “What does the Quran say about patience?” or “Show me 2:255”.');
    }
    if (_containsAny(text, _greetingWords)) {
      return const Reply(
          ConversationKind.greeting,
          "Hello! I'm Rafiq Al-Quran, a Quran-only assistant. I can find verses for a topic "
          'or a reference and show you their source.',
          'Try: “What does the Quran say about mercy?” or open the surah list to read.');
    }
    if (_containsAny(text, _thanksWords)) {
      return const Reply(ConversationKind.thanks, "You're welcome — ask any time.", null);
    }
    if (_containsAny(text, _byeWords)) {
      return const Reply(ConversationKind.farewell,
          'Goodbye. Your bookmarks, notes and reading position stay saved on this device.', null);
    }
    if (_containsAny(text, _howAreYouPhrases)) {
      return const Reply(ConversationKind.howAreYou,
          "I'm a small Quran-focused assistant, always ready to search. How can I help you study?", null);
    }
    if (_containsAny(text, _identityPhrases)) {
      return const Reply(
          ConversationKind.identity,
          "I'm Rafiq Al-Quran: a native app that finds relevant verses in the "
          'Quran and shows them with an identified translation and the dataset source.',
          "I don't interpret verses or issue rulings — I only retrieve and display them.");
    }
    if (_containsAny(text, _capabilityPhrases)) {
      return const Reply(
          ConversationKind.capabilities,
          'I can search the Quran for a topic (for example patience or mercy), open any verse '
          'reference such as 2:255, read all 114 surahs offline, and let you bookmark, '
          'write notes, save searches and follow reading plans. You can also ask how to use '
          'any feature — for example “how do I bookmark a verse” or '
          '“how do I listen to recitation”.',
          "You can also attach a text file, or an image when AI reading is enabled, and I'll look for matching verses.");
    }
    if (_containsAny(text, _rulingPhrases)) {
      return const Reply(
          ConversationKind.rulingRequest,
          "I don't provide fatwas, tafsir or interpretation — this app only retrieves verses "
          'from the Quran with an identified translation and its source. For a ruling or interpretation, please consult a qualified scholar.',
          'For a ruling or explanation, please consult a qualified scholar. I can still find verses about the topic.');
    }
    if (_containsAny(text, _offtopicPhrases)) {
      return const Reply(
          ConversationKind.offtopic,
          "I'm focused on the Quran, so I can't help with that. I don't write my own answers or "
          'interpretation — but I can find verses about a topic if you ask in that way.',
          'For general questions outside the Quran, a general reference will serve you better.');
    }
    final help = _helpFor(text);
    if (help != null) return Reply(ConversationKind.help, help, null);
    return null;
  }

  /// Static "where to look next" guidance shown with a safe fallback answer.
  static List<String> guidance() => const [
        'This app only shows verses from the Quran with their identified translation and source. '
            'It does not give rulings, tafsir or its own explanations.',
        'For a ruling, tafsir or a personal religious question, consult a qualified scholar or your local imam.',
        'For Hadith and Fiqh, study with a qualified teacher; well-known Hadith collections include '
            'Sahih al-Bukhari and Sahih Muslim, and Fiqh works follow your school of thought.',
        'For general knowledge outside the Quran, use a general reference — this app cannot help there.',
        'This app takes no responsibility for decisions made based on its output.',
      ];

  /// Shown when a non-text attachment cannot be read because AI assistance is off.
  static String attachmentUnavailable() =>
      'I can read .txt and .docx files on this device. Reading images or PDFs needs the optional '
      'AI service, which is not enabled right now. You can type your question instead.';

  /// Shown when the AI service is configured but unreachable.
  static String aiUnavailable() =>
      'The optional AI assistance service could not be reached. I can still search the Quran for '
      'a typed question; images and PDFs need the AI service.';

  /// Shown when the local daily AI allowance is used up.
  static String aiLimitReached() =>
      'The free AI assistance limit for today has been reached. Typed Quran search still works '
      'offline, and you can try attachments again tomorrow.';

  /// One-time disclosure before the first AI request.
  static String aiConsentTitle() => 'AI assistance (optional)';

  static String aiConsentMessage() =>
      'To answer naturally, read attached screenshots and write Quran-only reasoning answers with '
      'references, your question, a short recent-conversation context, the references/translations of '
      'the verses the app found, and (for count/list questions) one reviewed index fact may be sent to '
      "this app's server and processed with an AI provider. The AI never supplies Quran text — every "
      "verse always comes from the app's validated dataset. The app does not store your question or "
      'attachment on the server. You can turn AI assistance off in Settings at any time.';

  /// In-app feature help. Deliberately local and deterministic: it describes how
  /// to use the app and never gives religious content.
  static String? _helpFor(String text) {
    final asksHow = _containsAny(text, const [
      'help', 'how do i', 'how can i', 'how to', 'where is', 'where do i', 'where can i', //
      'what is this app', 'features', 'guide', 'instructions', 'what can i do here',
    ]);
    if (_containsAny(text, const [
      'recitation', 'recite', 'reciter', 'verse audio', 'quran audio', 'listen to quran', 'play quran', 'listen', //
    ])) {
      return 'You can listen to Quran recitation inside the app:\n'
          '• ⋮ menu → “Verse audio — Quran.com recitation”\n'
          '• Browse surahs → tap “▷ Listen” on any surah\n'
          '• Or open a verse and tap “Recitation — Quran.com”\n'
          'Choose a reciter, then play/pause, replay, seek or move verse by verse; it auto-advances. '
          'Audio streams from Quran.com (internet needed) and is never saved on the device. To hear '
          'an answer read aloud, tap Listen on the answer card (device text-to-speech).';
    }
    if (_containsAny(text, const [
      'bookmark', 'save verse', 'saved verse', 'note', 'saved search', 'reading plan', 'study tool', 'study tools', //
      'collection', 'export', 'side-by-side', 'compare translation',
    ])) {
      return 'Study tools are free and stored on this device:\n'
          '• Open a surah and tap Save on a verse to bookmark it into a collection.\n'
          '• ⋮ menu → “Study tools — collections, notes, plans” gives bookmark '
          'collections, personal notes, saved searches, reading plans with progress, side-by-side '
          'comparison and export.\n'
          '• The Browse surahs screen also has Bookmarks and History shortcuts in its top bar.';
    }
    if (_containsAny(text, const [
      'language', 'dark mode', 'light mode', 'theme', 'text size', 'font size', 'arabic font', 'interface', //
    ])) {
      return 'Top bar controls: tap the language code (for example en ▾) to choose from ten large '
          'language rows; A− and A+ change the reading text size; the moon/sun button switches '
          'dark and light theme. The same controls appear on most screens.';
    }
    if (_containsAny(text, const ['attach', 'attachment', 'upload', 'docx', 'pdf', 'screenshot', 'image', 'file'])) {
      return 'Tap 📎 ATTACH in the composer. Text, .md and .docx files are read on this device '
          'and used to search the Quran. Images can be read only when optional AI assistance is '
          'enabled. PDF attachments are not supported yet. You can send a message with only an '
          'attachment and no typed question.';
    }
    if (_containsAny(text, const ['ai', 'artificial intelligence', 'ai assistance', 'ai conversation', 'chatbot', 'gemini'])) {
      return 'AI assistance is optional and free to the customer. ⋮ menu → “AI conversation” '
          'turns it on or off; the first use shows a one-time consent notice. It is used only for '
          'short chat replies and reading attached screenshots — it never supplies Quran text or '
          'interpretation. Limit: 30 AI requests per day on this device (plus free-tier server '
          'limits). Without it the app works fully offline and images cannot be read.';
    }
    if (_containsAny(text, const ['privacy', 'my data', 'data stored', 'tracking', 'personal data'])) {
      return 'Your questions, bookmarks, notes, reading history and reading plans are stored on this '
          'device only. There is no account and no ads, and the app never reads RQM Books accounting '
          'data. Open ⋮ menu → Privacy for the full policy, including what the optional AI and '
          'feedback features send.';
    }
    if (_containsAny(text, const ['report', 'feedback', 'bug', 'complaint', 'suggest a feature'])) {
      return '⋮ menu → “Report an issue” sends a bug report, translation concern, '
          'missing-verse note or suggestion. Diagnostics and a contact detail are included only if '
          "you tick or enter them. Reports go to the app's own support API, never to WhatsApp "
          'automatically.';
    }
    if (_containsAny(text, const ['support', 'donate', 'donation', 'subscription', 'paid', 'price', 'premium', 'free'])) {
      return 'The app is free: reading, search, conversation, recitation and all study tools have no paid '
          'plan, subscription or add-on. ⋮ menu → “Support the app” explains voluntary '
          'ways to help; donation collection is currently disabled, so no checkout form is shown.';
    }
    if (_containsAny(text, const ['voice', 'microphone', 'mic', 'text to speech', 'tts', 'dictate', 'speak'])) {
      return "Tap the microphone (Voice) button to dictate a question; voice input uses the device's "
          'speech recognition. On an answer, tap Listen to hear the translation and summary through '
          "the device's text-to-speech — it is never presented as Quranic recitation. Voice "
          'features can be turned off in the menu.';
    }
    if (_containsAny(text, const [
      'search', 'find verse', 'find a verse', 'look up a verse', 'search the quran', 'verse reference', //
    ])) {
      return 'Type a topic (for example patience) or a verse reference such as 2:255 in the composer and '
          'tap Send. Answers show the Arabic verse, an identified translation, the surah:verse '
          'reference and the dataset source. Use More verses for the next matches, Listen to hear '
          'it read aloud, or New conversation to start over.';
    }
    if (!asksHow) return null;
    return 'I can help you use the app. Try asking:\n'
        '• “how do I bookmark a verse”\n'
        '• “how do I listen to recitation”\n'
        '• “how do I change the language”\n'
        '• “how do I attach a file”\n'
        '• “is there a paid plan” or “privacy”\n'
        'I can also search the Quran for any topic or a reference such as 2:255.';
  }

  static final RegExp _spaces = RegExp(r'\s+');

  static String _normalize(String value) =>
      value.toLowerCase().replaceAll('’', "'").replaceAll('‘', "'").replaceAll(_spaces, ' ').trim();

  static bool _containsAny(String text, List<String> needles) {
    for (final needle in needles) {
      if (needle.contains(' ') || !_isAscii(needle)) {
        // Phrases and non-Latin greetings are matched as substrings.
        if (text.contains(needle)) return true;
      } else {
        // Whole-word match for single ASCII words so "hi" does not match "thing".
        if (RegExp('\\b${RegExp.escape(needle)}\\b').hasMatch(text)) return true;
      }
    }
    return false;
  }

  static bool _isAscii(String value) {
    for (final c in value.codeUnits) {
      if (c > 127) return false;
    }
    return true;
  }
}
