import { SwapOutlined } from '@ant-design/icons'
import { Alert, Button, Col, Flex, Row, Skeleton, Typography } from 'antd'
import { useNavigate } from 'react-router'
import { AccountCard } from '@/components/AccountCard'
import { SystemStatus } from '@/components/SystemStatus'
import { useAccounts } from '@/features/accounts/api'
import { useDemoCustomerId } from '@/lib/demoIdentity'

export function DashboardPage() {
  const customerId = useDemoCustomerId()
  const accounts = useAccounts()
  const navigate = useNavigate()

  return (
    <Flex vertical gap={24}>
      <Flex justify="space-between" align="center" wrap gap={12}>
        <Typography.Title level={4} style={{ margin: 0 }}>
          Tài khoản của bạn
        </Typography.Title>
        <Button type="primary" icon={<SwapOutlined />} onClick={() => void navigate('/transfer')}>
          Chuyển tiền
        </Button>
      </Flex>

      {!customerId ? (
        <Alert type="info" showIcon title="Chọn một khách hàng demo ở góc trên bên phải để bắt đầu." />
      ) : accounts.isPending ? (
        <Skeleton active />
      ) : accounts.isError ? (
        <Alert
          type="error"
          showIcon
          title="Không tải được danh sách tài khoản"
          description={accounts.error.message}
          action={
            <Button size="small" onClick={() => void accounts.refetch()}>
              Thử lại
            </Button>
          }
        />
      ) : (
        <Row gutter={[16, 16]}>
          {accounts.data.map((account) => (
            <Col key={account.accountNo} xs={24} md={12} xl={8}>
              <AccountCard account={account} />
            </Col>
          ))}
        </Row>
      )}

      <SystemStatus />
    </Flex>
  )
}
