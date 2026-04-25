import { RadialBarChart, RadialBar, PolarAngleAxis, ResponsiveContainer } from "recharts";

export default function PressingGauge({ value = 0, history = [], dark = true }) {
  const v = Math.round(value);
  const isCritical = v < 55;
  const color = isCritical ? "#E30613" : v < 70 ? "#f59e0b" : "#22c55e";
  const data = [{ name: "p", value: v, fill: color }];
  const border = dark ? "1px solid rgba(255,255,255,0.08)" : "1px solid rgba(0,0,0,0.1)";

  return (
    <div
      className={isCritical ? "pulse-red" : ""}
      style={{
        padding: 24,
        background: dark ? "rgba(20,20,20,0.6)" : "#fff",
        border,
        backdropFilter: "blur(6px)",
      }}
    >
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 12 }}>
        <div className="label" style={{ opacity: 0.6 }}>Pressing Efficiency · LIVE</div>
        <span
          className="chip-mono"
          style={{ color: isCritical ? "var(--uc-red)" : dark ? "rgba(255,255,255,0.7)" : "rgba(0,0,0,0.7)" }}
        >
          {isCritical ? "CRITICAL" : v < 70 ? "WARN" : "STABLE"}
        </span>
      </div>

      <div style={{ position: "relative", height: 220 }}>
        <ResponsiveContainer width="100%" height="100%">
          <RadialBarChart innerRadius="70%" outerRadius="100%" data={data} startAngle={210} endAngle={-30}>
            <PolarAngleAxis type="number" domain={[0, 100]} tick={false} />
            <RadialBar
              background={{ fill: dark ? "rgba(255,255,255,0.08)" : "rgba(0,0,0,0.08)" }}
              dataKey="value"
              cornerRadius={0}
            />
          </RadialBarChart>
        </ResponsiveContainer>
        <div
          style={{
            position: "absolute",
            inset: 0,
            display: "grid",
            placeItems: "center",
            pointerEvents: "none",
          }}
        >
          <div style={{ textAlign: "center" }}>
            <div className="font-display" style={{ fontWeight: 900, fontSize: 60, lineHeight: 1, color }}>
              {v}
              <span style={{ fontSize: 22, opacity: 0.6, verticalAlign: "super" }}>%</span>
            </div>
            <div className="label" style={{ opacity: 0.5, marginTop: 8 }}>Last 5 duels</div>
          </div>
        </div>
      </div>

      <Sparkline data={history} dark={dark} />
    </div>
  );
}

function Sparkline({ data = [], dark = true }) {
  if (!data.length) return <div style={{ height: 40 }} />;
  const w = 320, h = 36;
  const xs = data.map((_, i) => (i / Math.max(1, data.length - 1)) * w);
  const ys = data.map((d) => h - (Math.max(0, Math.min(100, d.value)) / 100) * h);
  const path = xs.map((x, i) => `${i === 0 ? "M" : "L"} ${x.toFixed(1)} ${ys[i].toFixed(1)}`).join(" ");

  return (
    <svg viewBox={`0 0 ${w} ${h}`} style={{ width: "100%", height: 40, marginTop: 12 }}>
      <path d={path} fill="none" stroke={dark ? "#ffffff" : "#0a0a0a"} strokeOpacity="0.5" strokeWidth="1.5" />
      {ys.length > 0 && (
        <circle cx={xs[xs.length - 1]} cy={ys[ys.length - 1]} r="3" fill="#E30613" />
      )}
    </svg>
  );
}
