import { useState } from "react";

// ── Helpers ──────────────────────────────────────────────────────────────────
const SS = (id) => `https://img.sofascore.com/api/v1/player/${id}/image`;

const CLUSTER_META = {
  "High Volume":    { bg: "#dbeafe", color: "#1d4ed8", border: "#93c5fd" },
  "High Intensity": { bg: "#fee2e2", color: "#b91c1c", border: "#fca5a5" },
  "Balanced":       { bg: "#dcfce7", color: "#15803d", border: "#86efac" },
  "Low Load":       { bg: "#fef9c3", color: "#a16207", border: "#fde047" },
};

const AC_META = {
  below_optimal: { label: "SUB OPT",  bg: "#fef9c3", color: "#a16207", border: "#fde047" },
  optimal:       { label: "OPTIM",    bg: "#dcfce7", color: "#15803d", border: "#86efac" },
  above_optimal: { label: "PESTE OPT", bg: "#fee2e2", color: "#b91c1c", border: "#fca5a5" },
};

function formColor(v) {
  if (v >= 80) return "#16a34a";
  if (v >= 65) return "#f59e0b";
  return "#E30613";
}

function fatigueColor(prob) {
  if (prob >= 0.65) return "#b91c1c";
  if (prob >= 0.40) return "#a16207";
  return "#15803d";
}

function fatigueBg(prob) {
  if (prob >= 0.65) return "#fee2e2";
  if (prob >= 0.40) return "#fef9c3";
  return "#dcfce7";
}

function fatigueLabel(prob) {
  if (prob >= 0.65) return "RISC RIDICAT";
  if (prob >= 0.40) return "MODERAT";
  return "RISC SCĂZUT";
}

function MiniBar({ value, color, max = 100 }) {
  return (
    <div style={{ height: 3, background: "rgba(255,255,255,0.1)", width: "100%", marginTop: 3, borderRadius: 2 }}>
      <div style={{ height: "100%", width: `${Math.min((value / max) * 100, 100)}%`, background: color, borderRadius: 2 }} />
    </div>
  );
}

function acColor(val) {
  if (val > 1.2) return "#b91c1c";
  if (val > 1.05) return "#f59e0b";
  if (val < 0.9) return "#60a5fa";
  return "#22c55e";
}

