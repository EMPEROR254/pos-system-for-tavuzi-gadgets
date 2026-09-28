import { beforeEach, describe, expect, it } from 'vitest'
import type { SellableProduct } from '../../services/products'
import { cable, phone } from '../../test/fixtures'
import { cartProblems, cartTotals, type CartLine } from './cartMath'
import { useCart } from './cartStore'
import { paymentProblem, type PaymentRow } from './PaymentDialog'

const line = (product: SellableProduct, changes: Partial<CartLine> = {}): CartLine => ({
  key: String(product.id),
  product,
  unitId: product.units[0].id,
  quantity: 1,
  discount: 0,
  serials: [],
  ...changes,
})

describe('cart totals', () => {
  it('adds lines and works out the VAT inside the prices', () => {
    const totals = cartTotals([line(phone), line(cable, { quantity: 2 })], 'RETAIL')
    expect(totals.total).toBe(12000)
    expect(totals.vat).toBe(1600) // 11600 × 16/116; the cable is exempt
    expect(totals.items).toBe(3)
  })

  it('uses wholesale prices, falling back to retail', () => {
    const totals = cartTotals([line(phone), line(cable)], 'WHOLESALE')
    expect(totals.total).toBe(11200)
    expect(cartTotals([line(cable, { unitId: 22, quantity: 2 })], 'WHOLESALE').total).toBe(6000)
  })

  it('takes discounts off the line and its VAT', () => {
    const totals = cartTotals([line(phone, { discount: 600 })], 'RETAIL')
    expect(totals.discount).toBe(600)
    expect(totals.total).toBe(11000)
    expect(totals.vat).toBe(1517.24)
  })

  it('asks for one IMEI per phone', () => {
    expect(cartProblems([line(phone, { quantity: 2, serials: ['490154203237518'] })], 'RETAIL')).toEqual([
      'Tecno Spark 20: enter 2 IMEIs.',
    ])
    expect(cartProblems([line(phone, { serials: ['490154203237518'] })], 'RETAIL')).toEqual([])
  })
})

describe('paused sales', () => {
  beforeEach(() => useCart.getState().clear())

  it('pauses a sale, serves the next customer, then resumes it', () => {
    const cart = useCart.getState()
    cart.add(cable)
    cart.add(cable)
    expect(useCart.getState().cart.lines[0].quantity).toBe(2)
    const firstRef = useCart.getState().cart.clientRef

    useCart.getState().hold('Mama Njeri')
    expect(useCart.getState().cart.lines).toHaveLength(0)
    expect(useCart.getState().cart.clientRef).not.toBe(firstRef)

    useCart.getState().add(phone)
    const held = useCart.getState().held.find((h) => h.label === 'Mama Njeri')!
    useCart.getState().resume(held.id)

    // The resumed cart is back (same sale id), and the phone cart was paused, not lost.
    expect(useCart.getState().cart.clientRef).toBe(firstRef)
    expect(useCart.getState().cart.lines[0].product.name).toBe('USB cable')
    expect(useCart.getState().held.some((h) => h.cart.lines[0].product.name === 'Tecno Spark 20')).toBe(true)
  })
})

describe('payment rules', () => {
  const row = (method: PaymentRow['method'], amount: string, reference = ''): PaymentRow => ({
    key: Math.random(),
    method,
    amount,
    reference,
  })

  it('accepts split payments with change on cash only', () => {
    expect(paymentProblem([row('MPESA_TILL', '1000', 'SJK4H7XYZ1'), row('CASH', '1000')], 1500)).toBeNull()
    expect(paymentProblem([row('CARD', '2000')], 1500)).toMatch(/change is only given on cash/)
  })

  it('needs a proper M-Pesa code and the full amount', () => {
    expect(paymentProblem([row('MPESA_PAYBILL', '1500', 'ABC')], 1500)).toMatch(/10-character/)
    expect(paymentProblem([row('CASH', '1000')], 1500)).toBe('KES 500 still to pay.')
  })
})
