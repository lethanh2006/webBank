import { createBrowserRouter } from 'react-router'
import { MainLayout } from '@/layouts/MainLayout'
import { AccountDetailPage } from '@/pages/AccountDetailPage'
import { DashboardPage } from '@/pages/DashboardPage'
import { NotFoundPage } from '@/pages/NotFoundPage'
import { TransferPage } from '@/pages/TransferPage'

export const router = createBrowserRouter([
  {
    path: '/',
    element: <MainLayout />,
    children: [
      { index: true, element: <DashboardPage /> },
      { path: 'accounts/:accountNo', element: <AccountDetailPage /> },
      { path: 'transfer', element: <TransferPage /> },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
])
