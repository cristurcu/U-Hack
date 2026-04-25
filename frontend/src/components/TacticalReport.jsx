import { useEffect, useState } from "react";
import { fetchTacticalReport, fetchInsight } from "../lib/api";
import { Sparkles, Loader2 } from "lucide-react";

const MOCK_REPORT = {
  summary: {
    formation: "4-2-3-1",
    score: { home: 1, away: 0 },
    xg_home: 1.8,
    xg_away: 0.4,
    avg_pressing: 49,
    passes: 312,
    pass_accuracy: 84,
    shots: 8,
    shots_on_target: 4,
  },
  phases: [
    { label: "0-15'",  pressing: 52, tempo: "Ridicat"  },
    { label: "15-45'", pressing: 45, tempo: "Mediu"    },
    { label: "45-60'", pressing: 55, tempo: "Ridicat"  },
    { label: "60-90'", pressing: 48, tempo: "Mediu"    },
  ],
  recommendations: [
    "Presați mai sus în primele 15 minute pentru a forța erori",
    "Bic are nevoie de odihnă — luați în calcul o substituție după min. 70",
    "Exploatați mai mult flancul drept — Oancea are spațiu să avanseze",
    "Thiam trebuie să caute șutul mai devreme în careu",
  ],
  key_moments: [
    { minute: 58, text: "GOL — Stoica finalizează din pasă lui Nistor (xG 0.62)" },
    { minute: 45, text: "Popa a ratat o oportunitate de șut cu valoare ridicată (xG 0.48)" },
    { minute: 32, text: "Breakdown pressing în mijlocul terenului — contraatac Hermannstadt" },
    { minute: 67, text: "Scădere de intensitate detectată la Bic — alertă oboseală declanșată" },
  ],
};

