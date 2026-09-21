import Link from "next/link";
import { cookies } from "next/headers";
import { redirect } from "next/navigation";
import { adminFetch } from "@/lib/backend";
import { ADMIN_SESSION_COOKIE } from "@/lib/session";

export default async function AdminLayout({ children }: { children: React.ReactNode }) {
  // 401이면 adminFetch가 자체적으로 /login으로 redirect한다.
  await adminFetch("/api/admin/auth/session");

  async function logoutAction() {
    "use server";
    const store = await cookies();
    const token = store.get(ADMIN_SESSION_COOKIE)?.value;
    if (token) {
      await fetch(`${process.env.ADMIN_BACKEND_URL}/api/admin/auth/logout`, {
        method: "POST",
        headers: { Cookie: `ADMIN_SESSION=${token}` },
      });
    }
    store.delete(ADMIN_SESSION_COOKIE);
    redirect("/login");
  }

  return (
    <div className="min-h-screen bg-neutral-50">
      <header className="border-b border-neutral-200 bg-white">
        <nav className="mx-auto flex max-w-5xl items-center justify-between px-4 py-3">
          <div className="flex gap-4 text-sm font-medium text-neutral-700">
            <Link href="/characters">캐릭터</Link>
            <Link href="/conversations">대화 로그</Link>
            <Link href="/sessions">세션</Link>
          </div>
          <form action={logoutAction}>
            <button type="submit" className="text-sm text-neutral-500 hover:text-neutral-900">
              로그아웃
            </button>
          </form>
        </nav>
      </header>
      <main className="mx-auto max-w-5xl px-4 py-6">{children}</main>
    </div>
  );
}
