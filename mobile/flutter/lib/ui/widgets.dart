import 'package:flutter/material.dart';

import '../data/app_state.dart';
import 'theme.dart';

/// Short bottom message (Android `Toast`).
void toast(BuildContext context, String message) {
  if (!context.mounted) return;
  final messenger = ScaffoldMessenger.maybeOf(context);
  if (messenger == null) return;
  messenger.hideCurrentSnackBar();
  messenger.showSnackBar(SnackBar(content: Text(message), duration: const Duration(milliseconds: 2600)));
}

/// Root of every screen: the illustrated themed page background.
class PageScaffold extends StatelessWidget {
  final Widget child;
  final bool resizeToAvoidBottomInset;

  const PageScaffold({super.key, required this.child, this.resizeToAvoidBottomInset = true});

  @override
  Widget build(BuildContext context) {
    final palette = Palette.of(context);
    return Scaffold(
      backgroundColor: palette.paper,
      resizeToAvoidBottomInset: resizeToAvoidBottomInset,
      body: Container(
        decoration: BoxDecoration(
          color: palette.paper,
          image: DecorationImage(image: AssetImage(palette.pageBackground), fit: BoxFit.cover),
        ),
        child: child,
      ),
    );
  }
}

/// Standard header (`Ui.bar`): back arrow, serif title, small silver subtitle,
/// then the caller's actions right-aligned. Draws over the status bar.
class RafiqBar extends StatelessWidget {
  final String title;
  final String? subtitle;
  final VoidCallback? onBack;
  final List<Widget> actions;

  const RafiqBar({super.key, required this.title, this.subtitle, this.onBack, this.actions = const []});

  @override
  Widget build(BuildContext context) {
    final palette = Palette.of(context);
    final top = MediaQuery.paddingOf(context).top;
    return Container(
      decoration: const BoxDecoration(gradient: Palette.headerGradient),
      padding: EdgeInsets.fromLTRB(10, top + 12, 8, 12),
      child: Row(
        children: [
          if (onBack != null)
            Semantics(
              button: true,
              label: 'Back',
              child: GestureDetector(
                onTap: onBack,
                behavior: HitTestBehavior.opaque,
                child: Padding(
                  padding: const EdgeInsets.only(left: 8, right: 12, top: 4, bottom: 4),
                  child: Text('←', style: Fonts.body(20, palette.sun, height: 1.1)),
                ),
              ),
            ),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(title,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: Fonts.display(20, palette.onBar).copyWith(letterSpacing: -0.2, height: 1.15)),
                if (subtitle != null && subtitle!.isNotEmpty)
                  Padding(
                    padding: const EdgeInsets.only(top: 2),
                    child: Text(subtitle!,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: Fonts.body(11.5, palette.silver, letterSpacing: 0.35, height: 1.2)),
                  ),
              ],
            ),
          ),
          ...actions,
        ],
      ),
    );
  }
}

/// Thin multicolour rule under the header (`Ui.goldRule`).
class GoldRule extends StatelessWidget {
  const GoldRule({super.key});

  @override
  Widget build(BuildContext context) =>
      Container(height: 3, decoration: BoxDecoration(gradient: Palette.of(context).ruleGradient));
}

/// Moon/sun control that flips the light/dark theme (`Ui.themeToggle`).
class ThemeToggle extends StatelessWidget {
  const ThemeToggle({super.key});

  @override
  Widget build(BuildContext context) {
    final palette = Palette.of(context);
    return Semantics(
      button: true,
      label: palette.dark ? 'Switch to light theme' : 'Switch to dark theme',
      child: GestureDetector(
        onTap: AppState.instance.toggleTheme,
        behavior: HitTestBehavior.opaque,
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 11, vertical: 4),
          child: Text(palette.dark ? '☀' : '☾', style: Fonts.body(26, palette.sun, height: 1.1)),
        ),
      ),
    );
  }
}

