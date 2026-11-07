import 'chapter.dart';
import 'provenance.dart';
import 'translation_meta.dart';

/// A single verified passage ready for display.
class Passage {
  final String verseKey;
  final String chapterNameEnglish;
  final String chapterNameArabic;
  final String arabic;
  final String translationText;
  final String translationLanguage;
  final String? translationTitle;
  final String? translationAuthor;
  final String translationDirection;
  final String translationLicenseStatus;
  final bool fallbackToEnglish;

  /// "Al-Baqarah 2:255".
  final String referenceLabel;
  final String dataset;
  final String datasetVersion;
  final String datasetSourceUrl;
  final String datasetChecksum;

  Passage(this.verseKey, Chapter chapter, this.arabic, this.translationText, this.translationLanguage,
      TranslationMeta? meta, this.fallbackToEnglish, Provenance provenance)
      : chapterNameEnglish = chapter.nameEnglish,
        chapterNameArabic = chapter.nameArabic,
        translationTitle = meta?.title,
        translationAuthor = meta?.author,
        translationDirection = meta == null ? 'ltr' : meta.direction,
        translationLicenseStatus = meta == null ? 'unverified' : meta.licenseStatus,
        referenceLabel = '${chapter.nameEnglish} $verseKey',
        dataset = provenance.dataset,
        datasetVersion = provenance.version,
        datasetSourceUrl = provenance.sourceUrl,
        datasetChecksum = provenance.checksum;
}
