// The customer on a sale: type a phone number (or name) to find someone who has
// bought before, or fill in a new one. A phone number is enough - the server adds
// the customer. Promotions need the customer's consent (the tick box).
import { useQuery } from '@tanstack/react-query'
import { displayPhone, lookupCustomers } from '../../services/customers'
import { inputClass } from '../../components/Field'
import { useDebounced } from '../../utils/useDebounced'
import type { Cart, CustomerFields } from './cartStore'

type Props = { cart: Cart; onChange: (changes: Partial<CustomerFields>) => void }

export function CustomerPicker({ cart, onChange }: Props) {
  const typed = useDebounced(cart.customerPhone.trim() || cart.customerName.trim(), 300)
  const matches = useQuery({
    queryKey: ['customer-lookup', typed],
    queryFn: () => lookupCustomers(typed),
    enabled: !cart.customerId && typed.length >= 3,
  })
  const hasPhone = cart.customerPhone.trim().length > 0

  if (cart.customerId) {
    return (
      <div className="space-y-2 text-sm">
        <div className="flex items-center justify-between gap-2 rounded-xl bg-background px-3 py-2">
          <div>
            <p className="font-medium">{cart.customerName}</p>
            <p className="text-xs text-muted">{displayPhone(cart.customerPhone)}</p>
          </div>
          <button
            type="button"
            onClick={() => onChange({ customerId: null, customerName: '', customerPhone: '', marketingOptIn: false })}
            className="min-h-11 rounded-lg px-2 text-muted hover:text-foreground"
          >
            Change
          </button>
        </div>
        <SmsReceipt cart={cart} onChange={onChange} />
      </div>
    )
  }

  return (
    <div className="space-y-2 text-sm">
      <input
        aria-label="Customer phone"
        placeholder="Phone, e.g. 0712 345 678"
        inputMode="tel"
        value={cart.customerPhone}
        onChange={(e) => onChange({ customerPhone: e.target.value, smsReceipt: cart.smsReceipt ?? true })}
        className={inputClass}
      />
      <input
        aria-label="Customer name"
        placeholder="Name"
        value={cart.customerName}
        onChange={(e) => onChange({ customerName: e.target.value })}
        className={inputClass}
      />
      {(matches.data ?? []).length > 0 && (
        <ul aria-label="Matching customers" className="rounded-xl border border-border">
          {(matches.data ?? []).map((c) => (
            <li key={c.id} className="border-b border-border last:border-0">
              <button
                type="button"
                onClick={() =>
                  onChange({
                    customerId: c.id,
                    customerName: c.name,
                    customerPhone: c.phone,
                    marketingOptIn: c.marketingOptIn,
                    smsReceipt: cart.smsReceipt ?? true,
                  })
                }
                className="flex min-h-11 w-full items-center justify-between gap-2 px-3 text-left hover:bg-background"
              >
                <span>{c.name}</span>
                <span className="text-xs text-muted">{displayPhone(c.phone)}</span>
              </button>
            </li>
          ))}
        </ul>
      )}
      {hasPhone && (
        <>
          <label className="flex min-h-11 items-center gap-2">
            <input
              type="checkbox"
              className="size-5 accent-brand"
              checked={cart.marketingOptIn ?? false}
              onChange={(e) => onChange({ marketingOptIn: e.target.checked })}
            />
            Customer agrees to promotional SMS
          </label>
          <SmsReceipt cart={cart} onChange={onChange} />
        </>
      )}
    </div>
  )
}

function SmsReceipt({ cart, onChange }: Props) {
  return (
    <label className="flex min-h-11 items-center gap-2">
      <input
        type="checkbox"
        className="size-5 accent-brand"
        checked={cart.smsReceipt ?? true}
        onChange={(e) => onChange({ smsReceipt: e.target.checked })}
      />
      Send SMS receipt
    </label>
  )
}