/// Small gold text action used in header bars and cards (`Ui.action`).
class GoldAction extends StatelessWidget {
  final String label;
  final VoidCallback? onTap;
  final double size;
  final String? semanticLabel;

  const GoldAction(this.label, {super.key, this.onTap, this.size = 14, this.semanticLabel});

  @override
  Widget build(BuildContext context) {
    final palette = Palette.of(context);
    return Semantics(
      button: true,
      label: semanticLabel,
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(8),
        child: Padding(
          padding: const EdgeInsets.all(10),
          child: Text(label, style: Fonts.medium(size, palette.goldDeep, letterSpacing: 0.14, height: 1.2)),
        ),
      ),
    );
  }
}

/// Plain green text action used under answers (`Ui.textAction`).
class TextAction extends StatelessWidget {
  final String label;
  final VoidCallback? onTap;

  const TextAction(this.label, {super.key, this.onTap});

  @override
  Widget build(BuildContext context) {
    final palette = Palette.of(context);
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(8),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 9),
        child: Text(label, style: Fonts.medium(13.5, palette.green, height: 1.2)),
      ),
    );
  }
}

/// Round glyph button (`Ui.circle`).
class CircleGlyph extends StatelessWidget {
  final String glyph;
  final Color fill;
  final Color glyphColor;
  final double size;
  final VoidCallback? onTap;
  final String? semanticLabel;
  final double? fontSize;

  const CircleGlyph(this.glyph,
      {super.key,
      required this.fill,
      required this.glyphColor,
      this.size = 46,
      this.onTap,
      this.semanticLabel,
      this.fontSize});

  @override
  Widget build(BuildContext context) {
    return Semantics(
      button: onTap != null,
      label: semanticLabel,
      child: GestureDetector(
        onTap: onTap,
        child: Container(
          width: size,
          height: size,
          alignment: Alignment.center,
          decoration: BoxDecoration(color: fill, shape: BoxShape.circle),
          child: Text(glyph, style: Fonts.body(fontSize ?? 20, glyphColor, weight: FontWeight.bold, height: 1.0)),
        ),
      ),
    );
  }
}

/// Silver hairline card with a card fill (`Ui.cardBox`).
class CardBox extends StatelessWidget {
  final Widget child;
  final EdgeInsetsGeometry padding;
  final EdgeInsetsGeometry? margin;
  final Color? color;
  final Color? border;
  final double radius;
  final VoidCallback? onTap;
  final VoidCallback? onLongPress;

  const CardBox({
    super.key,
    required this.child,
    this.padding = const EdgeInsets.fromLTRB(20, 18, 20, 18),
    this.margin,
    this.color,
    this.border,
    this.radius = 22,
    this.onTap,
    this.onLongPress,
  });

  @override
  Widget build(BuildContext context) {
    final palette = Palette.of(context);
    final box = Material(
      color: color ?? palette.card,
      elevation: 1.5,
      shadowColor: Colors.black26,
      borderRadius: BorderRadius.circular(radius),
      child: InkWell(
        onTap: onTap,
        onLongPress: onLongPress,
        borderRadius: BorderRadius.circular(radius),
        child: Container(
          width: double.infinity,
          padding: padding,
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(radius),
            border: Border.all(color: border ?? palette.line),
          ),
          child: child,
        ),
      ),
    );
    return margin == null ? box : Padding(padding: margin!, child: box);
  }
}

/// Small pill used for statuses and flags (`Ui.chip`).
class Pill extends StatelessWidget {
  final String text;
  final Color textColor;
  final Color fill;
  final Color? stroke;

  const Pill(this.text, {super.key, required this.textColor, required this.fill, this.stroke});

  @override
  Widget build(BuildContext context) => Container(
        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
        decoration: BoxDecoration(
          color: fill,
          borderRadius: BorderRadius.circular(20),
          border: stroke == null ? null : Border.all(color: stroke!),
        ),
        child: Text(text, style: Fonts.medium(11, textColor, height: 1.2)),
      );
}

