import 'package:flutter/material.dart';

import '../data/prefs.dart';
import '../data/study_store.dart';
import '../logic/logic.dart';
import 'theme.dart';
import 'widgets.dart';

/// The hub sections of the Study screen (overview chips, collections, notes,
/// saved searches, reading plans, export) and their dialogs.
class StudySections {
  final BuildContext context;
  final Palette palette;
  final Dataset? dataset;
  final QuranMeta? meta;
  final StudyStore store;
  final Prefs prefs;
  final String Function(String key) referenceLabel;
  final Future<void> Function(int chapterId, String? verseKey) openReader;
  final void Function(String subject, String body) share;
  final void Function(String query) askSearch;
  final VoidCallback refresh;
  final void Function(String name) showCollection;

  const StudySections({
    required this.context,
    required this.palette,
    required this.dataset,
    required this.meta,
    required this.store,
    required this.prefs,
    required this.referenceLabel,
    required this.openReader,
    required this.share,
    required this.askSearch,
    required this.refresh,
    required this.showCollection,
  });

  // --------------------------------------------------------------- helpers
  static Widget card(Palette palette, Widget child) =>
      CardBox(margin: const EdgeInsets.only(top: 8), child: child);

  /// Gold-deep uppercase text action that reads correctly on the paper background.
  static Widget textAction(Palette palette, String label, VoidCallback onTap) => InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(8),
        child: Padding(
          padding: const EdgeInsets.all(8),
          child: Text(label.toUpperCase(), style: Fonts.body(11, palette.goldDeep, weight: FontWeight.bold, letterSpacing: 0.9)),
        ),
      );

  Widget _sectionHeader(String title, [List<Widget> actions = const []]) => Padding(
        padding: const EdgeInsets.only(top: 24),
        child: Row(children: [SectionLabel(title), const Spacer(), ...actions]),
      );

  Widget _emptyText(String message) => Padding(
        padding: const EdgeInsets.only(top: 6, bottom: 2),
        child: Text(message, style: Fonts.body(12, palette.muted)),
      );

  static int _chapterIdOf(String key) => int.tryParse(key.split(':').first) ?? 0;

  String _snippet(String text, int max) {
    final clean = text.trim().replaceAll(RegExp(r'\s+'), ' ');
    return clean.length > max ? '${clean.substring(0, max - 1)}…' : clean;
  }

  // -------------------------------------------------------------- overview
  Widget overview() => SingleChildScrollView(
        scrollDirection: Axis.horizontal,
        padding: const EdgeInsets.only(top: 14),
        child: Row(
          children: [
            for (final label in [
              '${store.bookmarkTotal()} bookmarks',
              '${store.noteCount()} notes',
              '${store.searches().length} searches',
              '${store.plans().length} plans',
            ])
              Padding(
                padding: const EdgeInsets.only(right: 8),
                child: Pill(label, textColor: palette.goldDeep, fill: palette.goldSoft, stroke: palette.line),
              ),
          ],
        ),
      );

  // ----------------------------------------------------------- collections
  List<Widget> collections() {
    final names = store.collections();
    return [
      _sectionHeader('Bookmark collections', [textAction(palette, 'New collection', _newCollectionDialog)]),
      card(
        palette,
        Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            for (var i = 0; i < names.length; i++) ...[
              if (i > 0) const Hairline(),
              _collectionRow(names[i]),
            ],
            if (names.isEmpty) _emptyText('No collections yet.'),
          ],
        ),
      ),
      Padding(
        padding: const EdgeInsets.only(top: 6),
        child: Text('Tap a collection to browse it · long-press to delete it (its bookmarks move to General).',
            style: Fonts.body(10, palette.muted)),
      ),
    ];
  }

  Widget _collectionRow(String name) {
    final count = store.bookmarkCount(name);
    return InkWell(
      onTap: () => showCollection(name),
      onLongPress: () => _confirmDeleteCollection(name),
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 5),
        child: Row(
          children: [
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(name, style: Fonts.body(14, palette.ink)),
                  Padding(
                    padding: const EdgeInsets.only(top: 2),
                    child: Text('$count${count == 1 ? ' saved verse' : ' saved verses'}', style: Fonts.body(10, palette.muted)),
                  ),
                ],
              ),
            ),
            Padding(
              padding: const EdgeInsets.only(left: 8, right: 4),
              child: Text('›', style: Fonts.body(18, palette.gold)),
            ),
          ],
        ),
      ),
    );
  }

  Future<void> _confirmDeleteCollection(String name) async {
    if (name.toLowerCase() == StudyData.defaultCollection.toLowerCase()) {
      toast(context, 'The General collection cannot be deleted.');
      return;
    }
    final count = store.bookmarkCount(name);
    final ok = await confirm(
        context,
        'Delete collection?',
        '"$name" will be removed. $count${count == 1 ? ' saved verse moves' : ' saved verses move'}'
            ' to General; verses and notes are not deleted.',
        positive: 'Delete',
        destructive: true);
    if (!ok || !context.mounted) return;
    store.removeCollection(name);
    refresh();
    toast(context, 'Collection deleted. Bookmarks moved to General.');
  }

  Future<void> _newCollectionDialog() async {
    final raw = await promptText(context, 'New collection', hint: 'Collection name', positive: 'Create');
    if (raw == null || !context.mounted) return;
    if (raw.trim().isEmpty) {
      toast(context, 'Enter a collection name');
      return;
    }
    final name = store.addCollection(StudyData.sanitizeCollectionName(raw));
    refresh();
    toast(context, 'Collection "$name" ready.');
  }

  // ----------------------------------------------------------------- notes
  List<Widget> notes() {
    final keys = store.noteKeys();
    final limit = keys.length < 50 ? keys.length : 50;
    return [
      _sectionHeader('Personal notes', [
        textAction(palette, 'CSV', () => share('Rafiq notes (CSV)', notesCsv())),
        textAction(palette, 'Text', () => share('Rafiq notes', notesText())),
      ]),
      card(
        palette,
        Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            for (var i = 0; i < limit; i++) ...[
              if (i > 0) const Hairline(),
              _noteRow(keys[i]),
            ],
            if (keys.isEmpty) _emptyText('No personal notes yet. Open a verse in the reader and add a note.'),
            if (keys.length > limit) _emptyText('Showing 50 of ${keys.length} notes. Export to read them all.'),
          ],
        ),
      ),
    ];
  }

  Widget _noteRow(String key) {
    final snippet = _snippet(store.note(key), 140);
    return InkWell(
      onTap: () => openReader(_chapterIdOf(key), key),
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 5),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(children: [Expanded(child: Label(referenceLabel(key))), Text('›', style: Fonts.body(16, palette.gold))]),
            if (snippet.isNotEmpty)
              Padding(padding: const EdgeInsets.only(top: 4), child: Text(snippet, style: Fonts.body(12, palette.muted))),
          ],
        ),
      ),
    );
  }

  // -------------------------------------------------------------- searches
  List<Widget> searches() {
    final list = store.searches();
    return [
      _sectionHeader('Saved searches', [if (list.isNotEmpty) textAction(palette, 'Clear', _confirmClearSearches)]),
      card(
        palette,
        Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            for (var i = 0; i < list.length; i++) ...[
              if (i > 0) const Hairline(),
              InkWell(
                onTap: () => askSearch(list[i]),
                child: Padding(
                  padding: const EdgeInsets.symmetric(vertical: 6),
                  child: Row(
                    children: [
                      Expanded(child: Text('“${list[i]}”', style: Fonts.body(13, palette.ink))),
                      Text('›', style: Fonts.body(16, palette.gold)),
                    ],
                  ),
                ),
              ),
            ],
            if (list.isEmpty) _emptyText('No saved searches yet. Searches you run in the assistant appear here.'),
          ],
        ),
      ),
    ];
  }

  Future<void> _confirmClearSearches() async {
    final ok = await confirm(context, 'Clear saved searches?',
        'This removes the saved search list from this device. Your reading, notes and bookmarks are not affected.',
        positive: 'Clear', destructive: true);
    if (!ok || !context.mounted) return;
    store.clearSearches();
    refresh();
    toast(context, 'Saved searches cleared.');
  }

  // ----------------------------------------------------------------- plans
  List<Widget> plans() {
    final list = store.plans();
    return [
      _sectionHeader('Reading plans', [textAction(palette, 'New plan', _newPlanDialog)]),
      for (final plan in list) _planCard(plan),
      if (list.isEmpty)
        card(palette, _emptyText('No reading plans yet. A plan tracks which surahs you have completed, at your own pace.')),
    ];
  }

  Widget _planCard(Plan plan) {
    final complete = plan.isComplete;
    final next = plan.nextChapter();
    return card(
      palette,
      Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(child: Text(plan.name, style: Fonts.display(15, palette.ink))),
              Pill('${plan.percent()}%', textColor: palette.goldDeep, fill: palette.goldSoft, stroke: palette.line),
            ],
          ),
          Padding(
            padding: const EdgeInsets.only(top: 4, bottom: 8),
            child: Text('${StudyData.presetTitle(plan.preset)}  ·  ${plan.days}-day goal', style: Fonts.body(11, palette.muted)),
          ),
          ClipRRect(
            borderRadius: BorderRadius.circular(3),
            child: LinearProgressIndicator(
                value: (plan.percent() / 100).clamp(0.0, 1.0),
                minHeight: 6,
                backgroundColor: palette.line,
                color: palette.gold),
          ),
          Padding(
            padding: const EdgeInsets.only(top: 5, bottom: 10),
            child: Text(plan.progressText(), style: Fonts.body(11, palette.muted)),
          ),
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: [
              RafiqButton('Continue', primary: true, onPressed: !complete && next > 0 ? () => openReader(next, null) : null),
              RafiqButton('Delete', textColor: palette.danger, onPressed: () => _confirmDeletePlan(plan)),
            ],
          ),
          if (complete)
            Padding(
              padding: const EdgeInsets.only(top: 8),
              child: Text('Plan complete. Nothing left to read in this plan.', style: Fonts.body(10, palette.green)),
            ),
        ],
      ),
    );
  }

  Future<void> _confirmDeletePlan(Plan plan) async {
    final ok = await confirm(context, 'Delete reading plan?',
        '"${plan.name}" is removed from this device. Quran text, bookmarks and notes are not affected.',
        positive: 'Delete', destructive: true);
    if (!ok || !context.mounted) return;
    store.deletePlan(plan.id);
    refresh();
    toast(context, 'Reading plan deleted.');
  }

  Future<void> _newPlanDialog() async {
    final created = await showDialog<Plan>(
      context: context,
      builder: (context) => _NewPlanDialog(store: store, palette: palette),
    );
    if (created == null || !context.mounted) return;
    refresh();
    toast(context, 'Reading plan "${created.name}" created.');
  }

  // ------------------------------------------------------ export & share
  List<Widget> exportSection() => [
        _sectionHeader('Export & share'),
        card(
          palette,
          Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('Plain-text exports you can send to any app on the device. Nothing is uploaded automatically.',
                  style: Fonts.body(12, palette.muted)),
              Padding(
                padding: const EdgeInsets.only(top: 10),
                child: Wrap(
                  spacing: 8,
                  runSpacing: 8,
                  children: [
                    RafiqButton('Bookmarks (CSV)', onPressed: () => share('Rafiq bookmarks (CSV)', bookmarksCsv())),
                    RafiqButton('References (text)', onPressed: () => share('Rafiq references', bookmarksText())),
                  ],
                ),
              ),
            ],
          ),
        ),
      ];

  String bookmarksCsv() {
    final out = StringBuffer()..writeln(StudyData.csvHeader(['Reference', 'Collection', 'Note']));
    for (final key in store.bookmarkedKeys(null)) {
      var collection = store.collectionOf(key);
      if (collection == null || collection.isEmpty) collection = StudyData.defaultCollection;
      out.writeln(StudyData.csv([referenceLabel(key), collection, store.note(key)]));
    }
    return out.toString();
  }

  String bookmarksText() {
    final keys = store.bookmarkedKeys(null);
    if (keys.isEmpty) return '';
    final out = StringBuffer('Bookmarked references — Rafiq Al-Quran\n\n');
    for (final key in keys) {
      out.writeln(referenceLabel(key));
    }
    out.write('\nExported from Rafiq Al-Quran\n');
    return out.toString();
  }

  String notesCsv() {
    final out = StringBuffer()..writeln(StudyData.csvHeader(['Reference', 'Collection', 'Note']));
    for (final key in store.noteKeys()) {
      out.writeln(StudyData.csv([referenceLabel(key), store.collectionOf(key) ?? '', store.note(key)]));
    }
    return out.toString();
  }

  String notesText() {
    final keys = store.noteKeys();
    if (keys.isEmpty) return '';
    final out = StringBuffer('Personal notes — Rafiq Al-Quran\n\n');
    for (final key in keys) {
      out.write('${referenceLabel(key)}\n${store.note(key)}\n\n');
    }
    out.write('Exported from Rafiq Al-Quran\n');
    return out.toString();
  }
}

