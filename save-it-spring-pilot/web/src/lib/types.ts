/**
 * UI 도메인 타입 — Spring 백엔드 응답을 이 형태로 정규화해서 컴포넌트에 전달.
 *
 * 원본 save-it 은 Supabase UUID(string) 였으나, 우리 Spring 은 int auto-increment.
 * id 타입만 number 로 바꾸고 나머지 snake_case 필드는 그대로 유지 (컴포넌트 변경 최소화).
 */

export type ParaCategory = "project" | "area" | "resource" | "archive";

export interface Folder {
  id: number;
  user_id?: string;
  name: string;
  para_category: ParaCategory | null;
  created_at?: string;
}

export interface Link {
  id: number;
  user_id?: string;
  folder_id: number | null;
  url: string;
  title: string;
  description: string | null;
  priority: number;
  is_read: boolean;
  created_at: string;
  read_at: string | null;
  // Spring 추가 메타 (선택 표시용)
  para_status?: ParaCategory | null;
  host?: string | null;
  content_type?: string | null;
  thumbnail_url?: string | null;
}

// Spring 응답 → UI Link 변환
import type {
  SpringLinkResponse,
  SpringFolderResponse,
} from "./api";

export function toLink(s: SpringLinkResponse, folderId: number | null = null): Link {
  return {
    id: s.id,
    folder_id: folderId,
    url: s.link,
    title: s.title,
    description: null,
    priority: 0,
    is_read: s.read ?? false,
    created_at: s.createdAt ?? s.lastUpdate,
    read_at: s.readAt ?? null,
    para_status: s.paraStatus
      ? (s.paraStatus.toLowerCase() as ParaCategory)
      : null,
    host: s.host ?? null,
    content_type: s.contentType ?? null,
    thumbnail_url: s.thumbnailUrl ?? null,
  };
}

export function toFolder(s: SpringFolderResponse): Folder {
  return {
    id: s.id,
    name: s.name,
    para_category: s.paraCategory
      ? (s.paraCategory.toLowerCase() as ParaCategory)
      : null,
  };
}
