import { App, Alert, Button, Card, Descriptions, Flex, Form, Input, InputNumber, Result, Select, Steps, Tag, Typography } from 'antd'
import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router'
import { lookupHolder, useAccounts } from '@/features/accounts/api'
import { type TransferResult, useCreateTransfer } from '@/features/transfers/api'
import { type TransferDraft, clearTransferDraft, readTransferDraft, saveTransferDraft } from '@/features/transfers/draftStore'
import { useDemoCustomerId } from '@/lib/demoIdentity'
import { ApiError } from '@/lib/http'
import { formatAccountNo, formatMoney, groupThousands, ungroupThousands } from '@/lib/money'

interface FormValues {
  sourceAccountNo: string
  destinationAccountNo: string
  amount: string
  description?: string
}

export function TransferPage() {
  const customerId = useDemoCustomerId()
  const [draft, setDraft] = useState<TransferDraft | undefined>(() => readTransferDraft(customerId))
  const [step, setStep] = useState(draft ? 1 : 0)
  const [now, setNow] = useState(Date.now)
  const [result, setResult] = useState<TransferResult>()
  const [form] = Form.useForm<FormValues>()
  const [checking, setChecking] = useState(false)
  const accounts = useAccounts()
  const transfer = useCreateTransfer()
  const navigate = useNavigate()
  const { message } = App.useApp()
  const draftCreatedAt = draft?.createdAt

  useEffect(() => {
    if (draftCreatedAt === undefined) return
    const timer = setTimeout(() => setNow(Date.now()), Math.max(0, draftCreatedAt + 24 * 60 * 60 * 1000 - Date.now()))
    return () => clearTimeout(timer)
  }, [draftCreatedAt])

  const reviewTransfer = async (values: FormValues) => {
    setChecking(true)
    try {
      const holder = await lookupHolder(values.destinationAccountNo)
      const nextDraft: TransferDraft = {
        request: {
          sourceAccountNo: values.sourceAccountNo,
          destinationAccountNo: values.destinationAccountNo,
          amount: values.amount,
          description: values.description?.trim() || undefined,
        },
        holder,
        idempotencyKey: crypto.randomUUID(),
        createdAt: Date.now(),
        submitted: false,
      }
      if (!customerId) return
      saveTransferDraft(customerId, nextDraft)
      setDraft(nextDraft)
      transfer.reset()
      setStep(1)
    } catch (error) {
      form.setFields([
        {
          name: 'destinationAccountNo',
          errors: [error instanceof ApiError ? error.message : 'Không tra cứu được tài khoản nhận'],
        },
      ])
    } finally {
      setChecking(false)
    }
  }

  const confirm = () => {
    if (!draft || !customerId) return
    if (Date.now() - draft.createdAt >= 24 * 60 * 60 * 1000) {
      setNow(Date.now())
      return
    }
    // Same key on every click: if the first attempt reached the server, a retry
    // returns its stored result instead of moving the money a second time.
    const submitted = { ...draft, submitted: true }
    saveTransferDraft(customerId, submitted)
    setDraft(submitted)
    transfer.mutate(
      { customerId, request: draft.request, idempotencyKey: draft.idempotencyKey },
      {
        onSuccess: (value) => {
          setResult(value)
          setStep(2)
        },
      },
    )
  }

  const startOver = () => {
    if (customerId) clearTransferDraft(customerId)
    form.resetFields()
    transfer.reset()
    setDraft(undefined)
    setResult(undefined)
    setStep(0)
  }

  const error = transfer.error instanceof ApiError ? transfer.error : undefined
  const unresolved = draft?.submitted && (!error || error.retryable)
  // The server retains keys for 24 hours. Never resubmit an older stored intent automatically.
  const expired = draft !== undefined && now - draft.createdAt >= 24 * 60 * 60 * 1000

  if (expired && step === 1) {
    return (
      <Result
        status="warning"
        title="Giao dịch cũ cần được đối chiếu"
        subTitle="Yêu cầu đã quá thời gian thử lại an toàn. Hãy kiểm tra sao kê trước khi tạo giao dịch mới."
        extra={[
          <Button key="statement" onClick={() => void navigate(`/accounts/${draft.request.sourceAccountNo}`)}>Xem sao kê</Button>,
          <Button key="new" onClick={startOver}>Đã kiểm tra, tạo giao dịch mới</Button>,
        ]}
      />
    )
  }

  return (
    <Card style={{ maxWidth: 720 }}>
      <Steps
        current={step}
        style={{ marginBottom: 32 }}
        items={[{ title: 'Thông tin' }, { title: 'Xác nhận' }, { title: 'Kết quả' }]}
      />

      {step === 0 && (
        <Form<FormValues>
          form={form}
          layout="vertical"
          initialValues={draft?.request}
          onFinish={(values) => void reviewTransfer(values)}
          requiredMark="optional"
        >
          <Form.Item name="sourceAccountNo" label="Từ tài khoản" rules={[{ required: true, message: 'Chọn tài khoản nguồn' }]}>
            <Select
              loading={accounts.isPending}
              placeholder="Chọn tài khoản nguồn"
              options={accounts.data
                ?.filter((a) => a.status === 'ACTIVE')
                .map((a) => ({
                  value: a.accountNo,
                  label: `${formatAccountNo(a.accountNo)} · ${formatMoney(a.availableBalance)}`,
                }))}
            />
          </Form.Item>

          <Form.Item
            name="destinationAccountNo"
            label="Đến số tài khoản"
            extra="Số tài khoản 12 chữ số, chữ số cuối là số kiểm tra."
            rules={[
              { required: true, message: 'Nhập số tài khoản nhận' },
              { pattern: /^\d{12}$/, message: 'Số tài khoản gồm 12 chữ số' },
            ]}
          >
            <Input inputMode="numeric" maxLength={12} placeholder="VD: 100000000032" />
          </Form.Item>

          <Form.Item
            name="amount"
            label="Số tiền"
            rules={[
              { required: true, message: 'Nhập số tiền' },
              { pattern: /^[1-9]\d*$/, message: 'Số tiền VND phải là số nguyên dương' },
            ]}
          >
            <InputNumber<string>
              stringMode
              min="1"
              style={{ width: '100%' }}
              suffix="VND"
              formatter={(value) => groupThousands(value)}
              parser={(value) => ungroupThousands(value)}
            />
          </Form.Item>

          <Form.Item name="description" label="Nội dung">
            <Input maxLength={140} showCount placeholder="Chuyển tiền nội bộ" />
          </Form.Item>

          <Button type="primary" htmlType="submit" loading={checking} block>
            Tiếp tục
          </Button>
        </Form>
      )}

      {step === 1 && draft && (
        <Flex vertical gap={16}>
          <Descriptions
            bordered
            column={1}
            items={[
              { key: 'from', label: 'Từ tài khoản', children: formatAccountNo(draft.request.sourceAccountNo) },
              { key: 'to', label: 'Đến tài khoản', children: formatAccountNo(draft.holder.accountNo) },
              { key: 'holder', label: 'Người nhận', children: <strong>{draft.holder.name}</strong> },
              {
                key: 'amount',
                label: 'Số tiền',
                children: <strong>{formatMoney({ amount: draft.request.amount, currency: draft.holder.currency })}</strong>,
              },
              { key: 'description', label: 'Nội dung', children: draft.request.description ?? 'Chuyển tiền nội bộ' },
            ]}
          />

          {error && (
            <Alert
              type={error.retryable ? 'warning' : 'error'}
              showIcon
              title={error.message}
              description={
                error.retryable
                  ? 'Có thể giao dịch chưa tới được máy chủ. Bấm "Thử lại": hệ thống dùng lại cùng mã chống trùng nên tiền không bị trừ hai lần.'
                  : `Mã lỗi: ${error.code ?? error.status}`
              }
            />
          )}

          {unresolved && !error && !transfer.isPending && (
            <Alert
              type="warning"
              showIcon
              title="Giao dịch trước chưa xác định được kết quả"
              description="Bấm Thử lại để lấy kết quả bằng cùng mã chống trùng. Không tạo giao dịch mới cho đến khi đã đối chiếu."
            />
          )}

          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            Idempotency-Key: <Typography.Text code>{draft.idempotencyKey}</Typography.Text>
          </Typography.Text>

          <Flex gap={12} justify="end" wrap>
            <Button onClick={() => setStep(0)} disabled={transfer.isPending || unresolved}>
              Quay lại
            </Button>
            <Button type="primary" loading={transfer.isPending} onClick={confirm}>
              {unresolved ? 'Thử lại' : 'Xác nhận chuyển tiền'}
            </Button>
          </Flex>
        </Flex>
      )}

      {step === 2 && result && (
        <Result
          status="success"
          title={`Đã chuyển ${formatMoney(result.transfer.amount)}`}
          subTitle={
            <Flex vertical align="center" gap={4}>
              <span>
                Tới {result.transfer.destinationName} · {formatAccountNo(result.transfer.destinationAccountNo)}
              </span>
              <Typography.Text copyable>{result.transfer.reference}</Typography.Text>
              {result.replayed && <Tag color="blue">Kết quả phát lại từ lần gửi trước (idempotent replay)</Tag>}
            </Flex>
          }
          extra={[
            <Button key="new" type="primary" onClick={startOver}>
              Giao dịch mới
            </Button>,
            <Button
              key="account"
              onClick={() => {
                void message.info('Số dư đã được cập nhật')
                void navigate(`/accounts/${result.transfer.sourceAccountNo}`)
              }}
            >
              Xem tài khoản nguồn
            </Button>,
          ]}
        />
      )}
    </Card>
  )
}
