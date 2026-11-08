// Generates the iOS AppIcon.appiconset from play-assets/play-icon-1024-master.png.
//
//   cd mobile/flutter && dart run tool/make_app_icons.dart
//
// Every size listed in ios/Runner/Assets.xcassets/AppIcon.appiconset/Contents.json
// is written as an opaque PNG (iOS icons must not have an alpha channel).
import 'dart:convert';
import 'dart:io';

import 'package:image/image.dart' as img;

void main(List<String> args) {
  final flutterRoot = File(Platform.script.toFilePath()).parent.parent.path;
  final source = args.isNotEmpty ? args.first : '$flutterRoot/../../play-assets/play-icon-1024-master.png';
  final setDir = Directory('$flutterRoot/ios/Runner/Assets.xcassets/AppIcon.appiconset');
  final contents = File('${setDir.path}/Contents.json');
  if (!contents.existsSync()) {
    stderr.writeln('Missing ${contents.path}');
    exit(1);
  }
  final master = img.decodePng(File(source).readAsBytesSync());
  if (master == null) {
    stderr.writeln('Could not decode $source');
    exit(1);
  }
  // Flatten onto the splash background so transparent corners never show.
  final flat = img.Image(width: master.width, height: master.height, numChannels: 3);
  img.fill(flat, color: img.ColorRgb8(0x08, 0x18, 0x24));
  img.compositeImage(flat, master);

  final manifest = jsonDecode(contents.readAsStringSync()) as Map<String, dynamic>;
  final images = (manifest['images'] as List).cast<Map<String, dynamic>>();
  final written = <String>{};
  for (final entry in images) {
    final size = double.parse((entry['size'] as String).split('x').first);
    final scale = int.parse((entry['scale'] as String).replaceAll('x', ''));
    final pixels = (size * scale).round();
    final filename = entry['filename'] as String;
    if (!written.add(filename)) continue;
    final resized = img.copyResize(flat, width: pixels, height: pixels, interpolation: img.Interpolation.cubic);
    File('${setDir.path}/$filename').writeAsBytesSync(img.encodePng(resized));
    stdout.writeln('$filename  ${pixels}x$pixels');
  }
  stdout.writeln('Wrote ${written.length} icons into ${setDir.path}');
}
