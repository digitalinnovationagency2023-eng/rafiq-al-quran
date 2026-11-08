import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:url_launcher/url_launcher.dart';

import '../../data/api.dart';
import '../../data/app_state.dart';
import '../../data/attachments.dart';
import '../../logic/logic.dart';
import '../history_screen.dart';
import '../theme.dart';
import '../widgets.dart';
import 'answer_view.dart';
import 'drawer.dart';
import 'empty_state.dart';
import 'main_controller.dart';

/// The conversational home screen. Quran answers always come from the bundled
/// dataset; a deterministic conversation layer handles small talk; the optional
/// server AI never supplies verses. Port of `MainActivity`.
class MainScreen extends StatefulWidget {
  const MainScreen({super.key});

  /// The published App Store page used by the update prompt.
  static const String appStoreUrl = 'https://apps.apple.com/app/id6832014561';

  @override
  State<MainScreen> createState() => _MainScreenState();
}

class _MainScreenState extends State<MainScreen> with WidgetsBindingObserver implements MainHooks {
  late final MainController _controller = MainController(this);
  final TextEditingController _input = TextEditingController();
  final FocusNode _inputFocus = FocusNode();
  final ScrollController _scroll = ScrollController();
  QuranMeta? _meta;
  int _titleTapCount = 0;
  int _titleTapWindowStart = 0;
  int _lastScrollRequest = 0;

