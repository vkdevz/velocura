export default function Spinner({ size=20, color="var(--accent)" }) {
  return (
    <span role="status" aria-label="Loading" style={{
      display:"inline-block", width:size, height:size,
      border:"2px solid var(--fill-tertiary)",
      borderTopColor:color, borderRadius:"50%",
      animation:"spin 0.7s linear infinite",
    }} />
  );
}

export { Spinner };
