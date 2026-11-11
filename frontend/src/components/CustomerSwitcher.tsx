import { UserOutlined } from '@ant-design/icons'
import { useQueryClient } from '@tanstack/react-query'
import { Select, Space, Tooltip } from 'antd'
import { useEffect } from 'react'
import { useDemoCustomers } from '@/features/demo/api'
import { setDemoCustomerId, useDemoCustomerId } from '@/lib/demoIdentity'

/** Phase 1 only: pick which demo customer the app acts as (replaced by login in phase 2). */
export function CustomerSwitcher() {
  const customers = useDemoCustomers()
  const customerId = useDemoCustomerId()
  const queryClient = useQueryClient()

  // Select the first demo customer automatically, or drop an id that no longer exists.
  useEffect(() => {
    const list = customers.data
    if (!list?.length) return
    if (!customerId || !list.some((c) => String(c.id) === customerId)) {
      setDemoCustomerId(String(list[0].id))
    }
  }, [customers.data, customerId])

  return (
    <Tooltip title="Danh tính giả lập cho giai đoạn 1, sẽ thay bằng đăng nhập Keycloak">
      <Space>
        <UserOutlined />
        <Select
          style={{ minWidth: 220 }}
          loading={customers.isPending}
          value={customerId ?? undefined}
          placeholder="Chọn khách hàng demo"
          options={customers.data?.map((c) => ({
            value: String(c.id),
            label: `${c.fullName} · CIF ${c.cifNo}`,
          }))}
          onChange={(value: string) => {
            setDemoCustomerId(value)
            queryClient.removeQueries({ queryKey: ['transactions'] })
          }}
        />
      </Space>
    </Tooltip>
  )
}
