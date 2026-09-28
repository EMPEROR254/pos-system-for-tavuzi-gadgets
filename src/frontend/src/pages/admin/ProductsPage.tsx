// Product catalogue (owner and manager): search, filter by category or status,
// page through, add and edit. A table on laptops, cards on phones.
import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Button } from '../../components/Button'
import { inputClass } from '../../components/Field'
import { listProducts, productCategories, VAT_LABELS, type Product } from '../../services/products'
import { formatKes } from '../../utils/format'
import { useDebounced } from '../../utils/useDebounced'
import { ProductDialog } from './ProductDialog'

export default function ProductsPage() {
  const [search, setSearch] = useState('')
  const [category, setCategory] = useState('')
  const [active, setActive] = useState<'true' | 'false' | ''>('true')
  const [page, setPage] = useState(0)
  const [editing, setEditing] = useState<Product | null>(null)
  const [adding, setAdding] = useState(false)
  const q = useDebounced(search.trim())
  const filters = { q, category, active, page, size: 20 }
  const products = useQuery({
    queryKey: ['products', filters],
    queryFn: () => listProducts(filters),
    placeholderData: keepPreviousData,
  })
  const categories = useQuery({ queryKey: ['products', 'categories'], queryFn: productCategories })

  const price = (p: Product) => p.units[0]?.retailPrice ?? 0

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Products</h1>
          <p className="text-sm text-muted">What the till sells, in which units, at which prices.</p>
        </div>
        <Button onClick={() => setAdding(true)}>+ Add product</Button>
      </div>

      <div className="grid gap-3 rounded-2xl border border-border bg-card p-4 shadow-sm sm:grid-cols-[1fr_auto_auto]">
        <input
          aria-label="Search products"
          type="search"
          placeholder="Search by name, SKU or barcode"
          value={search}
          onChange={(e) => {
            setSearch(e.target.value)
            setPage(0)
          }}
          className={inputClass}
        />
        <select
          aria-label="Category"
          value={category}
          onChange={(e) => {
            setCategory(e.target.value)
            setPage(0)
          }}
          className={inputClass}
        >
          <option value="">All categories</option>
          {(categories.data ?? []).map((c) => (
            <option key={c} value={c}>
              {c}
            </option>
          ))}
        </select>
        <select
          aria-label="Status"
          value={active}
          onChange={(e) => {
            setActive(e.target.value as 'true' | 'false' | '')
            setPage(0)
          }}
          className={inputClass}
        >
          <option value="true">On sale</option>
          <option value="false">Hidden</option>
          <option value="">All</option>
        </select>
      </div>

      {products.isPending ? (
        <div role="status" aria-label="Loading products" className="space-y-2">
          {Array.from({ length: 5 }, (_, i) => (
            <div key={i} className="h-14 animate-pulse rounded-xl bg-card motion-reduce:animate-none" />
          ))}
        </div>
      ) : products.isError ? (
        <div role="alert" className="rounded-2xl border border-danger/40 bg-card p-6 text-center">
          <p className="font-medium text-danger">Could not load products.</p>
          <p className="mt-1 text-sm text-muted">{products.error.message}</p>
          <Button variant="secondary" className="mt-4" onClick={() => void products.refetch()}>
            Try again
          </Button>
        </div>
      ) : products.data.content.length === 0 ? (
        <div className="rounded-2xl border border-dashed border-border bg-card p-10 text-center">
          <p className="font-medium">No products found</p>
          <p className="mt-1 text-sm text-muted">{q || category ? 'Try another search.' : 'Add your first product.'}</p>
        </div>
      ) : (
        <>
          <div className="hidden overflow-hidden rounded-2xl border border-border bg-card shadow-sm md:block">
            <table className="w-full text-left text-sm">
              <thead className="border-b border-border text-muted">
                <tr>
                  <th scope="col" className="px-4 py-3 font-medium">
                    Name
                  </th>
                  <th scope="col" className="px-4 py-3 font-medium">
                    Category
                  </th>
                  <th scope="col" className="px-4 py-3 font-medium">
                    Units
                  </th>
                  <th scope="col" className="px-4 py-3 font-medium">
                    VAT
                  </th>
                  <th scope="col" className="px-4 py-3 text-right font-medium">
                    Retail
                  </th>
                  <th scope="col" className="px-4 py-3">
                    <span className="sr-only">Actions</span>
                  </th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {products.data.content.map((p) => (
                  <tr key={p.id} className={p.active ? '' : 'text-muted'}>
                    <td className="px-4 py-3">
                      <p className="font-medium">{p.name}</p>
                      <p className="text-xs text-muted">
                        {p.sku}
                        {p.serialType !== 'NONE' && ` · ${p.serialType}`}
                        {p.warrantyMonths > 0 && ` · ${p.warrantyMonths} mo warranty`}
                      </p>
                    </td>
                    <td className="px-4 py-3">{p.category ?? '—'}</td>
                    <td className="px-4 py-3">{p.units.map((u) => u.name).join(', ')}</td>
                    <td className="px-4 py-3">{VAT_LABELS[p.vatCategory]}</td>
                    <td className="px-4 py-3 text-right tabular-nums">{formatKes(price(p))}</td>
                    <td className="px-4 py-2 text-right">
                      <Button variant="ghost" onClick={() => setEditing(p)} aria-label={`Edit ${p.name}`}>
                        Edit
                      </Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <ul className="space-y-3 md:hidden">
            {products.data.content.map((p) => (
              <li key={p.id}>
                <button
                  type="button"
                  onClick={() => setEditing(p)}
                  className="w-full rounded-2xl border border-border bg-card p-4 text-left shadow-sm"
                >
                  <div className="flex items-start justify-between gap-3">
                    <p className="font-medium">{p.name}</p>
                    <p className="font-semibold tabular-nums">{formatKes(price(p))}</p>
                  </div>
                  <p className="text-sm text-muted">
                    {p.category ?? 'No category'} · {p.units.map((u) => u.name).join(', ')}
                    {!p.active && ' · hidden'}
                  </p>
                </button>
              </li>
            ))}
          </ul>
          <nav aria-label="Pages" className="flex items-center justify-between text-sm">
            <p className="text-muted">
              {products.data.totalElements} products · page {products.data.page + 1} of{' '}
              {Math.max(products.data.totalPages, 1)}
            </p>
            <div className="flex gap-2">
              <Button variant="secondary" disabled={page === 0} onClick={() => setPage(page - 1)}>
                Previous
              </Button>
              <Button
                variant="secondary"
                disabled={page + 1 >= products.data.totalPages}
                onClick={() => setPage(page + 1)}
              >
                Next
              </Button>
            </div>
          </nav>
        </>
      )}

      {(adding || editing) && (
        <ProductDialog
          open
          product={editing}
          categories={categories.data ?? []}
          onClose={() => {
            setAdding(false)
            setEditing(null)
          }}
        />
      )}
    </div>
  )
}
