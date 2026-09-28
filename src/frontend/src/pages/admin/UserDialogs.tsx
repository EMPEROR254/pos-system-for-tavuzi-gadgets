// Dialogs for the staff screen: add a staff member, edit one, reset a password.
import { zodResolver } from '@hookform/resolvers/zod'
import { useQueryClient } from '@tanstack/react-query'
import { useForm, type FieldValues, type Path, type UseFormSetError } from 'react-hook-form'
import { z } from 'zod'
import { Button } from '../../components/Button'
import { Dialog } from '../../components/Dialog'
import { SelectField, TextField } from '../../components/Field'
import { ApiError } from '../../services/api'
import { useSession } from '../../services/session'
import { createUser, resetPassword, updateUser, type User } from '../../services/users'
import { ROLES, roleLabel } from '../../utils/permissions'
import { toast } from '../../utils/toast'

const roleSchema = z.enum(['OWNER', 'MANAGER', 'CASHIER'], { message: 'Choose a role.' })
const fullName = z.string().trim().min(1, 'Enter the full name.').max(100, 'At most 100 characters.')
const password = z.string().min(8, 'Use at least 8 characters.').max(100, 'At most 100 characters.')

/** Puts the server's messages next to the right inputs, or at the top of the form. */
function showServerErrors<T extends FieldValues>(error: unknown, setError: UseFormSetError<T>) {
  if (error instanceof ApiError && Object.keys(error.fieldErrors).length > 0) {
    for (const [field, message] of Object.entries(error.fieldErrors)) setError(field as Path<T>, { message })
  } else {
    setError('root', { message: error instanceof Error ? error.message : 'Could not save. Please try again.' })
  }
}

function FormError({ message }: { message?: string }) {
  if (!message) return null
  return (
    <p role="alert" className="rounded-xl bg-danger-soft px-3 py-2 text-sm text-danger">
      {message}
    </p>
  )
}

const roleOptions = ROLES.map((r) => (
  <option key={r.value} value={r.value}>
    {r.label}
  </option>
))

const createSchema = z.object({
  fullName,
  username: z
    .string()
    .trim()
    .regex(/^[A-Za-z0-9._-]{3,50}$/, '3-50 letters, digits, dots, dashes or underscores.'),
  role: roleSchema,
  password,
})
type CreateForm = z.infer<typeof createSchema>

export function CreateUserDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
  const queryClient = useQueryClient()
  const form = useForm<CreateForm>({ resolver: zodResolver(createSchema), defaultValues: { role: 'CASHIER' } })
  const { register, handleSubmit, reset, setError, watch, formState } = form
  const { errors, isSubmitting } = formState
  const role = watch('role')

  const close = () => {
    reset()
    onClose()
  }

  const onSubmit = handleSubmit(async (values) => {
    try {
      const user = await createUser(values)
      await queryClient.invalidateQueries({ queryKey: ['users'] })
      toast.success(`${user.fullName} can now sign in as "${user.username}".`)
      close()
    } catch (e) {
      if (e instanceof ApiError && e.status === 409) setError('username', { message: e.message })
      else showServerErrors(e, setError)
    }
  })

  return (
    <Dialog
      open={open}
      title="Add staff member"
      onClose={close}
      footer={
        <>
          <Button variant="secondary" onClick={close}>
            Cancel
          </Button>
          <Button type="submit" form="create-user" busy={isSubmitting}>
            Add
          </Button>
        </>
      }
    >
      <form id="create-user" onSubmit={onSubmit} noValidate className="space-y-4">
        <FormError message={errors.root?.message} />
        <TextField label="Full name" autoComplete="off" error={errors.fullName?.message} {...register('fullName')} />
        <TextField
          label="Username"
          autoComplete="off"
          autoCapitalize="none"
          spellCheck={false}
          hint="Used to sign in. Not case-sensitive."
          error={errors.username?.message}
          {...register('username')}
        />
        <SelectField
          label="Role"
          hint={ROLES.find((r) => r.value === role)?.description}
          error={errors.role?.message}
          {...register('role')}
        >
          {roleOptions}
        </SelectField>
        <TextField
          label="First password"
          type="password"
          autoComplete="new-password"
          hint="At least 8 characters. They can change it under My account."
          error={errors.password?.message}
          {...register('password')}
        />
      </form>
    </Dialog>
  )
}

