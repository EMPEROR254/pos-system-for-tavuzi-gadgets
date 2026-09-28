// Staff accounts (owner only): search, filter, sort and page through staff;
// add, edit, disable and reset passwords. A table on laptops, cards on phones.
import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Button } from '../../components/Button'
import { inputClass } from '../../components/Field'
import { listUsers, type User, type UserFilters } from '../../services/users'
import { formatDate } from '../../utils/format'
import { ROLES, roleLabel, type Role } from '../../utils/permissions'
import { useDebounced } from '../../utils/useDebounced'
import { CreateUserDialog, EditUserDialog, ResetPasswordDialog } from './UserDialogs'

const PAGE_SIZE = 20
type SortKey = NonNullable<UserFilters['sort']>

export default function UsersPage() {
  const [search, setSearch] = useState('')
  const [role, setRole] = useState<Role | ''>('')
  const [active, setActive] = useState<'true' | 'false' | ''>('true')
  const [sort, setSort] = useState<SortKey>('fullName')
  const [dir, setDir] = useState<'asc' | 'desc'>('asc')
  const [page, setPage] = useState(0)
  const [adding, setAdding] = useState(false)
  const [editing, setEditing] = useState<User | null>(null)
  const [resetting, setResetting] = useState<User | null>(null)
  const q = useDebounced(search.trim())

  const filters: UserFilters = { q, role, active, page, size: PAGE_SIZE, sort, dir }
  const users = useQuery({
    queryKey: ['users', filters],
    queryFn: () => listUsers(filters),
    placeholderData: keepPreviousData,
  })

  // Any filter change starts again at page 1.
  const onFilter =
    <T,>(set: (v: T) => void) =>
    (value: T) => {
      set(value)
      setPage(0)
    }
  const sortBy = (key: SortKey) => {
    setDir(sort === key && dir === 'asc' ? 'desc' : 'asc')
    setSort(key)
    setPage(0)
  }

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Staff</h1>
          <p className="text-sm text-muted">Who can sign in, and what their role lets them do.</p>
        </div>
        <Button onClick={() => setAdding(true)}>+ Add staff member</Button>
      </div>

      <div className="grid gap-3 rounded-2xl border border-border bg-card p-4 shadow-sm sm:grid-cols-[1fr_auto_auto]">
        <label className="sr-only" htmlFor="staff-search">
          Search staff
        </label>
        <input
          id="staff-search"
          type="search"
          placeholder="Search by name or username"
          value={search}
          onChange={(e) => onFilter(setSearch)(e.target.value)}
          className={inputClass}
        />
        <label className="sr-only" htmlFor="staff-role">
          Role
        </label>
        <select
          id="staff-role"
          value={role}
          onChange={(e) => onFilter(setRole)(e.target.value as Role | '')}
          className={inputClass}
        >
          <option value="">All roles</option>
          {ROLES.map((r) => (
            <option key={r.value} value={r.value}>
              {r.label}
            </option>
          ))}
        </select>
        <label className="sr-only" htmlFor="staff-status">
          Status
        </label>
        <select
          id="staff-status"
          value={active}
          onChange={(e) => onFilter(setActive)(e.target.value as 'true' | 'false' | '')}
          className={inputClass}
        >
          <option value="true">Active</option>
          <option value="false">Disabled</option>
          <option value="">All</option>
        </select>
      </div>

      {users.isPending ? (
        <ListSkeleton />
      ) : users.isError ? (
        <div role="alert" className="rounded-2xl border border-danger/40 bg-card p-6 text-center">
          <p className="font-medium text-danger">Could not load staff.</p>
          <p className="mt-1 text-sm text-muted">{users.error.message}</p>
          <Button variant="secondary" className="mt-4" onClick={() => void users.refetch()}>
            Try again
          </Button>
        </div>
      ) : users.data.content.length === 0 ? (
        <div className="rounded-2xl border border-dashed border-border bg-card p-10 text-center">
          <p className="font-medium">No staff found</p>
          <p className="mt-1 text-sm text-muted">
            {q || role || active ? 'Try another search or filter.' : 'Add your first staff member.'}
          </p>
        </div>
      ) : (
        <>
          <UserTable
            users={users.data.content}
            sort={sort}
            dir={dir}
            onSort={sortBy}
            onEdit={setEditing}
            onReset={setResetting}
          />
          <UserCards users={users.data.content} onEdit={setEditing} onReset={setResetting} />
          <Pagination
            page={users.data.page}
            totalPages={users.data.totalPages}
            total={users.data.totalElements}
            onPage={setPage}
          />
        </>
      )}

      <CreateUserDialog open={adding} onClose={() => setAdding(false)} />
      <EditUserDialog user={editing} onClose={() => setEditing(null)} />
      <ResetPasswordDialog user={resetting} onClose={() => setResetting(null)} />
    </div>
  )
}

