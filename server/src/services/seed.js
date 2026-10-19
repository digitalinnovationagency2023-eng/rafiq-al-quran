// Seed rows that are safe to create on first start. Recitation metadata is
// seeded as a *candidate* only: rights_status stays "unverified" and enabled 0,
// so no audio is offered until an operator records the recording rights.
export const seedReciters = (db) => {
  const insert = db.prepare(
    `INSERT INTO audio_reciters (key, name, style, provider, audio_url_template, rights_status, enabled, notes)
     VALUES (?, ?, ?, ?, ?, 'unverified', 0, ?)
     ON CONFLICT(key) DO NOTHING`
  );
  const candidates = [
    {
      key: "abdul-baset-murattal",
      name: "Abdul Basit Abdus Samad",
      style: "Murattal",
      provider: "quran.com",
      template: "https://verses.quran.com/{verseKey}.mp3",
      notes:
        "Candidate source. Do not enable until the recording's usage terms have been verified and documented.",
    },
    {
      key: "maher-al-muaiqly",
      name: "Maher Al-Muaiqly",
      style: "Murattal",
      provider: "quran.com",
      template: "https://verses.quran.com/{verseKey}.mp3",
      notes:
        "Candidate source. Do not enable until the recording's usage terms have been verified and documented.",
    },
    {
      key: "abdul-rahman-al-sudais",
      name: "Abdul Rahman Al-Sudais",
      style: "Murattal",
      provider: "quran.com",
      template: "https://verses.quran.com/{verseKey}.mp3",
      notes:
        "Candidate source. Do not enable until the recording's usage terms have been verified and documented.",
    },
  ];
  for (const candidate of candidates) {
    insert.run(candidate.key, candidate.name, candidate.style, candidate.provider, candidate.template, candidate.notes);
  }
};
