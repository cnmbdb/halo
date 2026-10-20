// Absolute dates remain readable when JavaScript is unavailable.
(() => {
  const now = Date.now();
  const units = [[31536000000, "年"], [2592000000, "个月"], [86400000, "天"], [3600000, "小时"], [60000, "分钟"]];
  for (const time of document.querySelectorAll("#home-post-ranking time[data-relative-time]")) {
    const timestamp = Date.parse(time.dateTime);
    if (!Number.isFinite(timestamp)) continue;
    const elapsed = Math.max(0, now - timestamp);
    time.title = time.textContent;
    const unit = units.find(([duration]) => elapsed >= duration);
    time.textContent = unit ? `${Math.floor(elapsed / unit[0])} ${unit[1]}前` : "刚刚";
  }
})();
