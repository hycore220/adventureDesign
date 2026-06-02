/**
 * save-it UI 가 기대하는 도메인 타입.
 *
 * Supabase 의 raw 응답(`uuid id`, `snake_case`) 대신, Spring REST 응답을
 * `api.ts` 의 정규화 함수가 이 형태로 변환해서 넘긴다.
 *
 * 변경점 (Supabase → Spring 포팅):
 *   - id: string(uuid) → number(int)   ← UI 는 키 비교만 하므로 영향 거의 없음
 *   - folder_id: 미지정 링크는 null (우리 모델엔 미지정 없음 — UI 에선 미사용)
 */

export type ParaCategory = "project" | "area" | "resource" | "archive";

export interface Folder {
  id: number;
  name: string;
  para_category: ParaCategory | null;
}

export interface Link {
  id: number;
  folder_id: number | null;
  url: string;
  title: string;
  description: string | null;
  priority: number;
  is_read: boolean;
  created_at: string;
  read_at: string | null;
}
