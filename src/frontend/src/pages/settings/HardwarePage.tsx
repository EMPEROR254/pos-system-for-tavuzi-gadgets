// Hardware (owner): the receipt printer and cash drawer, a test print, and
// advice on what to buy and how to set up desktops, laptops and Android phones.
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { Button } from '../../components/Button'
import { SelectField, TextField } from '../../components/Field'
import { ListError, ListSkeleton } from '../../components/ListStates'
import { ApiError } from '../../services/api'
import { getPrinter, savePrinter, testPrint, type PrinterConfig } from '../../services/hardware'
import { toast } from '../../utils/toast'

export default function HardwarePage() {
  const printer = useQuery({ queryKey: ['hardware', 'printer'], queryFn: getPrinter })
  return (
    <div className="mx-auto max-w-3xl space-y-5">
      <h1 className="text-2xl font-semibold tracking-tight">Hardware</h1>
      {printer.isPending ? (
        <ListSkeleton label="Loading printer settings" rows={3} />
      ) : printer.isError ? (
        <ListError what="printer settings" error={printer.error} onRetry={() => void printer.refetch()} />
      ) : (
        <PrinterForm initial={printer.data} />
      )}
      <Advice />
    </div>
  )
}

function PrinterForm({ initial }: { initial: PrinterConfig }) {
  const queryClient = useQueryClient()
  const [config, setConfig] = useState<PrinterConfig>(initial)
  const [busy, setBusy] = useState<'save' | 'test' | null>(null)
  useEffect(() => setConfig(initial), [initial])
  const set = (changes: Partial<PrinterConfig>) => setConfig({ ...config, ...changes })

  const save = async () => {
    setBusy('save')
    try {
      queryClient.setQueryData(['hardware', 'printer'], await savePrinter(config))
      toast.success('Printer settings saved.')
    } catch (e) {
      toast.error(e instanceof ApiError ? e.message : 'Could not save.')
    } finally {
      setBusy(null)
    }
  }

  const test = async () => {
    setBusy('test')
    try {
      await testPrint()
      toast.success('Test page sent. Check the printer.')
    } catch (e) {
      toast.error(e instanceof ApiError ? e.message : 'Test print failed.')
    } finally {
      setBusy(null)
    }
  }

  return (
    <section className="space-y-4 rounded-2xl border border-border bg-card p-5 shadow-sm">
      <div>
        <h2 className="font-semibold">Receipt printer</h2>
        <p className="text-sm text-muted">
          A network (Ethernet or Wi-Fi) thermal printer prints for every till - desktop, laptop and phone. Leave the IP
          address empty to print from the browser instead.
        </p>
      </div>
      <div className="grid gap-4 sm:grid-cols-3">
        <TextField
          label="Printer IP address"
          placeholder="e.g. 192.168.1.50"
          value={config.host ?? ''}
          onChange={(e) => set({ host: e.target.value })}
        />
        <TextField
          label="Port"
          inputMode="numeric"
          value={String(config.port)}
          onChange={(e) => set({ port: Number(e.target.value) || 0 })}
        />
        <SelectField label="Paper" value={config.width} onChange={(e) => set({ width: Number(e.target.value) })}>
          <option value={48}>80 mm (48 characters)</option>
          <option value={42}>80 mm (42 characters)</option>
          <option value={32}>58 mm (32 characters)</option>
        </SelectField>
      </div>
      <label className="flex min-h-11 items-center gap-3 text-sm">
        <input
          type="checkbox"
          className="size-5 accent-brand"
          checked={config.openDrawer}
          onChange={(e) => set({ openDrawer: e.target.checked })}
        />
        Open the cash drawer when a cash sale prints (drawer plugged into the printer)
      </label>
      <label className="flex min-h-11 items-center gap-3 text-sm">
        <input
          type="checkbox"
          className="size-5 accent-brand"
          checked={config.autoPrint}
          onChange={(e) => set({ autoPrint: e.target.checked })}
        />
        Print the receipt automatically after every sale
      </label>
      <div className="flex gap-2">
        <Button busy={busy === 'save'} onClick={() => void save()}>
          Save
        </Button>
        <Button variant="secondary" busy={busy === 'test'} disabled={!initial.host} onClick={() => void test()}>
          Print test page
        </Button>
      </div>
    </section>
  )
}

function Advice() {
  return (
    <section className="space-y-3 rounded-2xl border border-border bg-card p-5 text-sm shadow-sm">
      <h2 className="font-semibold">What to buy and how to set it up</h2>
      <ul className="list-disc space-y-2 pl-5">
        <li>
          <span className="font-medium">Receipt printer:</span> an 80 mm thermal printer with an Ethernet (LAN) port and
          ESC/POS support, e.g. Epson TM-T20III, Xprinter XP-Q200 or XP-Q80 LAN. Plug it into the shop router, give it a
          fixed IP address, then enter it above.
        </li>
        <li>
          <span className="font-medium">Cash drawer:</span> any drawer with an RJ11 cable plugs into the printer; tick
          “Open the cash drawer” above.
        </li>
        <li>
          <span className="font-medium">Barcode scanner:</span> any USB scanner (it types like a keyboard) works on the
          desktop and laptop Sell screens. On an Android phone use the camera button on the Sell screen.
        </li>
        <li>
          <span className="font-medium">Android phone:</span> open the system in Chrome, then menu → “Add to Home
          screen” to use it like an app. The phone must be on the shop Wi-Fi.
        </li>
      </ul>
    </section>
  )
}
