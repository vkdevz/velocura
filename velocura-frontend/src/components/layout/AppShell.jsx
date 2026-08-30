import NavBar from "./NavBar";

export default function AppShell({ children }) {
  return (
    <>
      <NavBar />
      <main style={{ paddingTop:"52px", minHeight:"100dvh" }}>
        {children}
      </main>
    </>
  );
}

export { AppShell };
