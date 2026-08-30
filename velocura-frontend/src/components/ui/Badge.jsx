import s from "./Badge.module.css";

/** tone: "blue"|"green"|"red"|"orange"|"yellow"|"gray" */
export default function Badge({ tone="blue", children, className="" }) {
  return <span className={[s.badge, s[tone], className].join(" ")}>{children}</span>;
}

export { Badge };
