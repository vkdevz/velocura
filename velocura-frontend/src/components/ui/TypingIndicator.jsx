export default function TypingIndicator() {
  return (
    <div role="status" aria-label="Thinking" style={{
      display:"inline-flex", alignItems:"center", gap:"4px",
      padding:"var(--space-3) var(--space-4)",
      background:"var(--bg-elevated-2)",
      borderRadius:"18px 18px 18px 4px",
      width:"fit-content",
    }}>
      <span className="typing-dot" />
      <span className="typing-dot" />
      <span className="typing-dot" />
    </div>
  );
}

export { TypingIndicator };
