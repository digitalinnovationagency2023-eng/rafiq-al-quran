---
title: Privacy Policy — Rafiq Al-Quran
---

# Privacy Policy — Rafiq Al-Quran

**Last updated: 21 October 2026**
Publisher: Digital Innovative Agency FZ LLC · info@rqmbooks.com · +971 9 501 0254

## Data stored on your device
Your questions and the answers shown, bookmarks and bookmark collections, reading history, reading position,
saved notes, saved searches, reading plans, and preferences (language, text size, theme, translation visibility,
voice setting, reciter choice) are stored locally on your device. No account is required to use the app.

## Data that may leave your device
The core experience — reading the Quran, searching, receiving answers, using the study tools and the local
conversation replies — works offline from a dataset bundled inside the app. Data leaves the device only in these
cases:

- **Recitation**, when you press play: the app requests the verse audio file from Quran.com's public CDN over
  HTTPS (see "Recitation" below).
- **Optional AI assistance**, only when the app's operator has configured an AI service, you have switched it on
  and you have accepted the one-time consent notice (see "Optional AI assistance" below).
- **Feedback reports**, when you choose to send one: category, description, interface language, optional verse
  reference and optional contact detail. Conversation text and diagnostics are sent only if you explicitly tick
  the consent box.
- **The operator admin screen**, which is operator-only and read-only: it sends the operator token to the app's
  own API to read service status. It is not part of normal app use, and it does not send your bookmarks, notes,
  reading history or other study data.

## Optional AI assistance
AI assistance is **optional and off by default**. A fresh install works fully locally: Quran reading, search,
answers, conversation replies and the study tools need no account and no network. The app's operator can enable a
free-tier AI service (for example a free Google AI Studio / Gemini API key) on the app's own server; without that
configuration the app does not contact an AI provider at all.

When you switch AI assistance on (menu → **AI conversation**) and accept the one-time consent notice, the
following may be sent over HTTPS to the app's own server, which forwards it to the configured AI provider for that
single request:

- the question text you typed;
- if you attached an image, that image.

The AI service is used only to (a) write a short, neutral small-talk or off-topic reply, (b) suggest search words
for the app's own Quran search, and (c) read text from an attached screenshot (OCR). It **never** supplies Quran
text, translations, verse references or interpretation: every verse and translation you see is taken from the
validated dataset bundled with the app and rendered by the app itself.

The request is processed transiently. The app's server does not store your question text, the attachment or the
AI provider's answer, and it logs metadata only (such as whether a request succeeded). No account, name or
advertising identifier is attached. The configured AI provider is a third party and processes the request under
its own terms; free-tier services may have different data-use terms from paid tiers.

Free-tier limits: the app allows up to 30 AI-assisted requests per day on the device, and the service also applies
rate limits (currently about 20 requests per 10 minutes per client, with an optional monthly cap). When AI
assistance is unavailable, switched off or rate-limited, the app falls back to its local behaviour and tells you.

You can turn AI assistance off at any time in the menu. With it off, conversation replies are produced locally and
attached images cannot be read; typed questions, text and .docx attachments, Quran reading and search are
unaffected.

## Attachments
The composer's attach button lets you add a file to a question.

- **Text files** (`.txt`, `.md` and other plain-text files) are read on your device and used to search the bundled
  Quran dataset. They are not uploaded.
- **`.docx` documents** have their text extracted on your device (no upload) and are used in the same way.
- **Images** (PNG, JPEG, WebP) cannot be read on the device. If the optional AI service is enabled and you send a
  message with an image attached, the image is sent to the app's server (see "Optional AI assistance") so that
  visible text can be extracted from it (OCR). Without the AI service, images are not readable and the app says
  so. OCR returns only text from your own image; it never supplies Quran text.
- **PDFs and other file types** are not supported yet; the app shows a clear message.

Attachments are used for the current question only and are not stored by the app's server. Please do not attach
documents or screenshots containing sensitive personal information.

## Recitation
Verse recitation is now enabled. When you press play, the app streams that verse's audio from **Quran.com's public
CDN** over HTTPS. The reciters offered are Mishary Rashid Alafasy, Abdul Basit Abdus Samad (Murattal) and Muhammad
Siddiq Al-Minshawi (Murattal). The player shows the reciter name and the attribution **"Source: Quran.com"**. The
recitation rights review for this source is recorded (21 October 2026).

No audio is downloaded, cached or stored on your device, and the app does not modify or generate recitation. As
with any internet request, Quran.com's CDN receives the device's IP address and the requested verse file; no
account or study data is sent. Recitation needs an internet connection, and availability depends on Quran.com's
service. The Arabic text and translations shown alongside the player still come from the app's bundled dataset,
not from the audio service.

## Voice features
Speech input uses your device's speech recognition, which may process audio on your device or with your platform
provider. Spoken answers use your device's text-to-speech; only the translation and summary are spoken, and the
Arabic verse is not presented as recitation. The app does not record or upload audio. Voice can be disabled in the
app menu.

## Payments and donations
Billing, subscriptions, in-app purchases and donation collection are **not implemented or enabled in this
release**. No payment is requested, and the app collects no card details. If paid tools or donations are enabled
in a future release, this page will be updated first.

## What we never do
We do not sell data, we do not run advertising, we do not use advertising identifiers, and we never ask an AI
service to supply Quran text or interpretation. The app never reads RQM Books accounting, customer, invoice or
payroll data.

## Children
The app is suitable for general audiences and does not knowingly collect personal information from children.

## Retention and deletion
Locally stored data is removed when you clear the app's data or uninstall it. Feedback reports are kept for review
and can be deleted on request. In-app conversations can be deleted from the app. AI-assisted questions and
attachments are not stored by the app's server. Recitation audio is not stored on the device.

## Your choices
Use the app's menu to turn AI assistance off, disable voice, remove a saved operator token, and delete
conversations with "New conversation". Use the Privacy page to review this information. Contact
info@rqmbooks.com for data requests.

## Changes
We will update this page when the app changes; the date above shows the latest revision.
