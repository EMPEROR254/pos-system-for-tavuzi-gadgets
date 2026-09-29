import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as sales from '../../services/sales'
import { renderApp } from '../../test/renderApp'

vi.mock('../../services/sales', async (original) => ({
  ...(await original<typeof sales>()),
  getReceipt: vi.fn(),
  returnItems: vi.fn(),
}))

const receipt: sales.Receipt = {
  shopName: 'TAVUZI GADGETS',
  shopDetails: ['Resma Plaza, Nakuru', 'KRA PIN: P051234567X'],
  paymentInstructions: ['M-Pesa Buy Goods Till: 123456', 'M-Pesa Paybill: 654321 Acc: TAVUZI'],
  returnPolicy: "No returns or exchanges without the owner's approval.",
  footer: ['Thank you for shopping with us!'],
  etims: {
    status: 'SENT',
    invoiceNo: 7,
    scuReceiptNo: 101,
    totalReceiptNo: 101,
    internalData: 'INTRL101',
    signature: 'SIGN101',
    scuId: 'KRACU0100000001',
    mrcNo: 'WIS001',
    scuDate: '20260928101500',
    verifyUrl: 'https://etims-sbx.kra.go.ke/common/link/etims/receipt/indexEtimsReceiptData?Data=P051234567X00SIGN101',
  },
  vatBreakdown: [
    { code: 'A', ratePercent: 0, taxable: 400, vat: 0, gross: 400 },
    { code: 'B', ratePercent: 16, taxable: 10000, vat: 1600, gross: 11600 },
  ],
  sale: {
    id: 7,
    receiptNo: 'TG000007',
    createdAt: '2026-09-28T07:00:00Z',
    cashierName: 'Mary Wanjiru',
    status: 'COMPLETED',
    priceTier: 'RETAIL',
    customerName: null,
    customerPhone: null,
    subtotal: 12000,
    discountTotal: 0,
    vatTotal: 1600,
    total: 12000,
    paidTotal: 12000,
    changeDue: 0,
    lines: [
      {
        id: 70,
        productName: 'Tecno Spark 20',
        unitName: 'Piece',
        quantity: 1,
        returnedQuantity: 0,
        unitPrice: 11600,
        discount: 0,
        lineTotal: 11600,
        vatCode: 'B',
        vatRate: 16,
        vatAmount: 1600,
        warrantyEndsAt: '2027-09-28T07:00:00Z',
        serials: [{ serial: '490154203237518', returned: false }],
      },
    ],
    payments: [{ method: 'MPESA_TILL', label: 'M-Pesa Till', amount: 12000, reference: 'SJK4H7XYZ1' }],
  },
}

beforeEach(() => vi.mocked(sales.getReceipt).mockResolvedValue(receipt))

describe('ReceiptPage', () => {
  it('shows what the spec asks for on the receipt', async () => {
    renderApp('/sales/7/receipt?new=1', 'CASHIER')
    const r = await screen.findByRole('article', { name: 'Receipt TG000007' })
    expect(within(r).getByText('M-Pesa Buy Goods Till: 123456')).toBeInTheDocument()
    expect(within(r).getByText('M-Pesa Paybill: 654321 Acc: TAVUZI')).toBeInTheDocument()
    expect(within(r).getByText(/without the owner's approval/)).toBeInTheDocument()
    expect(within(r).getByText('IMEI/SN: 490154203237518')).toBeInTheDocument()
    expect(within(r).getByText(/Warranty until/)).toBeInTheDocument()
    expect(within(r).getByText('B VAT 16% on 10,000')).toBeInTheDocument()
    expect(within(r).getByText('M-Pesa Till SJK4H7XYZ1')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Next sale' })).toHaveAttribute('href', '/sell')
    // KRA eTIMS details and a QR code customers can scan to check the invoice with KRA.
    expect(within(r).getByText('KRACU0100000001')).toBeInTheDocument()
    expect(within(r).getByText('SIGN101')).toBeInTheDocument()
    expect(await within(r).findByRole('img', { name: 'QR code to check this invoice with KRA' })).toBeInTheDocument()
  })

  it('sends a cashier return with the owner approval', async () => {
    vi.mocked(sales.returnItems).mockResolvedValue({
      id: 1,
      receiptNo: 'TG000007',
      refundAmount: 11600,
      vatAmount: 1600,
      status: 'PARTLY_RETURNED',
    })
    renderApp('/sales/7/receipt', 'CASHIER')
    await userEvent.click(await screen.findByRole('button', { name: 'Return items…' }))
    const dialog = screen.getByRole('dialog')
    const qty = within(dialog).getByLabelText('Quantity of Tecno Spark 20 to return')
    await userEvent.clear(qty)
    await userEvent.type(qty, '1')
    await userEvent.type(within(dialog).getByLabelText('Reason'), 'Faulty screen')
    await userEvent.type(within(dialog).getByLabelText('Their username'), 'owner')
    await userEvent.type(within(dialog).getByLabelText('Their password'), 'owner-pass')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save return' }))

    expect(await screen.findByText(/Give back KES 11,600 by Cash/)).toBeInTheDocument()
    expect(sales.returnItems).toHaveBeenCalledWith(7, {
      lines: [{ saleLineId: 70, quantity: 1, serials: undefined }],
      reason: 'Faulty screen',
      refundMethod: 'CASH',
      approval: { username: 'owner', password: 'owner-pass' },
    })
  })
})
