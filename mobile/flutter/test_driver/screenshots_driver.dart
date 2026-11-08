// Host side of integration_test/screenshots_test.dart. Writes every screenshot
// the test takes to $SCREENSHOT_DIR (default: screenshots/) as <name>.png.
import 'dart:io';

import 'package:integration_test/integration_test_driver_extended.dart';

Future<void> main() async {
  final directory = Directory(Platform.environment['SCREENSHOT_DIR'] ?? 'screenshots');
  await integrationDriver(
    onScreenshot: (String name, List<int> bytes, [Map<String, Object?>? args]) async {
      await directory.create(recursive: true);
      final file = File('${directory.path}${Platform.pathSeparator}$name.png');
      await file.writeAsBytes(bytes, flush: true);
      stdout.writeln('Saved ${file.path} (${bytes.length} bytes)');
      return true;
    },
    // The response data repeats every PNG as a JSON integer list; do not write it.
    responseDataCallback: null,
  );
}
