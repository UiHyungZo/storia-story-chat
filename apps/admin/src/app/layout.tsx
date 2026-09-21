import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "Storia 운영 콘솔",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="ko">
      <body>{children}</body>
    </html>
  );
}
