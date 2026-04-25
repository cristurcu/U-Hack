import { useState, useRef, useEffect } from "react";
import { fetchChat } from "../lib/api";
import { Sparkles, Send, Loader2 } from "lucide-react";

const SUGGESTED = [
  "Care au fost cele mai mari probleme defensive?",
  "Care jucător a avut cea mai bună eficiență la pressing?",
  "De ce am pierdut ritmul în repriza a doua?",
  "Ce schimbări tactice trebuie să facem la meciul următor?",
  "Explică momentele cheie care au decis meciul.",
];

const MOCK_ANSWERS = {
  default: [
    "Pe baza datelor din meci, eficiența pressingului a scăzut semnificativ după minutul 60, în special în zonele de mijloc central. Metricile de intensitate ale lui Bic arată tipare clare de oboseală începând din minutul 63.",
    "Analiza rețelei de pase relevă o conectivitate puternică între Nistor și Popa (16 schimburi), dar acoperire slabă pe flancul drept — Gheorghe a fost izolat cu doar 6 schimburi cu atacantul.",
    "U Cluj a dominat posesia în prima repriză cu 55% intensitate pressing, dar Hermannstadt a exploatat spațiul lăsat în spatele alergărilor suprapuse ale lui Chipciu în repriza a doua.",
    "Datele privind calitatea deciziilor arată 3 oportunități de șut ratate de Popa (xG combinat 0.91). Decizii mai rapide de șut în careu ar fi putut schimba rezultatul.",
    "Analiza line-breaking îl identifică pe Stoica ca cel mai periculos alergător — 10 din alergările sale au dus direct la șuturi pe poartă, cu o medie de 34m per alergare progresivă.",
  ],
};

let mockIdx = 0;

export default function CoachChat({ matchId, match }) {
  const [messages, setMessages] = useState([
    {
      role: "assistant",
      text: `Pregătit să analizez **${match?.home || "U Cluj"} ${match?.score || ""} ${match?.away || ""}** (${match?.date || ""}). Întreabă-mă orice despre tactică, performanța jucătorilor sau momente cheie.`,
    },
  ]);
  const [input, setInput]   = useState("");
  const [loading, setLoading] = useState(false);
  const bottomRef = useRef(null);
  const inputRef  = useRef(null);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages]);

  const send = async (text) => {
    const q = (text || input).trim();
    if (!q || loading) return;
    setInput("");
    setMessages((m) => [...m, { role: "user", text: q }]);
    setLoading(true);
    try {
      const r = await fetchChat(q, matchId);
      setMessages((m) => [...m, { role: "assistant", text: r.answer || r.insight || r.response }]);
    } catch {
      // fallback mock
      const answer = MOCK_ANSWERS.default[mockIdx % MOCK_ANSWERS.default.length];
      mockIdx++;
      setMessages((m) => [...m, { role: "assistant", text: answer }]);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={{ display: "flex", flexDirection: "column", height: "65vh", background: "#fff", border: "1px solid rgba(0,0,0,0.1)" }}>

      {/* Header */}
      <div style={{ padding: "14px 20px", borderBottom: "1px solid rgba(0,0,0,0.08)", display: "flex", alignItems: "center", gap: 8 }}>
        <Sparkles size={14} color="var(--uc-red)" />
        <span className="label" style={{ fontWeight: 700, fontSize: 12 }}>Asistent AI Antrenor</span>
        <span className="font-mono" style={{ fontSize: 10, opacity: 0.4, marginLeft: "auto" }}>
          {match?.home} {match?.score} {match?.away} · {match?.date}
        </span>
      </div>

      {/* Messages */}
      <div style={{ flex: 1, overflowY: "auto", padding: "16px 20px", display: "flex", flexDirection: "column", gap: 12 }}>
        {messages.map((msg, i) => (
          <div key={i} style={{ display: "flex", flexDirection: "column", alignItems: msg.role === "user" ? "flex-end" : "flex-start" }}>
            <div style={{
              maxWidth: "78%",
              padding: "10px 14px",
              fontSize: 13,
              lineHeight: 1.6,
              background: msg.role === "user" ? "#0a0a0a" : "#f4f4f4",
              color: msg.role === "user" ? "#fff" : "#0a0a0a",
              borderLeft: msg.role === "assistant" ? "3px solid var(--uc-red)" : "none",
            }}>
              {msg.text}
            </div>
            <span style={{ fontSize: 10, opacity: 0.35, marginTop: 3, fontFamily: "JetBrains Mono, monospace" }}>
              {msg.role === "user" ? "Antrenor" : "Analist AI"}
            </span>
          </div>
        ))}

        {loading && (
          <div style={{ display: "flex", alignItems: "center", gap: 8, opacity: 0.5, fontSize: 12 }}>
            <Loader2 size={13} style={{ animation: "spin 1s linear infinite" }} />
            <span style={{ fontFamily: "JetBrains Mono, monospace" }}>Se analizează datele meciului…</span>
          </div>
        )}
        <div ref={bottomRef} />
      </div>

      {/* Suggested questions */}
      {messages.length <= 1 && (
        <div style={{ padding: "0 20px 12px", display: "flex", gap: 6, flexWrap: "wrap" }}>
          {SUGGESTED.map((s, i) => (
            <button
              key={i}
              onClick={() => send(s)}
              style={{
                fontSize: 11,
                fontFamily: "JetBrains Mono, monospace",
                padding: "5px 10px",
                background: "none",
                border: "1px solid rgba(0,0,0,0.15)",
                cursor: "pointer",
                color: "rgba(0,0,0,0.6)",
                transition: "all 0.15s",
              }}
              onMouseEnter={(e) => { e.target.style.borderColor = "var(--uc-red)"; e.target.style.color = "var(--uc-red)"; }}
              onMouseLeave={(e) => { e.target.style.borderColor = "rgba(0,0,0,0.15)"; e.target.style.color = "rgba(0,0,0,0.6)"; }}
            >
              {s}
            </button>
          ))}
        </div>
      )}

      {/* Input */}
      <div style={{ padding: "12px 20px", borderTop: "1px solid rgba(0,0,0,0.08)", display: "flex", gap: 10 }}>
        <input
          ref={inputRef}
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={(e) => e.key === "Enter" && send()}
          placeholder="Întreabă despre tactică, jucători, momente cheie…"
          style={{
            flex: 1,
            padding: "10px 14px",
            fontFamily: "JetBrains Mono, monospace",
            fontSize: 12,
            border: "1px solid rgba(0,0,0,0.15)",
            outline: "none",
            background: "#fafafa",
          }}
        />
        <button
          onClick={() => send()}
          disabled={!input.trim() || loading}
          style={{
            padding: "10px 16px",
            background: input.trim() && !loading ? "#0a0a0a" : "rgba(0,0,0,0.08)",
            border: "none",
            cursor: input.trim() && !loading ? "pointer" : "default",
            color: input.trim() && !loading ? "#fff" : "rgba(0,0,0,0.3)",
            display: "flex", alignItems: "center", gap: 6,
            transition: "all 0.2s",
          }}
        >
          <Send size={14} />
        </button>
      </div>
    </div>
  );
}