type RowActions = { onEdit: (u: User) => void; onReset: (u: User) => void }

function StatusBadge({ user }: { user: User }) {
  const [label, tone] = !user.active
    ? ['Disabled', 'bg-background text-muted']
    : user.locked
      ? ['Locked', 'bg-danger-soft text-danger']
      : ['Active', 'bg-brand-soft text-foreground']
  return <span className={`inline-block rounded-full px-2.5 py-0.5 text-xs font-medium ${tone}`}>{label}</span>
}

function UserTable({
  users,
  sort,
  dir,
  onSort,
  onEdit,
  onReset,
}: RowActions & { users: User[]; sort: SortKey; dir: 'asc' | 'desc'; onSort: (k: SortKey) => void }) {
  const header = (key: SortKey, label: string) => (
    <th
      scope="col"
      aria-sort={sort === key ? (dir === 'asc' ? 'ascending' : 'descending') : undefined}
      className="px-4 py-3 font-medium"
    >
      <button
        type="button"
        onClick={() => onSort(key)}
        className="inline-flex items-center gap-1 hover:text-foreground"
      >
        {label}
        <span aria-hidden className="text-xs">
          {sort === key ? (dir === 'asc' ? '▲' : '▼') : ''}
        </span>
      </button>
    </th>
  )
  return (
    <div className="hidden overflow-hidden rounded-2xl border border-border bg-card shadow-sm md:block">
      <table className="w-full text-left text-sm">
        <thead className="border-b border-border text-muted">
          <tr>
            {header('fullName', 'Name')}
            {header('username', 'Username')}
            {header('role', 'Role')}
            <th scope="col" className="px-4 py-3 font-medium">
              Status
            </th>
            {header('createdAt', 'Added')}
            <th scope="col" className="px-4 py-3">
              <span className="sr-only">Actions</span>
            </th>
          </tr>
        </thead>
        <tbody className="divide-y divide-border">
          {users.map((u) => (
            <tr key={u.id} className="hover:bg-background/60">
              <td className="px-4 py-3 font-medium">{u.fullName}</td>
              <td className="px-4 py-3 text-muted">{u.username}</td>
              <td className="px-4 py-3">{roleLabel(u.role)}</td>
              <td className="px-4 py-3">
                <StatusBadge user={u} />
              </td>
              <td className="px-4 py-3 text-muted tabular-nums">{formatDate(u.createdAt)}</td>
              <td className="px-4 py-2 text-right whitespace-nowrap">
                <Button variant="ghost" onClick={() => onEdit(u)} aria-label={`Edit ${u.fullName}`}>
                  Edit
                </Button>
                <Button variant="ghost" onClick={() => onReset(u)} aria-label={`Reset password for ${u.fullName}`}>
                  Reset password
                </Button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

function UserCards({ users, onEdit, onReset }: RowActions & { users: User[] }) {
  return (
    <ul className="space-y-3 md:hidden">
      {users.map((u) => (
        <li key={u.id} className="rounded-2xl border border-border bg-card p-4 shadow-sm">
          <div className="flex items-start justify-between gap-3">
            <div>
              <p className="font-medium">{u.fullName}</p>
              <p className="text-sm text-muted">
                {u.username} · {roleLabel(u.role)}
              </p>
            </div>
            <StatusBadge user={u} />
          </div>
          <div className="mt-3 grid grid-cols-2 gap-2">
            <Button variant="secondary" onClick={() => onEdit(u)} aria-label={`Edit ${u.fullName}`}>
              Edit
            </Button>
            <Button variant="secondary" onClick={() => onReset(u)} aria-label={`Reset password for ${u.fullName}`}>
              Reset password
            </Button>
          </div>
        </li>
      ))}
    </ul>
  )
}

function Pagination({
  page,
  totalPages,
  total,
  onPage,
}: {
  page: number
  totalPages: number
  total: number
  onPage: (p: number) => void
}) {
  return (
    <nav aria-label="Pages" className="flex items-center justify-between gap-3 text-sm">
      <p className="text-muted">
        {total} {total === 1 ? 'person' : 'people'} · page {page + 1} of {Math.max(totalPages, 1)}
      </p>
      <div className="flex gap-2">
        <Button variant="secondary" disabled={page === 0} onClick={() => onPage(page - 1)}>
          Previous
        </Button>
        <Button variant="secondary" disabled={page + 1 >= totalPages} onClick={() => onPage(page + 1)}>
          Next
        </Button>
      </div>
    </nav>
  )
}

function ListSkeleton() {
  return (
    <div className="space-y-3" role="status" aria-label="Loading staff">
      {Array.from({ length: 4 }, (_, i) => (
        <div key={i} className="h-16 animate-pulse rounded-2xl bg-card motion-reduce:animate-none" />
      ))}
    </div>
  )
}
