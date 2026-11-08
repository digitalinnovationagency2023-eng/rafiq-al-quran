import 'dart:async';

import 'package:flutter/foundation.dart';

import '../../data/api.dart';
import '../../data/app_state.dart';
import '../../data/attachments.dart';
import '../../data/history_codec.dart';
import '../../data/voice.dart';
import '../../logic/logic.dart';
import 'ai_flow.dart';

/// What the controller needs from the screen.
abstract class MainHooks {
  void toast(String message);

  /// One-time AI disclosure. Returns true (Continue), false (Use offline only) or null (dismissed).
  Future<bool?> askAiConsent();
}

/// Chat state and flows of the home screen (port of the non-view half of
/// `MainActivity`): dataset loading, submit pipeline, history and archives,
/// attachments, voice and hands-free loop.
class MainController extends ChangeNotifier {
  final MainHooks hooks;
  final AppState app = AppState.instance;
  late final AiFlow ai = AiFlow(app.prefs);
  final VoiceService voice = VoiceService.instance;

  Dataset? dataset;
  String datasetError = '';
  final List<Turn> turns = <Turn>[];
  String conversationId = _newId();
  String status = '';
  Timer? _busyTicker;
  bool aiAvailable = false;
  bool aiQuotaNoticeShown = false;
  AttachmentFile? attachment;
  bool handsFree = false;
  bool listening = false;
  Answer? speakingAnswer;
  Answer? _pendingSpeech;
  bool ttsPreparing = false;

  /// Index of the last answer turn whose render should show the quota card.
  int? quotaNoticeAfterTurn;

  /// Bumped whenever the message list should scroll to the bottom.
  int scrollRequest = 0;

  MainController(this.hooks) {
    handsFree = app.prefs.handsFree;
    voice.onSpeechDone = _onTtsDone;
    voice.onSpeechError = (message) {
      speakingAnswer = null;
      hooks.toast(message);
      notifyListeners();
    };
  }

  static String _newId() {
    final now = DateTime.now().microsecondsSinceEpoch;
    return '$now-${now.hashCode.toRadixString(16)}';
  }

  // --------------------------------------------------------------- loading
  Future<void> load() async {
    try {
      final loaded = await app.assets.ready(app.prefs.language);
      dataset = loaded;
      _loadHistory();
      status = '';
      notifyListeners();
    } catch (error) {
      datasetError = 'Dataset error: $error';
      status = datasetError;
      notifyListeners();
    }
    unawaited(_checkAi());
    if (app.prefs.voiceEnabled) unawaited(voice.ensureTts());
  }

  Future<void> _checkAi() async {
    try {
      aiAvailable = Api.optBool(await Api.assistantStatus(), 'enabled');
    } catch (_) {
      aiAvailable = false; // AI is optional; offline builds use local conversation.
    }
    notifyListeners();
  }

  @override
  void dispose() {
    _stopBusyTicker();
    voice.onSpeechDone = null;
    voice.onSpeechError = null;
    unawaited(voice.stop());
    unawaited(voice.cancelListening());
    saveHistory();
    super.dispose();
  }

  /// Leaving the screen must not keep reading aloud or recording.
  void onHidden() {
    if (handsFree) {
      stopHandsFree(false);
    } else {
      unawaited(voice.stop());
      speakingAnswer = null;
      _pendingSpeech = null;
      stopListening();
    }
  }

  // ---------------------------------------------------------------- status
  /// Working indicator: a short message with animated trailing dots.
  void busy(String message) {
    _stopBusyTicker();
    var step = 1;
    status = message;
    notifyListeners();
    _busyTicker = Timer.periodic(const Duration(milliseconds: 420), (_) {
      status = message + '.' * (step % 4);
      step++;
      notifyListeners();
    });
  }

  void idle() {
    _stopBusyTicker();
    status = '';
    notifyListeners();
  }

  void _stopBusyTicker() {
    _busyTicker?.cancel();
    _busyTicker = null;
  }

  void setStatus(String message) {
    status = message;
    notifyListeners();
  }

  // -------------------------------------------------------------- language
  Future<void> applyLanguage(String code) async {
    app.setLanguage(code);
    notifyListeners();
    try {
      await app.assets.ensureLanguage(code);
    } catch (_) {
      // English remains the fallback translation.
    }
    hooks.toast('Interface language: ${Language.byCode(code).englishName}');
  }

  void changeTextScale(double delta) {
    app.setTextScale((app.prefs.textScale + delta).clamp(0.9, 1.5));
    notifyListeners();
  }

