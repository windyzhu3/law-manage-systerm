const { test, expect } = require('@playwright/test')
const { setupTodo } = require('./fixtures')

test('case assignment completion submits selected lawyer', async ({ page }) => {
  const capture = {}
  await setupTodo(page, {
    todo_id: 201,
    todo_no: 'TD201',
    template_code: 'CASE_ASSIGN',
    title: '案件分配',
    business_no: 'CS-001',
    status: 'SUBMITTED',
    priority: 'NORMAL',
    sla_status: 'NORMAL'
  }, capture, {
    businessType: 'CASE',
    businessId: 201,
    ui: { config: { fields: [{ key: 'lawyerId', type: 'user', label: '主办律师ID', required: true }] } }
  })

  await page.getByRole('button', { name: '完成' }).click()
  const lawyerField = page.locator('.el-form-item').filter({ hasText: '主办律师ID' })
  await expect(lawyerField).toBeVisible()
  await lawyerField.locator('input').fill('12')
  await page.getByRole('button', { name: '确认', exact: true }).click()

  await expect.poll(() => capture.body && capture.body.fields && capture.body.fields.lawyerId).toBe(12)
})
