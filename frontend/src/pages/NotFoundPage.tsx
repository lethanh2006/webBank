import { Button, Result } from 'antd'
import { useNavigate } from 'react-router'

export function NotFoundPage() {
  const navigate = useNavigate()

  return (
    <Result
      status="404"
      title="404"
      subTitle="Trang bạn tìm không tồn tại."
      extra={
        <Button type="primary" onClick={() => void navigate('/')}>
          Về trang chủ
        </Button>
      }
    />
  )
}