  // ---------------------------------------------------------------- submit
  Future<void> submit(String rawQuestion) async {
    if (dataset == null) {
      hooks.toast('The dataset is still loading.');
      return;
    }
    final question = rawQuestion.trim();
    ai.quotaBlocked = false;
    aiQuotaNoticeShown = false;
    final file = attachment;
    if (question.isEmpty && file == null) return;
    if (question.isNotEmpty) app.store.addSearch(question);

    final chat = file == null ? Conversation.classify(question) : null;
    if (chat != null) {
      _startConversationTurn(question, chat);
      return;
    }
    if (file != null && !file.readableLocally && !file.image) {
      _startConversationTurn(question,
          Reply.of(ConversationKind.attachmentHelp, file.error ?? Conversation.attachmentUnavailable(), null));
      clearAttachment();
      return;
    }
    final aiNeeded = aiAvailable && app.prefs.aiAssistEnabled && (file == null || file.image);
    if (aiNeeded && !app.prefs.aiConsentShown) {
      final consent = await hooks.askAiConsent();
      if (consent == null) return;
      if (!consent) app.prefs.aiAssistEnabled = false;
      app.prefs.aiConsentShown = true;
    }
    await _performSubmit(question, file);
  }

  Future<void> _performSubmit(String originalQuestion, AttachmentFile? file) async {
    final bubbleText = originalQuestion.isEmpty ? '[Attachment: ${file == null ? 'file' : file.name}]' : originalQuestion;
    turns.add(Turn('user', bubbleText, null));
    clearAttachment();
    final language = app.prefs.language;
    busy(file != null && file.image ? 'Reading the attachment' : 'Searching the Quran');
    final data = dataset!;
    Answer? answer;
    FactResult? indexFact;
    String? note;
    try {
      var searchText = originalQuestion;
      if (file != null) {
        if (file.readableLocally) {
          searchText = '$originalQuestion\n${file.text}'.trim();
          note = 'Read “${file.name}” on this device.';
        } else if (file.image) {
          final result = await ai.callAi(originalQuestion, file, language);
          if (result.imageText.trim().isNotEmpty) {
            searchText = '$originalQuestion\n${result.imageText}'.trim();
            note = 'Text from the attached image was read by the AI service.';
          } else {
            _finishAnswer(conversationAnswer(language, Conversation.attachmentUnavailable(), null));
            return;
          }
        }
      }
      if (searchText.trim().isEmpty) {
        answer = conversationAnswer(language, Conversation.attachmentUnavailable(), null);
      } else {
        AiResult? aiResult;
        if (aiAvailable && app.prefs.aiAssistEnabled && originalQuestion.isNotEmpty && (file == null || !file.image)) {
          aiResult = await ai.callAi(originalQuestion, null, language);
        }
        if (aiResult != null &&
            (aiResult.intent == 'smalltalk' || aiResult.intent == 'offtopic') &&
            aiResult.reply.isNotEmpty) {
          answer = conversationAnswer(language, aiResult.reply, null);
        } else {
          if (aiResult != null && aiResult.searchText.trim().isNotEmpty) searchText = aiResult.searchText;
          // Make sure the selected translation is in memory before composing.
          try {
            await app.assets.ensureLanguage(language);
          } catch (_) {
            // English fallback.
          }
          indexFact = Facts.match(data, originalQuestion);
          answer = indexFact != null
              ? AnswerComposer.factAnswer(data, language, indexFact.message, indexFact.verseKeys)
              : AnswerComposer.answer(data, searchText, language);
        }
      }
    } catch (_) {
      answer = AnswerComposer.answer(data, originalQuestion, language);
    }
    // Optional deeper reasoning answer: the AI may only use the verses (and the
    // reviewed index fact) we already retrieved.
    if (answer.kind == 'answer' &&
        answer.passages.isNotEmpty &&
        aiAvailable &&
        app.prefs.aiAssistEnabled &&
        app.prefs.aiConsentShown) {
      answer = await ai.deepenAnswer(answer, originalQuestion, language, indexFact?.aiFact, recentHistory(6), busy);
    }
    _finishAnswer(answer);
    if (note != null) hooks.toast(note);
  }

  void _finishAnswer(Answer answer) {
    idle();
    turns.add(Turn('assistant', null, answer));
    if (ai.quotaBlocked && !aiQuotaNoticeShown) {
      aiQuotaNoticeShown = true;
      quotaNoticeAfterTurn = turns.length - 1;
    }
    ai.quotaBlocked = false;
    saveHistory();
    scrollRequest++;
    notifyListeners();
    if (handsFree) {
      setStatus('Thinking…');
      Future<void>.delayed(const Duration(milliseconds: 350), () => speakAnswer(answer, automatic: true));
    }
  }

