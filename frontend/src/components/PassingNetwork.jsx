import { useEffect, useState } from "react";
import Pitch from "./Pitch";
import { fetchPassingNetwork } from "../lib/api";

export default function PassingNetwork({ dark = true, pollMs = 4000, height = "420px" }) {
  const [data, setData] = useState({ nodes: [], edges: [] });
  const border = dark ? "1px solid rgba(255,255,255,0.08)" : "1px solid rgba(0,0,0,0.1)";

  useEffect(() => {
    let active = true;
    const load = async () => {
      try {
        const d = await fetchPassingNetwork();
        if (active) setData(d);
      } catch {}
    };
    load();
    const t = setInterval(load, pollMs);
    return () => { active = false; clearInterval(t); };
  }, [pollMs]);

  const maxEdge = Math.max(1, ...data.edges.map((e) => e.count));
  const nodeMap = Object.fromEntries(data.nodes.map((n) => [n.num, n]));
  const totalPasses = data.edges.reduce((a, b) => a + b.count, 0);

  return (
    <div style={{ padding: 16, background: dark ? "rgba(20,20,20,0.6)" : "#fff", border, backdropFilter: "blur(6px)", display: "flex", flexDirection: "column" }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 12 }}>
        <div className="label" style={{ opacity: 0.6 }}>Passing Network · Live</div>
        <span className="chip-mono" style={{ color: dark ? "rgba(255,255,255,0.6)" : "rgba(0,0,0,0.6)" }}>
          {totalPasses} passes
        </span>
      </div>
      <div style={{ position: "relative", height }}>
        <Pitch dark={dark}>
          {data.edges.map((e, i) => {
            const a = nodeMap[e.a], b = nodeMap[e.b];
            if (!a || !b) return null;
            const w = 0.15 + 1.0 * (e.count / maxEdge);
            return (
              <line
                key={i}
                x1={a.x} y1={a.y} x2={b.x} y2={b.y}
                stroke={dark ? "#ffffff" : "#0a0a0a"}
                strokeOpacity={0.15 + 0.55 * (e.count / maxEdge)}
                strokeWidth={w}
              />
            );
          })}
          {data.nodes.map((n) => (
            <g key={n.num}>
              <circle
                cx={n.x} cy={n.y} r={2.6}
                fill={n.pos === "GK" ? "#E30613" : dark ? "#ffffff" : "#0a0a0a"}
                stroke={dark ? "#0a0a0a" : "#ffffff"}
                strokeWidth="0.4"
              />
              <text x={n.x} y={n.y - 3.6} textAnchor="middle" fontSize="2.2" fontFamily="JetBrains Mono"
                fill={dark ? "#ffffff" : "#0a0a0a"} opacity="0.85">
                {n.name?.split(" ").pop()}
              </text>
            </g>
          ))}
        </Pitch>
      </div>
    </div>
  );
}
