import { useMemo } from "react";

export default function DecisionQualityReport({ events = [] }) {
  const stats = useMemo(() => {
    const by = {};
    events.forEach((e) => {
      const p = e.player || "—";
      const q = e.extra?.quality || "average";
      by[p] = by[p] || { player: p, good: 0, poor: 0, average: 0, total: 0 };
      by[p][q] = (by[p][q] || 0) + 1;
      by[p].total += 1;
    });
    return Object.values(by).sort((a, b) => b.total - a.total);
  }, [events]);

  return (
    <div style={{ padding: 24, background: "#fff", border: "1px solid rgba(0,0,0,0.1)" }}>
      <div className="label" style={{ opacity: 0.6, marginBottom: 16 }}>Decision Quality Report</div>

      {stats.length === 0 ? (
        <div style={{ fontSize: 13, opacity: 0.5, padding: "24px 0" }}>No decisions logged yet.</div>
      ) : (
        <table style={{ width: "100%", fontSize: 13, borderCollapse: "collapse" }}>
          <thead>
            <tr style={{ borderBottom: "1px solid rgba(0,0,0,0.1)", textAlign: "left" }}>
              {["Player", "Total", "Good", "Poor", "Quality"].map((h) => (
                <th key={h} className="label" style={{ padding: "8px 0", opacity: 0.6, fontWeight: 700 }}>{h}</th>
              ))}
            </tr>
          </thead>
          <tbody style={{ fontFamily: "JetBrains Mono, monospace" }}>
            {stats.map((s) => {
              const ratio = s.total ? Math.round((100 * s.good) / s.total) : 0;
              return (
                <tr key={s.player} style={{ borderBottom: "1px solid rgba(0,0,0,0.05)" }}>
                  <td className="font-display" style={{ padding: "8px 0", fontWeight: 700, textTransform: "uppercase" }}>{s.player}</td>
                  <td>{s.total}</td>
                  <td style={{ color: "#16a34a" }}>{s.good}</td>
                  <td style={{ color: "var(--uc-red)" }}>{s.poor}</td>
                  <td>
                    <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                      <div style={{ height: 6, width: 128, background: "rgba(0,0,0,0.1)", position: "relative" }}>
                        <div style={{ position: "absolute", inset: "0 auto 0 0", width: `${ratio}%`, background: "#22c55e" }} />
                      </div>
                      <span>{ratio}%</span>
                    </div>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      )}
    </div>
  );
}
