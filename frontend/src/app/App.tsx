import { QueryClientProvider } from '@tanstack/react-query'
import { App as AntApp, ConfigProvider, type ThemeConfig } from 'antd'
import viVN from 'antd/locale/vi_VN'
import dayjs from 'dayjs'
import 'dayjs/locale/vi'
import { RouterProvider } from 'react-router/dom'
import { queryClient } from './queryClient'
import { router } from './router'

dayjs.locale('vi')

const theme: ThemeConfig = {
  token: { colorPrimary: '#0b5cab', borderRadius: 8 },
}

export function App() {
  return (
    <ConfigProvider locale={viVN} theme={theme}>
      <AntApp>
        <QueryClientProvider client={queryClient}>
          <RouterProvider router={router} />
        </QueryClientProvider>
      </AntApp>
    </ConfigProvider>
  )
}
