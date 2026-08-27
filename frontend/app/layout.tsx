import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "LeetDuel | Real-time coding matches",
  description:
    "Practice with purpose in real-time coding matches. Solve the same challenge as another developer and build your ELO rating.",
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html lang="en" className="h-full antialiased">
      <body className="min-h-full flex flex-col bg-bg text-fg font-sans">
        {children}
      </body>
    </html>
  );
}
