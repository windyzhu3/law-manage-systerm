const { test, expect } = require('@playwright/test')
const { setupTodo } = require('./fixtures')

test('archive completion exposes decision archive number and material checklist', async ({ page }) => {
  const capture = {}
  await setupTodo(page, {
    todo_id: 301,
    todo_no: 'TD301',
    template_code: 'CASE_ARCHIVE_CONFIRM',
    title: '归档确认',
    business_no: 'CS-002',
    status: 'SUBMITTED',
    priority: 'NORMAL',
    sla_status: 'NORMAL'
  }, capture, {
    businessType: 'MATTER',
    businessId: 301,
    ui: {
      config: {
        fields: [
          {
            key: 'action',
            type: 'dict',
            label: '确认动作',
            required: true,
            options: [{ label: '确认归档', value: 'confirm' }]
          },
          { key: 'archiveNo', type: 'text', label: '归档编号', required: true },
          {
            key: 'materials',
            type: 'materialChecklist',
            label: '材料清单',
            requirements: [{ materialType: 'ARCHIVE_FILE', label: '归档材料', minCount: 1 }]
          }
        ]
      }
    }
  })

  await page.getByRole('button', { name: '完成' }).click()
  await expect(page.getByText('确认动作')).toBeVisible()
  await expect(page.getByText('归档编号')).toBeVisible()
  await expect(page.getByText('材料清单')).toBeVisible()
  await expect(page.getByRole('button', { name: '选择归档材料', exact: true })).toBeVisible()
})
