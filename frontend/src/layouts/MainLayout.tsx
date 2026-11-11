import { BankOutlined, DashboardOutlined, SwapOutlined } from '@ant-design/icons'
import { Layout, Menu, theme } from 'antd'
import { Outlet, useLocation, useNavigate } from 'react-router'
import { CustomerSwitcher } from '@/components/CustomerSwitcher'

const { Header, Sider, Content } = Layout

const menuItems = [
  { key: '/', icon: <DashboardOutlined />, label: 'Tổng quan' },
  { key: '/transfer', icon: <SwapOutlined />, label: 'Chuyển tiền' },
]

export function MainLayout() {
  const navigate = useNavigate()
  const { pathname } = useLocation()
  const { token } = theme.useToken()
  const selectedKey = pathname.startsWith('/transfer') ? '/transfer' : '/'

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
          selectedKeys={[selectedKey]}
          items={menuItems}
          onClick={({ key }) => void navigate(key)}
        />
      </Sider>
      <Layout>
        <Header
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'flex-end',
            paddingInline: 24,
            background: token.colorBgContainer,
            borderBottom: `1px solid ${token.colorBorderSecondary}`,
          }}
        >
          <CustomerSwitcher />
        </Header>
        <Content style={{ padding: 24 }}>
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  )
}
