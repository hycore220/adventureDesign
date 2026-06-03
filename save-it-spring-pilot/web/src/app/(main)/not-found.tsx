import Link from "next/link";
import { Button } from "@/components/ui/button";

export default function NotFound() {
  return (
    <div className="flex min-h-svh flex-col items-center justify-center gap-4 p-8 text-center">
      <h2 className="text-lg font-semibold">찾을 수 없는 페이지예요</h2>
      <Link href="/">
        <Button variant="outline">라이브러리로 돌아가기</Button>
      </Link>
    </div>
  );
}
