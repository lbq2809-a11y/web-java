import type { ReactNode } from 'react';
import { Navbar } from './Navbar';
import { Footer } from './Footer';

interface AppShellProps {
  children: ReactNode;
  hideFooter?: boolean;
}

export function AppShell({ children, hideFooter = false }: AppShellProps) {
  return (
    <div className="flex min-h-screen flex-col bg-[#F8FAFC] text-[#0F172A]">
      <Navbar />
      <main className="flex-1">{children}</main>
      {!hideFooter && <Footer />}
    </div>
  );
}
