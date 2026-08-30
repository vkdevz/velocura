import { forwardRef } from "react";
import s from "./Input.module.css";

const Input = forwardRef(function Input(
  { label, hint, error, prefix, suffix, className="", ...props }, ref
) {
  return (
    <div className={[s.field, className].join(" ")}>
      {label && <label className={s.label}>{label}</label>}
      <div className={[s.wrap, error ? s.err : ""].join(" ")}>
        {prefix && <span className={s.affix}>{prefix}</span>}
        <input ref={ref} className={s.input} {...props} />
        {suffix && <span className={s.affix}>{suffix}</span>}
      </div>
      {error  && <p className={s.errMsg}>{error}</p>}
      {!error && hint && <p className={s.hint}>{hint}</p>}
    </div>
  );
});

export { Input };
export default Input;
