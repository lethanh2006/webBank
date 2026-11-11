/** Money as sent by the API: the amount is a decimal string, never a JS number. */
export interface MoneyView {
  amount: string
  currency: string
}

const formatters = new Map<string, Intl.NumberFormat>()

function formatterFor(currency: string) {
  let formatter = formatters.get(currency)
  if (!formatter) {
    formatter = new Intl.NumberFormat('vi-VN', { style: 'currency', currency })
    formatters.set(currency, formatter)
  }
  return formatter
}

/** Display only. Integer amounts go through BigInt so no digit is ever lost. */
export function formatMoney(money: MoneyView): string {
  const formatter = formatterFor(money.currency)
  return /^-?\d+$/.test(money.amount)
    ? formatter.format(BigInt(money.amount))
    : formatter.format(Number(money.amount))
}

/** "1500000" -> "1.500.000" (Vietnamese thousands separator) for amount inputs. */
export function groupThousands(value: string | number | undefined): string {
  if (value === undefined || value === '') return ''
  return String(value).replace(/\B(?=(\d{3})+(?!\d))/g, '.')
}

export function ungroupThousands(value: string | undefined): string {
  return (value ?? '').replace(/\./g, '')
}

/** "100000000016" -> "1000 0000 0016", easier to read and to compare. */
export function formatAccountNo(accountNo: string): string {
  return /^\d{12}$/.test(accountNo) ? accountNo.replace(/(\d{4})(?=\d)/g, '$1 ') : accountNo
}