/// Primary = green with white text; secondary = card with ink text (`Ui.button`).
class RafiqButton extends StatelessWidget {
  final String label;
  final bool primary;
  final VoidCallback? onPressed;
  final Color? textColor;
  final bool expand;

  const RafiqButton(this.label, {super.key, this.primary = false, this.onPressed, this.textColor, this.expand = false});

  @override
  Widget build(BuildContext context) {
    final palette = Palette.of(context);
    final enabled = onPressed != null;
    final button = Material(
      color: primary ? palette.green.withValues(alpha: enabled ? 1 : 0.5) : palette.card,
      borderRadius: BorderRadius.circular(24),
      child: InkWell(
        onTap: onPressed,
        borderRadius: BorderRadius.circular(24),
        child: Container(
          constraints: const BoxConstraints(minHeight: 54),
          padding: const EdgeInsets.symmetric(horizontal: 22, vertical: 12),
          alignment: Alignment.center,
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(24),
            border: primary ? null : Border.all(color: palette.line),
          ),
          child: Text(label,
              textAlign: TextAlign.center,
              style: Fonts.medium(16, textColor ?? (primary ? Colors.white : palette.ink.withValues(alpha: enabled ? 1 : 0.5)),
                  letterSpacing: 0.16, height: 1.2)),
        ),
      ),
    );
    return expand ? SizedBox(width: double.infinity, child: button) : button;
  }
}

/// Hairline divider with the Android 10dp margins (`Ui.divider`).
class Hairline extends StatelessWidget {
  final double margin;

  const Hairline({super.key, this.margin = 10});

  @override
  Widget build(BuildContext context) =>
      Container(height: 1, color: Palette.of(context).line, margin: EdgeInsets.symmetric(vertical: margin));
}

/// `Ui.label`: small letter-spaced muted label.
class Label extends StatelessWidget {
  final String text;
  final Color? color;

  const Label(this.text, {super.key, this.color});

  @override
  Widget build(BuildContext context) => Text(text, style: Fonts.label(color ?? Palette.of(context).muted));
}

/// `Ui.sectionLabel`.
class SectionLabel extends StatelessWidget {
  final String text;

  const SectionLabel(this.text, {super.key});

  @override
  Widget build(BuildContext context) => Text(text, style: Fonts.section(Palette.of(context).ink));
}

/// Right-aligned Amiri Arabic text.
class QuranText extends StatelessWidget {
  final String text;
  final double size;
  final Color color;
  final double height;
  final TextAlign align;

  const QuranText(this.text,
      {super.key, required this.size, required this.color, this.height = 1.55, this.align = TextAlign.right});

  @override
  Widget build(BuildContext context) => Text(text,
      textDirection: TextDirection.rtl, textAlign: align, style: Fonts.quran(size, color, height: height));
}

/// A single-line text input styled like the Android search field.
class SearchField extends StatelessWidget {
  final TextEditingController controller;
  final String hint;
  final ValueChanged<String>? onChanged;

  const SearchField({super.key, required this.controller, required this.hint, this.onChanged});

  @override
  Widget build(BuildContext context) {
    final palette = Palette.of(context);
    return SizedBox(
      height: 46,
      child: TextField(
        controller: controller,
        onChanged: onChanged,
        style: Fonts.body(14, palette.ink, height: 1.2),
        decoration: InputDecoration(
          hintText: hint,
          isDense: true,
          contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 13),
        ),
      ),
    );
  }
}

/// Styled multi-line text field for dialogs and forms.
class RafiqField extends StatelessWidget {
  final TextEditingController controller;
  final String hint;
  final int minLines;
  final int maxLines;
  final TextInputType? keyboardType;
  final bool obscure;