// ── Mock data matching GET /players output schema ─────────────────────────────
const PLAYERS = [
  { id:  1, name: "Alessandro Murgia",      pos: "CMF", num: 8,  ssId: 559470,  form_score_7d: 76, form_score_14d: 74, fatigue_risk_prob: 0.18, ac_combined_status: "optimal",       ac_321: 1.04, ac_721: 1.08, ac_728: 1.06, ac_ewma: 1.07, cluster_profile: "Balanced" },
  { id:  2, name: "Alex Mihai Orban",       pos: "GK",  num: 30, ssId: 2086370, form_score_7d: 63, form_score_14d: 61, fatigue_risk_prob: 0.09, ac_combined_status: "below_optimal",  ac_321: 0.91, ac_721: 0.95, ac_728: 0.93, ac_ewma: 0.94, cluster_profile: "Low Load" },
  { id:  3, name: "Alexandru Bota",         pos: "CB",  num: 5,  ssId: 1477485, form_score_7d: 72, form_score_14d: 70, fatigue_risk_prob: 0.21, ac_combined_status: "optimal",       ac_321: 1.02, ac_721: 1.04, ac_728: 1.03, ac_ewma: 1.03, cluster_profile: "Balanced" },
  { id:  4, name: "Alexandru Chipciu",      pos: "LB",  num: 77, ssId: 44435,   form_score_7d: 68, form_score_14d: 70, fatigue_risk_prob: 0.71, ac_combined_status: "above_optimal", ac_321: 1.29, ac_721: 1.22, ac_728: 1.25, ac_ewma: 1.26, cluster_profile: "High Intensity" },
  { id:  5, name: "Alin Chinteș",           pos: "CB",  num: 26, ssId: 1426614, form_score_7d: 69, form_score_14d: 67, fatigue_risk_prob: 0.14, ac_combined_status: "optimal",       ac_321: 0.99, ac_721: 1.01, ac_728: 1.00, ac_ewma: 1.00, cluster_profile: "Balanced" },
  { id:  6, name: "Alin Toșca",             pos: "CB",  num: 4,  ssId: 110210,  form_score_7d: 80, form_score_14d: 78, fatigue_risk_prob: 0.38, ac_combined_status: "optimal",       ac_321: 1.12, ac_721: 1.10, ac_728: 1.11, ac_ewma: 1.10, cluster_profile: "High Volume" },
  { id:  7, name: "Andrei Gheorghiță",      pos: "RW",  num: 17, ssId: 1113174, form_score_7d: 75, form_score_14d: 73, fatigue_risk_prob: 0.44, ac_combined_status: "above_optimal", ac_321: 1.18, ac_721: 1.14, ac_728: 1.16, ac_ewma: 1.15, cluster_profile: "High Intensity" },
  { id:  8, name: "Andrej Fábry",           pos: "GK",  num: 1,  ssId: 824206,  form_score_7d: 71, form_score_14d: 69, fatigue_risk_prob: 0.11, ac_combined_status: "optimal",       ac_321: 0.98, ac_721: 1.00, ac_728: 0.99, ac_ewma: 0.99, cluster_profile: "Low Load" },
  { id:  9, name: "Atanas Trică",           pos: "CF",  num: 9,  ssId: 1907275, form_score_7d: 77, form_score_14d: 74, fatigue_risk_prob: 0.22, ac_combined_status: "optimal",       ac_321: 1.05, ac_721: 1.06, ac_728: 1.05, ac_ewma: 1.06, cluster_profile: "Balanced" },
  { id: 10, name: "Dan Nistor",             pos: "CMF", num: 10, ssId: 93842,   form_score_7d: 83, form_score_14d: 80, fatigue_risk_prob: 0.27, ac_combined_status: "optimal",       ac_321: 1.10, ac_721: 1.08, ac_728: 1.09, ac_ewma: 1.08, cluster_profile: "High Volume" },
  { id: 11, name: "Dino Mikanović",         pos: "RB",  num: 24, ssId: 245249,  form_score_7d: 74, form_score_14d: 72, fatigue_risk_prob: 0.25, ac_combined_status: "optimal",       ac_321: 1.07, ac_721: 1.09, ac_728: 1.08, ac_ewma: 1.08, cluster_profile: "Balanced" },
  { id: 12, name: "Dorin Codrea",           pos: "DMF", num: 55, ssId: 946446,  form_score_7d: 70, form_score_14d: 68, fatigue_risk_prob: 0.17, ac_combined_status: "optimal",       ac_321: 1.01, ac_721: 1.03, ac_728: 1.02, ac_ewma: 1.02, cluster_profile: "Balanced" },
  { id: 13, name: "Elio Capradossi",        pos: "CB",  num: 3,  ssId: 284355,  form_score_7d: 79, form_score_14d: 77, fatigue_risk_prob: 0.20, ac_combined_status: "optimal",       ac_321: 1.06, ac_721: 1.07, ac_728: 1.06, ac_ewma: 1.07, cluster_profile: "Balanced" },
  { id: 14, name: "Gabriel Simion",         pos: "LW",  num: 98, ssId: 849788,  form_score_7d: 73, form_score_14d: 71, fatigue_risk_prob: 0.41, ac_combined_status: "above_optimal", ac_321: 1.15, ac_721: 1.12, ac_728: 1.13, ac_ewma: 1.13, cluster_profile: "High Intensity" },
  { id: 15, name: "Issouf Macalou",         pos: "CF",  num: 19, ssId: 1101188, form_score_7d: 82, form_score_14d: 79, fatigue_risk_prob: 0.23, ac_combined_status: "optimal",       ac_321: 1.08, ac_721: 1.05, ac_728: 1.07, ac_ewma: 1.06, cluster_profile: "High Volume" },
  { id: 16, name: "Iulian Cristea",         pos: "CB",  num: 6,  ssId: 578526,  form_score_7d: 70, form_score_14d: 73, fatigue_risk_prob: 0.13, ac_combined_status: "below_optimal", ac_321: 0.95, ac_721: 0.98, ac_728: 0.96, ac_ewma: 0.97, cluster_profile: "Balanced" },
  { id: 17, name: "Jasper van der Werff",   pos: "CB",  num: 15, ssId: 925011,  form_score_7d: 78, form_score_14d: 76, fatigue_risk_prob: 0.19, ac_combined_status: "optimal",       ac_321: 1.05, ac_721: 1.06, ac_728: 1.05, ac_ewma: 1.06, cluster_profile: "Balanced" },
  { id: 18, name: "Jonathan Cissé",         pos: "DMF", num: 22, ssId: 1005396, form_score_7d: 72, form_score_14d: 70, fatigue_risk_prob: 0.36, ac_combined_status: "optimal",       ac_321: 1.09, ac_721: 1.11, ac_728: 1.10, ac_ewma: 1.10, cluster_profile: "High Volume" },
  { id: 19, name: "Jovo Lukić",             pos: "DMF", num: 16, ssId: 927797,  form_score_7d: 66, form_score_14d: 68, fatigue_risk_prob: 0.16, ac_combined_status: "optimal",       ac_321: 0.98, ac_721: 1.00, ac_728: 0.99, ac_ewma: 0.99, cluster_profile: "Balanced" },
  { id: 20, name: "Matei Moraru",           pos: "GK",  num: 23, ssId: 1152491, form_score_7d: 60, form_score_14d: 62, fatigue_risk_prob: 0.08, ac_combined_status: "below_optimal", ac_321: 0.89, ac_721: 0.93, ac_728: 0.91, ac_ewma: 0.92, cluster_profile: "Low Load" },
  { id: 21, name: "Miguel Muñoz Fernández", pos: "RB",  num: 2,  ssId: 914160,  form_score_7d: 75, form_score_14d: 73, fatigue_risk_prob: 0.24, ac_combined_status: "optimal",       ac_321: 1.06, ac_721: 1.09, ac_728: 1.07, ac_ewma: 1.08, cluster_profile: "Balanced" },
  { id: 22, name: "Mouhamadou Drammeh",     pos: "LW",  num: 7,  ssId: 1085101, form_score_7d: 81, form_score_14d: 79, fatigue_risk_prob: 0.20, ac_combined_status: "optimal",       ac_321: 1.03, ac_721: 1.04, ac_728: 1.03, ac_ewma: 1.04, cluster_profile: "High Volume" },
  { id: 23, name: "Omar El Sawy",           pos: "AMF", num: 11, ssId: 1146209, form_score_7d: 78, form_score_14d: 76, fatigue_risk_prob: 0.46, ac_combined_status: "above_optimal", ac_321: 1.17, ac_721: 1.13, ac_728: 1.15, ac_ewma: 1.14, cluster_profile: "High Intensity" },
  { id: 24, name: "Ovidiu Bic",             pos: "DMF", num: 94, ssId: 812807,  form_score_7d: 62, form_score_14d: 66, fatigue_risk_prob: 0.82, ac_combined_status: "above_optimal", ac_321: 1.38, ac_721: 1.31, ac_728: 1.34, ac_ewma: 1.33, cluster_profile: "High Intensity" },
  { id: 25, name: "Taiwo Quadri Olakunle",  pos: "CF",  num: 18, ssId: 2430478, form_score_7d: 74, form_score_14d: 72, fatigue_risk_prob: 0.22, ac_combined_status: "optimal",       ac_321: 1.05, ac_721: 1.07, ac_728: 1.06, ac_ewma: 1.06, cluster_profile: "Balanced" },
  { id: 26, name: "Virgiliu Postolachi",    pos: "LW",  num: 93, ssId: 901913,  form_score_7d: 77, form_score_14d: 75, fatigue_risk_prob: 0.39, ac_combined_status: "optimal",       ac_321: 1.08, ac_721: 1.10, ac_728: 1.09, ac_ewma: 1.09, cluster_profile: "High Volume" },
];

