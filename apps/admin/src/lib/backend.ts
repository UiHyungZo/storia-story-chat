import "server-only";
import { cookies } from "next/headers";
import { redirect } from "next/navigation";
import { ADMIN_SESSION_COOKIE } from "./session";

/**
 * 백엔드(Spring)를 호출하는 유일한 지점. "server-only"를 import하고 있어서 이 모듈을
 * 클라이언트 컴포넌트에서 실수로 import하면 빌드 타임에 에러가 난다 — 브라우저가 백엔드
 * 세션 토큰을 절대 볼 수 없도록 강제하는 장치.
 */
export async function adminFetch(path: string, init?: RequestInit): Promise<Response> {
  const store = await cookies();
  const token = store.get(ADMIN_SESSION_COOKIE)?.value;
  if (!token) {
    redirect("/login");
  }

  const res = await fetch(`${process.env.ADMIN_BACKEND_URL}${path}`, {
    ...init,
    headers: {
      ...init?.headers,
      Cookie: `ADMIN_SESSION=${token}`,
    },
    cache: "no-store",
  });

  if (res.status === 401) {
    redirect("/login");
  }

  return res;
}
