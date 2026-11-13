import { Alert, Button, Flex, Spin } from 'antd'
import { Outlet } from 'react-router'
import { useDemoCustomers } from '@/features/demo/api'
import { useDemoCustomerId } from '@/lib/demoIdentity'
import { ApiError } from '@/lib/http'

/** Don't mount banking screens until the selected demo identity is available. */
export function DemoAccess() {
  const customers = useDemoCustomers()
  const customerId = useDemoCustomerId()

  if (customers.isPending) {
    return (
      <Flex vertical gap={16} role="status" aria-live="polite">
        <Alert
          type="info"
          showIcon
          title="Đang tải khách hàng demo…"
          description="Máy chủ có thể cần 1–2 phút để khởi động sau thời gian nghỉ. Trang sẽ tự kết nối lại."
        />
        <Spin />
      </Flex>
    )
  }

  if (!customers.data?.length) {
    const requestId = customers.error instanceof ApiError ? customers.error.problem?.requestId : undefined
    return (
      <Alert
        type={customers.isError ? 'error' : 'warning'}
        showIcon
        title={customers.isError ? 'Không tải được khách hàng demo' : 'Chưa có khách hàng demo'}
        description={
          <>
            {customers.isError
              ? 'Không lấy được dữ liệu để bắt đầu. Vui lòng thử kết nối lại.'
              : 'Máy chủ đã phản hồi nhưng chưa có dữ liệu khách hàng. Vui lòng thử lại sau.'}
            {requestId && <div>Mã yêu cầu: {requestId}</div>}
          </>
        }
        action={<Button onClick={() => void customers.refetch()}>Thử lại</Button>}
      />
    )
  }

  if (!customers.data.some((customer) => String(customer.id) === customerId)) {
    return <Spin tip="Đang chọn khách hàng demo…"><div style={{ minHeight: 80 }} /></Spin>
  }

  // Changing identity must discard the previous customer's transfer form and draft.
  return (
    <Flex vertical gap={16}>
      {customers.isError && (
        <Alert
          type="warning"
          showIcon
          title="Chưa cập nhật được danh sách khách hàng"
          description="Bạn vẫn có thể tiếp tục với khách hàng hiện tại."
          action={<Button onClick={() => void customers.refetch()}>Thử lại</Button>}
        />
      )}
      <Outlet key={customerId} />
    </Flex>
  )
}
