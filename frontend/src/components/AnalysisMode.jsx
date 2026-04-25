import { useState } from "react";
import TacticalReport from "./TacticalReport";
import Heatmap from "./Heatmap";
import DecisionQualityReport from "./DecisionQualityReport";
import XGComparison from "./XGComparison";
import PassingNetwork from "./PassingNetwork";

const TABS = [
  { id: "tactical", label: "Tactical Intelligence" },
  { id: "heatmaps", label: "Heatmaps" },
  { id: "decision", label: "Decision Quality" },
  { id: "xg", label: "xG vs Goals" },
  { id: "network", label: "Passing Network" },
];

export default function AnalysisMode({ liveState }) {
  const [tab, setTab] = useState("tactical");

  return (
    <div style={{ padding: "32px 40px", maxWidth: 1500, margin: "0 auto" }}>
      <div style={{ marginBottom: 24 }}>
        <h1 className="font-display" style={{ fontWeight: 900, fontSize: 48, textTransform: "uppercase", margin: 0, letterSpacing: "-1px" }}>
          Post-Match Analysis
        </h1>
        <p className="font-mono" style={{ fontSize: 11, opacity: 0.5, marginTop: 4 }}>
          Full tactical intelligence · {liveState?.home || "U CLUJ"} vs {liveState?.away || "—"} · {liveState?.minute ?? 0}'
        </p>
      </div>

      {/* Tabs */}
      <div style={{ display: "flex", gap: 2, marginBottom: 24, borderBottom: "1px solid rgba(0,0,0,0.1)" }}>
        {TABS.map((t) => (
          <button
            key={t.id}
            onClick={() => setTab(t.id)}
            style={{
              padding: "12px 16px",
              fontFamily: "Barlow Condensed, sans-serif",
              fontWeight: 700,
              fontSize: 13,
              textTransform: "uppercase",
              letterSpacing: "0.1em",
              border: "none",
              borderBottom: tab === t.id ? "2px solid var(--uc-red)" : "2px solid transparent",
              background: "none",
              color: tab === t.id ? "#0a0a0a" : "rgba(0,0,0,0.4)",
              cursor: "pointer",
              transition: "all 0.2s",
              marginBottom: -1,
            }}
          >
            {t.label}
          </button>
        ))}
      </div>

      {/* Content */}
      <div className="fade-up">
        {tab === "tactical"  && <TacticalReport dark={false} />}
        {tab === "heatmaps"  && <Heatmap dark={false} />}
        {tab === "decision"  && <DecisionQualityReport events={liveState?.decision_events || []} />}
        {tab === "xg"        && <XGComparison liveState={liveState} />}
        {tab === "network"   && <PassingNetwork dark={false} pollMs={6000} height="560px" />}
      </div>
    </div>
  );
}
