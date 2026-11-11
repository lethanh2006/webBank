import { BankOutlined, DashboardOutlined } from '@ant-design/icons'
import { Layout, Menu, Typography, theme } from 'antd'
import { Outlet, useLocation, useNavigate } from 'react-router'

const { Header, Sider, Content } = Layout

const menuItems = [{ key: '/', icon: <DashboardOutlined />, label: 'Tổng quan' }]

export function MainLayout() {
  const navigate = useNavigate()
  const { pathname } = useLocation()
  const { token } = theme.useToken()

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Sider theme="light" breakpoint="lg" collapsedWidth={0}>
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 8,
            height: 64,
            paddingInline: 24,
            fontSize: 18,
            fontWeight: 600,
            color: token.colorPrimary,
          }}
        >
          <BankOutlined /> WebBank
        </div>
        <Menu
          mode="inline"
          selectedKeys={[pathname]}
          items={menuItems}
          onClick={({ key }) => void navigate(key)}
        />
      </Sider>
      <Layout>
        <Header
          style={{
            display: 'flex',
            alignItems: 'center',
            paddingInline: 24,
            background: token.colorBgContainer,
            borderBottom: `1px solid ${token.colorBorderSecondary}`,
          }}
        >
          <Typography.Title level={4} style={{ margin: 0 }}>
            Ngân hàng số WebBank
          </Typography.Title>
        </Header>
        <Content style={{ padding: 24 }}>
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  )
}
