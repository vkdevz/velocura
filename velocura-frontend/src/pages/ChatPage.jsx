import React from "react";
import { useLocation, useSearchParams } from "react-router-dom";
import ChatWindow from "../components/ChatWindow";
import AppShell from "../components/layout/AppShell";

export default function ChatPage() {
  const location = useLocation();
  const [searchParams] = useSearchParams();
  const initialQuery = location.state?.initialQuery || searchParams.get("q") || "";

  return (
    <AppShell>
      <ChatWindow initialQuery={initialQuery} />
    </AppShell>
  );
}
