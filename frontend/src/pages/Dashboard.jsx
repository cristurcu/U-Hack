import { useState } from "react";
import LiveMode from "../components/LiveMode";
import AnalysisMode from "../components/AnalysisMode";
import MatchHeader from "../components/MatchHeader";
import FatigueBanner from "../components/FatigueBanner";
import useMatchData from "../hooks/useMatchData";

export default function Dashboard() {
  const [mode, setMode] = useState("live");
  const { state } = useMatchData(2000, true);

  return (
    <div className={mode === "live" ? "live-mode" : "analysis-mode"}>
      <FatigueBanner alerts={state?.fatigue_alerts || []} mode={mode} />
      <MatchHeader state={state} mode={mode} setMode={setMode} />
      {mode === "live" ? <LiveMode state={state} /> : <AnalysisMode liveState={state} />}
      <footer
        style={{
          padding: "24px",
          textAlign: "center",
          opacity: 0.3,
          fontSize: 10,
          letterSpacing: "0.22em",
          textTransform: "uppercase",
          fontFamily: "JetBrains Mono, monospace",
        }}
      >
        U CLUJ · Football Analytics Platform · v1.0
      </footer>
    </div>
  );
}