export default function TacticalReport({ dark = false }) {
  const [report, setReport] = useState(MOCK_REPORT);
  const [insight, setInsight] = useState(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    fetchTacticalReport().then(setReport).catch(() => {});
  }, []);

  const askInsight = async (topic) => {
    setLoading(true);
    try {
      const r = await fetchInsight(topic);
      setInsight(r);
    } finally {
      setLoading(false);
    }
  };

  if (!report) {
    return (
      <div style={{ padding: 24, fontSize: 13, opacity: 0.5, fontFamily: "JetBrains Mono, monospace" }}>
        Se încarcă raportul tactic…
      </div>
    );
  }

  const s = report.summary;

  return (
    <div style={{ display: "grid", gridTemplateColumns: "1fr 340px", gap: 16, alignItems: "start" }}>

      {/* LEFT — Recommendations + AI Insight */}
      <div style={{ display: "flex", flexDirection: "column", gap: 16 }}>

        {/* Recommendations */}
        <div style={{ padding: 24, background: "#fff", border: "1px solid rgba(0,0,0,0.1)" }}>
          <div className="label" style={{ opacity: 0.6, marginBottom: 16 }}>Recomandări · Vizualizare Antrenor</div>
          <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
            {(report.recommendations || []).map((r, i) => (
              <div key={i} style={{ display: "flex", alignItems: "flex-start", gap: 12, padding: "10px 14px", borderLeft: "3px solid var(--uc-red)", background: "rgba(0,0,0,0.015)" }}>
                <span className="font-mono" style={{ fontSize: 11, opacity: 0.4, flexShrink: 0, marginTop: 2 }}>{String(i + 1).padStart(2, "0")}</span>
                <span style={{ fontSize: 13, lineHeight: 1.5 }}>{r}</span>
              </div>
            ))}
          </div>
        </div>

        {/* AI Insight */}
        <div style={{ padding: 24, background: "#fff", border: "1px solid rgba(0,0,0,0.1)" }}>
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 16 }}>
            <div className="label" style={{ opacity: 0.6, display: "flex", alignItems: "center", gap: 6 }}>
              <Sparkles size={12} /> Insight Tactic AI
            </div>
            <div style={{ display: "flex", gap: 8 }}>
              {[{ key: "pressing", label: "pressing" }, { key: "fatigue", label: "oboseală" }, { key: "general", label: "general" }].map((t) => (
                <button
                  key={t.key}
                  onClick={() => askInsight(t.key)}
                  style={{
                    padding: "4px 12px",
                    fontSize: 11,
                    fontFamily: "JetBrains Mono, monospace",
                    textTransform: "uppercase",
                    letterSpacing: "0.1em",
                    border: "1px solid rgba(0,0,0,0.15)",
                    background: "none",
                    cursor: "pointer",
                  }}
                >
                  {t.label}
                </button>
              ))}
            </div>
          </div>
          {loading && (
            <div style={{ display: "flex", alignItems: "center", gap: 8, fontSize: 13, opacity: 0.7 }}>
              <Loader2 size={14} style={{ animation: "spin 1s linear infinite" }} /> Se analizează…
            </div>
          )}
          {insight && (
            <div style={{ fontSize: 13, lineHeight: 1.6, borderLeft: "2px solid var(--uc-red)", paddingLeft: 16, paddingTop: 8, paddingBottom: 8 }}>
              {insight.insight}
            </div>
          )}
          {!insight && !loading && (
            <div style={{ fontSize: 13, opacity: 0.5 }}>Alege un subiect pentru a genera comentariu analitic.</div>
          )}
        </div>
      </div>

      {/* RIGHT — Phase Breakdown */}
      <div style={{ padding: 24, background: "#fff", border: "1px solid rgba(0,0,0,0.1)" }}>
        <div className="label" style={{ opacity: 0.6, marginBottom: 16 }}>Faze de joc · {s.formation}</div>
        <div style={{ display: "flex", flexDirection: "column", gap: 12 }}>
          {(report.phases || []).map((ph, i) => (
            <div key={i} style={{ border: "1px solid rgba(0,0,0,0.08)", padding: "14px 16px" }}>
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "baseline", marginBottom: 8 }}>
                <div className="font-mono" style={{ fontSize: 11, opacity: 0.6 }}>{ph.label}</div>
                <div className="label" style={{ opacity: 0.5, fontSize: 10 }}>{ph.tempo}</div>
              </div>
              <div className="font-display" style={{ fontWeight: 900, fontSize: 36, lineHeight: 1 }}>
                {ph.pressing}<span style={{ fontSize: 16, opacity: 0.5 }}>%</span>
              </div>
              <div style={{ marginTop: 8, height: 4, background: "rgba(0,0,0,0.06)" }}>
                <div style={{ height: "100%", width: `${ph.pressing}%`, background: ph.pressing >= 50 ? "var(--uc-red)" : "#f59e0b", transition: "width 0.5s" }} />
              </div>
            </div>
          ))}
        </div>

        {/* Key moments */}
        <div style={{ marginTop: 20 }}>
          <div className="label" style={{ opacity: 0.6, marginBottom: 12 }}>Momente cheie</div>
          <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
            {(report.key_moments || []).map((km, i) => (
              <div key={i} style={{ display: "flex", gap: 10, fontSize: 12, alignItems: "flex-start" }}>
                <span className="font-mono" style={{ opacity: 0.4, flexShrink: 0 }}>{String(km.minute).padStart(2,"0")}'</span>
                <span style={{ opacity: 0.75, lineHeight: 1.4 }}>{km.text}</span>
              </div>
            ))}
          </div>
        </div>
      </div>

    </div>
  );
}

function BigStat({ label, value, sub, color = "#0a0a0a" }) {
  return (
    <div style={{ border: "1px solid rgba(0,0,0,0.1)", padding: 12 }}>
      <div className="label" style={{ opacity: 0.6 }}>{label}</div>
      <div className="font-display" style={{ fontWeight: 900, fontSize: 28, color }}>{value}</div>
      {sub && <div className="font-mono" style={{ fontSize: 10, opacity: 0.6 }}>{sub}</div>}
    </div>
  );
}
