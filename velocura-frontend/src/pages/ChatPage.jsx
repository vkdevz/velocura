import React from "react";
import ChatWindow from "../components/ChatWindow";
import AppShell from "../components/layout/AppShell";

export default function ChatPage() {
  return (
    <AppShell>
      <ChatWindow />
    </AppShell>
  );
}