  AppState get app => AppState.instance;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _controller.onTranscript = (text) => _input.text = text;
    _controller.addListener(_onControllerChanged);
    app.pendingAsk.addListener(_onPendingAsk);
    _controller.load().then((_) {
      app.assets.meta().then((meta) {
        if (mounted) setState(() => _meta = meta);
      }).catchError((_) {});
      _onPendingAsk();
    });
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    app.pendingAsk.removeListener(_onPendingAsk);
    _controller.removeListener(_onControllerChanged);
    _controller.dispose();
    _input.dispose();
    _inputFocus.dispose();
    _scroll.dispose();
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.paused || state == AppLifecycleState.hidden) _controller.onHidden();
  }

  void _onControllerChanged() {
    if (!mounted) return;
    setState(() {});
    if (_controller.scrollRequest != _lastScrollRequest) {
      _lastScrollRequest = _controller.scrollRequest;
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (_scroll.hasClients) {
          _scroll.animateTo(_scroll.position.maxScrollExtent,
              duration: const Duration(milliseconds: 300), curve: Curves.easeOut);
        }
      });
    }
  }

  /// Saved-search deep link: run the query as soon as the dataset is ready.
  void _onPendingAsk() {
    final ask = app.pendingAsk.value;
    if (ask == null || ask.trim().isEmpty || _controller.dataset == null) return;
    app.pendingAsk.value = null;
    _input.text = ask;
    _submit();
  }

  // ------------------------------------------------------------------ hooks
  @override
  void toast(String message) => toastIn(message);

  void toastIn(String message) {
    if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(message)));
  }

  @override
  Future<bool?> askAiConsent() => showDialog<bool>(
        context: context,
        builder: (context) => AlertDialog(
          title: Text(Conversation.aiConsentTitle()),
          content: SingleChildScrollView(child: Text(Conversation.aiConsentMessage())),
          actions: [
            TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Use offline only')),
            TextButton(onPressed: () => Navigator.pop(context, true), child: const Text('Continue')),
          ],
        ),
      );

  // ---------------------------------------------------------------- actions
  Future<void> _submit() async {
    final text = _input.text;
    if (text.trim().isEmpty && _controller.attachment == null) return;
    _input.clear();
    await _controller.submit(text);
  }

  Future<void> _pickAttachment() async {
    try {
      final result = await FilePicker.pickFiles(type: FileType.any);
      if (result.isEmpty) return;
      final picked = result.first;
      _controller.busy('Reading the attachment');
      final length = await picked.length() ?? 0;
      final bytes = length > Attachments.maxBytes ? null : await picked.readAsBytes();
      final file = Attachments.read(picked.name, bytes);
      _controller.idle();
      _controller.setAttachment(file);
    } catch (_) {
      _controller.idle();
      toast('No file picker is available on this device.');
    }
  }

  Future<void> _openHistory() async {
    _controller.archiveCurrentConversation();
    final result = await Navigator.push<Map<String, dynamic>>(
        context, MaterialPageRoute<Map<String, dynamic>>(builder: (_) => const HistoryScreen()));
    if (result != null) _controller.loadArchivedConversation(result);
  }

  void _registerTitleTap() {
    final now = DateTime.now().millisecondsSinceEpoch;
    if (_titleTapCount == 0 || now - _titleTapWindowStart > 4000) {
      _titleTapCount = 0;
      _titleTapWindowStart = now;
    }
    _titleTapCount++;
    if (_titleTapCount >= 5) {
      _titleTapCount = 0;
      if (!app.operatorUnlocked) {
        app.operatorUnlocked = true;
        toast('Operator admin unlocked for this session');
      }
    }
  }

  Future<void> _checkForUpdates() async {
    toast('Checking for updates…');
    try {
      final remote = await Api.appVersion();
      final latestCode = Api.optInt(remote, 'versionCode', app.versionCode);
      final latestName = Api.optString(remote, 'versionName');
      final storeUrl = Api.optString(remote, 'iosUrl', MainScreen.appStoreUrl);
      if (!mounted) return;
      if (latestCode > app.versionCode) {
        final update = await confirm(
            context,
            'Update available',
            'Rafiq Al-Quran $latestName is available. You have ${app.versionName}. '
                'Update for the latest voice features and fixes.',
            positive: 'Update',
            negative: 'Later');
        if (update) await _openStore(storeUrl);
      } else {
        toast("You're on the latest version (${app.versionName}).");
      }
    } catch (_) {
      toast("Couldn't check for updates. Check your connection and try again.");
    }
  }

  Future<void> _openStore(String url) async {
    try {
      final opened = await launchUrl(Uri.parse(url), mode: LaunchMode.externalApplication);
      if (!opened) throw StateError('launch failed');
    } catch (_) {
      toast("Couldn't open the store. Search for Rafiq Al-Quran on the App Store.");
    }
  }

  // ------------------------------------------------------------------ build
  @override
  Widget build(BuildContext context) {
    final palette = Palette.of(context);
    final menus = MainMenus(context: context, controller: _controller, openHistory: _openHistory, checkForUpdates: _checkForUpdates);
    return PageScaffold(
      child: Column(
        children: [
          _topBar(palette, menus),
          Container(height: 1, color: palette.line),
          Expanded(
            child: Container(
              color: palette.paper.withAlpha(0),
              child: ListView(
                controller: _scroll,
                padding: const EdgeInsets.fromLTRB(20, 12, 20, 18),
                children: [
                  ..._messages(),
                  if (_controller.status.isNotEmpty)
                    Padding(
                      padding: const EdgeInsets.only(top: 6, bottom: 4),
                      child: Text(_controller.status, textAlign: TextAlign.center, style: Fonts.body(12.5, palette.muted)),
                    ),
                ],
              ),
            ),
          ),
          _composer(palette),
        ],
      ),
    );
  }

  Widget _topBar(Palette palette, MainMenus menus) {
    final top = MediaQuery.paddingOf(context).top;
    return Container(
      decoration: const BoxDecoration(gradient: Palette.headerGradient),
      padding: EdgeInsets.fromLTRB(10, top + 14, 12, 14),
      child: Row(
        children: [
          Semantics(
            button: true,
            label: 'Menu',
            child: GestureDetector(
              onTap: menus.showMenu,
              behavior: HitTestBehavior.opaque,
              child: Padding(
                padding: const EdgeInsets.fromLTRB(10, 4, 12, 4),
                child: Text('☰', style: Fonts.body(30, palette.onBar, height: 1.0)),
              ),
            ),
          ),
          Expanded(
            child: GestureDetector(
              onTap: _registerTitleTap,
              behavior: HitTestBehavior.opaque,
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('Rafiq', style: Fonts.display(23, palette.onBar)),
                  Padding(
                    padding: const EdgeInsets.only(top: 1),
                    child: Text('QUR’AN COMPANION',
                        maxLines: 1, style: Fonts.medium(8.5, palette.goldBright, letterSpacing: 0.94, height: 1.2)),
                  ),
                ],
              ),
            ),
          ),
          Semantics(
            button: true,
            label: 'New conversation',
            child: GestureDetector(
              onTap: _controller.newConversation,
              behavior: HitTestBehavior.opaque,
              child: Padding(
                padding: const EdgeInsets.fromLTRB(9, 2, 9, 2),
                child: Text('＋', style: Fonts.body(30, palette.onBar, height: 1.0)),
              ),
            ),
          ),
          const ThemeToggle(),
        ],
      ),
    );
  }

  List<Widget> _messages() {
    final turns = _controller.turns;
    if (turns.isEmpty) {
      return [
        EmptyState(
          dataset: _controller.dataset,
          meta: _meta,
          onAsk: () => _inputFocus.requestFocus(),
          onSuggestion: (question) {
            _input.text = question;
            _submit();
          },
        ),
      ];
    }
    final widgets = <Widget>[];
    for (var i = 0; i < turns.length; i++) {
      final turn = turns[i];
      if (turn.role == 'user') {
        widgets.add(UserBubble(turn.question ?? ''));
      } else if (turn.answer != null) {
        widgets.add(AnswerView(answer: turn.answer!, controller: _controller));
        if (_controller.quotaNoticeAfterTurn == i) widgets.add(const AiQuotaNotice());
      }
    }
    return widgets;
  }

  Widget _composer(Palette palette) {
    final rtl = Language.byCode(app.prefs.language).rtl;
    final attachment = _controller.attachment;
    final bottom = MediaQuery.paddingOf(context).bottom;
    return Padding(
      padding: EdgeInsets.fromLTRB(14, 8, 14, 12 + bottom),
      child: Column(
        children: [
          if (attachment != null)
            Container(
              margin: const EdgeInsets.only(bottom: 6),
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
              decoration: BoxDecoration(color: palette.greenSoft, borderRadius: BorderRadius.circular(12)),
              child: Row(
                children: [
                  Expanded(child: Text(_controller.attachmentLabel(), style: Fonts.body(12, palette.ink))),
                  TextAction('Remove', onTap: _controller.clearAttachment),
                ],
              ),
            ),
          Material(
            color: palette.card,
            elevation: 4,
            shadowColor: Colors.black26,
            borderRadius: BorderRadius.circular(30),
            child: Container(
              padding: const EdgeInsets.fromLTRB(5, 6, 5, 6),
              decoration: BoxDecoration(borderRadius: BorderRadius.circular(30), border: Border.all(color: palette.line)),
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.end,
                children: [
                  Semantics(
                    button: true,
                    label: 'Attach an image',
                    child: GestureDetector(
                      onTap: _pickAttachment,
                      behavior: HitTestBehavior.opaque,
                      child: SizedBox(
                        width: 36,
                        height: 46,
                        child: Center(child: Text('📎', style: Fonts.body(19, palette.muted, height: 1.0))),
                      ),
                    ),
                  ),
                  Expanded(
                    child: TextField(
                      controller: _input,
                      focusNode: _inputFocus,
                      minLines: 1,
                      maxLines: 5,
                      textDirection: rtl ? TextDirection.rtl : TextDirection.ltr,
                      textInputAction: TextInputAction.newline,
                      style: Fonts.body(15.5, palette.ink, height: 1.3),
                      decoration: InputDecoration(
                        hintText: 'Ask Qur’an…',
                        hintStyle: Fonts.body(15.5, palette.muted, height: 1.3),
                        border: InputBorder.none,
                        enabledBorder: InputBorder.none,
                        focusedBorder: InputBorder.none,
                        filled: false,
                        isDense: true,
                        contentPadding: const EdgeInsets.symmetric(horizontal: 7, vertical: 11),
                      ),
                    ),
                  ),
                  Semantics(
                    button: true,
                    label: 'Speak your question',
                    child: GestureDetector(
                      onTap: _controller.startListening,
                      behavior: HitTestBehavior.opaque,
                      child: SizedBox(
                        width: 38,
                        height: 46,
                        child: Center(
                          child: Text('🎤',
                              style: Fonts.body(21, _controller.listening ? palette.green : palette.muted, height: 1.0)),
                        ),
                      ),
                    ),
                  ),
                  CircleGlyph('↑', fill: palette.green, glyphColor: Colors.white, size: 46, semanticLabel: 'Send', onTap: _submit),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}
