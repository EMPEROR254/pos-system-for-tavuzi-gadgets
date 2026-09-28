// Staff sign-in with username and password.
import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { Navigate, useNavigate, useSearchParams } from 'react-router'
import { z } from 'zod'
import { Button } from '../components/Button'
import { TextField } from '../components/Field'
import { ThemeToggle } from '../components/ThemeToggle'
import { ApiError } from '../services/api'
import { login } from '../services/auth'
import { useSession } from '../services/session'

const schema = z.object({
  username: z.string().trim().min(1, 'Enter your username.'),
  password: z.string().min(1, 'Enter your password.'),
})
type LoginForm = z.infer<typeof schema>

/** Only same-site paths, so a crafted link can't send staff to another website. */
function safeNext(next: string | null): string {
  return next && next.startsWith('/') && !next.startsWith('//') ? next : '/'
}

export default function LoginPage() {
  const status = useSession((s) => s.status)
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const [serverError, setServerError] = useState<string | null>(null)
  const [showPassword, setShowPassword] = useState(false)
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<LoginForm>({ resolver: zodResolver(schema) })

  if (status === 'signedIn') return <Navigate to={safeNext(params.get('next'))} replace />

  const onSubmit = handleSubmit(async ({ username, password }) => {
    setServerError(null)
    try {
      await login(username, password)
      navigate(safeNext(params.get('next')), { replace: true })
    } catch (e) {
      setServerError(e instanceof ApiError ? e.message : 'Could not sign in. Please try again.')
    }
  })

  return (
    <div className="flex min-h-screen flex-col">
      <div className="flex justify-end p-4">
        <ThemeToggle />
      </div>
      <main className="flex flex-1 items-start justify-center px-4 pb-16 sm:items-center">
        <div className="w-full max-w-sm animate-slide-up">
          <div className="mb-6 text-center">
            <span className="mx-auto flex size-14 items-center justify-center rounded-2xl bg-brand text-xl font-bold text-brand-foreground shadow-sm">
              TG
            </span>
            <h1 className="mt-4 text-2xl font-semibold tracking-tight">Tavuzi Gadgets</h1>
            <p className="mt-1 text-sm text-muted">Sign in to the shop system</p>
          </div>
          <form
            onSubmit={onSubmit}
            noValidate
            className="space-y-4 rounded-2xl border border-border bg-card p-6 shadow-sm"
          >
            {serverError && (
              <p role="alert" className="rounded-xl bg-danger-soft px-3 py-2 text-sm text-danger">
                {serverError}
              </p>
            )}
            <TextField
              label="Username"
              autoComplete="username"
              autoCapitalize="none"
              autoCorrect="off"
              spellCheck={false}
              error={errors.username?.message}
              {...register('username')}
            />
            <div className="relative">
              <TextField
                label="Password"
                type={showPassword ? 'text' : 'password'}
                autoComplete="current-password"
                className="pr-16"
                error={errors.password?.message}
                {...register('password')}
              />
              <button
                type="button"
                onClick={() => setShowPassword((s) => !s)}
                aria-label={showPassword ? 'Hide password' : 'Show password'}
                className="absolute top-[1.625rem] right-1 min-h-11 rounded-lg px-3 text-xs font-medium text-muted hover:text-foreground"
              >
                {showPassword ? 'Hide' : 'Show'}
              </button>
            </div>
            <Button type="submit" busy={isSubmitting} className="w-full">
              Sign in
            </Button>
          </form>
          <p className="mt-6 text-center text-xs text-muted">Forgot your password? Ask the owner to reset it.</p>
        </div>
      </main>
    </div>
  )
}