function PlayerCard({ p }) {
  const [flipped, setFlipped] = useState(false);
  const cluster = CLUSTER_META[p.cluster_profile] || CLUSTER_META["Balanced"];
  const acMeta  = AC_META[p.ac_combined_status]   || AC_META["optimal"];
  const initials = p.name.split(" ").map(w => w[0]).join("").slice(0, 2).toUpperCase();
  const fProb = p.fatigue_risk_prob;

  return (
    <div
      onMouseEnter={() => setFlipped(true)}
      onMouseLeave={() => setFlipped(false)}
      style={{ perspective: 1000, cursor: "pointer", height: 360 }}
    >
      <div style={{
        position: "relative",
        width: "100%",
        height: "100%",
        transformStyle: "preserve-3d",
        transform: flipped ? "rotateY(180deg)" : "rotateY(0deg)",
        transition: "transform 0.55s cubic-bezier(0.4, 0.2, 0.2, 1)",
      }}>

        {/* ── FRONT ── */}
        <div style={{
          position: "absolute", inset: 0,
          backfaceVisibility: "hidden",
          WebkitBackfaceVisibility: "hidden",
          overflow: "hidden",
        }}>
          {/* Blurred bg photo */}
          <img
            src={SS(p.ssId)}
            alt=""
            style={{ position: "absolute", inset: "-10px", width: "calc(100% + 20px)", height: "calc(100% + 20px)", objectFit: "cover", objectPosition: "center top", filter: "blur(14px) brightness(0.35)", transform: "scale(1.05)" }}
            onError={(e) => { e.currentTarget.style.display = "none"; }}
          />
          {/* Dark base bg */}
          <div style={{ position: "absolute", inset: 0, background: "linear-gradient(160deg, #0a0a0a 0%, #1a0505 100%)" }} />

          {/* Fallback initials */}
          <div style={{ position: "absolute", inset: 0, display: "flex", alignItems: "center", justifyContent: "center" }}>
            <span className="font-display" style={{ fontSize: 52, fontWeight: 900, color: "rgba(255,255,255,0.1)" }}>{initials}</span>
          </div>

          {/* Main photo */}
          <img
            src={SS(p.ssId)}
            alt={p.name}
            style={{ position: "absolute", left: "50%", top: "4px", transform: "translateX(-50%)", height: "78%", width: "auto", objectFit: "contain", objectPosition: "top center" }}
            onError={(e) => { e.currentTarget.style.display = "none"; }}
          />

          {/* Gradient overlay bottom */}
          <div style={{ position: "absolute", inset: 0, background: "linear-gradient(to top, rgba(0,0,0,0.95) 0%, rgba(0,0,0,0.4) 45%, transparent 70%)" }} />

          {/* Top badges */}
          <div style={{ position: "absolute", top: 8, left: 8, fontFamily: "JetBrains Mono, monospace", fontSize: 11, color: "rgba(255,255,255,0.7)", fontWeight: 700, background: "rgba(0,0,0,0.4)", padding: "2px 6px" }}>
            #{p.num}
          </div>
          <div style={{ position: "absolute", top: 8, right: 8, padding: "2px 6px", background: fatigueBg(fProb), color: fatigueColor(fProb), fontSize: 8, fontFamily: "JetBrains Mono, monospace", fontWeight: 700 }}>
            {fatigueLabel(fProb)}
          </div>

          {/* Bottom info */}
          <div style={{ position: "absolute", bottom: 0, left: 0, right: 0, padding: "10px 12px" }}>
            <div style={{ padding: "1px 6px", background: "var(--uc-red)", color: "#fff", fontSize: 8, fontFamily: "JetBrains Mono, monospace", fontWeight: 700, letterSpacing: "0.1em", display: "inline-block", marginBottom: 4 }}>{p.pos}</div>
            <div className="font-display" style={{ fontWeight: 900, fontSize: 13, textTransform: "uppercase", color: "#fff", lineHeight: 1.1, textShadow: "0 1px 4px rgba(0,0,0,0.8)", whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }}>{p.name}</div>
            <div style={{ marginTop: 4, display: "inline-block", padding: "1px 6px", background: cluster.bg, color: cluster.color, fontSize: 8, fontFamily: "JetBrains Mono, monospace", fontWeight: 700 }}>{p.cluster_profile}</div>
          </div>
        </div>

        {/* ── BACK ── */}
        <div style={{
          position: "absolute", inset: 0,
          backfaceVisibility: "hidden",
          WebkitBackfaceVisibility: "hidden",
          transform: "rotateY(180deg)",
          background: "#0a0a0a",
          color: "#fff",
          padding: "14px 14px 12px",
          display: "flex", flexDirection: "column", gap: 8,
          border: "1px solid rgba(227,6,19,0.4)",
          overflow: "hidden",
        }}>
          {/* Name + pos */}
          <div style={{ borderBottom: "1px solid rgba(255,255,255,0.07)", paddingBottom: 6 }}>
            <div className="font-display" style={{ fontWeight: 900, fontSize: 13, textTransform: "uppercase", whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }}>{p.name}</div>
            <div style={{ fontFamily: "JetBrains Mono, monospace", fontSize: 9, color: "rgba(255,255,255,0.35)", marginTop: 2 }}>#{p.num} · {p.pos}</div>
          </div>

          {/* Form Score */}
          <div>
            <div className="label" style={{ fontSize: 9, opacity: 0.35, marginBottom: 5 }}>Scor Formă</div>
            <div style={{ display: "flex", gap: 8 }}>
              {[{ label: "7d", val: p.form_score_7d }, { label: "14d", val: p.form_score_14d }].map(f => (
                <div key={f.label} style={{ flex: 1 }}>
                  <div style={{ display: "flex", justifyContent: "space-between", alignItems: "baseline" }}>
                    <span style={{ fontFamily: "JetBrains Mono, monospace", fontSize: 9, opacity: 0.4 }}>{f.label}</span>
                    <span className="font-display" style={{ fontWeight: 900, fontSize: 20, color: formColor(f.val) }}>{f.val}</span>
                  </div>
                  <MiniBar value={f.val} color={formColor(f.val)} />
                </div>
              ))}
            </div>
          </div>

          {/* Fatigue Risk */}
          <div style={{ padding: "6px 8px", background: fatigueBg(fProb), borderLeft: `3px solid ${fatigueColor(fProb)}`, display: "flex", justifyContent: "space-between", alignItems: "center" }}>
            <span style={{ fontFamily: "JetBrains Mono, monospace", fontSize: 9, color: fatigueColor(fProb), fontWeight: 700 }}>Risc Oboseală</span>
            <span className="font-display" style={{ fontWeight: 900, fontSize: 18, color: fatigueColor(fProb) }}>{Math.round(fProb * 100)}%</span>
          </div>

          {/* AC Status */}
          <div style={{ padding: "4px 8px", background: acMeta.bg, borderLeft: `3px solid ${acMeta.color}`, display: "flex", justifyContent: "space-between", alignItems: "center" }}>
            <span style={{ fontFamily: "JetBrains Mono, monospace", fontSize: 9, color: acMeta.color, fontWeight: 700 }}>Status A:C</span>
            <span style={{ fontFamily: "JetBrains Mono, monospace", fontSize: 9, color: acMeta.color, fontWeight: 700 }}>{acMeta.label}</span>
          </div>

          {/* A:C Windows */}
          <div>
            <div className="label" style={{ fontSize: 9, opacity: 0.35, marginBottom: 5 }}>Ferestre Acut:Cronic</div>
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "4px 10px" }}>
              {[
                { label: "3:21", val: p.ac_321 },
                { label: "7:21", val: p.ac_721 },
                { label: "7:28", val: p.ac_728 },
                { label: "EWMA", val: p.ac_ewma },
              ].map(w => (
                <div key={w.label}>
                  <div style={{ display: "flex", justifyContent: "space-between", alignItems: "baseline" }}>
                    <span style={{ fontFamily: "JetBrains Mono, monospace", fontSize: 9, opacity: 0.4 }}>{w.label}</span>
                    <span style={{ fontFamily: "JetBrains Mono, monospace", fontSize: 11, fontWeight: 700, color: acColor(w.val) }}>{w.val.toFixed(2)}</span>
                  </div>
                  <MiniBar value={w.val * 50} color={acColor(w.val)} max={80} />
                </div>
              ))}
            </div>
          </div>

          {/* Cluster */}
          <div style={{ marginTop: "auto", padding: "4px 8px", background: cluster.bg, color: cluster.color, fontSize: 9, fontFamily: "JetBrains Mono, monospace", fontWeight: 700, textAlign: "center", letterSpacing: "0.05em" }}>
            {p.cluster_profile.toUpperCase()}
          </div>
        </div>

      </div>
    </div>
  );
}

