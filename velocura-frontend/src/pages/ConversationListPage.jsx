import React from "react";
import AppShell from "../components/layout/AppShell";
import ConversationList from "../components/chat/ConversationList";

export default function ConversationListPage() {
  return (
    <AppShell>
      <ConversationList />
    </AppShell>
  );
}
