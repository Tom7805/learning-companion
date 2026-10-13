import { expect, type Page } from '@playwright/test'

/**
 * Thao tác với khung ứng dụng (NCL-02-CN-001) giống người dùng thật trên cả máy tính và điện thoại: máy tính có thanh
 * bên luôn hiện, điện thoại mở ngăn kéo bằng nút trên thanh trên cùng.
 */

/** Nút trình đơn tài khoản: ở đáy thanh bên (máy tính) hoặc trên thanh trên cùng (điện thoại). */
export function accountMenuButton(page: Page) {
  return page.getByRole('button', { name: /^Trình đơn tài khoản của / })
}

export async function openAccountMenu(page: Page) {
  await accountMenuButton(page).click()
  const menu = page.getByRole('menu', { name: 'Tài khoản' })
  await expect(menu).toBeVisible()
  return menu
}

export async function logoutThroughUi(page: Page) {
  const menu = await openAccountMenu(page)
  await menu.getByRole('menuitem', { name: 'Đăng xuất' }).click()
  await expect(page).toHaveURL(/\/login\?loggedOut=1$/)
}

/** Vùng thanh bên đang dùng được: thanh bên của máy tính, hoặc ngăn kéo vừa mở trên điện thoại. */
export async function openSidebar(page: Page) {
  await expect(page.getByTestId('app-shell')).toBeVisible()
  const opener = page.getByTestId('open-sidebar')
  if (await opener.isVisible()) {
    await opener.click()
    const drawer = page.getByRole('dialog', { name: 'Thanh bên' })
    await expect(drawer).toBeVisible()
    return drawer
  }
  return page.getByTestId('sidebar')
}

/** Đi tới một mục chức năng (Trang chính, Thư viện…) bằng thanh bên. */
export async function navigateTo(page: Page, label: string) {
  const sidebar = await openSidebar(page)
  await sidebar.getByRole('navigation', { name: 'Chức năng' }).getByRole('link', { name: label, exact: true }).click()
}

/** Bảo mật, Thiết bị, Quyền riêng tư là trang của tài khoản: mở từ Cài đặt trong trình đơn tài khoản. */
export async function openAccountPage(page: Page, label: 'Bảo mật' | 'Thiết bị' | 'Quyền riêng tư') {
  const menu = await openAccountMenu(page)
  await menu.getByRole('menuitem', { name: 'Cài đặt' }).click()
  await page.getByRole('dialog', { name: 'Cài đặt' }).getByRole('link', { name: label }).click()
}
