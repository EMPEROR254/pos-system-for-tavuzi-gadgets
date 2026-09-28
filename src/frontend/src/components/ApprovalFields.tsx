// Supervisor approval on the same screen: a manager (discounts) or the owner
// (returns) types their username and password. The server checks it, and wrong
// passwords count towards the normal login lock.
import type { Approval } from '../services/sales'
import { TextField } from './Field'

type Props = { who: string; value: Approval; onChange: (value: Approval) => void }

export function ApprovalFields({ who, value, onChange }: Props) {
  return (
    <fieldset className="space-y-3 rounded-xl border border-border bg-background p-3">
      <legend className="px-1 text-sm font-medium">Approval by {who}</legend>
      <div className="grid gap-3 sm:grid-cols-2">
        <TextField
          label="Their username"
          autoComplete="off"
          autoCapitalize="none"
          value={value.username}
          onChange={(e) => onChange({ ...value, username: e.target.value })}
        />
        <TextField
          label="Their password"
          type="password"
          autoComplete="off"
          value={value.password}
          onChange={(e) => onChange({ ...value, password: e.target.value })}
        />
      </div>
    </fieldset>
  )
}
