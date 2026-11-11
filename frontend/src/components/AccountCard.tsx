import { Card, Flex, Tag, Typography } from 'antd'
import { useNavigate } from 'react-router'
import type { Account } from '@/features/accounts/api'
import { formatAccountNo, formatMoney } from '@/lib/money'

const statusTag: Record<Account['status'], { color: string; label: string }> = {
  ACTIVE: { color: 'success', label: 'Hoạt động' },
  FROZEN: { color: 'warning', label: 'Phong tỏa' },
  CLOSED: { color: 'default', label: 'Đã đóng' },
}

export function AccountCard({ account }: { account: Account }) {
  const navigate = useNavigate()
  const status = statusTag[account.status]

  return (
    <Card hoverable onClick={() => void navigate(`/accounts/${account.accountNo}`)}>
      <Flex vertical gap={8}>
        <Flex justify="space-between" align="center">
          <Typography.Text type="secondary">Tài khoản thanh toán</Typography.Text>
          <Tag color={status.color}>{status.label}</Tag>
        </Flex>
        <Typography.Text strong copyable={{ text: account.accountNo }}>
          {formatAccountNo(account.accountNo)}
        </Typography.Text>
        <Typography.Text type="secondary">{account.name}</Typography.Text>
        <Typography.Title level={3} style={{ margin: 0 }}>
          {formatMoney(account.availableBalance)}
        </Typography.Title>
        <Typography.Text type="secondary">Số dư khả dụng</Typography.Text>
      </Flex>
    </Card>
  )
}
