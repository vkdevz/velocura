export default function Divider({ inset=false }) {
  return <hr style={{ border:"none",
    borderTop:"1px solid var(--separator)",
    marginLeft: inset ? "var(--space-5)" : 0 }} />;
}

export { Divider };
