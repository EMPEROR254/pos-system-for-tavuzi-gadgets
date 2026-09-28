// The till's cart and paused ("held") sales. Saved in this browser, so a page
// reload or a flaky connection never loses a customer's cart, and a cashier
// can pause a sale, serve the next customer, then pick it up again.
import { create } from 'zustand'
import { persist } from 'zustand/middleware'
import type { SellableProduct } from '../../services/products'
import type { PriceTier } from '../../services/sales'
import { randomId } from '../../utils/id'
import type { CartLine } from './cartMath'

export type Cart = {
  /** Sent with checkout so a retried sale is never saved twice. */
  clientRef: string
  priceTier: PriceTier
  lines: CartLine[]
  customerName: string
  customerPhone: string
}

export type HeldSale = { id: string; label: string; heldAt: string; cart: Cart }

type CartState = {
  cart: Cart
  held: HeldSale[]
  add: (product: SellableProduct, unitId?: number) => void
  update: (key: string, changes: Partial<Pick<CartLine, 'quantity' | 'unitId' | 'discount' | 'serials'>>) => void
  remove: (key: string) => void
  setTier: (tier: PriceTier) => void
  setCustomer: (name: string, phone: string) => void
  clear: () => void
  hold: (label: string) => void
  resume: (id: string) => void
  discardHeld: (id: string) => void
}

export const emptyCart = (): Cart => ({
  clientRef: randomId(),
  priceTier: 'RETAIL',
  lines: [],
  customerName: '',
  customerPhone: '',
})

export const useCart = create<CartState>()(
  persist(
    (set, get) => ({
      cart: emptyCart(),
      held: [],
      add: (product, unitId) =>
        set(({ cart }) => {
          const unit = unitId ?? product.units[0].id
          // Scanning the same item again adds one more - except for phones etc.,
          // where each unit needs its own IMEI anyway, so a separate count is clearer.
          const same = cart.lines.find((l) => l.product.id === product.id && l.unitId === unit)
          if (same) {
            return {
              cart: {
                ...cart,
                lines: cart.lines.map((l) => (l === same ? { ...l, quantity: l.quantity + 1 } : l)),
              },
            }
          }
          const line: CartLine = { key: randomId(), product, unitId: unit, quantity: 1, discount: 0, serials: [] }
          return { cart: { ...cart, lines: [...cart.lines, line] } }
        }),
      update: (key, changes) =>
        set(({ cart }) => ({
          cart: { ...cart, lines: cart.lines.map((l) => (l.key === key ? { ...l, ...changes } : l)) },
        })),
      remove: (key) => set(({ cart }) => ({ cart: { ...cart, lines: cart.lines.filter((l) => l.key !== key) } })),
      setTier: (priceTier) => set(({ cart }) => ({ cart: { ...cart, priceTier } })),
      setCustomer: (customerName, customerPhone) =>
        set(({ cart }) => ({ cart: { ...cart, customerName, customerPhone } })),
      clear: () => set({ cart: emptyCart() }),
      hold: (label) => {
        const { cart, held } = get()
        if (cart.lines.length === 0) return
        const sale: HeldSale = { id: randomId(), label, heldAt: new Date().toISOString(), cart }
        set({ held: [sale, ...held], cart: emptyCart() })
      },
      resume: (id) => {
        const { cart, held } = get()
        const sale = held.find((h) => h.id === id)
        if (!sale) return
        // Whatever is in the cart now is held, so nothing is ever lost.
        const others = held.filter((h) => h.id !== id)
        const parked =
          cart.lines.length > 0
            ? [{ id: randomId(), label: 'Held automatically', heldAt: new Date().toISOString(), cart }, ...others]
            : others
        set({ cart: sale.cart, held: parked })
      },
      discardHeld: (id) => set(({ held }) => ({ held: held.filter((h) => h.id !== id) })),
    }),
    { name: 'tavuzi-till', version: 1 },
  ),
)
