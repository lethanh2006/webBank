import { expect, test, type Page } from '@playwright/test'

const customers = [
  { id: 1, cifNo: '1000001', fullName: 'Nguyễn Văn An' },
  { id: 2, cifNo: '1000002', fullName: 'Trần Thị Bình' },
]
const money = { amount: '50000000', currency: 'VND' }
const account = {
  accountNo: '100000000016', name: 'NGUYEN VAN AN', type: 'CURRENT',
  status: 'ACTIVE', ledgerBalance: money, availableBalance: money,
}

async function reviewTransfer(page: Page) {
  await page.route('**/api/v1/demo/customers', (route) => route.fulfill({ json: customers }))
  await page.route('**/api/v1/account-holders/*', (route) => route.fulfill({
    json: { accountNo: '100000000032', name: 'TRAN THI BINH', currency: 'VND' },
  }))
  await page.goto('/transfer')
  await page.getByLabel('Từ tài khoản').click()
  await page.getByLabel('Từ tài khoản').press('ArrowDown')
  await page.getByLabel('Từ tài khoản').press('Enter')
  await page.getByLabel('Đến số tài khoản', { exact: true }).fill('100000000032')
  await page.getByLabel('Số tiền', { exact: true }).fill('1000')
  await page.getByRole('button', { name: 'Tiếp tục', exact: true }).click()
  await expect(page.getByRole('button', { name: 'Xác nhận chuyển tiền', exact: true })).toBeVisible()
}

const completed = {
  reference: 'FT26111312345678', destinationAccountNo: '100000000032', destinationName: 'TRAN THI BINH',
  sourceAccountNo: account.accountNo, amount: { amount: '1000', currency: 'VND' },
}

test.beforeEach(async ({ page }) => {
  await page.route('**/api/v1/system/ping', (route) => route.fulfill({
    json: { status: 'UP', service: 'webbank', serverTime: '2026-11-13T03:00:00Z' },
  }))
  await page.route('**/api/v1/accounts', (route) => route.fulfill({ json: [account] }))
})

test('explains an empty customer list and recovers without a page reload', async ({ page }) => {
  let ready = false
  await page.route('**/api/v1/demo/customers', (route) => route.fulfill({ json: ready ? customers : [] }))
  await page.goto('/')
  await expect(page.getByText('Chưa có khách hàng demo')).toBeVisible()
  await expect(page.getByRole('button', { name: 'Chuyển tiền', exact: true })).toHaveCount(0)
  ready = true
  await page.getByRole('button', { name: 'Thử lại', exact: true }).click()
  await expect(page.getByText('Nguyễn Văn An · CIF 1000001')).toBeVisible()
  await expect(page.getByRole('heading', { name: '50.000.000 ₫' })).toBeVisible()
})

test('shows loading during a cold start and automatically recovers from a transient failure', async ({ page }) => {
  let calls = 0
  await page.route('**/api/v1/demo/customers', async (route) => {
    calls++
    if (calls === 1) await route.fulfill({ status: 503, json: { detail: 'Starting' } })
    else await route.fulfill({ json: customers })
  })
  await page.goto('/')
  await expect(page.getByText('Đang tải khách hàng demo…')).toBeVisible()
  await expect(page.getByRole('heading', { name: '50.000.000 ₫' })).toBeVisible()
  expect(calls).toBe(2)
})

test('surfaces customer API errors even when ping succeeds, with a working retry', async ({ page }) => {
  let ready = false
  await page.route('**/api/v1/demo/customers', (route) => ready
    ? route.fulfill({ json: customers })
    : route.fulfill({ status: 404, json: { detail: 'Demo profile unavailable', requestId: 'demo-request-123' } }))
  await page.goto('/transfer')
  await expect(page.getByText('Không tải được khách hàng demo')).toBeVisible()
  await expect(page.getByText('demo-request-123', { exact: false })).toBeVisible()
  ready = true
  await page.getByRole('button', { name: 'Thử lại', exact: true }).click()
  await expect(page.getByRole('button', { name: 'Tiếp tục', exact: true })).toBeVisible()
})

test('discards the previous customer transfer draft when switching identity', async ({ page }) => {
  await reviewTransfer(page)
  await page.getByRole('banner').getByRole('combobox').click()
  await page.getByRole('banner').getByRole('combobox').press('ArrowDown')
  await page.getByRole('banner').getByRole('combobox').press('Enter')
  await expect(page.getByRole('button', { name: 'Tiếp tục', exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Xác nhận chuyển tiền', exact: true })).toHaveCount(0)
})

test('prevents switching customers while a transfer is being submitted', async ({ page }) => {
  await reviewTransfer(page)
  let release!: () => void
  const held = new Promise<void>((resolve) => { release = resolve })
  await page.route('**/api/v1/transfers', async (route) => {
    await held
    await route.fulfill({ status: 201, json: completed })
  })
  await page.getByRole('button', { name: 'Xác nhận chuyển tiền', exact: true }).click()
  try {
    await expect(page.getByRole('banner').getByRole('combobox')).toBeDisabled()
  } finally {
    release()
  }
  await expect(page.getByText('Đã chuyển 1.000 ₫', { exact: true })).toBeVisible()
  await expect(page.getByRole('banner').getByRole('combobox')).toBeEnabled()
})

test('keeps the transfer confirmation after a failed background customer refresh', async ({ page }) => {
  await page.clock.install()
  await reviewTransfer(page)
  await page.route('**/api/v1/demo/customers', (route) => route.fulfill({ status: 404, json: { detail: 'Unavailable' } }))
  await page.clock.fastForward(31_000)
  await page.evaluate(() => window.dispatchEvent(new Event('visibilitychange')))
  await expect(page.getByText('Chưa cập nhật được danh sách khách hàng')).toBeVisible()
  await expect(page.getByRole('button', { name: 'Xác nhận chuyển tiền', exact: true })).toBeVisible()
})

test('restores an unresolved transfer after reload and retries with the same key', async ({ page }) => {
  await reviewTransfer(page)
  const keys: string[] = []
  await page.route('**/api/v1/transfers', async (route) => {
    keys.push(route.request().headers()['idempotency-key'])
    if (keys.length === 1) await route.abort('failed')
    else await route.fulfill({ status: 201, json: completed })
  })
  await page.getByRole('button', { name: 'Xác nhận chuyển tiền', exact: true }).click()
  await expect(page.getByRole('button', { name: /Thử lại/ })).toBeEnabled()
  await expect(page.getByRole('button', { name: 'Quay lại', exact: true })).toBeDisabled()
  await page.reload()
  await expect(page.getByRole('button', { name: /Thử lại/ })).toBeEnabled()
  await page.getByRole('button', { name: /Thử lại/ }).click()
  await expect(page.getByText('Đã chuyển 1.000 ₫', { exact: true })).toBeVisible()
  expect(keys).toHaveLength(2)
  expect(keys[1]).toBe(keys[0])
})

test('requires statement reconciliation instead of resubmitting a draft older than key retention', async ({ page }) => {
  await page.clock.install()
  await reviewTransfer(page)
  let posts = 0
  await page.route('**/api/v1/transfers', async (route) => {
    posts++
    await route.fulfill({ status: 201, json: completed })
  })
  await page.clock.fastForward(24 * 60 * 60 * 1000)
  await expect(page.getByText('Giao dịch cũ cần được đối chiếu', { exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Xác nhận chuyển tiền', exact: true })).toHaveCount(0)
  expect(posts).toBe(0)
})
