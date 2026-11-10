/**
 * Các thẻ của trang không gian học. Story sau thêm chức năng (hỏi đáp, ghi chú, thẻ ghi nhớ…) thì thêm một mục vào
 * đây và một nhánh hiển thị trong WorkspacePage.
 */
export const WORKSPACE_TABS = ['overview', 'assistant', 'sources'] as const

export type WorkspaceTab = (typeof WORKSPACE_TABS)[number]

export function isWorkspaceTab(value: string | null): value is WorkspaceTab {
  return value !== null && (WORKSPACE_TABS as readonly string[]).includes(value)
}

export const workspaceTabId = (baseId: string, tab: WorkspaceTab) => `${baseId}-tab-${tab}`
export const workspacePanelId = (baseId: string, tab: WorkspaceTab) => `${baseId}-panel-${tab}`
