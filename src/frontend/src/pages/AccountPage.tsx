// My account: see your details and change your own password.
import { zodResolver } from '@hookform/resolvers/zod'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { Button } from '../components/Button'
import { TextField } from '../components/Field'
import { ApiError } from '../services/api'
import { changePassword } from '../services/auth'
import { useSession } from '../services/session'
import { roleLabel } from '../utils/permissions'
import { toast } from '../utils/toast'

const schema = z
  .object({
    currentPassword: z.string().min(1, 'Enter your current password.'),
    newPassword: z.string().min(8, 'Use at least 8 characters.').max(100, 'At most 100 characters.'),
    confirm: z.string(),
  })
  .refine((v) => v.newPassword === v.confirm, { path: ['confirm'], message: 'The passwords do not match.' })
type PasswordForm = z.infer<typeof schema>

export default function AccountPage() {
  const user = useSession((s) => s.user)
  const {
    register,
    handleSubmit,
    reset,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<PasswordForm>({ resolver: zodResolver(schema) })

  const onSubmit = handleSubmit(async ({ currentPassword, newPassword }) => {
    try {
      await changePassword(currentPassword, newPassword)
      reset()
      toast.success('Your password was changed.')
    } catch (e) {
      const message = e instanceof ApiError ? e.message : 'Could not change the password.'
      setError(e instanceof ApiError && e.status === 400 ? 'currentPassword' : 'root', { message })
    }
  })

  return (
    <div className="mx-auto max-w-lg animate-fade-in space-y-6">
      <h1 className="text-2xl font-semibold tracking-tight">My account</h1>
      <section className="rounded-2xl border border-border bg-card p-6 shadow-sm">
        <dl className="grid grid-cols-[auto_1fr] gap-x-6 gap-y-2 text-sm">
          <dt className="text-muted">Name</dt>
          <dd>{user?.fullName}</dd>
          <dt className="text-muted">Username</dt>
          <dd>{user?.username}</dd>
          <dt className="text-muted">Role</dt>
          <dd>{user && roleLabel(user.role)}</dd>
        </dl>
      </section>
      <form onSubmit={onSubmit} noValidate className="space-y-4 rounded-2xl border border-border bg-card p-6 shadow-sm">
        <h2 className="font-semibold">Change password</h2>
        {errors.root && (
          <p role="alert" className="rounded-xl bg-danger-soft px-3 py-2 text-sm text-danger">
            {errors.root.message}
          </p>
        )}
        <TextField
          label="Current password"
          type="password"
          autoComplete="current-password"
          error={errors.currentPassword?.message}
          {...register('currentPassword')}
        />
        <TextField
          label="New password"
          type="password"
          autoComplete="new-password"
          hint="At least 8 characters."
          error={errors.newPassword?.message}
          {...register('newPassword')}
        />
        <TextField
          label="Repeat new password"
          type="password"
          autoComplete="new-password"
          error={errors.confirm?.message}
          {...register('confirm')}
        />
        <Button type="submit" busy={isSubmitting} className="w-full sm:w-auto">
          Change password
        </Button>
      </form>
    </div>
  )
}
