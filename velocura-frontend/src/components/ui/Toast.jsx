import React, { useEffect } from "react";
import { CheckCircle2, AlertCircle, X } from "lucide-react";
import s from "./Toast.module.css";

export default function Toast({ message, type = "success", onClose, duration = 4000 }) {
  useEffect(() => {
    if (!message) return;
    const timer = setTimeout(() => {
      if (onClose) onClose();
    }, duration);
    return () => clearTimeout(timer);
  }, [message, duration, onClose]);

  if (!message) return null;

  const isSuccess = type === "success";

  return (
    <div className={s.toastContainer}>
      <div className={[s.toast, isSuccess ? s.success : s.error].join(" ")} role="alert">
        <div className={s.iconWrap}>
          {isSuccess ? (
            <CheckCircle2 size={18} className={s.successIcon} />
          ) : (
            <AlertCircle size={18} className={s.errorIcon} />
          )}
        </div>
        <span className={s.message}>{message}</span>
        <button
          type="button"
          className={s.closeBtn}
          onClick={onClose}
          aria-label="Dismiss notification"
        >
          <X size={14} />
        </button>
      </div>
    </div>
  );
}

export { Toast };
