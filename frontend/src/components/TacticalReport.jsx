import { useEffect, useState } from "react";
import { fetchTacticalReport, fetchInsight } from "../lib/api";
import { Sparkles, Loader2 } from "lucide-react";

export default function TacticalReport({ dark = false }) {
  const [report, setReport] = useState(null);
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
        Loading tactical report…
      </div>
    );
  }

  const s = report.summary;

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 20 }}>
      {/* Summary */}
      <div style={{ padding: 24, background: "#fff", border: "1px solid rgba(0,0,0,0.1)" }}>
        <div className="label" style={{ opacity: 0.6, marginBottom: 16 }}>Match Summary · {s.formation}</div>
        <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(140px, 1fr))", gap: 12 }}>
          <BigStat label="Score" value={`${s.score?.home}-${s.score?.away}`} />
          <BigStat label="xG (home)" value={(s.xg_home ?? 0).toFixed(2)} />
          <BigStat label="xG (away)" value={(s.xg_away ?? 0).toFixed(2)} color="var(--uc-red)" />
          <BigStat label="Pressing avg" value={`${s.avg_pressing ?? 0}%`} />
          <BigStat label="Passes" value={`${s.passes ?? 0}`} sub={`${s.pass_accuracy ?? 0}% acc`} />
          <BigStat label="Shots / OT" value={`${s.shots ?? 0} / ${s.shots_on_target ?? 0}`} />
        </div>
      </div>

      {/* Phases + Recommendations */}
      <div style={{ display: "grid", gridTemplateColumns: "2fr 1fr", gap: 16 }}>
        <div style={{ padding: 20, background: "#fff", border: "1px solid rgba(0,0,0,0.1)" }}>
          <div className="label" style={{ opacity: 0.6, marginBottom: 16 }}>Phase Breakdown</div>
          <div style={{ display: "grid", gridTemplateColumns: "repeat(4, 1fr)", gap: 12 }}>
            {(report.phases || []).map((ph, i) => (
              <div key={i} style={{ border: "1px solid rgba(0,0,0,0.1)", padding: 12 }}>
                <div className="font-mono" style={{ fontSize: 11, opacity: 0.6 }}>{ph.label}</div>
                <div className="font-display" style={{ fontWeight: 900, fontSize: 28 }}>
                  {ph.pressing}<span style={{ fontSize: 14, opacity: 0.6 }}>%</span>
                </div>
                <div className="label" style={{ opacity: 0.6, marginTop: 4 }}>{ph.tempo}</div>
              </div>
            ))}
          </div>
        </div>

        <div style={{ padding: 20, background: "#fff", border: "1px solid rgba(0,0,0,0.1)" }}>
          <div className="label" style={{ opacity: 0.6, marginBottom: 12 }}>Recommendations</div>
          <ul style={{ listStyle: "none", padding: 0, margin: 0, display: "flex", flexDirection: "column", gap: 8 }}>
            {(report.recommendations || []).map((r, i) => (
              <li key={i} style={{ display: "flex", alignItems: "flex-start", gap: 8, fontSize: 13 }}>
                <span style={{ width: 6, height: 6, background: "var(--uc-red)", display: "inline-block", marginTop: 5, flexShrink: 0 }} />
                {r}
              </li>
            ))}
          </ul>
        </div>
      </div>

      {/* AI Insight */}
      <div style={{ padding: 20, background: "#fff", border: "1px solid rgba(0,0,0,0.1)" }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 12 }}>
          <div className="label" style={{ opacity: 0.6, display: "flex", alignItems: "center", gap: 6 }}>
            <Sparkles size={12} /> AI Tactical Insight
          </div>
          <div style={{ display: "flex", gap: 8 }}>
            {["pressing", "fatigue", "general"].map((t) => (
              <button
                key={t}
                onClick={() => askInsight(t)}
                style={{
                  padding: "4px 12px",
                  fontSize: 11,
                  fontFamily: "JetBrains Mono, monospace",
                  textTransform: "uppercase",
                  letterSpacing: "0.1em",
                  border: "1px solid rgba(0,0,0,0.15)",
                  background: "none",
                  cursor: "pointer",
                  transition: "all 0.2s",
                }}
              >
                {t}
              </button>
            ))}
          </div>
        </div>
        {loading && (
          <div style={{ display: "flex", alignItems: "center", gap: 8, fontSize: 13, opacity: 0.7 }}>
            <Loader2 size={14} style={{ animation: "spin 1s linear infinite" }} /> Analyzing…
          </div>
        )}
        {insight && (
          <div style={{ fontSize: 13, lineHeight: 1.6, borderLeft: "2px solid var(--uc-red)", paddingLeft: 16, paddingTop: 8, paddingBottom: 8 }}>
            {insight.insight}
          </div>
        )}
        {!insight && !loading && (
          <div style={{ fontSize: 13, opacity: 0.5 }}>Pick a topic to generate analyst commentary.</div>
        )}
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