const editSchema = z.object({ fullName, role: roleSchema, active: z.boolean() })
type EditForm = z.infer<typeof editSchema>

export function EditUserDialog({ user, onClose }: { user: User | null; onClose: () => void }) {
  const queryClient = useQueryClient()
  const me = useSession((s) => s.user)
  const isMe = user?.id === me?.id
  const { register, handleSubmit, setError, watch, formState } = useForm<EditForm>({
    resolver: zodResolver(editSchema),
    values: user ? { fullName: user.fullName, role: user.role, active: user.active } : undefined,
  })
  const { errors, isSubmitting } = formState
  const active = watch('active')

  const onSubmit = handleSubmit(async (values) => {
    if (!user) return
    try {
      await updateUser(user.id, values)
      await queryClient.invalidateQueries({ queryKey: ['users'] })
      toast.success(`Saved changes for ${values.fullName}.`)
      onClose()
    } catch (e) {
      showServerErrors(e, setError)
    }
  })

  return (
    <Dialog
      open={user !== null}
      title="Edit staff member"
      onClose={onClose}
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" form="edit-user" busy={isSubmitting}>
            Save
          </Button>
        </>
      }
    >
      <form id="edit-user" onSubmit={onSubmit} noValidate className="space-y-4">
        <FormError message={errors.root?.message} />
        <p className="text-sm text-muted">
          Username: <span className="font-medium text-foreground">{user?.username}</span>
        </p>
        <TextField label="Full name" autoComplete="off" error={errors.fullName?.message} {...register('fullName')} />
        {isMe ? (
          // Your own role and status can't change here (the server refuses too),
          // so they are shown as text instead of inputs.
          <p className="text-sm text-muted">
            Role: <span className="font-medium text-foreground">{user && roleLabel(user.role)}</span> · you can't change
            your own role or disable yourself.
          </p>
        ) : (
          <>
            <SelectField label="Role" error={errors.role?.message} {...register('role')}>
              {roleOptions}
            </SelectField>
            <label className="flex min-h-11 cursor-pointer items-center gap-3 text-sm">
              <input type="checkbox" className="size-5 accent-brand" {...register('active')} />
              Account active (can sign in)
            </label>
          </>
        )}
        {!active && !isMe && (
          <p className="rounded-xl bg-danger-soft px-3 py-2 text-sm text-danger">
            When you save, this person is signed out and can no longer sign in. Their past sales stay on record.
          </p>
        )}
      </form>
    </Dialog>
  )
}

const resetSchema = z
  .object({ password, confirm: z.string() })
  .refine((v) => v.password === v.confirm, { path: ['confirm'], message: 'The passwords do not match.' })
type ResetForm = z.infer<typeof resetSchema>

export function ResetPasswordDialog({ user, onClose }: { user: User | null; onClose: () => void }) {
  const queryClient = useQueryClient()
  const { register, handleSubmit, reset, setError, formState } = useForm<ResetForm>({
    resolver: zodResolver(resetSchema),
  })
  const { errors, isSubmitting } = formState

  const close = () => {
    reset()
    onClose()
  }

  const onSubmit = handleSubmit(async ({ password: newPassword }) => {
    if (!user) return
    try {
      await resetPassword(user.id, newPassword)
      await queryClient.invalidateQueries({ queryKey: ['users'] })
      toast.success(`New password set for ${user?.fullName}. Tell them in person.`)
      close()
    } catch (e) {
      showServerErrors(e, setError)
    }
  })

  return (
    <Dialog
      open={user !== null}
      title="Reset password"
      onClose={close}
      footer={
        <>
          <Button variant="secondary" onClick={close}>
            Cancel
          </Button>
          <Button type="submit" form="reset-password" busy={isSubmitting}>
            Set password
          </Button>
        </>
      }
    >
      <form id="reset-password" onSubmit={onSubmit} noValidate className="space-y-4">
        <FormError message={errors.root?.message} />
        <p className="text-sm text-muted">
          Sets a new password for <span className="font-medium text-foreground">{user?.fullName}</span>, unlocks the
          account and signs them out on every device.
        </p>
        <TextField
          label="New password"
          type="password"
          autoComplete="new-password"
          error={errors.password?.message}
          {...register('password')}
        />
        <TextField
          label="Repeat new password"
          type="password"
          autoComplete="new-password"
          error={errors.confirm?.message}
          {...register('confirm')}
        />
      </form>
    </Dialog>
  )
}
