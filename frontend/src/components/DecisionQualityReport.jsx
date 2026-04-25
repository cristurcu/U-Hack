import { useMemo } from "react";

const MOCK_EVENTS = [
  { player: "D. Popa",    extra: { quality: "poor"    }, minute: 45, text: "Trebuia să șuteze — a ales să paseze (pierdut xG 0.48)" },
  { player: "D. Popa",    extra: { quality: "good"    }, minute: 12, text: "Pasă în adâncime perfectă la Thiam" },
  { player: "D. Popa",    extra: { quality: "poor"    }, minute: 67, text: "Pierdut posesia sub presiune" },
  { player: "I. Stoica",  extra: { quality: "good"    }, minute: 58, text: "Gol — decizie corectă de a șuta" },
  { player: "I. Stoica",  extra: { quality: "good"    }, minute: 34, text: "Pasă inteligentă bate pressingul" },
  { player: "I. Stoica",  extra: { quality: "average" }, minute: 71, text: "Joc de menținere — decizie neutră" },
  { player: "O. Bic",     extra: { quality: "good"    }, minute: 22, text: "Intercepție — declanșator pressing corect" },
  { player: "O. Bic",     extra: { quality: "poor"    }, minute: 55, text: "Duel pierdut — moment greșit pentru a angaja" },
  { player: "O. Bic",     extra: { quality: "average" }, minute: 40, text: "Pasă sigură înapoi" },
  { player: "D. Nistor",  extra: { quality: "good"    }, minute: 18, text: "Pasă care sparge linia la Popa" },
  { player: "D. Nistor",  extra: { quality: "poor"    }, minute: 63, text: "Conducere riscantă — minge pierdută în propria jumătate" },
  { player: "M. Thiam",   extra: { quality: "good"    }, minute: 30, text: "Șut pe poartă — decizie corectă" },
  { player: "M. Thiam",   extra: { quality: "poor"    }, minute: 48, text: "A ținut mingea prea mult — oportunitate pierdută" },
  { player: "A. Chipciu", extra: { quality: "good"    }, minute: 25, text: "Alergare suprapusă creează spațiu" },
  { player: "A. Chipciu", extra: { quality: "average" }, minute: 60, text: "Centrare sigură — previzibilă" },
];

export default function DecisionQualityReport({ events = MOCK_EVENTS }) {
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

  const recentEvents = [...events].reverse().slice(0, 6);

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 16 }}>

      {/* Player table */}
      <div style={{ padding: 24, background: "#fff", border: "1px solid rgba(0,0,0,0.1)" }}>
        <div className="label" style={{ opacity: 0.6, marginBottom: 16 }}>Calitatea Deciziilor · Per Jucător</div>
        <table style={{ width: "100%", fontSize: 13, borderCollapse: "collapse" }}>
          <thead>
            <tr style={{ borderBottom: "1px solid rgba(0,0,0,0.1)", textAlign: "left" }}>
              {["Jucător", "Total", "Bune", "Slabe", "Scor calitate"].map((h) => (
                <th key={h} className="label" style={{ padding: "8px 0", opacity: 0.6, fontWeight: 700 }}>{h}</th>
              ))}
            </tr>
          </thead>
          <tbody style={{ fontFamily: "JetBrains Mono, monospace" }}>
            {stats.map((s) => {
              const ratio = s.total ? Math.round((100 * s.good) / s.total) : 0;
              const color = ratio >= 60 ? "#16a34a" : ratio >= 40 ? "#f59e0b" : "#E30613";
              return (
                <tr key={s.player} style={{ borderBottom: "1px solid rgba(0,0,0,0.05)" }}>
                  <td className="font-display" style={{ padding: "10px 0", fontWeight: 700, textTransform: "uppercase" }}>{s.player}</td>
                  <td>{s.total}</td>
                  <td style={{ color: "#16a34a", fontWeight: 700 }}>{s.good}</td>
                  <td style={{ color: "var(--uc-red)", fontWeight: 700 }}>{s.poor}</td>
                  <td>
                    <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                      <div style={{ height: 6, width: 120, background: "rgba(0,0,0,0.08)", position: "relative" }}>
                        <div style={{ position: "absolute", inset: "0 auto 0 0", width: `${ratio}%`, background: color, transition: "width 0.5s" }} />
                      </div>
                      <span style={{ color, fontWeight: 700 }}>{ratio}%</span>
                    </div>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>

      {/* Recent decisions feed */}
      <div style={{ padding: 24, background: "#fff", border: "1px solid rgba(0,0,0,0.1)" }}>
        <div className="label" style={{ opacity: 0.6, marginBottom: 16 }}>Decizii cheie · Cronologia meciului</div>
        <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
          {recentEvents.map((e, i) => {
            const q = e.extra?.quality;
            const borderLeft = q === "good" ? "3px solid #22c55e" : q === "poor" ? "3px solid var(--uc-red)" : "3px solid #f59e0b";
            const badge = q === "good" ? { bg: "#dcfce7", color: "#16a34a", label: "BUNĂ" }
                        : q === "poor" ? { bg: "#fee2e2", color: "#E30613", label: "SLABĂ" }
                        : { bg: "#fef9c3", color: "#b45309", label: "MED" };
            return (
              <div key={i} style={{ display: "flex", alignItems: "flex-start", gap: 12, padding: "10px 14px", borderLeft, background: "rgba(0,0,0,0.015)" }}>
                <span className="font-mono" style={{ fontSize: 11, opacity: 0.5, flexShrink: 0, marginTop: 2 }}>
                  {String(e.minute).padStart(2, "0")}'
                </span>
                <div style={{ flex: 1 }}>
                  <div className="font-display" style={{ fontWeight: 700, fontSize: 13, textTransform: "uppercase" }}>{e.player}</div>
                  <div style={{ fontSize: 12, opacity: 0.7, marginTop: 2 }}>{e.text}</div>
                </div>
                <span style={{ fontSize: 10, fontFamily: "JetBrains Mono, monospace", fontWeight: 700, padding: "2px 8px", background: badge.bg, color: badge.color, flexShrink: 0 }}>
                  {badge.label}
                </span>
              </div>
            );
          })}
        </div>
      </div>

    </div>
  );
}
