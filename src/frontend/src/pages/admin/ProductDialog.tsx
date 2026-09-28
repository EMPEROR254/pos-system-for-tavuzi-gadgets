// Add or edit a product with its selling units (e.g. Piece and Carton of 20),
// retail and wholesale prices (VAT included), VAT type and IMEI/serial tracking.
import { zodResolver } from '@hookform/resolvers/zod'
import { useQueryClient } from '@tanstack/react-query'
import { useFieldArray, useForm } from 'react-hook-form'
import { z } from 'zod'
import { Button } from '../../components/Button'
import { Dialog } from '../../components/Dialog'
import { inputClass, SelectField, TextField } from '../../components/Field'
import { ApiError } from '../../services/api'
import {
  createProduct,
  SERIAL_LABELS,
  updateProduct,
  VAT_LABELS,
  type Product,
  type SerialType,
  type VatCategory,
} from '../../services/products'
import { toast } from '../../utils/toast'

const price = z.coerce.number({ message: 'Enter a number.' }).min(0, "Can't be negative.")
const optionalPrice = z.preprocess((v) => (v === '' || v == null ? null : v), price.nullable())

const schema = z.object({
  name: z.string().trim().min(1, 'Enter the product name.').max(150),
  sku: z.string().trim().max(50),
  category: z.string().trim().max(60),
  vatCategory: z.enum(['STANDARD', 'ZERO_RATED', 'EXEMPT']),
  serialType: z.enum(['NONE', 'IMEI', 'SERIAL']),
  warrantyMonths: z.coerce.number().int('Whole months.').min(0).max(120, 'At most 120.'),
  costPrice: price,
  active: z.boolean(),
  units: z
    .array(
      z.object({
        id: z.number().nullable().optional(),
        name: z.string().trim().min(1, 'Enter a name.').max(40),
        factor: z.coerce.number().int('Whole number.').min(1, 'At least 1.'),
        barcode: z.string().trim().max(50),
        retailPrice: price,
        wholesalePrice: optionalPrice,
      }),
    )
    .min(1, 'Add at least one unit.')
    .refine((units) => units.filter((u) => Number(u.factor) === 1).length === 1, {
      message: 'Exactly one unit must hold 1 piece (the base unit).',
    }),
})
type FormValues = z.input<typeof schema>
type Parsed = z.output<typeof schema>

function toForm(p: Product | null): FormValues {
  return p
    ? {
        name: p.name,
        sku: p.sku ?? '',
        category: p.category ?? '',
        vatCategory: p.vatCategory,
        serialType: p.serialType,
        warrantyMonths: p.warrantyMonths,
        costPrice: p.costPrice,
        active: p.active,
        units: p.units.map((u) => ({ ...u, barcode: u.barcode ?? '', wholesalePrice: u.wholesalePrice ?? '' })),
      }
    : {
        name: '',
        sku: '',
        category: '',
        vatCategory: 'STANDARD',
        serialType: 'NONE',
        warrantyMonths: 0,
        costPrice: 0,
        active: true,
        units: [{ id: null, name: 'Piece', factor: 1, barcode: '', retailPrice: '', wholesalePrice: '' }],
      }
}

type Props = { product: Product | null; open: boolean; categories: string[]; onClose: () => void }

