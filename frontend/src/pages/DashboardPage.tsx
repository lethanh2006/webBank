import { Alert, Card, Descriptions, Skeleton, Tag } from 'antd'
import dayjs from 'dayjs'
import { usePing } from '@/features/system/api'

export function DashboardPage() {
  const ping = usePing()

  return (
    <Card title="Trạng thái hệ thống" style={{ maxWidth: 560 }}>
      {ping.isPending ? (
        <Skeleton active paragraph={{ rows: 2 }} />
      ) : ping.isError ? (
        <Alert
          type="error"
          showIcon
          title="Không kết nối được backend"
          description={
            <>
              {ping.error.message}. Hãy chạy <code>docker compose up -d</code> rồi{' '}
              <code>cd backend &amp;&amp; ./mvnw spring-boot:run</code>.
            </>
          }
        />
      ) : (
        <Descriptions
          column={1}
          items={[
            {
              key: 'status',
              label: 'Backend',
              children: <Tag color="success">{ping.data.status}</Tag>,
            },
            { key: 'service', label: 'Service', children: ping.data.service },
            {
              key: 'time',
              label: 'Giờ máy chủ',
              children: dayjs(ping.data.serverTime).format('HH:mm:ss DD/MM/YYYY'),
            },
          ]}
        />
      )}
    </Card>
  )
}
