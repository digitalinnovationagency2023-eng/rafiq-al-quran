// The mandatory notice under every substantive answer, plus the safe fallback
// presentation. The notice is rendered verbatim and marked with its review status.
export const NoticeBlock = ({ notice, t }) => {
  if (!notice) return null;
  return (
    <details className="notice">
      <summary>
        <span className="notice-label">{t("noticeLabel")}</span>
        {notice.reviewStatus !== "verified" && <span className="badge badge-pending">translation pending review</span>}
      </summary>
      <p>{notice.text}</p>
    </details>
  );
};

export const FallbackBlock = ({ answer, t }) => (
  <div className="fallback" role="status">
    <span className="notice-label">{t("fallbackLabel")}</span>
    <p>{answer.message}</p>
    <ul className="limits">
      {(answer.limits || []).map((limit) => (
        <li key={limit}>{limit}</li>
      ))}
    </ul>
    <NoticeBlock notice={answer.notice} t={t} />
  </div>
);