export function ProductDialog({ product, open, categories, onClose }: Props) {
  const queryClient = useQueryClient()
  const { register, control, handleSubmit, setError, formState } = useForm<FormValues, unknown, Parsed>({
    resolver: zodResolver(schema),
    values: toForm(product),
  })
  const units = useFieldArray({ control, name: 'units' })
  const { errors, isSubmitting } = formState

  const onSubmit = handleSubmit(async (v) => {
    const body = {
      ...v,
      sku: v.sku || null,
      category: v.category || null,
      units: v.units.map((u) => ({ ...u, barcode: u.barcode || null })),
    }
    try {
      const saved = product ? await updateProduct(product.id, body) : await createProduct(body)
      await queryClient.invalidateQueries({ queryKey: ['products'] })
      toast.success(`Saved ${saved.name}.`)
      onClose()
    } catch (e) {
      setError('root', { message: e instanceof ApiError ? e.message : 'Could not save the product.' })
    }
  })

  const cell = `${inputClass} min-w-0`

  return (
    <Dialog
      open={open}
      title={product ? 'Edit product' : 'Add product'}
      onClose={onClose}
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" form="product-form" busy={isSubmitting}>
            Save
          </Button>
        </>
      }
    >
      <form id="product-form" onSubmit={onSubmit} noValidate className="space-y-4">
        {errors.root && (
          <p role="alert" className="rounded-xl bg-danger-soft px-3 py-2 text-sm text-danger">
            {errors.root.message}
          </p>
        )}
        <TextField label="Name" error={errors.name?.message} {...register('name')} />
        <div className="grid gap-4 sm:grid-cols-2">
          <TextField label="SKU / code (optional)" error={errors.sku?.message} {...register('sku')} />
          <TextField
            label="Category (optional)"
            list="product-categories"
            error={errors.category?.message}
            {...register('category')}
          />
          <datalist id="product-categories">
            {categories.map((c) => (
              <option key={c} value={c} />
            ))}
          </datalist>
          <SelectField label="VAT" {...register('vatCategory')}>
            {(Object.keys(VAT_LABELS) as VatCategory[]).map((k) => (
              <option key={k} value={k}>
                {VAT_LABELS[k]}
              </option>
            ))}
          </SelectField>
          <SelectField label="Record IMEI / serial at the till" {...register('serialType')}>
            {(Object.keys(SERIAL_LABELS) as SerialType[]).map((k) => (
              <option key={k} value={k}>
                {SERIAL_LABELS[k]}
              </option>
            ))}
          </SelectField>
          <TextField
            label="Warranty (months)"
            inputMode="numeric"
            error={errors.warrantyMonths?.message}
            {...register('warrantyMonths')}
          />
          <TextField
            label="Cost of 1 piece (KES)"
            inputMode="decimal"
            hint="For profit reports. Not shown at the till."
            error={errors.costPrice?.message}
            {...register('costPrice')}
          />
        </div>

        <fieldset className="space-y-2">
          <legend className="text-sm font-medium">Selling units and prices (VAT included)</legend>
          {units.fields.map((field, i) => {
            const e = errors.units?.[i]
            return (
              <div key={field.id} className="grid grid-cols-2 gap-2 rounded-xl border border-border p-3 sm:grid-cols-5">
                <input
                  aria-label={`Unit ${i + 1} name`}
                  placeholder="Name"
                  className={`${cell} col-span-2 sm:col-span-1`}
                  {...register(`units.${i}.name`)}
                />
                <input
                  aria-label={`Unit ${i + 1} pieces`}
                  placeholder="Pieces"
                  inputMode="numeric"
                  className={cell}
                  {...register(`units.${i}.factor`)}
                />
                <input
                  aria-label={`Unit ${i + 1} retail price`}
                  placeholder="Retail"
                  inputMode="decimal"
                  className={`${cell} text-right`}
                  {...register(`units.${i}.retailPrice`)}
                />
                <input
                  aria-label={`Unit ${i + 1} wholesale price`}
                  placeholder="Wholesale"
                  inputMode="decimal"
                  className={`${cell} text-right`}
                  {...register(`units.${i}.wholesalePrice`)}
                />
                <div className="flex gap-2">
                  <input
                    aria-label={`Unit ${i + 1} barcode`}
                    placeholder="Barcode"
                    className={cell}
                    {...register(`units.${i}.barcode`)}
                  />
                  {units.fields.length > 1 && (
                    <button
                      type="button"
                      aria-label={`Remove unit ${i + 1}`}
                      onClick={() => units.remove(i)}
                      className="flex size-11 shrink-0 items-center justify-center rounded-xl text-muted hover:bg-background"
                    >
                      ✕
                    </button>
                  )}
                </div>
                {e && (
                  <p className="col-span-full text-sm text-danger">
                    {e.name?.message ?? e.factor?.message ?? e.retailPrice?.message ?? e.wholesalePrice?.message}
                  </p>
                )}
              </div>
            )
          })}
          {errors.units?.root?.message && <p className="text-sm text-danger">{errors.units.root.message}</p>}
          {errors.units?.message && <p className="text-sm text-danger">{errors.units.message}</p>}
          <Button
            variant="secondary"
            onClick={() =>
              units.append({ id: null, name: '', factor: '', barcode: '', retailPrice: '', wholesalePrice: '' })
            }
          >
            + Add unit (e.g. Carton)
          </Button>
        </fieldset>

        <label className="flex min-h-11 cursor-pointer items-center gap-3 text-sm">
          <input type="checkbox" className="size-5 accent-brand" {...register('active')} />
          On sale (untick to hide it from the till)
        </label>
      </form>
    </Dialog>
  )
}