export default function PlayerRoster() {
  const [filter, setFilter]     = useState("ALL");
  const [riskFilter, setRiskFilter] = useState("ALL");

  const positions = ["ALL", "GK", "CB", "RB", "LB", "DMF", "CMF", "AMF", "RW", "LW", "CF"];
  const risks     = ["ALL", "below_optimal", "optimal", "above_optimal"];
  const riskLabels = { ALL: "Toți", below_optimal: "Sub optim", optimal: "Optim", above_optimal: "Peste optim" };

  const filtered = PLAYERS.filter((p) => {
    const posOk  = filter === "ALL"     || p.pos === filter;
    const riskOk = riskFilter === "ALL" || p.ac_combined_status === riskFilter;
    return posOk && riskOk;
  });

  const highRisk = PLAYERS.filter(p => p.fatigue_risk_prob >= 0.65).length;
  const avgForm  = Math.round(PLAYERS.reduce((a, p) => a + p.form_score_7d, 0) / PLAYERS.length);

  return (
    <div style={{ padding: "28px 40px", background: "#f5f5f5", minHeight: "100%" }}>

      {/* Header */}
      <div style={{ marginBottom: 24, display: "flex", justifyContent: "space-between", alignItems: "flex-end", flexWrap: "wrap", gap: 16 }}>
        <div>
          <h1 className="font-display" style={{ fontWeight: 900, fontSize: 40, textTransform: "uppercase", margin: 0, letterSpacing: "-1px" }}>
            Monitor Încărcare Lot
          </h1>
          <p className="font-mono" style={{ fontSize: 11, opacity: 0.5, marginTop: 4 }}>
            {PLAYERS.length} jucători · Hover pentru analiză detaliată
          </p>
        </div>
        {/* Summary stats */}
        <div style={{ display: "flex", gap: 12 }}>
          {[
            { label: "Form mediu (7z)", value: avgForm, color: formColor(avgForm) },
            { label: "Risc oboseală ridicat", value: highRisk, color: "#E30613" },
            { label: "Total jucători", value: PLAYERS.length, color: "#0a0a0a" },
          ].map((s) => (
            <div key={s.label} style={{ padding: "10px 18px", background: "#fff", border: "1px solid rgba(0,0,0,0.1)", textAlign: "center" }}>
              <div className="label" style={{ fontSize: 9, opacity: 0.5 }}>{s.label}</div>
              <div className="font-display" style={{ fontWeight: 900, fontSize: 28, color: s.color }}>{s.value}</div>
            </div>
          ))}
        </div>
      </div>

      {/* Filters */}
      <div style={{ display: "flex", gap: 16, marginBottom: 20, flexWrap: "wrap", alignItems: "center" }}>
        <div style={{ display: "flex", gap: 4, flexWrap: "wrap" }}>
          {positions.map((pos) => (
            <button key={pos} onClick={() => setFilter(pos)} style={{
              padding: "4px 12px", fontSize: 11, fontFamily: "JetBrains Mono, monospace",
              textTransform: "uppercase", letterSpacing: "0.08em",
              border: "1px solid rgba(0,0,0,0.15)", cursor: "pointer",
              background: filter === pos ? "#0a0a0a" : "none",
              color: filter === pos ? "#fff" : "rgba(0,0,0,0.5)",
              transition: "all 0.15s",
            }}>{pos}</button>
          ))}
        </div>
        <div style={{ width: 1, height: 24, background: "rgba(0,0,0,0.1)" }} />
        <div style={{ display: "flex", gap: 4 }}>
          {risks.map((r) => {
            const meta = r === "ALL" ? { bg: "#0a0a0a", color: "#fff" } : AC_META[r];
            const isActive = riskFilter === r;
            return (
              <button key={r} onClick={() => setRiskFilter(r)} style={{
                padding: "4px 12px", fontSize: 11, fontFamily: "JetBrains Mono, monospace",
                textTransform: "uppercase", letterSpacing: "0.08em",
                border: `1px solid ${isActive ? meta.color : "rgba(0,0,0,0.15)"}`,
                cursor: "pointer",
                background: isActive ? meta.bg : "none",
                color: isActive ? meta.color : "rgba(0,0,0,0.5)",
                transition: "all 0.15s",
              }}>{riskLabels[r]}</button>
            );
          })}
        </div>
      </div>

      {/* Grid */}
      <div style={{ display: "grid", gridTemplateColumns: "repeat(6, 1fr)", gap: 12 }}>
        {filtered.map((p) => <PlayerCard key={p.id} p={p} />)}
      </div>

    </div>
  );
}