  void _startConversationTurn(String question, Reply reply) {
    clearAttachment();
    final bubbleText = question.isEmpty ? '[Message]' : question;
    turns.add(Turn('user', bubbleText, null));
    final limits = <String>[];
    final hint = reply.hint;
    if (hint != null) limits.add(hint);
    if (reply.kind == ConversationKind.rulingRequest) limits.addAll(Conversation.guidance());
    _finishAnswer(conversationAnswer(app.prefs.language, reply.text, limits));
  }

  static Answer conversationAnswer(String language, String message, List<String>? limits) =>
      Answer('conversation', language, message, const <Passage>[], 0, '', false, null, limits ?? <String>[], false,
          const <String>[]);

  /// The last few conversation turns (excluding the current question) for AI context.
  List<Turn> recentHistory(int limit) {
    final recent = <Turn>[];
    for (var index = turns.length - 2; index >= 0 && recent.length < limit; index--) {
      recent.insert(0, turns[index]);
    }
    return recent;
  }

  Future<void> askMore(Answer previous) async {
    final data = dataset;
    if (data == null) return;
    final language = app.prefs.language;
    String? question;
    for (var index = turns.length - 1; index >= 0; index--) {
      if (turns[index].role == 'user') {
        question = turns[index].question;
        break;
      }
    }
    if (question == null || question.trim().isEmpty || question.startsWith('[')) {
      hooks.toast('Ask a topic question first.');
      return;
    }
    busy('Looking for more verses');
    try {
      await app.assets.ensureLanguage(language);
    } catch (_) {
      // English fallback.
    }
    final answer = AnswerComposer.answer(data, question, language, previous.shownVerseKeys);
    idle();
    if (answer.kind == 'fallback') {
      hooks.toast('No additional relevant passages were found.');
      return;
    }
    turns.add(Turn('assistant', null, answer));
    saveHistory();
    scrollRequest++;
    notifyListeners();
  }

  // ---------------------------------------------------------------- history
  void newConversation() {
    if (handsFree) stopHandsFree(false);
    archiveCurrentConversation();
    conversationId = _newId();
    turns.clear();
    quotaNoticeAfterTurn = null;
    app.prefs.clearHistory();
    clearAttachment();
    notifyListeners();
  }

  /// Archives the current chat so it appears on the History screen (device only).
  void archiveCurrentConversation() {
    if (turns.isEmpty) return;
    final list = HistoryCodec.archive(
        app.prefs.conversations(), conversationId, turns, DateTime.now().millisecondsSinceEpoch);
    app.prefs.setConversations(list);
  }

  void loadArchivedConversation(Map<String, dynamic>? item) {
    if (item == null) return;
    try {
      final restoredId = item['id']?.toString() ?? '';
      conversationId = restoredId.isEmpty ? _newId() : restoredId;
      final encoded = item['turns'];
      if (encoded is! List) return;
      turns
        ..clear()
        ..addAll(HistoryCodec.verified(HistoryCodec.decodeList(encoded), dataset));
      quotaNoticeAfterTurn = null;
      saveHistory();
      scrollRequest++;
      notifyListeners();
      hooks.toast('Conversation restored');
    } catch (_) {
      hooks.toast('That conversation could not be restored.');
    }
  }

  void _loadHistory() {
    turns
      ..clear()
      ..addAll(HistoryCodec.verified(HistoryCodec.decode(app.prefs.history), dataset));
    scrollRequest++;
  }

  void saveHistory() => app.prefs.setHistory(HistoryCodec.encode(turns));

  /// Plain-text copy of an answer (summary, message, then reference + translation).
  static String copyText(Answer answer) {
    final text = StringBuffer();
    final summary = answer.summary;
    if (summary != null && summary.trim().isNotEmpty) text.write('${summary.trim()}\n\n');
    if (answer.message.trim().isNotEmpty) text.write('${answer.message.trim()}\n\n');
    for (final passage in answer.passages) {
      text.write('${passage.referenceLabel}\n${passage.translationText}\n\n');
    }
    return text.toString().trim();
  }

  // ------------------------------------------------------------ attachments
  void setAttachment(AttachmentFile file) {
    attachment = file;
    notifyListeners();
    if (file.error != null) hooks.toast(file.error!);
  }

  void clearAttachment() {
    attachment = null;
    notifyListeners();
  }

  String attachmentLabel() {
    final file = attachment;
    if (file == null) return '';
    if (file.image) {
      final mode = aiAvailable && app.prefs.aiAssistEnabled
          ? 'image — will be read by the AI service'
          : 'image — AI service is off';
      return 'Attached: ${file.name} ($mode)';
    }
    return 'Attached: ${file.summary()}';
  }

