// Cart totals shown on the till while selling. The server prices the sale again
// at checkout (it is the source of truth); these match its rules: prices include
// VAT, VAT per line = total × rate / (100 + rate), rounded to the cent.
// Amounts are handled in cents to avoid floating-point surprises.
import type { SellableProduct, Unit } from '../../services/products'
import type { PriceTier } from '../../services/sales'

export type CartLine = {
  key: string
  product: SellableProduct
  unitId: number
  quantity: number
  /** KES off the whole line. */
  discount: number
  serials: string[]
}

export type CartTotals = { subtotal: number; discount: number; vat: number; total: number; items: number }

const cents = (kes: number) => Math.round(kes * 100)

export function unitOf(line: CartLine): Unit {
  return line.product.units.find((u) => u.id === line.unitId) ?? line.product.units[0]
}

export function unitPrice(unit: Unit, tier: PriceTier): number {
  return tier === 'WHOLESALE' && unit.wholesalePrice != null ? unit.wholesalePrice : unit.retailPrice
}

/** How many IMEIs/serials a line needs (0 when the product doesn't track them). */
export function serialsNeeded(line: CartLine): number {
  return line.product.serialType === 'NONE' ? 0 : line.quantity * unitOf(line).factor
}

export function lineAmounts(line: CartLine, tier: PriceTier) {
  const gross = cents(unitPrice(unitOf(line), tier)) * line.quantity
  const discount = Math.min(cents(line.discount), gross)
  const total = gross - discount
  const rate = line.product.vatRate
  const vat = rate > 0 ? Math.round((total * rate) / (100 + rate)) : 0
  return { gross: gross / 100, discount: discount / 100, total: total / 100, vat: vat / 100 }
}

export function cartTotals(lines: CartLine[], tier: PriceTier): CartTotals {
  let subtotal = 0
  let discount = 0
  let vat = 0
  let total = 0
  let items = 0
  for (const line of lines) {
    const a = lineAmounts(line, tier)
    subtotal += cents(a.gross)
    discount += cents(a.discount)
    vat += cents(a.vat)
    total += cents(a.total)
    items += line.quantity
  }
  return { subtotal: subtotal / 100, discount: discount / 100, vat: vat / 100, total: total / 100, items }
}

/** Problems that stop checkout, e.g. a phone without its IMEI. */
export function cartProblems(lines: CartLine[], tier: PriceTier): string[] {
  const problems: string[] = []
  for (const line of lines) {
    const needed = serialsNeeded(line)
    const given = line.serials.filter((s) => s.trim()).length
    if (needed > 0 && given !== needed) {
      const kind = line.product.serialType === 'IMEI' ? 'IMEI' : 'serial number'
      problems.push(`${line.product.name}: enter ${needed} ${kind}${needed === 1 ? '' : 's'}.`)
    }
    if (line.discount > lineAmounts({ ...line, discount: 0 }, tier).gross) {
      problems.push(`${line.product.name}: the discount is bigger than the price.`)
    }
  }
  return problems
}
