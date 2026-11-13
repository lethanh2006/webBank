import { expect, test } from '@playwright/test'

// This test uses the local Spring Boot API and its seeded PostgreSQL, without API mocks.
test('transfers money, replays the same key once, and shows the real statement @backend', async ({ page, request }) => {
  const customersResponse = await request.get('/api/v1/demo/customers')
  expect(customersResponse.ok()).toBeTruthy()
  const customers = await customersResponse.json()
  expect(customers.length).toBeGreaterThanOrEqual(2)
  const headers = { 'X-Demo-Customer-Id': String(customers[0].id) }
  const receiverHeaders = { 'X-Demo-Customer-Id': String(customers[1].id) }
  const sourceResponse = await request.get('/api/v1/accounts', { headers })
  const destinationResponse = await request.get('/api/v1/accounts', { headers: receiverHeaders })
  expect(sourceResponse.ok()).toBeTruthy()
  expect(destinationResponse.ok()).toBeTruthy()
  const [source] = await sourceResponse.json()
  const [destination] = await destinationResponse.json()
  const initialSource = BigInt(source.availableBalance.amount)
  const initialDestination = BigInt(destination.availableBalance.amount)
  const description = `Kiểm thử demo ${Date.now()}`

  await page.addInitScript((id: string) => localStorage.setItem('webbank.demoCustomerId', id), String(customers[0].id))
  await page.goto('/transfer')
  await page.getByLabel('Từ tài khoản').click()
  const formattedSource = source.accountNo.replace(/(.{4})(?=.)/g, '$1 ')
  await page.locator('.ant-select-item-option').filter({ hasText: formattedSource }).click()
  await page.getByLabel('Đến số tài khoản', { exact: true }).fill(destination.accountNo)
  await page.getByLabel('Số tiền', { exact: true }).fill('1000')
  await page.getByRole('textbox', { name: 'Nội dung' }).fill(description)
  await page.getByRole('button', { name: 'Tiếp tục', exact: true }).click()
  await expect(page.getByText(destination.name, { exact: true })).toBeVisible()

  const posted = page.waitForResponse((response) => response.url().endsWith('/api/v1/transfers')
    && response.request().method() === 'POST')
  await page.getByRole('button', { name: 'Xác nhận chuyển tiền', exact: true }).click()
  const response = await posted
  expect(response.status()).toBe(201)
  await expect(page.getByText('Đã chuyển 1.000 ₫', { exact: true })).toBeVisible()
  const first = await response.json()
  expect(first.sourceAccountNo).toBe(source.accountNo)
  const key = response.request().headers()['idempotency-key']
  expect(key).toBeTruthy()
  const replay = await request.post('/api/v1/transfers', {
    headers: { ...headers, 'Idempotency-Key': key },
    data: response.request().postDataJSON(),
  })
  expect(replay.status()).toBe(201)
  expect(replay.headers()['idempotent-replayed']).toBe('true')
  expect(await replay.json()).toEqual(first)

  const afterSource = await request.get(`/api/v1/accounts/${source.accountNo}`, { headers })
  const afterDestination = await request.get(`/api/v1/accounts/${destination.accountNo}`, { headers: receiverHeaders })
  expect(BigInt((await afterSource.json()).availableBalance.amount)).toBe(initialSource - 1000n)
  expect(BigInt((await afterDestination.json()).availableBalance.amount)).toBe(initialDestination + 1000n)

  await page.getByRole('button', { name: 'Xem tài khoản nguồn', exact: true }).click()
  const row = page.getByRole('row').filter({ hasText: description })
  await expect(row).toBeVisible()
  await expect(row).toContainText(first.reference)
  await expect(row).toContainText('−1.000 ₫')
})