/// "New reading plan": name, preset, days 1–365.
class _NewPlanDialog extends StatefulWidget {
  final StudyStore store;
  final Palette palette;

  const _NewPlanDialog({required this.store, required this.palette});

  @override
  State<_NewPlanDialog> createState() => _NewPlanDialogState();
}

class _NewPlanDialogState extends State<_NewPlanDialog> {
  static const List<String> _presetKeys = [StudyData.presetFull, StudyData.presetJuz30, StudyData.presetLast10];
  final TextEditingController _name = TextEditingController();
  final TextEditingController _days = TextEditingController();
  String _preset = StudyData.presetFull;
  String? _error;

  @override
  void dispose() {
    _name.dispose();
    _days.dispose();
    super.dispose();
  }

  void _create() {
    final rawDays = _days.text.trim();
    var dayCount = 30;
    if (rawDays.isNotEmpty) {
      final parsed = int.tryParse(rawDays);
      if (parsed == null) {
        setState(() => _error = 'Enter a number of days');
        return;
      }
      dayCount = parsed;
    }
    dayCount = dayCount.clamp(1, 365);
    final created = widget.store.createPlan(_name.text, _preset, dayCount);
    Navigator.pop(context, created);
  }

  @override
  Widget build(BuildContext context) {
    final palette = widget.palette;
    return AlertDialog(
      title: const Text('New reading plan'),
      content: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          RafiqField(controller: _name, hint: 'Plan name (optional)'),
          const SizedBox(height: 10),
          DropdownButtonFormField<String>(
            initialValue: _preset,
            items: [
              for (final key in _presetKeys)
                DropdownMenuItem(value: key, child: Text(StudyData.presetTitle(key), style: Fonts.body(14, palette.ink))),
            ],
            onChanged: (value) => setState(() => _preset = value ?? StudyData.presetFull),
          ),
          const SizedBox(height: 10),
          RafiqField(controller: _days, hint: 'Days (for example 30)', keyboardType: TextInputType.number),
          if (_error != null)
            Padding(
              padding: const EdgeInsets.only(top: 8),
              child: Align(alignment: Alignment.centerLeft, child: Text(_error!, style: Fonts.body(12, palette.danger))),
            ),
        ],
      ),
      actions: [
        TextButton(onPressed: () => Navigator.pop(context), child: const Text('Cancel')),
        TextButton(onPressed: _create, child: const Text('Create')),
      ],
    );
  }
}
