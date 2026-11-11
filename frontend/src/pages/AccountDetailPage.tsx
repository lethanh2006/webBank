import { ArrowLeftOutlined } from '@ant-design/icons'
import { Alert, Button, Card, Descriptions, Flex, Result, Skeleton, Table, Typography } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import dayjs from 'dayjs'
import { useNavigate, useParams } from 'react-router'
import { type Transaction, useAccount, useTransactions } from '@/features/accounts/api'
import { ApiError } from '@/lib/http'
import { formatAccountNo, formatMoney } from '@/lib/money'

const columns: ColumnsType<Transaction> = [
  {
    title: 'Thời gian',
    dataIndex: 'createdAt',
    width: 160,
    render: (value: string) => dayjs(value).format('HH:mm DD/MM/YYYY'),
  },
  {
    title: 'Nội dung',
    key: 'description',
    render: (_, tx) => (
      <Flex vertical>
        <span>{tx.description}</span>
        <Typography.Text type="secondary" style={{ fontSize: 12 }}>
          {tx.reference}
        </Typography.Text>
      </Flex>
    ),
  },
  {
    title: 'Đối ứng',
    key: 'counterparty',
    responsive: ['md'],
    render: (_, tx) =>
      tx.counterpartyName ? (
        <Flex vertical>
          <span>{tx.counterpartyName}</span>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            {tx.counterpartyAccountNo && formatAccountNo(tx.counterpartyAccountNo)}
          </Typography.Text>
        </Flex>
      ) : null,
  },
  {
    title: 'Số tiền',
    key: 'amount',
    align: 'right',
    render: (_, tx) => (
      <Typography.Text type={tx.direction === 'CREDIT' ? 'success' : 'danger'} strong>
        {tx.direction === 'CREDIT' ? '+' : '−'}
        {formatMoney(tx.amount)}
      </Typography.Text>
    ),
  },
  {
    title: 'Số dư sau giao dịch',
    key: 'balanceAfter',
    align: 'right',
    responsive: ['sm'],
    render: (_, tx) => formatMoney(tx.balanceAfter),
  },
]

export function AccountDetailPage() {
  const { accountNo = '' } = useParams()
  const navigate = useNavigate()
  const account = useAccount(accountNo)
  const transactions = useTransactions(accountNo)

  if (account.error instanceof ApiError && account.error.status === 404) {
    return (
      <Result
        status="404"
        title="Không tìm thấy tài khoản"
        subTitle="Tài khoản không tồn tại hoặc không thuộc về bạn."
        extra={<Button onClick={() => void navigate('/')}>Về trang chủ</Button>}
      />
    )
  }

  const rows = transactions.data?.pages.flatMap((page) => page.items) ?? []

  return (
    <Flex vertical gap={16}>
      <Button type="link" icon={<ArrowLeftOutlined />} style={{ alignSelf: 'flex-start', padding: 0 }}
        onClick={() => void navigate('/')}>
        Tài khoản của bạn
      </Button>

      <Card>
        {account.isPending ? (
          <Skeleton active paragraph={{ rows: 2 }} />
        ) : account.isError ? (
          <Alert type="error" showIcon title={account.error.message} />
        ) : (
          <Descriptions
            title={formatAccountNo(account.data.accountNo)}
            column={{ xs: 1, sm: 2, lg: 3 }}
            items={[
              { key: 'name', label: 'Chủ tài khoản', children: account.data.name },
              { key: 'available', label: 'Số dư khả dụng', children: <strong>{formatMoney(account.data.availableBalance)}</strong> },
              { key: 'ledger', label: 'Số dư sổ sách', children: formatMoney(account.data.ledgerBalance) },
            ]}
          />
        )}
      </Card>

      <Card title="Lịch sử giao dịch">
        <Table<Transaction>
          rowKey="id"
          columns={columns}
          dataSource={rows}
          loading={transactions.isPending}
          pagination={false}
          scroll={{ x: 'max-content' }}
        />
        {transactions.hasNextPage && (
          <Flex justify="center" style={{ marginTop: 16 }}>
            <Button loading={transactions.isFetchingNextPage} onClick={() => void transactions.fetchNextPage()}>
              Tải thêm
            </Button>
          </Flex>
        )}
      </Card>
    </Flex>
  )
}
