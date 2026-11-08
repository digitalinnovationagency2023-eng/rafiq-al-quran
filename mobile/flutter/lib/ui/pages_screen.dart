import 'package:flutter/material.dart';
import 'package:share_plus/share_plus.dart';

import '../data/app_state.dart';
import '../logic/logic.dart';
import 'report_form.dart';
import 'study_screen.dart';
import 'theme.dart';
import 'widgets.dart';

/// Static information pages plus the feedback form. Port of `PagesActivity`
/// (which has no top bar; a small back arrow is added for iOS).
class PagesScreen extends StatefulWidget {
  /// `about`, `privacy`, `sources`, `support`, `tools` or `report`.
  final String page;

  const PagesScreen({super.key, required this.page});

  static void open(BuildContext context, String page) =>
      Navigator.push(context, MaterialPageRoute<void>(builder: (_) => PagesScreen(page: page)));

  @override
  State<PagesScreen> createState() => _PagesScreenState();
}

class _PagesScreenState extends State<PagesScreen> {
  Dataset? _dataset;

  @override
  void initState() {
    super.initState();
    if (widget.page == 'sources') {
      AppState.instance.assets.dataset().then((data) {
        if (mounted) setState(() => _dataset = data);
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final palette = Palette.of(context);
    final top = MediaQuery.paddingOf(context).top;
    return PageScaffold(
      child: ListView(
        padding: EdgeInsets.fromLTRB(16, top + 10, 16, 24),
        children: [
          Row(
            children: [
              InkWell(
                onTap: () => Navigator.pop(context),
                borderRadius: BorderRadius.circular(8),
                child: Padding(
                  padding: const EdgeInsets.fromLTRB(4, 6, 12, 6),
                  child: Text('← Back', style: Fonts.medium(14, palette.goldDeep, height: 1.2)),
                ),
              ),
            ],
          ),
          const SizedBox(height: 8),
          ..._content(palette),
        ],
      ),
    );
  }

  List<Widget> _content(Palette palette) {
    switch (widget.page) {
      case 'sources':
        return _sources(palette);
      case 'support':
        return _support(palette);
      case 'tools':
        return _tools(palette);
      case 'report':
        return [
          _heading(palette, 'Report an issue',
              'Tell us about a missing verse, a wrong reference, a translation display problem, an accessibility issue or a bug. Reports are reviewed before anything is treated as a correction.'),
          const ReportForm(),
        ];
      case 'privacy':
        return _privacy(palette);
      default:
        return _about(palette);
    }
  }

  Widget _heading(Palette palette, String title, String? lede) => Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(title, style: Fonts.body(24, palette.ink, weight: FontWeight.bold, height: 1.25)),
          if (lede != null)
            Padding(padding: const EdgeInsets.symmetric(vertical: 8), child: Text(lede, style: Fonts.body(13, palette.muted))),
        ],
      );

  /// Silver hairline card matching the rest of the app.
  Widget _section(Palette palette, String title, String body) => Container(
        margin: const EdgeInsets.only(top: 12),
        width: double.infinity,
        padding: const EdgeInsets.fromLTRB(14, 12, 14, 12),
        decoration: BoxDecoration(
            color: palette.paper, borderRadius: BorderRadius.circular(14), border: Border.all(color: palette.line)),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(title, style: Fonts.body(16, palette.ink, weight: FontWeight.bold, height: 1.3)),
            Padding(padding: const EdgeInsets.only(top: 6), child: SelectableText(body, style: Fonts.body(13, palette.muted))),
          ],
        ),
      );

  List<Widget> _about(Palette palette) => [
        _heading(palette, 'About Rafiq Al-Quran',
            'A free, Quran-focused conversational app. It helps users locate and read verses, with the Arabic text and an identified translation shown separately.'),
        _section(
            palette,
            'What it does',
            '• Retrieves verses from a validated local Quran dataset and identified translations.\n'
            '• Shows the exact Arabic text, the surah and verse reference, and the source metadata for each passage.\n'
            '• Lets users request more passages, open a specific reference, and listen to the translation read aloud.\n'
            '• Works offline: reading, search and answers use the bundled dataset.'),
        _section(
            palette,
            'What it does not do',
            "• It does not use Hadith, Tafsir, external religious websites or a model's own religious knowledge.\n"
            '• It does not write or paraphrase Quranic verses.\n'
            '• It does not issue fatwas, rulings or authoritative interpretation.\n'
            '• It does not claim its search results are exhaustive.\n'
            '• It does not present a translation as the Arabic text, or imitated speech as Quranic recitation.'),
        _section(palette, 'Limitations',
            'Retrieval is keyword and concept based over the bundled dataset. It may misunderstand a question, miss relevant verses, or rank a less relevant passage highly. When it cannot identify sufficiently relevant verses it says so explicitly and never concludes that the Quran does not address a topic. Please consult qualified scholars where religious interpretation is required.'),
        _section(palette, 'Contact',
            'Digital Innovative Agency FZ LLC\nEmail: info@rqmbooks.com\nPhone / WhatsApp: +971 9 501 0254\nWhatsApp chat: https://wa.me/97195010254'),
        _section(palette, 'Notices',
            'The English and Arabic notices are the approved wording. Other interface languages show the English notice and are marked as pending professional review.'),
      ];

  List<Widget> _privacy(Palette palette) => [
        _heading(palette, 'Privacy', 'What the app stores and what it never does.'),
        _section(
            palette,
            'Stored on this device',
            '• Your questions and the answers shown, so the conversation can be restored.\n'
            '• Interface preferences: language, text size and voice setting.\n'
            '• Study data you create: bookmark collections, personal notes, saved searches and reading plans.\n'
            '• Nothing else. There is no account and no advertising identifier.'),
        _section(
            palette,
            'Never done',
            '• No raw payment card data is stored; payment is hosted by the provider.\n'
            '• Conversation text is never attached to a report without your explicit consent.\n'
            '• Nothing is sent to WhatsApp or another third party automatically.\n'
            '• The app does not read RQM Books accounting, customer, invoice or payroll data.'),
        _section(palette, 'Voice features',
            "Voice input uses the device's speech recognition; audio may be processed by the platform provider. Spoken answers use the device's speech synthesis for the translation and summary only and are never presented as Quranic recitation. Both can be disabled in the menu."),
        _section(palette, 'Optional AI assistance',
            "AI assistance is off unless the operator enables a free AI service. When it is used, your question and any attached image are sent to this app's own server, which processes them with an AI provider only to understand the wording or to read the image. Nothing is stored and no account or identifier is attached; the feature can be turned off in the menu. The AI never supplies Quran text or interpretation — every verse comes from the bundled dataset. Without AI the app works locally."),
        _section(palette, 'Attachments',
            'Text files (.txt/.md) and .docx documents are read on this device and are never uploaded. Images are sent only to the optional AI service when it is enabled. PDF attachments are not supported yet. Do not attach sensitive personal documents.'),
        _section(palette, 'Recitation',
            "Verse recitation streams from Quran.com's public CDN over the internet, shows the reciter name and 'Source: Quran.com' attribution, and is not stored on the device. The recitation rights review for this source was recorded on 21 October 2026. Text-to-speech is used only for translations and summaries and is never presented as Quranic recitation."),
        _section(palette, 'Isolation', 'This app keeps its own database and never reads RQM Books accounting data.'),
      ];

  List<Widget> _sources(Palette palette) {
    final dataset = _dataset;
    final widgets = <Widget>[
      _heading(palette, 'Sources & licences',
          'The app serves only the Quranic text and translations bundled in its own dataset. No Hadith, Tafsir or model-generated religious content is used.'),
    ];
    if (dataset == null) {
      widgets.add(Padding(
          padding: const EdgeInsets.only(top: 12), child: Text('Loading the dataset…', style: Fonts.body(13, palette.muted))));
      return widgets;
    }
    final provenance = dataset.provenance();
    final translations = StringBuffer();
    for (final entry in dataset.translations().entries) {
      final meta = entry.value;
      translations.write('• ${entry.key.toUpperCase()}: ${meta.title}');
      if (meta.author.isNotEmpty) translations.write(' — ${meta.author}');
      translations.write('\n   Licence status: ${meta.licenseStatus}\n');
    }
    widgets.addAll([
      _section(
          palette,
          'Quran dataset',
          'Dataset: ${provenance.dataset}\n'
          'Version: ${provenance.version}\n'
          'Source: ${provenance.sourceUrl}\n'
          'Imported: ${provenance.importedAt}\n'
          'Verses: ${dataset.verseCount()} · Chapters: ${dataset.chapterCount()}\n'
          'Checksum SHA-256:\n${provenance.checksum}'),
      _section(palette, 'Translations', translations.toString()),
      _section(palette, 'Recitation',
          "Verse recitation is enabled and streams from Quran.com's public CDN over the internet, with the reciter name and “Source: Quran.com” attribution shown in the player. Audio is not stored on the device. The recitation rights review for this source was recorded by the publisher on 21 October 2026. Text-to-speech is used only for translations and summaries and is never presented as Quranic recitation."),
      _section(palette, 'Required notices',
          'English and Arabic: verified wording.\nOther languages: the English notice is shown and marked as pending review.'),
    ]);
    return widgets;
  }

  List<Widget> _support(Palette palette) => [
        _heading(palette, 'Support the app',
            'Rafiq Al-Quran is free to read, search and use. There is no account, no subscription and no payment required.'),
        _section(
            palette,
            'We do not need money',
            'We do not ask for donations, and we do not need any money from you.\n\n'
            'If anyone asks for money in our name, or tells you the app needs a payment, please do not pay them. '
            'That request is not from us.'),
        _section(
            palette,
            'How you can support us',
            "• Make du'a for us and for everyone who reads and learns from the Quran.\n"
            '• Use the app regularly, and use it with your family and friends.\n'
            '• Share it with someone who will benefit from it.\n'
            '• Send us your valuable feedback, corrections and suggestions.\n\n'
            'That is all we ask. Your prayers, your use and your feedback are worth more to us than any donation.'),
        Padding(
          padding: const EdgeInsets.only(top: 10),
          child: Wrap(
            spacing: 8,
            runSpacing: 8,
            children: [
              RafiqButton('Share the app', onPressed: _shareApp),
              RafiqButton('Send feedback', primary: true, onPressed: () => PagesScreen.open(context, 'report')),
            ],
          ),
        ),
        Padding(
          padding: const EdgeInsets.only(top: 16),
          child: Text('No purchase, subscription, donation or payment is requested in this app.',
              style: Fonts.body(11, palette.muted)),
        ),
      ];

  List<Widget> _tools(Palette palette) => [
        _heading(palette, 'Study tools are included free',
            'Every study tool is included at no cost. Reading the Quran, search and these tools never require an account or a payment.'),
        _section(
            palette,
            'What is included',
            '• Bookmark collections: group saved verses into named collections.\n'
            '• Personal notes: attach a private note to any verse.\n'
            '• Saved searches: keep the searches you run and repeat them later.\n'
            '• Reading plans: follow a plan and track your progress.\n'
            '• Cross-language comparison: read the Arabic and a translation side by side in the reader.\n'
            '• Export: share bookmarks and notes as CSV or plain text through the share sheet.'),
        _section(palette, 'Stored on this device',
            'Collections, notes, saved searches and reading plans are stored on this device only. Reading the Quran remains free and offline.'),
        Padding(
          padding: const EdgeInsets.only(top: 18),
          child: RafiqButton('Open study tools',
              primary: true,
              expand: true,
              onPressed: () => Navigator.push(context, MaterialPageRoute<void>(builder: (_) => const StudyScreen()))),
        ),
        Padding(
          padding: const EdgeInsets.only(top: 14),
          child: Text('No plans, subscriptions or add-ons are sold in this app.', style: Fonts.body(11, palette.muted)),
        ),
      ];

  void _shareApp() {
    SharePlus.instance.share(ShareParams(
        subject: 'Rafiq Al-Quran',
        text: 'Rafiq Al-Quran is a free app for reading and searching the Quran, with the Arabic text '
            'and identified translations. It works offline.'));
  }
}