  // ----------------------------------------------------------------- speech
  void toggleHandsFree() {
    if (handsFree) {
      stopHandsFree(true);
      return;
    }
    if (!app.prefs.voiceEnabled) {
      app.prefs.voiceEnabled = true;
      hooks.toast('Voice features enabled.');
    }
    handsFree = true;
    app.prefs.handsFree = true;
    unawaited(voice.ensureTts());
    setStatus('Listening… speak your question.');
    hooks.toast('Voice chat on. Speak, and the answer will be read aloud.');
    unawaited(startListening());
  }

  void stopHandsFree(bool notify) {
    final was = handsFree;
    handsFree = false;
    app.prefs.handsFree = false;
    stopListening();
    unawaited(voice.stop());
    speakingAnswer = null;
    _pendingSpeech = null;
    status = '';
    notifyListeners();
    if (notify && was) hooks.toast('Voice chat off. You can type instead.');
  }

  void _onTtsDone() {
    speakingAnswer = null;
    notifyListeners();
    if (!handsFree) return;
    setStatus('Listening…');
    Future<void>.delayed(const Duration(milliseconds: 500), () {
      if (handsFree) unawaited(startListening());
    });
  }

  Future<void> speakAnswer(Answer answer, {bool automatic = false}) async {
    if (!app.prefs.voiceEnabled) {
      if (!automatic) hooks.toast('Voice features are disabled in the menu.');
      return;
    }
    if (listening) stopListening();
    if (voice.isSpeaking) {
      await voice.stop();
      if (!automatic && identical(answer, speakingAnswer)) {
        speakingAnswer = null;
        notifyListeners();
        hooks.toast('Stopped reading aloud.');
        return;
      }
    }
    if (ttsPreparing) {
      _pendingSpeech = answer;
      return;
    }
    ttsPreparing = true;
    if (automatic) setStatus('Preparing the voice…');
    final engineError = await voice.ensureTts();
    ttsPreparing = false;
    if (engineError != null) {
      speakingAnswer = null;
      hooks.toast(engineError);
      if (automatic) setStatus('Tap ◉ to try again.');
      return;
    }
    final target = _pendingSpeech ?? answer;
    _pendingSpeech = null;
    // Never read the text with the UI language's voice: the voice follows the
    // language of the text that is actually spoken.
    final error = await voice.speak(VoiceService.spokenText(target), VoiceService.spokenLanguage(target));
    if (error != null) {
      speakingAnswer = null;
      hooks.toast(error);
      if (automatic) setStatus('Tap ◉ to try again.');
    } else {
      speakingAnswer = target;
      if (automatic) {
        setStatus('Speaking…');
      } else {
        hooks.toast('Reading the translation and summary aloud. This is not a Quranic recitation.');
      }
    }
    notifyListeners();
  }

  /// Dictation result sink: the screen puts the text into the composer.
  void Function(String text)? onTranscript;

  Future<void> startListening() async {
    if (!app.prefs.voiceEnabled) {
      hooks.toast('Voice features are disabled in the menu.');
      return;
    }
    if (listening) {
      stopListening();
      status = '';
      notifyListeners();
      return;
    }
    final language = app.prefs.language;
    final available = await voice.recognizerAvailable(
      onError: (code) {
        listening = false;
        status = '';
        notifyListeners();
        hooks.toast(VoiceService.errorMessage(code, language));
        if (handsFree) setStatus('Tap ◉ to try again, or type instead.');
      },
      onStatus: (state) {
        if (state == 'done' || state == 'notListening') {
          listening = false;
          if (status.startsWith('●')) status = '';
          notifyListeners();
        }
      },
    );
    if (!available) {
      hooks.toast('Voice input is not available on this device. Allow the microphone and speech recognition in '
          'iOS Settings, or type instead.');
      return;
    }
    if (!await voice.supportsLanguage(language)) {
      hooks.toast(VoiceService.unsupportedLanguageMessage(language));
      return;
    }
    if (voice.isSpeaking) {
      await voice.stop();
      speakingAnswer = null;
    }
    listening = true;
    setStatus('● Listening… speak your question. Tap the mic to stop.');
    final started = await voice.startListening(language, onResult: (text, isFinal) {
      onTranscript?.call(text);
      if (!isFinal) return;
      listening = false;
      status = '';
      notifyListeners();
      if (text.trim().isEmpty) {
        if (handsFree) setStatus("I didn't catch that. Speak again, or type instead.");
        return;
      }
      if (handsFree) {
        setStatus('Thinking…');
        Future<void>.delayed(const Duration(milliseconds: 300), () => submit(text));
      } else {
        hooks.toast('You can edit the transcript before sending.');
      }
    });
    if (!started) {
      listening = false;
      status = '';
      notifyListeners();
      hooks.toast('Voice input could not start. You can type instead.');
    }
  }

  void stopListening() {
    unawaited(voice.cancelListening());
    listening = false;
    status = '';
    notifyListeners();
  }
}
