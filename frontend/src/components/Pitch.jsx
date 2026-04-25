export default function Pitch({ dark = true, children, className = "" }) {
  const stroke = dark ? "rgba(255,255,255,0.22)" : "rgba(0,0,0,0.25)";
  const bg = dark ? "rgba(255,255,255,0.02)" : "rgba(0,0,0,0.02)";

  return (
    <svg
      viewBox="0 0 100 100"
      className={className}
      style={{ width: "100%", height: "100%" }}
      preserveAspectRatio="none"
    >
      <rect x="0" y="0" width="100" height="100" fill={bg} />
      <rect x="1" y="1" width="98" height="98" fill="none" stroke={stroke} strokeWidth="0.3" />
      <line x1="50" y1="1" x2="50" y2="99" stroke={stroke} strokeWidth="0.25" />
      <circle cx="50" cy="50" r="8" fill="none" stroke={stroke} strokeWidth="0.25" />
      <circle cx="50" cy="50" r="0.6" fill={stroke} />
      {/* Left penalty area */}
      <rect x="1" y="25" width="14" height="50" fill="none" stroke={stroke} strokeWidth="0.25" />
      <rect x="1" y="38" width="5" height="24" fill="none" stroke={stroke} strokeWidth="0.25" />
      {/* Right penalty area */}
      <rect x="85" y="25" width="14" height="50" fill="none" stroke={stroke} strokeWidth="0.25" />
      <rect x="94" y="38" width="5" height="24" fill="none" stroke={stroke} strokeWidth="0.25" />
      {children}
    </svg>
  );
}
