import { forwardRef } from "react";
import s from "./Button.module.css";

/**
 * variant: "primary" | "secondary" | "ghost" | "destructive" | "tinted"
 * size:    "sm" | "md" | "lg"
 */
const Button = forwardRef(function Button(
  { variant="primary", size="md", disabled, loading, children, className="", ...props }, ref
) {
  return (
    <button ref={ref} disabled={disabled||loading}
      className={[s.btn, s[variant], s[size], className].join(" ")} {...props}>
      {loading && <span className={s.spinner} aria-hidden />}
      <span className={loading ? s.dimmed : ""}>{children}</span>
    </button>
  );
});

export { Button };
export default Button;
