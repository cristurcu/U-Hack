import PressingGauge from "./PressingGauge";
import EventFeed from "./EventFeed";
import XGTracker from "./XGTracker";
import DecisionFeed from "./DecisionFeed";
import PassingNetwork from "./PassingNetwork";
import PlayerLoadStrip from "./PlayerLoadStrip";

export default function LiveMode({ state }) {
  if (!state) {
    return (
      <div style={{ padding: "48px 32px", color: "rgba(255,255,255,0.5)", fontFamily: "JetBrains Mono, monospace", fontSize: 13 }}>
        Connecting to live match feed…
      </div>
    );
  }

  return (
    <div style={{ padding: "24px 32px", display: "grid", gridTemplateColumns: "repeat(12, 1fr)", gap: 20 }}>
      {/* Row 1 */}
      <div style={{ gridColumn: "span 4" }}>
        <PressingGauge value={state.pressing} history={state.pressing_history || []} dark />
      </div>
      <div style={{ gridColumn: "span 4" }}>
        <XGTracker history={state.xg_history || []} home={state.home} away={state.away} dark />
      </div>
      <div style={{ gridColumn: "span 4" }}>
        <EventFeed events={state.events || []} dark />
      </div>

      {/* Row 2 */}
      <div style={{ gridColumn: "span 8" }}>
        <PassingNetwork dark height="460px" />
      </div>
      <div style={{ gridColumn: "span 4" }}>
        <DecisionFeed events={state.decision_events || []} dark />
      </div>

      {/* Row 3 */}
      <div style={{ gridColumn: "span 12" }}>
        <PlayerLoadStrip players={state.players || []} dark />
      </div>
    </div>
  );
}
