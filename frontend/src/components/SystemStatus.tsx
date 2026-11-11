import { Badge, Typography } from 'antd'
import dayjs from 'dayjs'
import { usePing } from '@/features/system/api'

export function SystemStatus() {
  const ping = usePing()

  if (ping.isPending) return <Badge status="processing" text="Đang kiểm tra máy chủ…" />
  if (ping.isError) {
    return (
      <Badge
        status="error"
        text={
          <Typography.Text type="danger">
            {import.meta.env.DEV ? (
              <>
                Không kết nối được backend. Hãy chạy <code>docker compose up -d</code> và{' '}
                <code>./mvnw spring-boot:run</code>.
              </>
            ) : (
              'Máy chủ demo (gói miễn phí) đang khởi động sau thời gian nghỉ, thường mất khoảng 1 phút. Trang sẽ tự kết nối lại.'
            )}
          </Typography.Text>
        }
      />
    )
  }
  return (
    <Badge
      status="success"
      text={`Máy chủ hoạt động · ${dayjs(ping.data.serverTime).format('HH:mm:ss DD/MM/YYYY')}`}
    />
  )
}