  const RafiqField({
    super.key,
    required this.controller,
    required this.hint,
    this.minLines = 1,
    this.maxLines = 1,
    this.keyboardType,
    this.obscure = false,
  });

  @override
  Widget build(BuildContext context) {
    final palette = Palette.of(context);
    return TextField(
      controller: controller,
      minLines: obscure ? 1 : minLines,
      maxLines: obscure ? 1 : maxLines,
      obscureText: obscure,
      keyboardType: keyboardType,
      style: Fonts.body(15, palette.ink, height: 1.3),
      decoration: InputDecoration(hintText: hint),
    );
  }
}

/// `AlertDialog` with the app's wording; returns true on the positive button.
Future<bool> confirm(BuildContext context, String title, String message,
    {String positive = 'OK', String negative = 'Cancel', bool destructive = false}) async {
  final palette = Palette.of(context);
  final result = await showDialog<bool>(
    context: context,
    builder: (context) => AlertDialog(
      title: Text(title),
      content: Text(message),
      actions: [
        TextButton(onPressed: () => Navigator.pop(context, false), child: Text(negative)),
        TextButton(
          onPressed: () => Navigator.pop(context, true),
          child: Text(positive, style: TextStyle(color: destructive ? palette.danger : palette.green)),
        ),
      ],
    ),
  );
  return result == true;
}

/// Simple scrollable message dialog with a Close button.
Future<void> showMessage(BuildContext context, String title, String message, {String close = 'Close'}) =>
    showDialog<void>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(title),
        content: SingleChildScrollView(child: Text(message)),
        actions: [TextButton(onPressed: () => Navigator.pop(context), child: Text(close))],
      ),
    );

/// Single-choice list dialog; returns the chosen index or null.
Future<int?> chooseOne(BuildContext context, String title, List<String> labels, {int? selected}) =>
    showDialog<int>(
      context: context,
      builder: (context) {
        final palette = Palette.of(context);
        return SimpleDialog(
          title: Text(title),
          children: [
            for (var i = 0; i < labels.length; i++)
              SimpleDialogOption(
                onPressed: () => Navigator.pop(context, i),
                child: Row(
                  children: [
                    if (selected != null)
                      Padding(
                        padding: const EdgeInsets.only(right: 10),
                        child: Icon(i == selected ? Icons.radio_button_checked : Icons.radio_button_off,
                            size: 20, color: i == selected ? palette.green : palette.muted),
                      ),
                    Expanded(child: Text(labels[i], style: Fonts.body(15, palette.ink, height: 1.3))),
                  ],
                ),
              ),
            Align(
              alignment: Alignment.centerRight,
              child: TextButton(onPressed: () => Navigator.pop(context), child: const Text('Cancel')),
            ),
          ],
        );
      },
    );

/// Text prompt dialog; returns the entered text or null when cancelled.
Future<String?> promptText(BuildContext context, String title,
    {String? message, String hint = '', String initial = '', String positive = 'Save', bool multiline = false,
    String? neutral, TextInputType? keyboardType}) async {
  final controller = TextEditingController(text: initial);
  final palette = Palette.of(context);
  final result = await showDialog<String>(
    context: context,
    builder: (context) => AlertDialog(
      title: Text(title),
      content: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          if (message != null)
            Padding(padding: const EdgeInsets.only(bottom: 10), child: Text(message, style: Fonts.body(13, palette.muted))),
          RafiqField(
              controller: controller,
              hint: hint,
              minLines: multiline ? 3 : 1,
              maxLines: multiline ? 8 : 1,
              keyboardType: keyboardType),
        ],
      ),
      actions: [
        if (neutral != null)
          TextButton(onPressed: () => Navigator.pop(context, '\u0000$neutral'), child: Text(neutral)),
        TextButton(onPressed: () => Navigator.pop(context), child: const Text('Cancel')),
        TextButton(onPressed: () => Navigator.pop(context, controller.text), child: Text(positive)),
      ],
    ),
  );
  return result;
}
